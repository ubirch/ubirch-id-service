package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ NOK, Simple }
import javax.inject._
import org.json4s.Formats
import org.scalatra.json.NativeJsonSupport
import org.scalatra.{ NotFound, Ok, ScalatraServlet }

@Singleton
class InfoController @Inject() (jFormats: Formats) extends ScalatraServlet with NativeJsonSupport with LazyLogging {
  override protected implicit def jsonFormats: Formats = jFormats

  get("/") {
    Ok(Simple("Hallo, Hola, Hello, Salut, Hej, this is the Ubirch Identity Service."))
  }

  before() {
    contentType = formats("json")
  }

  notFound {
    logger.info("controller=InfoController route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
  }

}
