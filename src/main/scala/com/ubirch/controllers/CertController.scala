package com.ubirch.controllers

import java.util.Date

import com.ubirch.controllers.concerns.ControllerBase
import com.ubirch.models._
import com.ubirch.services.key.{ CertService, PubKeyService }
import com.ubirch.util.{ CertUtil, PublicKeyUtil }
import javax.inject._
import org.bouncycastle.util.encoders.Base64
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.Swagger

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

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
            curve <- PublicKeyUtil.associateCurve(algo)
            pubKey <- pubKeyService.recreatePublicKey(csr.getPublicKey.getEncoded, curve)
            pubKeyAsBase64 <- Try(Base64.toBase64String(pubKey.getPublicKey.getEncoded))
            sigAsBase64 <- Try(Base64.toBase64String(csr.getSignature))
          } yield {
            Ok(PublicKeyInfo(algo, new Date(), uuid.toString, pubKeyAsBase64, pubKeyAsBase64, None, new Date()))
          }).recover {
            case e =>
              e.printStackTrace()
              logger.error(e.getMessage)
              BadRequest(NOK.crsError("No common name as uuid a found."))
          }.get

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
