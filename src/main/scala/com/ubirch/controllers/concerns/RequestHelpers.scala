package com.ubirch.controllers.concerns

import com.ubirch.models.NOK
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.protocol.codec.MsgPackProtocolDecoder
import org.apache.commons.codec.binary.Hex
import org.json4s.jackson
import org.scalatra.AsyncResult
import org.scalatra.json.NativeJsonSupport

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import org.json4s.jackson.JsonMethods._

trait RequestHelpers extends NativeJsonSupport {

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
            Future.successful(NOK.parsingError(s"Couldn't parse [$bodyAsString] due to exception=${e.getClass.getCanonicalName} message=${e.getMessage}"))
      }
      asyncResult(res)
    }

  }

  object ReadBody {

    def read[T: Manifest]: ReadBody[T] = ReadBody(Try(parsedBody.extract[T]))
    def readMsgPack[T: Manifest]: ReadBody[(T, ProtocolMessage, String)] = {
      ReadBody {
        for {
          bodyString <- Try(request.body)
          bodyBytes <- Try(Hex.decodeHex(bodyString))
          decoder = MsgPackProtocolDecoder.getDecoder
          pm <- Try(decoder.decode(bodyBytes))
          payloadJsonNode = pm.getPayload
          payloadJValue <- Try(fromJsonNode(payloadJsonNode))
          pt <- Try(payloadJValue.extract[T])
        } yield {
          (pt, pm, bodyString)
        }
      }
    }
  }

}
