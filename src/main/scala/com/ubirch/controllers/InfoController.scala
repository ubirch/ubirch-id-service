package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ NOK, Simple }
import javax.inject._
import org.json4s.Formats
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }
import org.scalatra.{ CorsSupport, NotFound, Ok, ScalatraServlet }

/**
  * Represents a simple controller for the base path "/"
  * @param swagger Represents the Swagger Engine.
  * @param jFormats Represents the json formats for the system.
  */

@Singleton
class InfoController @Inject() (val swagger: Swagger, jFormats: Formats) extends ScalatraServlet with NativeJsonSupport with LazyLogging with SwaggerSupport with CorsSupport {
  override protected implicit def jsonFormats: Formats = jFormats

  protected val applicationDescription = "Device-related requests."

  val getSimpleCheck: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("simpleCheck")
      summary "Welcome / Health")

  get("/", operation(getSimpleCheck)) {
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
