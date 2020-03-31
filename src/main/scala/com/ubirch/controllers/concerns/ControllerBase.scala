package com.ubirch.controllers.concerns

import java.io.FileOutputStream
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.NOK
import com.ubirch.services.pm.ProtocolMessageService
import com.ubirch.services.pm.ProtocolMessageService.UnPacked
import javax.servlet.http.HttpServletRequest
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
      override val is = body()
    }
  }

  case class ReadBody[T](body: Try[T]) {

    def map[B](f: T => B): ReadBody[B] = copy(body = body.map(f))

    def async(action: T => Future[_]): AsyncResult = {
      val res = body match {
        case Success(t) => () => action(t)
        case Failure(e) =>
          () =>
            val bodyAsString = Try(jackson.compactJson(parsedBody)).getOrElse(parsedBody.toString)
            val msg = s"Couldn't parse [$bodyAsString] due to exception=${e.getClass.getCanonicalName} message=${e.getMessage}"
            logger.error(msg)
            Future.successful(BadRequest(NOK.parsingError(msg)))
      }
      asyncResult(res)
    }

  }

  object ReadBody {

    def store(bytes: Array[Byte]) = {
      val date = new Date()
      val os = new FileOutputStream(s"src/main/scala/com/ubirch/curl/data_${date.getTime}.mpack")
      os.write(bytes)
      os.close()
    }

    def readJson[T: Manifest](implicit request: HttpServletRequest): ReadBody[(T, String)] = ReadBody(for {
      body <- Try(request.body)
      b <- Try(parsedBody.extract[T])
    } yield (b, body))

    def readMsgPack(implicit request: HttpServletRequest): ReadBody[UnPacked] =
      ReadBody[UnPacked](for {
        bytes <- Try(IOUtils.toByteArray(request.getInputStream))
        // _ <- Try(store(bytes))
        unpacked <- pmService.unpackFromBytes(bytes)
      } yield unpacked)

  }

}
