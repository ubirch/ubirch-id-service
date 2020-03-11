package com.ubirch.controllers.concerns

import com.ubirch.models.NOK
import org.json4s.jackson
import org.scalatra.json.NativeJsonSupport

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

}
