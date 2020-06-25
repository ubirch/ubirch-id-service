package com.ubirch
package controllers

import com.ubirch.controllers.concerns.{ ControllerBase, SwaggerElements }
import com.ubirch.models._
import com.ubirch.services.key.CertService
import javax.inject._
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.{ ResponseMessage, Swagger, SwaggerSupportSyntax }

import scala.concurrent.ExecutionContext

@Singleton
class CertController @Inject() (
    val swagger: Swagger,
    jFormats: Formats,
    certService: CertService
)(implicit val executor: ExecutionContext, scheduler: Scheduler) extends ControllerBase {

  override protected val applicationDescription: String = "Cert Controller"
  override protected implicit val jsonFormats: Formats = jFormats

  before() {
    contentType = formats("json")
  }

  val postCsrRegister: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[PublicKeyInfo]("CSRRegister")
      summary "Certification Signing Requests"
      description "Validates and stores Certification Signing Requests"
      parameters bodyParam[Byte]("csr request").description("The certification request").required
      tags SwaggerElements.TAG_CERT_SERVICE
      responseMessages (
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Error registering csr"),
        ResponseMessage(SwaggerElements.INTERNAL_ERROR_CODE_500, "1.2 Sorry, something went wrong on our end")
      ))

  post("/v1/csr/register", operation(postCsrRegister)) {

    asyncResult { implicit request =>
      for {
        readBody <- Task.delay(ReadBody.readCSR(certService))
        res <- certService.processCSR(readBody.extracted)
          .map(x => Ok(x))
          .onErrorHandle {
            case e: CertServiceException =>
              logger.error("1.1 Error registering CSR: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              BadRequest(NOK.crsError("Error registering csr"))
            case e: Exception =>
              logger.error("1.2 Error registering CSR: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.serverError("1.2 Sorry, something went wrong on our end"))
          }
      } yield {
        res
      }

    }

  }

  val postCertRegister: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[PublicKeyInfo]("CertRegister")
      summary "Creates a key in the identity service based on the cert"
      description "Creates a key in the identity service based on the cert"
      parameters bodyParam[Byte]("cert request").description("The X509 certificate").required
      tags SwaggerElements.TAG_CERT_SERVICE
      responseMessages (
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Error registering Cert"),
        ResponseMessage(SwaggerElements.INTERNAL_ERROR_CODE_500, "1.2 Sorry, something went wrong on our end")
      ))

  post("/v1/cert/register", operation(postCertRegister)) {

    asyncResult { implicit request =>
      for {
        readBody <- Task.delay(ReadBody.readCert(certService))
        res <- certService.processCert(readBody.extracted)
          .map(x => Ok(x))
          .onErrorHandle {
            case e: CertServiceException =>
              logger.error("1.1 Error registering Cert: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              BadRequest(NOK.certError("Error registering Cert"))
            case e: Exception =>
              logger.error("1.2 Error registering Cert: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.certError("1.2 Sorry, something went wrong on our end"))
          }
      } yield {
        res
      }

    }

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
