package com.ubirch.controllers

import com.ubirch.controllers.concerns.ControllerBase
import com.ubirch.models._
import com.ubirch.services.key.DefaultPubKeyService.PubKeyServiceException
import com.ubirch.services.key.PubKeyService
import com.ubirch.services.pm.ProtocolMessageService
import javax.inject._
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.Swagger

import scala.concurrent.ExecutionContext

@Singleton
class KeyController @Inject() (
    val swagger: Swagger,
    jFormats: Formats,
    pubKeyService: PubKeyService,
    pmService: ProtocolMessageService
)(implicit val executor: ExecutionContext)
  extends ControllerBase(pmService) {

  override protected val applicationDescription: String = "Key Controller"
  override protected implicit val jsonFormats: Formats = jFormats

  before() {
    contentType = formats("json")
  }

  get("/v1/check") {
    Simple("I survived a check")
  }

  get("/v1/deepCheck") {
    Simple("I am alive after a deepCheck")
  }

  get("/v1/pubkey/*") {

    val pubKeyId = multiParams.get("splat")
      .flatMap(_.headOption)
      .filter(_.nonEmpty)
      .getOrElse(halt(BadRequest(NOK.pubKeyError("No pubKeyId parameter found"))))

    pubKeyService.getByPubKeyId(pubKeyId)
      .map { pks =>
        pks.toList match {
          case Nil => NotFound(NOK.pubKeyError("Key not found"))
          case pk :: _ => Ok(pk)
        }

      }
      .recover {
        case e: PubKeyServiceException =>
          logger.error("1.1 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
          InternalServerError(NOK.pubKeyError("Error retrieving pub key"))
        case e: Exception =>
          logger.error("1.2 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
          InternalServerError(NOK.serverError("Sorry, something went wrong on our end"))
      }
  }

  get("/v1/pubkey/current/hardwareId/:hardwareId") {

    val hwDeviceId = params.get("hardwareId")
      .filter(_.nonEmpty)
      .getOrElse(halt(BadRequest(NOK.pubKeyError("No hardwareId parameter found"))))

    pubKeyService.getByHardwareId(hwDeviceId)
      .map { pks => Ok(pks) }
      .recover {
        case e: PubKeyServiceException =>
          logger.error("2.1 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
          InternalServerError(NOK.pubKeyError("Error retrieving pub key"))
        case e: Exception =>
          logger.error("2.2 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
          InternalServerError(NOK.serverError("Sorry, something went wrong on our end"))
      }
  }

  post("/v1/pubkey") {
    ReadBody.readJson[PublicKey]
      .async { case (pk, body) =>
        pubKeyService.create(pk, body)
          .map { key => Ok(key) }
          .recover {
            case e: PubKeyServiceException =>
              logger.error("Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.pubKeyError("Error creating pub key"))
            case e: Exception =>
              logger.error("Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.serverError("Sorry, something went wrong on our end"))
          }
      }

  }

  post("/v1/pubkey/mpack") {
    ReadBody.readMsgPack
      .async { up =>
        pubKeyService.create(up.pm, up.rawProtocolMessage)
          .map { key => Ok(key) }
          .recover {
            case e: PubKeyServiceException =>
              logger.error("Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.pubKeyError("Error creating pub key"))
            case e: Exception =>
              logger.error("Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.serverError("Sorry, something went wrong on our end"))
          }
      }

  }

  delete("/v1/pubkey") { delete }
  /**
    * This has been added since the delete method cannot be tested with a body.
    */
  patch("/v1/pubkey") { delete }

  notFound {
    logger.info("controller=KeyController route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
  }

  private def delete = {
    ReadBody.readJson[PublicKeyDelete]
      .async { case (pkd, _) =>
        pubKeyService.delete(pkd)
          .map { dr =>
            if (dr) Ok(Simple("Key deleted"))
            else BadRequest(NOK.deleteKeyError("Failed to delete public key"))
          }
          .recover {
            case e: Exception =>
              logger.error("Error deleting pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.serverError("Sorry, something went wrong on our end"))
          }
      }
  }

}
