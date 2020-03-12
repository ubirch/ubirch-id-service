package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.controllers.concerns.RequestHelpers
import com.ubirch.models.{NOK, PublicKey, PublicKeyDAO}
import javax.inject._
import org.json4s.Formats
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}

@Singleton
class KeyController @Inject() (val swagger: Swagger, jFormats: Formats, publicKeyDAO: PublicKeyDAO)(implicit val executor: ExecutionContext)
  extends ScalatraServlet
  with FutureSupport
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with RequestHelpers
  with LazyLogging {

  implicit val scheduler = monix.execution.Scheduler(executor)

  override protected val applicationDescription: String = "Key Controller"
  override protected implicit val jsonFormats: Formats = jFormats

  before() {
    contentType = formats("json")
  }

  get("/v1/pubkey/:id") {
    "This is a key " + params.get("id")
  }

  post("/v1/pubkey") {
    withData[PublicKey] { pk =>

      async {

        val promise = Promise[ActionResult]()
        publicKeyDAO.insert(pk)
          .headOptionL
          .runToFuture
          .onComplete {
            case Success(Some(_)) =>
              promise.success(Ok(pk))
            case Success(None) =>
              promise.success(InternalServerError(NOK.pubKeyError("Error creating")))
            case Failure(e) =>
              logger.error("Error creating pub key {}", e.getMessage)
              promise.success(InternalServerError(NOK.pubKeyError("Error creating pub key")))
          }

        promise.future

      }

    }
  }

  delete("/v1/pubkey") {
    "This was an key" + params.get("id")
  }

  notFound {
    logger.info("route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
  }

}
