package com.ubirch.controllers.concerns

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.NOK
import com.ubirch.util.ProtocolHelpers
import com.ubirch.util.ProtocolHelpers.UnPacked
import org.json4s.jackson
import org.scalatra.json.NativeJsonSupport
import org.scalatra.{ AsyncResult, BadRequest }

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

trait RequestHelpers extends NativeJsonSupport with LazyLogging {

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

    def readJson[T: Manifest]: ReadBody[T] = ReadBody(Try(parsedBody.extract[T]))

    def readMsgPack[T: Manifest]: ReadBody[UnPacked[T]] =
      ReadBody(ProtocolHelpers.unpack[T](request.body))
  }

}
