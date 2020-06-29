package com.ubirch.controllers.concerns

import java.io.{ ByteArrayInputStream, FileOutputStream }
import java.security.cert.X509Certificate
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.NOK
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.key.CertService
import com.ubirch.services.pm.ProtocolMessageService
import com.ubirch.util.ServiceMetrics
import javax.servlet.http.{ HttpServletRequest, HttpServletRequestWrapper, HttpServletResponse }
import javax.servlet.{ ReadListener, ServletInputStream }
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import org.apache.commons.codec.binary.Hex
import org.apache.commons.compress.utils.IOUtils
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.json4s.JsonAST.JValue
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.SwaggerSupport

import scala.util.Try
import scala.util.control.NoStackTrace

/**
  * Represents a customized ServletInputStream that allows to cache the body of a request.
  * This trait is very important to be able to re-consume the body in case of need.
  * @param cachedBody Represents the InputStream as bytes.
  * @param raw Represents the raw ServletInputStream
  */
class CachedBodyServletInputStream(cachedBody: Array[Byte], raw: ServletInputStream) extends ServletInputStream {

  private val cachedInputStream = new ByteArrayInputStream(cachedBody)

  override def isFinished: Boolean = cachedInputStream.available() == 0
  override def isReady: Boolean = true
  override def setReadListener(readListener: ReadListener): Unit = raw.setReadListener(readListener)

  override def read(): Int = cachedInputStream.read()
  override def read(b: Array[Byte]): Int = read(b, 0, b.length)
  override def read(b: Array[Byte], off: Int, len: Int): Int = cachedInputStream.read(b, off, len)

}

/***
 * Represents a customized HttpServletRequest that allows us to decorate the original object with extra info
 * or extra functionality.
 * Initially, it supports the re-consumption of the body stream
 * @param httpServletRequest Represents the original Request
 */
class IdentityRequest(httpServletRequest: HttpServletRequest) extends HttpServletRequestWrapper(httpServletRequest) {

  val cachedBody = IOUtils.toByteArray(httpServletRequest.getInputStream)

  override def getInputStream: ServletInputStream = {
    new CachedBodyServletInputStream(cachedBody, httpServletRequest.getInputStream)
  }
}

/**
  * Represents a Handler that creates the customized request.
  * It should be mixed it with the corresponding ScalatraServlet.
  */
trait RequestEnricher extends Handler {
  abstract override def handle(request: HttpServletRequest, res: HttpServletResponse): Unit = {
    super.handle(new IdentityRequest(request), res)
  }
}

/**
  * Represents the base for a controllers that supports the IdentityRequest
  * and adds helpers to handle async responses and body parsing and extraction.
  */
