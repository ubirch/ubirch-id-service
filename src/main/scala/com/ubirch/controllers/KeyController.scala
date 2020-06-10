package com.ubirch.controllers

import com.ubirch.controllers.concerns.{ ControllerBase, SwaggerElements }
import com.ubirch.models._
import com.ubirch.services.key.DefaultPubKeyService.PubKeyServiceException
import com.ubirch.services.key.PubKeyService
import com.ubirch.services.pm.ProtocolMessageService
import com.ubirch.util.DateUtil
import javax.inject._
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.http.BadMessageException
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.{ ResponseMessage, Swagger, SwaggerSupportSyntax }

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents a controller for managing the http requests for pub key management
  *
  * @param swagger       Represents the Swagger Engine.
  * @param jFormats      Represents the json formats for the system.
  * @param pubKeyService Represents the internal service/component that knows how to work with keys.
  * @param pmService     Represents teh Protocol Message Service that knows how to decode bodies into
  *                      Protocol Messages.
  * @param executor      Represents the execution context for async processes.
  */

@Singleton
class KeyController @Inject() (
    val swagger: Swagger,
    jFormats: Formats,
    pubKeyService: PubKeyService,
    pmService: ProtocolMessageService
)(implicit val executor: ExecutionContext) extends ControllerBase {

  override protected val applicationDescription: String = "Key Controller"
  override protected implicit val jsonFormats: Formats = jFormats

  before() {
    contentType = formats("json")
  }

  val getV1Check: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getV1Check")
      summary "Welcome / Health"
      description "Check if KeyController service is up and running"
      tags SwaggerElements.TAG_HEALTH
      responseMessages ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Not successful response"))

  get("/v1/check", operation(getV1Check)) {
    logRequestInfo
    Simple("I survived a check")
  }

  val getV1DeepCheck: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getV1DeepCheck")
      summary "Health monitor deep check"
      description "allows a deep check of the service"
      tags SwaggerElements.TAG_HEALTH
      responseMessages ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "something is not fine"))

  get("/v1/deepCheck", operation(getV1DeepCheck)) {

    asyncResult { implicit request =>
      logRequestInfo
      pubKeyService.getSome()
        //We use a BooleanList Response to keep backwards compatibility with clients
        .map(_ => BooleanListResponse.OK("I am alive after a deepCheck @ " + DateUtil.nowUTC.toString()))
        .recover {
          case e: Exception =>
            logger.error("1.2 Error retrieving some pub keys: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            InternalServerError(NOK.serverError("1.2 Sorry, something went wrong on our end"))
        }
    }
  }

  val getV1PubKeyPubKey: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getV1PubKeyPubKey")
      summary "Retrieves public key"
      description "retrieves the given public key found by pubKeyID in the key registry with the given data; the public key must exist already"
      tags SwaggerElements.TAG_KEY_SERVICE
      parameters pathParam[String]("pubkey").description("public key for which to search for currently valid public keys").required
      responseMessages (
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "No pubKeyId parameter found in path"),
        ResponseMessage(SwaggerElements.NOT_FOUND_CODE_404, "Key not found")
      ))

  /**
    * This route is defined to handle the case that a pubkey contains a "/" and is not URL encoded
    * The  get("/v1/pubkey/:pubkey") road is still kept to be able to have the swagger documentation available
    * because swagger doesn't support splat parameters.
    */
  get("/v1/pubkey/*") {
    asyncResult { implicit request =>
      val pubKey = params.getOrElse("splat", halt(BadRequest(NOK.pubKeyError("No pubKeyId parameter found in path"))))
      handlePubKeyId(pubKey)
    }
  }

  get("/v1/pubkey/:pubkey", operation(getV1PubKeyPubKey)) {
    asyncResult { implicit request =>
      val pubkey = params.getOrElse("pubkey", halt(BadRequest(NOK.pubKeyError("No pubKeyId parameter found in path"))))
      handlePubKeyId(pubkey)
    }
  }

  val getV1CurrentHardwareId: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("getV1CurrentHardwareId")
      summary "Queries all currently valid public keys for this hardwareId"
      description "queries all currently valid public keys based on the hardwareId"
      tags SwaggerElements.TAG_KEY_SERVICE
      parameters pathParam[String]("hardwareId").description("hardwareId for which to search for currently valid public keys")
      responseMessages (
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "No hardwareId parameter found in path"),
        ResponseMessage(SwaggerElements.INTERNAL_ERROR_CODE_500, "Sorry, something went wrong on our end")
      ))

  /**
    * This route is defined to handle the case that a pubkey contains a "/" and is not URL encoded
    * The  get("/v1/pubkey/current/hardwareId/:hardwareId") road is still kept to be able to have the swagger documentation available
    * because swagger doesn't support splat parameters.
    */
  get("/v1/pubkey/current/hardwareId/*") {
    asyncResult { implicit request =>
      val hardwareId = params.getOrElse("splat", halt(BadRequest(NOK.pubKeyError("No hardwareId parameter found in path"))))
      handlePubKeyCurrentHardwareId(hardwareId)
    }
  }

  get("/v1/pubkey/current/hardwareId/:hardwareId", operation(getV1CurrentHardwareId)) {
    asyncResult { implicit request =>
      val hardwareId = params.getOrElse("hardwareId", halt(BadRequest(NOK.pubKeyError("No hardwareId parameter found in path"))))
      handlePubKeyCurrentHardwareId(hardwareId)
    }
  }

  val postV1PubKey: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("postV1PubKey")
      summary "Stores new public key"
      description "stores the given public key with its unique pubKeyID"
      tags SwaggerElements.TAG_KEY_SERVICE
      parameters bodyParam[String]("pubkey").description("the new public key object with the pubKey that should be stored for the unique pubKeyId - also part of the pub key object - in the key registry to be able to find the public key; pubKeyId may not exist already")
      responseMessages (
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Error creating pub key"),
        ResponseMessage(SwaggerElements.INTERNAL_ERROR_CODE_500, "Sorry, something went wrong on our end")
      ))

  post("/v1/pubkey", operation(postV1PubKey)) {

    logRequestInfo

    ReadBody.readJson[PublicKey](PublicKeyInfo.checkPubKeyId)
      .async { case (pk, body) =>
        pubKeyService.create(pk, body)
          .map { key => Ok(key) }
          .recover {
            case e: PubKeyServiceException =>
              logger.error("1.1 Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              BadRequest(NOK.pubKeyError("Error creating pub key"))
            case e: Exception =>
              logger.error("1.2 Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.serverError("1.2 Sorry, something went wrong on our end"))
          }
      }.run

  }

  val postV1PubKeyMsgPack: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("postV1PubKeyMsgPack")
      summary "Stores new public key received as msgpack format"
      description "stores the given public key with its unique pubKeyID"
      tags SwaggerElements.TAG_KEY_SERVICE
      consumes "application/octet-stream"
      produces "application/json"
      parameters bodyParam[String]("pubkey").description("a mgspack representation of the public key registration. The format follows both the json structure (with binary values instead of encoded) as well as the [ubirch-protocol](https://github.com/ubirch/ubirch-protocol#key-registration) format.")
      responseMessages (
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Error creating pub key"),
        ResponseMessage(SwaggerElements.INTERNAL_ERROR_CODE_500, "Sorry, something went wrong on our end")
      ))

  post("/v1/pubkey/mpack", operation(postV1PubKeyMsgPack)) {

    logRequestInfo

    ReadBody.readMsgPack(pmService)
      .async { up =>
        pubKeyService.create(up.pm, up.rawProtocolMessage)
          .map { key => Ok(key) }
          .recover {
            case e: PubKeyServiceException =>
              logger.error("2.1 Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              BadRequest(NOK.pubKeyError("Error creating pub key"))
            case e: Exception =>
              logger.error("2.2 Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.serverError("2.2 Sorry, something went wrong on our end"))
          }
      }.run

  }

  val deleteV1PubKey: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("deleteV1PubKey")
      summary "Deletes a public key"
      description "deletes a public key"
      tags SwaggerElements.TAG_KEY_SERVICE
      parameters bodyParam[String]("publicKeyToDelete").description("the public key to delete including signature of publicKey field") //.example("{\n  \"publicKey\": \"MC0wCAYDK2VkCgEBAyEAxUQcVYd3dt7jAJBtulZoz8QDftnND2X5//ittJ7XAhs=\",\n  \"signature\": \"/kED2IJKCAyro/szRoylAwaEx3E8U2OFI8zHNB8cEHdxy8JtgoR81YL1X/o7Xzkz30eqNjIsWfhmQNdaIma2Aw==\"\n}").required
      responseMessages (
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Failed to delete public key"),
        ResponseMessage(SwaggerElements.INTERNAL_ERROR_CODE_500, "Sorry, something went wrong on our end")
      ))

  delete("/v1/pubkey", operation(deleteV1PubKey)) {
    delete
  }
  /**
    * This has been added since the delete method cannot be tested with a body.
    */
  patch("/v1/pubkey") {
    delete
  }

  notFound {
    logger.info("controller=KeyController route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
  }

  error {
    case e: BadMessageException =>
      logger.error("bad_message :=", e)
      contentType = formats("json")
      logRequestInfo
      val path = request.getPathInfo

      val octet = "application/octet-stream"

      if (path == "/v1/pubkey/mpack" && (
        request.header("Content-Type").getOrElse("") != octet ||
        request.header("content-type").getOrElse("") != octet
      )) {

        logger.error(ReadBody.readMsgPack(pmService).toString)
        halt(BadRequest(NOK.parsingError("Bad Content Type. I am expecting =" + octet)))
      } else {
        halt(BadRequest(NOK.serverError("Bad message")))
      }
    case e =>
      logger.error("error key_controller :=", e)
      contentType = formats("json")
      logRequestInfo
      halt(BadRequest(NOK.serverError("There was an error. Please try again.")))
  }

  private def handlePubKeyId(pubKeyId: String)(implicit request: HttpServletRequest): Future[ActionResult] = {
    for {
      _ <- Future(logRequestInfo)
      res <- pubKeyService.getByPubKeyId(pubKeyId)
        .map { pks =>
          pks.toList match {
            case Nil => NotFound(NOK.pubKeyError("Key not found"))
            case pk :: _ => Ok(pk)
          }
        }
        .recover {
          case e: PubKeyServiceException =>
            logger.error("1.1 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            BadRequest(NOK.pubKeyError("Error retrieving pub key"))
          case e: Exception =>
            logger.error("1.2 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            InternalServerError(NOK.serverError("1.2 Sorry, something went wrong on our end"))
        }
    } yield res
  }

  private def handlePubKeyCurrentHardwareId(hwDeviceId: String)(implicit request: HttpServletRequest): Future[ActionResult] = {
    for {
      _ <- Future(logRequestInfo)
      res <- pubKeyService.getByHardwareId(hwDeviceId)
        .map { pks => Ok(pks) }
        .recover {
          case e: PubKeyServiceException =>
            logger.error("2.1 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            BadRequest(NOK.pubKeyError("Error retrieving pub key"))
          case e: Exception =>
            logger.error("2.2 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            InternalServerError(NOK.serverError("2.2 Sorry, something went wrong on our end"))
        }

    } yield res
  }

  private def delete(implicit request: HttpServletRequest) = {

    logRequestInfo

    ReadBody.readJson[PublicKeyDelete](x => x)
      .async { case (pkd, _) =>
        pubKeyService.delete(pkd)
          .map { dr =>
            if (dr) Ok(Simple("Key deleted"))
            else BadRequest(NOK.deleteKeyError("Failed to delete public key"))
          }
          .recover {
            case e: Exception =>
              logger.error("1.1 Error deleting pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.serverError("1.1 Sorry, something went wrong on our end"))
          }
      }
      .run
  }

}
