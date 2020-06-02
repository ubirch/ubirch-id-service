package com.ubirch.controllers

import com.ubirch.controllers.concerns.ControllerBase
import com.ubirch.models._
import com.ubirch.services.key.{ CertService, PubKeyService }
import com.ubirch.util.CertUtil
import javax.inject._
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.Swagger

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class CertController @Inject() (
    val swagger: Swagger,
    jFormats: Formats,
    pubKeyService: PubKeyService,
    certService: CertService
)(implicit val executor: ExecutionContext) extends ControllerBase {

  override protected val applicationDescription: String = "Cert Controller"
  override protected implicit val jsonFormats: Formats = jFormats

  before() {
    contentType = formats("json")
  }

  post("/v1/csr/register") {

    logRequestInfo

    ReadBody.readCSR(certService).async { mcsr =>

      val res = mcsr match {
        case Some(csr) =>

          (for {
            uuid <- CertUtil.CNAsUUID(csr.getSubject)
            algo <- CertUtil.algorithmName(csr.getSignatureAlgorithm)
          } yield {
            Simple(uuid.toString + " with " + algo)
          }).getOrElse {
            BadRequest(NOK.crsError("No common name as uuid a found."))
          }

        case None => BadRequest(NOK.crsError("The certificate request is invalid."))
      }

      Future.successful(res)

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
