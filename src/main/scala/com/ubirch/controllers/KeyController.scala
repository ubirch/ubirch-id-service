package com.ubirch.controllers

import com.ubirch.controllers.concerns.ControllerBase
import com.ubirch.models._
import com.ubirch.services.key.DefaultPubKeyService.PubKeyServiceException
import com.ubirch.services.key.PubKeyService
import com.ubirch.services.pm.ProtocolMessageService
import javax.inject._
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.{ ResponseMessage, Swagger, SwaggerSupportSyntax }

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

  val getV1Check: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getV1Check")
      summary "Welcome / Health"
      description "Check if service is up and running"
      tags (SwaggerElements.TAG_KEY_SERVICE, SwaggerElements.TAG_KEY_REGISTRY, SwaggerElements.TAG_WELCOME, SwaggerElements.TAG_HEALTH)
      responseMessages (
      ResponseMessage(SwaggerElements.OK_CODE_200, "Successful response; returns status object with welcome message"),
      ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Not successful response")
    ))

  get("/v1/check", operation(getV1Check)) {
    Simple("I survived a check")
  }

  val getV1DeepCheck: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getV1Check")
      summary "health monitor deep check"
      description "allows a deep check of the service"
      tags (SwaggerElements.TAG_KEY_SERVICE, SwaggerElements.TAG_KEY_REGISTRY, SwaggerElements.TAG_WELCOME, SwaggerElements.TAG_HEALTH)
      responseMessages (
      ResponseMessage(SwaggerElements.OK_CODE_200, "everything is fine"),
      ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "something is not fine")
    ))

  get("/v1/deepCheck", operation(getV1DeepCheck)) {
    Simple("I am alive after a deepCheck")
  }

  val getV1PubKeyPubKey: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getV1PubKeyHardwareId")
      summary "updates public key"
      description "updates the given public key found by pubKeyID in the key registry with the given data; the public key must exist already"
      tags (SwaggerElements.TAG_KEY_SERVICE, SwaggerElements.TAG_KEY_REGISTRY)
      parameters pathParam[String]("hardwareId").description("hardwareId for which to search for currently valid public keys").required
      responseMessages (
      ResponseMessage(SwaggerElements.OK_CODE_200, "Successful response; returns an array of currently valid public keys"),
      ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "No successful response")
    ))

  get("/v1/pubkey/*", operation(getV1PubKeyPubKey)) {

    val pubKeyId = multiParams.get("splat")
      .flatMap(_.headOption)
      .filter(_.nonEmpty)
      .getOrElse(halt(BadRequest(NOK.pubKeyError("No pubKeyId parameter found in path"))))

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

  val getV1CurrentHardwareId: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getV1CurrentHardwareId")
      summary "query only the currently valid public keys"
      description "query the currently valid public keys based on a hardwareId"
      tags (SwaggerElements.TAG_KEY_SERVICE, SwaggerElements.TAG_KEY_REGISTRY)
      parameters pathParam[String]("hardwareId").description("hardwareId for which to search for currently valid public keys").required
      responseMessages (
      ResponseMessage(SwaggerElements.OK_CODE_200, "Successful response; returns an array of currently valid public keys"),
      ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "No successful response")
    ))
  get("/v1/pubkey/current/hardwareId/*", operation(getV1CurrentHardwareId)) {

    val hwDeviceId = multiParams.get("splat")
      .flatMap(_.headOption)
      .filter(_.nonEmpty)
      .getOrElse(halt(BadRequest(NOK.pubKeyError("No hardwareId parameter found in path"))))

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

  val postV1PubKey: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("postV1PubKey")
      summary "stores new public key"
      description "stores the given public key with its unique pubKeyID"
      tags (SwaggerElements.TAG_KEY_SERVICE, SwaggerElements.TAG_KEY_REGISTRY)
      parameters bodyParam[String]("pubkey").description("the new public key object with the pubKey that should be stored for the unique pubKeyId - also part of the pub key object - in the key registry to be able to find the public key; pubKeyId may not exist already").required
      responseMessages (
        ResponseMessage(SwaggerElements.OK_CODE_200, "Successful response; returns created public key"),
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "No successful response", Option(SwaggerElements.ERROR_RESPONSE))
      ))

  post("/v1/pubkey", operation(postV1PubKey)) {
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

  val postV1PubKeyMsgPack: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("postV1PubKeyMsgPack")
      summary "stores new public key in msgpack format"
      description "stores the given public key with its unique pubKeyID"
      tags (SwaggerElements.TAG_KEY_SERVICE, SwaggerElements.TAG_KEY_REGISTRY, SwaggerElements.TAG_MSG_PACK)
      consumes "application/octet-stream"
      produces "application/json"
      parameters bodyParam[String]("pubkey").description("a mgspack representation of the public key registration. The format follows both the json structure (with binary values instead of encoded) as well as the [ubirch-protocol](https://github.com/ubirch/ubirch-protocol#key-registration) format.").required
      responseMessages (
        ResponseMessage(SwaggerElements.OK_CODE_200, "Successful response; returns created public key"),
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "No successful response")
      ))

  post("/v1/pubkey/mpack", operation(postV1PubKeyMsgPack)) {
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

  val deleteV1PubKey: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("deleteV1PubKey")
      summary "delete a public key"
      description "delete a public key"
      tags (SwaggerElements.TAG_KEY_SERVICE, SwaggerElements.TAG_KEY_REGISTRY)
      parameters bodyParam[String]("publicKeyToDelete").description("the public key to delete including signature of publicKey field").example("{\n  \"publicKey\": \"MC0wCAYDK2VkCgEBAyEAxUQcVYd3dt7jAJBtulZoz8QDftnND2X5//ittJ7XAhs=\",\n  \"signature\": \"/kED2IJKCAyro/szRoylAwaEx3E8U2OFI8zHNB8cEHdxy8JtgoR81YL1X/o7Xzkz30eqNjIsWfhmQNdaIma2Aw==\"\n}").required
      responseMessages (
        ResponseMessage(SwaggerElements.OK_CODE_200, "delete was successful or key did not exist"),
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "signature was invalid")
      ))

  delete("/v1/pubkey", operation(deleteV1PubKey)) { delete }
  /**
    * This has been added since the delete method cannot be tested with a body.
    */
  patch("/v1/pubkey", operation(deleteV1PubKey)) { delete }

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