abstract class ControllerBase extends ScalatraServlet
  with RequestEnricher
  with FutureSupport
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with ServiceMetrics
  with LazyLogging {

  def actionResult(body: HttpServletRequest => Task[ActionResult])(implicit request: HttpServletRequest, scheduler: Scheduler): Task[ActionResult] = {
    for {
      _ <- Task.delay(logRequestInfo)
      res <- Task.defer(body(request))
        .onErrorHandle {
          case FailedExtractionException(_, rawBody, e) =>
            val msg = s"Couldn't parse [$rawBody] due to exception=${e.getClass.getCanonicalName} message=${e.getMessage}"
            logger.error(msg)
            BadRequest(NOK.parsingError(msg))
        }
        .onErrorHandle { e =>

          val name = e.getClass.getCanonicalName
          val cause = Try(e.getCause.getMessage).getOrElse(e.getMessage)
          logger.error("Error 0.1 ", e)
          logger.error(s"Error 0.1 exception={} message={}", name, cause)
          InternalServerError(NOK.serverError("Sorry, something happened"))

        }
    } yield {
      res
    }

  }

  def asyncResultCore(body: () => CancelableFuture[ActionResult])(implicit request: HttpServletRequest, scheduler: Scheduler): AsyncResult = {
    new AsyncResult() { override val is = body() }
  }

  def asyncResult(name: String)(body: HttpServletRequest => Task[ActionResult])(implicit request: HttpServletRequest, scheduler: Scheduler): AsyncResult = {
    asyncResultCore(() => count(name)(actionResult(body).runToFuture))
  }

  def logRequestInfo(implicit request: HttpServletRequest): Unit = {
    val path = request.getPathInfo
    val method = request.getMethod
    val headers = request.headers.toList.map { case (k, v) => k + ":" + v }.mkString(",")
    logger.info("Path[{}]:{} {}", method, path, headers)
  }

  case class ReadBody[T](extracted: T, asString: String)
  case class FailedExtractionException(message: String, body: String, throwable: Throwable) extends Exception(message, throwable) with NoStackTrace

  object ReadBody {

    def getBodyAsBytes(implicit request: HttpServletRequest): Try[(Array[Byte], String)] = for {
      bytes <- Try(IOUtils.toByteArray(request.getInputStream))
      bytesAsString <- Try(Hex.encodeHexString(bytes))
    } yield (bytes, bytesAsString)

    def getBodyAsString(implicit request: HttpServletRequest): Try[String] = Try(request.body)

    def store(bytes: Array[Byte]): Unit = {
      val date = new Date()
      val os = new FileOutputStream(s"src/main/scala/com/ubirch/curl/data_${date.getTime}.mpack")
      os.write(bytes)
      os.close()
    }

    def readJson[T: Manifest](transformF: JValue => JValue)(implicit request: HttpServletRequest): ReadBody[T] = {
      lazy val _body = getBodyAsString
      val parsed = for {
        body <- _body
        _ = logger.info("body={}", body)
        b <- Try(transformF(parsedBody).extract[T])
      } yield ReadBody(b, body)

      parsed.recover { case e => throw FailedExtractionException("Error Parsing", _body.getOrElse("No Body Found"), e) }.get
    }

    def readMsgPack(pmService: ProtocolMessageService)(implicit request: HttpServletRequest): ReadBody[ProtocolMessage] = {
      lazy val _body = getBodyAsBytes
      val parsed = for {
        body <- _body
        (bytes, bytesAsString) = body
        _ = logger.info("body={}", bytesAsString)
        // _ <- Try(store(bytes))
        unpacked <- pmService.unpackFromBytes(bytes)
      } yield ReadBody(unpacked.pm, unpacked.rawProtocolMessage)

      parsed.recover { case e => throw FailedExtractionException("Error Parsing", _body.map(_._2).getOrElse("No Body Found"), e) }.get

    }

    def readCSR(certService: CertService)(implicit request: HttpServletRequest): ReadBody[JcaPKCS10CertificationRequest] = {
      lazy val _body = getBodyAsBytes
      val parsed = for {
        body <- _body
        (bytes, bytesAsString) = body
        _ = logger.info("body={}", bytesAsString)
        // _ <- Try(store(bytes))
        maybeCSR <- certService.extractCRS(bytes)
      } yield ReadBody(maybeCSR, bytesAsString)

      parsed.recover { case e => throw FailedExtractionException("Error Parsing", _body.map(_._2).getOrElse("No Body Found"), e) }.get

    }

    def readCert(certService: CertService)(implicit request: HttpServletRequest): ReadBody[X509Certificate] = {
      lazy val _body = getBodyAsBytes
      val parsed = for {
        body <- _body
        (bytes, bytesAsString) = body
        _ = logger.info("body={}", bytesAsString)
        // _ <- Try(store(bytes))
        maybeCert <- certService.extractCert(bytes)
      } yield ReadBody(maybeCert, bytesAsString)

      parsed.recover { case e => throw FailedExtractionException("Error Parsing", _body.map(_._2).getOrElse("No Body Found"), e) }.get

    }

  }

}
