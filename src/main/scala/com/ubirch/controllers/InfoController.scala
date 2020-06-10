package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.controllers.concerns.SwaggerElements
import com.ubirch.models.{ NOK, Simple }
import javax.inject._
import javax.servlet.http.HttpServletRequest
import org.json4s.Formats
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }

/**
  * Represents a simple controller for the base path "/"
  * @param swagger Represents the Swagger Engine.
  * @param jFormats Represents the json formats for the system.
  */

@Singleton
class InfoController @Inject() (val swagger: Swagger, jFormats: Formats) extends ScalatraServlet with NativeJsonSupport with LazyLogging with SwaggerSupport with CorsSupport {
  override protected implicit def jsonFormats: Formats = jFormats

  protected val applicationDescription = "Device-related requests."

  val getSimpleCheck: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("simpleCheck")
      summary "Welcome"
      description "Getting a hello from the system"
      tags SwaggerElements.TAG_WELCOME)

  get("/hola", operation(getSimpleCheck)) {
    hello
  }

  get("/hello", operation(getSimpleCheck)) {
    hello
  }

  get("/ping", operation(getSimpleCheck)) {
    Ok("pong")
  }

  get("/", operation(getSimpleCheck)) {
    Ok(Simple("Hallo, Hola, Hello, Salut, Hej, this is the Ubirch Identity Service."))
  }

  before() {
    contentType = formats("json")
  }

  notFound {
    logger.info("controller=InfoController route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
  }

  private def hello(implicit request: HttpServletRequest): ActionResult = {
    contentType = formats("txt")
    val data =
      """
        |@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@@@@@@@@@@@@@@'~~~     ~~~`@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@@@@@@@@@'                     `@@@@@@@@@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@@@@@@'                           `@@@@@@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@@@@'                               `@@@@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@@'                                   `@@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@'                                     `@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@'                                       `@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@                                         @@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@'                                         `@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@                                           @@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@                                           @@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@                       n,                  @@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@                     _/ | _                @@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@                    /'  `'/                @@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@a                 <~    .'                a@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@                 .'    |                 @@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@a              _/      |                a@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@a           _/      `.`.              a@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@@a     ____/ '   \__ | |______       a@@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@@@@a__/___/      /__\ \ \     \___.a@@@@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@@@@/  (___.'\_______)\_|_|        \@@@@@@@@@@@@@@@@@@@@@@@@
        |@@@@@@@@@@@@@@@@@@|\________                       ~~~~~\@@@@@@@@@@@@@@@@@@
        |~~~\@@@@@@@@@@@@@@||       |\___________________________/|@/~~~~~~~~~~~\@@@
        |    |~~~~\@@@@@@@/ |  |    | | by: S.C.E.S.W.          | ||\____________|@@
        |
        |------------------------------------------------
        |This ASCII pic can be found at
        |https://asciiart.website/index.php?art=animals/wolves
        |""".stripMargin
    Ok(data)
  }

}
