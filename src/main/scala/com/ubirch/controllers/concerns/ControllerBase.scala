package com.ubirch.controllers.concerns

import java.io.FileOutputStream
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.NOK
import com.ubirch.services.pm.ProtocolMessageService
import com.ubirch.services.pm.ProtocolMessageService.UnPacked
import javax.servlet.http.HttpServletRequest
import org.apache.commons.codec.binary.Hex
import org.apache.commons.compress.utils.IOUtils
import org.json4s.jackson
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.SwaggerSupport

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

abstract class ControllerBase(pmService: ProtocolMessageService) extends ScalatraServlet
  with FutureSupport
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with LazyLogging {

  private def asyncResult(body: () => Future[_]): AsyncResult = {
    new AsyncResult() {
      override val is = body().recover {
        case e =>
          logger.error(s"Error 0. exception={} message={}", e.getClass.getCanonicalName, e.getCause.getMessage)
          InternalServerError(NOK.serverError("Sorry, something happened"))
      }
    }
  }

  case class ReadBody[T] private (body: Try[T], rawBody: String, mg: Option[() => Future[_]]) {

    def map[B](f: T => B): ReadBody[B] = copy(body = body.map(f))

    def async(action: T => Future[_])(implicit request: HttpServletRequest): ReadBody[T] = {
      val res = body match {
        case Success(t) => () => action(t)
        case Failure(e) =>
          () =>
            Future {
              val msg = s"Couldn't parse [$rawBody] due to exception=${e.getClass.getCanonicalName} message=${e.getMessage}"
              logger.error(msg)
              BadRequest(NOK.parsingError(msg))
            }
      }

      copy(mg = Some(res))

    }

    def run: AsyncResult = {
      mg.map { g =>
        asyncResult(g)
      }.getOrElse {
        asyncResult(() => Future.successful(InternalServerError(NOK.serverError("No body to run"))))
      }

    }

  }

  object ReadBody {

    def apply[T](body: Try[T], rawBody: String): ReadBody[T] = new ReadBody[T](body, rawBody, None)

    def store(bytes: Array[Byte]) = {
      val date = new Date()
      val os = new FileOutputStream(s"src/main/scala/com/ubirch/curl/data_${date.getTime}.mpack")
      os.write(bytes)
      os.close()
    }

    def readJson[T: Manifest](implicit request: HttpServletRequest): ReadBody[(T, String)] = {

      val rawBody = Try(request.body)

      val body = for {
        body <- rawBody
        _ = logger.info("body={}", body)
        b <- Try(parsedBody.extract[T])
      } yield (b, body)

      ReadBody(body, rawBody.getOrElse("No Body Found"))
    }

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
