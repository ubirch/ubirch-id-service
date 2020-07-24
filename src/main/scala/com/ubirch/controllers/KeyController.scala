package com.ubirch
package controllers

import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.concerns.{ ControllerBase, SwaggerElements }
import com.ubirch.models._
import com.ubirch.services.key.PubKeyService
import com.ubirch.services.pm.ProtocolMessageService
import com.ubirch.util.{ DateUtil, TaskHelpers }
import io.prometheus.client.Counter
import javax.inject._
import javax.servlet.http.HttpServletRequest
import monix.eval.Task
import monix.execution.Scheduler
import org.eclipse.jetty.http.BadMessageException
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.{ ResponseMessage, Swagger, SwaggerSupportSyntax }

import scala.concurrent.ExecutionContext

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
    config: Config,
    val swagger: Swagger,
    jFormats: Formats,
    pubKeyService: PubKeyService,
    pmService: ProtocolMessageService
)(implicit val executor: ExecutionContext, scheduler: Scheduler) extends ControllerBase with TaskHelpers {

  override protected val applicationDescription: String = "Key Controller"
  override protected implicit val jsonFormats: Formats = jFormats

  val service: String = config.getString(GenericConfPaths.NAME)

  val successCounter: Counter = Counter.build()
    .name("pubkey_management_success")
    .help("Represents the number of public key management successes")
    .labelNames("service", "method")
    .register()

  val errorCounter: Counter = Counter.build()
    .name("pubkey_management_failures")
    .help("Represents the number of public key management failures")
    .labelNames("service", "method")
    .register()

  before() {
    contentType = formats("json")
  }

  val getV1Check: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Simple]("getV1Check")
      summary "Welcome / Health"
      description "Check if KeyController service is up and running"
      tags SwaggerElements.TAG_HEALTH
      responseMessages ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Not successful response"))

  get("/v1/check", operation(getV1Check)) {
    asyncResult("check") { _ =>
      Task.delay(Ok(Simple("I survived a check")))
    }
  }

  val getV1DeepCheck: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[BooleanListResponse]("getV1DeepCheck")
      summary "Health monitor deep check"
      description "allows a deep check of the service"
      tags SwaggerElements.TAG_HEALTH
      responseMessages ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "something is not fine"))

  get("/v1/deepCheck", operation(getV1DeepCheck)) {

    asyncResult("deep_check") { implicit request =>
      for {
        res <- pubKeyService.getSome()
          //We use a BooleanList Response to keep backwards compatibility with clients
          .map(_ => Ok(BooleanListResponse.OK("I am alive after a deepCheck @ " + DateUtil.nowUTC.toString())))
          .onErrorHandle {
            case e: Exception =>
              logger.error("1.2 Error retrieving some pub keys: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.serverError("1.2 Sorry, something went wrong on our end"))
          }
      } yield {
        res
      }

    }

  }

  val getV1PubKeyPubKey: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[PublicKey]("getV1PubKeyPubKey")
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
    asyncResult("get_by_pub_key") { implicit request =>
      val pubKey = params.getOrElse("splat", halt(BadRequest(NOK.pubKeyError("No pubKeyId parameter found in path"))))
      handlePubKeyId(pubKey)
    }
  }

  get("/v1/pubkey/:pubkey", operation(getV1PubKeyPubKey)) {
    asyncResult("get_by_pub_key") { implicit request =>
      val pubkey = params.getOrElse("pubkey", halt(BadRequest(NOK.pubKeyError("No pubKeyId parameter found in path"))))
      handlePubKeyId(pubkey)
    }
  }

  val getV1CurrentHardwareId: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[Seq[PublicKey]]("getV1CurrentHardwareId")
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
    asyncResult("get_by_hardware_id") { implicit request =>
      val hardwareId = params.getOrElse("splat", halt(BadRequest(NOK.pubKeyError("No hardwareId parameter found in path"))))
      handlePubKeyCurrentHardwareId(hardwareId)
    }
  }

  get("/v1/pubkey/current/hardwareId/:hardwareId", operation(getV1CurrentHardwareId)) {
    asyncResult("get_by_hardware_id") { implicit request =>
      val hardwareId = params.getOrElse("hardwareId", halt(BadRequest(NOK.pubKeyError("No hardwareId parameter found in path"))))
      handlePubKeyCurrentHardwareId(hardwareId)
    }
  }

  val postV1PubKey: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[PublicKey]("postV1PubKey")
      summary "Stores new public key"
      description "stores the given public key with its unique pubKeyID"
      tags SwaggerElements.TAG_KEY_SERVICE
      parameters bodyParam[String]("pubkey").description("the new public key object with the pubKey that should be stored for the unique pubKeyId - also part of the pub key object - in the key registry to be able to find the public key; pubKeyId may not exist already")
      responseMessages (
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Error creating pub key"),
        ResponseMessage(SwaggerElements.INTERNAL_ERROR_CODE_500, "Sorry, something went wrong on our end")
      ))

  post("/v1/pubkey", operation(postV1PubKey)) {

    asyncResult("create_by_json") { implicit request =>

      for {
        readBody <- Task.delay(ReadBody.readJson[PublicKey](PublicKeyInfo.checkPubKeyId))
        res <- pubKeyService.create(readBody.extracted, readBody.asString)
          .map { key => Ok(key) }
          .onErrorHandle {
            case e: PubKeyServiceException =>
              logger.error("1.1 Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              BadRequest(NOK.pubKeyError("Error creating pub key"))
            case e: Exception =>
              logger.error("1.2 Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.serverError("1.2 Sorry, something went wrong on our end"))
          }

      } yield {
        res
      }

    }

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

    asyncResult("create_by_msg_pack") { implicit request =>

      for {
        readBody <- Task.delay(ReadBody.readMsgPack(pmService))
        res <- pubKeyService.create(readBody.extracted, readBody.asString)
          .map { key => Ok(key) }
          .onErrorHandle {
            case e: PubKeyServiceException =>
              logger.error("2.1 Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              BadRequest(NOK.pubKeyError("Error creating pub key"))
            case e: Exception =>
              logger.error("2.2 Error creating pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
              InternalServerError(NOK.serverError("2.2 Sorry, something went wrong on our end"))
          }

      } yield {
        res
      }

    }

  }

  val revokeV1PubKey: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[PublicKeyRevoke]("revokeV1PubKey")
      summary "Revokes a public key"
      description "revokes a public key"
      tags SwaggerElements.TAG_KEY_SERVICE
      parameters bodyParam[String]("publicKeyToRevoke").description("the public key to revoke including signature of publicKey field")
      responseMessages (
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Failed to revoke public key"),
        ResponseMessage(SwaggerElements.INTERNAL_ERROR_CODE_500, "Sorry, something went wrong on our end")
      ))

  delete("/v1/pubkey/revoke", operation(revokeV1PubKey)) {
    asyncResult("revoke") { implicit request =>
      revoke
    }
  }
  /**
    * This has been added since the revoke method cannot be tested with a body.
    */
  patch("/v1/pubkey/revoke") {
    asyncResult("revoke") { implicit request =>
      revoke
    }
  }

  val deleteV1PubKey: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[PublicKeyDelete]("deleteV1PubKey")
      summary "Deletes a public key"
      description "deletes a public key"
      tags SwaggerElements.TAG_KEY_SERVICE
      parameters bodyParam[String]("publicKeyToDelete").description("the public key to delete including signature of publicKey field") //.example("{\n  \"publicKey\": \"MC0wCAYDK2VkCgEBAyEAxUQcVYd3dt7jAJBtulZoz8QDftnND2X5//ittJ7XAhs=\",\n  \"signature\": \"/kED2IJKCAyro/szRoylAwaEx3E8U2OFI8zHNB8cEHdxy8JtgoR81YL1X/o7Xzkz30eqNjIsWfhmQNdaIma2Aw==\"\n}").required
      responseMessages (
        ResponseMessage(SwaggerElements.ERROR_REQUEST_CODE_400, "Failed to delete public key"),
        ResponseMessage(SwaggerElements.INTERNAL_ERROR_CODE_500, "Sorry, something went wrong on our end")
      ))

  delete("/v1/pubkey", operation(deleteV1PubKey)) {
    asyncResult("delete") { implicit request =>
      delete
    }
  }
  /**
    * This has been added since the delete method cannot be tested with a body.
    */
  patch("/v1/pubkey") {
    asyncResult("delete") { implicit request =>
      delete
    }
  }

  notFound {
    asyncResult("not_found") { _ =>
      Task {
        logger.info("controller=KeyController route_not_found={} query_string={}", requestPath, request.getQueryString)
        NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
      }
    }
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

  private def handlePubKeyId(pubKeyId: String)(implicit request: HttpServletRequest): Task[ActionResult] = {
    for {
      res <- pubKeyService.getByPubKeyId(pubKeyId)
        .map { pks =>
          pks.toList match {
            case Nil => NotFound(NOK.pubKeyError("Key not found"))
            case pk :: _ => Ok(pk)
          }
        }
        .onErrorHandle {
          case e: PubKeyServiceException =>
            logger.error("1.1 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            BadRequest(NOK.pubKeyError("Error retrieving pub key"))
          case e: Exception =>
            logger.error("1.2 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            InternalServerError(NOK.serverError("1.2 Sorry, something went wrong on our end"))
        }
    } yield res
  }

  private def handlePubKeyCurrentHardwareId(hwDeviceId: String)(implicit request: HttpServletRequest): Task[ActionResult] = {
    for {
      res <- pubKeyService.getByHardwareId(hwDeviceId)
        .map { pks => Ok(pks) }
        .onErrorHandle {
          case e: PubKeyServiceException =>
            logger.error("2.1 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            BadRequest(NOK.pubKeyError("Error retrieving pub key"))
          case e: Exception =>
            logger.error("2.2 Error retrieving pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            InternalServerError(NOK.serverError("2.2 Sorry, something went wrong on our end"))
        }

    } yield res
  }

  private def revoke(implicit request: HttpServletRequest) = {
    for {
      readBody <- Task.delay(ReadBody.readJson[PublicKeyRevoke](x => x))
      res <- pubKeyService.revoke(readBody.extracted)
        .map { dr =>
          if (dr) Ok(Simple("Key revoked"))
          else BadRequest(NOK.deleteKeyError("Failed to revoke public key"))
        }
        .onErrorRecover {
          case e: Exception =>
            logger.error("1.1 Error revoking pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            InternalServerError(NOK.serverError("1.1 Sorry, something went wrong on our end"))
        }

    } yield {
      res
    }

  }

  private def delete(implicit request: HttpServletRequest) = {
    for {
      readBody <- Task.delay(ReadBody.readJson[PublicKeyDelete](x => x))
      res <- pubKeyService.delete(readBody.extracted)
        .map { dr =>
          if (dr) Ok(Simple("Key deleted"))
          else BadRequest(NOK.deleteKeyError("Failed to delete public key"))
        }
        .onErrorRecover {
          case e: Exception =>
            logger.error("1.1 Error deleting pub key: exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
            InternalServerError(NOK.serverError("1.1 Sorry, something went wrong on our end"))
        }

    } yield {
      res
    }

  }

}
