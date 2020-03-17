package com.ubirch.controllers.concerns

import com.ubirch.models.NOK
import org.json4s.jackson
import org.scalatra.AsyncResult
import org.scalatra.json.NativeJsonSupport

import scala.concurrent.Future
import scala.util.Try

trait RequestHelpers extends NativeJsonSupport {

  case class ReadBody[T](body: Option[T]) {

    def map[B](f: T => B) = copy(body = body.map(f))

    def async(action: T => Future[_]) = {
      body match {
        case Some(t) => action(t)
        case None =>
          val bodyAsString = Try(jackson.compactJson(parsedBody)).getOrElse(parsedBody.toString)
          Future.successful(NOK.parsingError(s"Couldn't parse [$bodyAsString]"))
      }
    }

  }

  object ReadBody {
    def read[T: Manifest] = {
      ReadBody(parsedBody.extractOpt[T])
    }
  }

  def readBody[T: Manifest](action: T => Any): Any = {
    parsedBody.extractOpt[T] match {
      case Some(t) => action(t)
      case None =>
        val bodyAsString = Try(jackson.compactJson(parsedBody)).getOrElse(parsedBody.toString)
        NOK.parsingError(s"Couldn't parse [$bodyAsString]")
    }
  }

  def readBodyAsync[T: Manifest](action: T => Future[_]): AsyncResult = async {
    parsedBody.extractOpt[T] match {
      case Some(t) => action(t)
      case None =>
        val bodyAsString = Try(jackson.compactJson(parsedBody)).getOrElse(parsedBody.toString)
        Future.successful(NOK.parsingError(s"Couldn't parse [$bodyAsString]"))
    }
  }

  def async(body: => Future[_]): AsyncResult = {
    new AsyncResult() {
      override val is = body
    }
  }

}
