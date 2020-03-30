package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{NOK, Simple}
import javax.inject._
import org.json4s.Formats
import org.scalatra.{CorsSupport, NotFound, Ok, ScalatraServlet}
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}


@Singleton
class InfoController @Inject() (val swagger: Swagger, jFormats: Formats) extends ScalatraServlet with NativeJsonSupport with LazyLogging with SwaggerSupport with CorsSupport {
  override protected implicit def jsonFormats: Formats = jFormats

  protected val applicationDescription = "Device-related requests."


  val getSimpleCheck: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("simpleCheck")
      summary "Welcome / Health"
      description "Check if the service is up and running"
      tags ("key-service", "health", "welcome")
    )

  get("/", operation(getSimpleCheck)) {
    Ok(Simple("Hallo, Hola, Hello, this is the ubirch identity service."))
  }

  before() {
    contentType = formats("json")
  }

  notFound {
    logger.info("controller=InfoController route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
  }

}
