package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.controllers.concerns.RequestHelpers
import com.ubirch.models.PublicKey
import javax.inject._
import org.json4s.Formats
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport }

import scala.concurrent.ExecutionContext

@Singleton
class KeyController @Inject() (val swagger: Swagger, jFormats: Formats)(implicit val executor: ExecutionContext)
  extends ScalatraServlet
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with RequestHelpers
  with LazyLogging {

  override protected val applicationDescription: String = "Key Controller"
  override protected implicit val jsonFormats: Formats = jFormats

  before() {
    contentType = formats("json")
  }

  get("/v1/pubkey/:id") {
    "This is a key " + params.get("id")
  }

  post("/v1/pubkey") {
    withData[PublicKey] { pk =>
      pk
    }
  }

  delete("/v1/pubkey") {
    "This was an key" + params.get("id")
  }

  notFound {
    logger.info("route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound("Not found")
  }

}
