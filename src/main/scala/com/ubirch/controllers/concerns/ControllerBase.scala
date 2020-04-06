package com.ubirch.controllers.concerns

import java.io.{ ByteArrayInputStream, FileOutputStream }
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.NOK
import com.ubirch.services.pm.ProtocolMessageService
import com.ubirch.services.pm.ProtocolMessageService.UnPacked
import javax.servlet.http.{ HttpServletRequest, HttpServletRequestWrapper, HttpServletResponse }
import javax.servlet.{ ReadListener, ServletInputStream }
import org.apache.commons.codec.binary.Hex
import org.apache.commons.compress.utils.IOUtils
import org.json4s.JsonAST.JValue
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.SwaggerSupport

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

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
  override def read(b: Array[Byte], off: Int, len: Int) = cachedInputStream.read(b, off, len)

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
  * @param pmService Represents teh Protocol Message Service that knows how to decode bodies into
  *                  Protocol Messages.
  */
abstract class ControllerBase(pmService: ProtocolMessageService) extends ScalatraServlet
  with RequestEnricher
  with FutureSupport
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with LazyLogging {

  def asyncResult(body: HttpServletRequest => Future[_])(implicit request: HttpServletRequest): AsyncResult = {
    new AsyncResult() {
      override val is = {
        val _body = try {
          body(request)
        } catch {
          case e: Exception =>
            val name = e.getClass.getCanonicalName
            val cause = Try(e.getCause.getMessage).getOrElse(e.getMessage)
            logger.error("Error 0.1 ", e)
            logger.error(s"Error 0.1 exception={} message={}", name, cause)
            Future(InternalServerError(NOK.serverError("Sorry, something happened")))
        }
        _body.recover {
          case e: Exception =>
            val name = e.getClass.getCanonicalName
            val cause = Try(e.getCause.getMessage).getOrElse(e.getMessage)
            logger.error("Error 0.2 ", e)
            logger.error(s"Error 0.2 exception={} message={}", name, cause)
            InternalServerError(NOK.serverError("Sorry, something happened"))
        }
      }
    }
  }

  def logRequestInfo(implicit request: HttpServletRequest) = {
    val path = request.getPathInfo
    val headers = request.headers.toList.map { case (k, v) => k + ":" + v }.mkString(",")
    logger.info("Path:{} {}", path, headers)
  }

  case class ReadBody[T] private (body: Try[T], rawBody: String, mg: Option[Future[_]]) {

    def map[B](f: T => B): ReadBody[B] = copy(body = body.map(f))

    def async(action: T => Future[_])(implicit request: HttpServletRequest): ReadBody[T] = {
      val res = body match {
        case Success(t) => action(t)
        case Failure(e) =>
          Future {
            val msg = s"Couldn't parse [$rawBody] due to exception=${e.getClass.getCanonicalName} message=${e.getMessage}"
            logger.error(msg)
            BadRequest(NOK.parsingError(msg))
          }
      }

      copy(mg = Some(res))

    }

    def run(implicit request: HttpServletRequest): AsyncResult = {
      mg.map { g =>
        asyncResult(_ => g)
      }.getOrElse {
        asyncResult(_ => Future.successful(InternalServerError(NOK.serverError("No action body to run"))))
      }

    }

  }

  object ReadBody {

    def store(bytes: Array[Byte]) = {
      val date = new Date()
      val os = new FileOutputStream(s"src/main/scala/com/ubirch/curl/data_${date.getTime}.mpack")
      os.write(bytes)
      os.close()
    }

    def readJson[T: Manifest](transformF: JValue => JValue)(implicit request: HttpServletRequest): ReadBody[(T, String)] = {

      val rawBody = Try(request.body)

      val body = for {
        body <- rawBody
        _ = logger.info("body={}", body)
        b <- Try(transformF(parsedBody).extract[T])
      } yield (b, body)

      ReadBody(body, rawBody.getOrElse("No Body Found"))
    }

    def apply[T](body: Try[T], rawBody: String): ReadBody[T] = new ReadBody[T](body, rawBody, None)

    def readMsgPack(implicit request: HttpServletRequest): ReadBody[UnPacked] = {

      val rawBody = for {
        bytes <- Try(IOUtils.toByteArray(request.getInputStream))
        bytesAsString <- Try(Hex.encodeHexString(bytes))
      } yield (bytes, bytesAsString)

      val body = for {
        _body <- rawBody
        (bytes, bytesAsString) = _body
        _ = logger.info("body={}", bytesAsString)
        // _ <- Try(store(bytes))
        unpacked <- pmService.unpackFromBytes(bytes)
      } yield unpacked

      ReadBody[UnPacked](body, rawBody.map { _._2 }.getOrElse("No Body Found"))

    }

  }

}
