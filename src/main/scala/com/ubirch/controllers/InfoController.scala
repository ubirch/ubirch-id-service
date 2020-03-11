package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import javax.inject._
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport }

import scala.concurrent.ExecutionContext

@Singleton
class InfoController @Inject() (val swagger: Swagger)(implicit val executor: ExecutionContext)
  extends ScalatraServlet
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with LazyLogging {

  override protected def applicationDescription: String = "Info"
  override protected implicit def jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/") {
    "This is info"
  }

  notFound {
    logger.info("route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound("Not found")
  }

}
