package com.ubirch.controllers.concerns

import com.ubirch.models.NOK
import org.json4s.jackson
import org.scalatra.AsyncResult
import org.scalatra.json.NativeJsonSupport

import scala.concurrent.Future
import scala.util.Try

trait RequestHelpers extends NativeJsonSupport {

  def withData[T: Manifest](action: T => Any): Any = {
    parsedBody.extractOpt[T] match {
      case Some(t) => action(t)
      case None =>
        val bodyAsString = Try(jackson.compactJson(parsedBody)).getOrElse(parsedBody.toString)
        NOK.parsingError(s"Couldn't parse [$bodyAsString]")
    }
  }

  def withDataAsync[T: Manifest](action: T => Future[_]): Future[_] = {
    parsedBody.extractOpt[T] match {
      case Some(t) => action(t)
      case None =>
        val bodyAsString = Try(jackson.compactJson(parsedBody)).getOrElse(parsedBody.toString)
        Future.successful(NOK.parsingError(s"Couldn't parse [$bodyAsString]"))
    }
  }

  def async(body: => Future[_]) = {
    new AsyncResult() {
      override val is = body
    }
  }

}
