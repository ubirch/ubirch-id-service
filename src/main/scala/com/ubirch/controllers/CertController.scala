package com.ubirch.controllers

import com.ubirch.controllers.concerns.ControllerBase
import com.ubirch.models._
import com.ubirch.services.key.CertService
import com.ubirch.services.key.DefaultCertService.CertServiceException
import javax.inject._
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.Swagger

import scala.concurrent.ExecutionContext

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

      certService.processCSR(csr)
        .map(x => Ok(x))
        .recover {
          case e: CertServiceException =>
            logger.error("1.1 Error registering CSR: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            BadRequest(NOK.crsError("Error registering csr"))
          case e: Exception =>
            logger.error("1.2 Error registering CSR: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            InternalServerError(NOK.serverError("1.2 Sorry, something went wrong on our end"))
        }

    }.run

  }

  post("/v1/cert/register") {

    logRequestInfo

    ReadBody.readCert(certService).async { cert =>

      certService.processCert(cert)
        .map(x => Ok(x))
        .recover {
          case e: CertServiceException =>
            logger.error("1.1 Error registering Cert: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            BadRequest(NOK.crsError("Error registering csr"))
          case e: Exception =>
            logger.error("1.2 Error registering Cert: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            InternalServerError(NOK.serverError("1.2 Sorry, something went wrong on our end"))
        }

    }.run

  }

  notFound {
    logger.info("controller=CertController route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
  }

  error {
    case e =>
      logger.error("error cert_controller :=", e)
      contentType = formats("json")
      logRequestInfo
      halt(BadRequest(NOK.serverError("There was an error. Please try again.")))
  }

}
