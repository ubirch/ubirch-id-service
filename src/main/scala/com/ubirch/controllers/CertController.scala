package com.ubirch.controllers

import com.ubirch.controllers.concerns.ControllerBase
import com.ubirch.models._
import com.ubirch.services.key.CertService
import javax.inject._
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.Swagger

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class CertController @Inject() (
    val swagger: Swagger,
    jFormats: Formats,
    certService: CertService
)(implicit val executor: ExecutionContext) extends ControllerBase {

  override protected val applicationDescription: String = "Cert Controller"
  override protected implicit val jsonFormats: Formats = jFormats

  before() {
    contentType = formats("json")
  }

  post("/v1/csr/register") {

    logRequestInfo
    ReadBody.readCSR(certService).async { csr =>

      Future.successful("Cert is valid")

    }.run

  }

  notFound {
    logger.info("controller=CertController route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
  }

  error {
    case e =>
      logger.error("error :=", e)
      contentType = formats("json")
      logRequestInfo
      halt(BadRequest(NOK.serverError("There was an error. Please try again.")))
  }

}
