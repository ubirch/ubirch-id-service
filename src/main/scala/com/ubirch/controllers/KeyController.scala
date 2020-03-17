package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.controllers.concerns.RequestHelpers
import com.ubirch.models.{ NOK, PublicKey, PublicKeyDelete, Simple }
import com.ubirch.services.key.DefaultPubKeyService.PubKeyServiceException
import com.ubirch.services.key.PubKeyService
import javax.inject._
import org.json4s.Formats
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport }

import scala.concurrent.ExecutionContext

@Singleton
class KeyController @Inject() (val swagger: Swagger, jFormats: Formats, pubKeyService: PubKeyService)(implicit val executor: ExecutionContext)
  extends ScalatraServlet
  with FutureSupport
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

  get("/v1/pubkey/:hardwareId") {

    val hwDeviceId = params.get("hardwareId")
      .filter(_.nonEmpty)
      .getOrElse(halt(BadRequest(NOK.pubKeyError("No hardwareId parameter found"))))

    pubKeyService.get(hwDeviceId)
      .map { pks => Ok(pks) }
      .recover {
        case e: Exception =>
          logger.error("Error retrieving pub key {}", e.getMessage)
          InternalServerError(NOK.pubKeyError("Error retrieving pub key"))
      }
  }

  post("/v1/pubkey") {

    ReadBody.read[PublicKey]
      .map(pk => pk.copy(raw = Some("-999")))
      .async { pk =>
        pubKeyService.create(pk)
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

  delete("/v1/pubkey") {
    ReadBody.read[PublicKeyDelete]
      .async { pkd =>
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

  notFound {
    logger.info("route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
  }

}
