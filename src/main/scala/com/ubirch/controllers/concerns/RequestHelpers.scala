package com.ubirch.controllers.concerns

import com.ubirch.models.NOK
import org.scalatra.json.NativeJsonSupport

trait RequestHelpers extends NativeJsonSupport {

  def withData[T: Manifest](action: T => Any): Any = {
    parsedBody.extractOpt[T] match {
      case Some(t) => action(t)
      case None => NOK.parsingError(s"Couldn't parse [$parsedBody]")
    }
  }

}
