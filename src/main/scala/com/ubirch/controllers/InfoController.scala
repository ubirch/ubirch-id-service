package com.ubirch.controllers

import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.concerns.{ ControllerBase, SwaggerElements }
import com.ubirch.models.{ NOK, Simple }
import io.prometheus.client.Counter
import javax.inject._
import javax.servlet.http.HttpServletRequest
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.{ Swagger, SwaggerSupportSyntax }

import scala.concurrent.ExecutionContext

/**
  * Represents a simple controller for the base path "/"
  * @param swagger Represents the Swagger Engine.
  * @param jFormats Represents the json formats for the system.
  */

@Singleton
class InfoController @Inject() (config: Config, val swagger: Swagger, jFormats: Formats)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase {

  override protected val applicationDescription = "Device-related requests."
  override protected implicit def jsonFormats: Formats = jFormats

  val service: String = config.getString(GenericConfPaths.NAME)

  val successCounter: Counter = Counter.build()
    .name("info_management_success")
    .help("Represents the number of info management successes")
    .labelNames("service", "method")
    .register()

  val errorCounter: Counter = Counter.build()
    .name("info_management_failures")
    .help("Represents the number of info management failures")
    .labelNames("service", "method")
    .register()

  val getSimpleCheck: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("simpleCheck")
      summary "Welcome"
      description "Getting a hello from the system"
      tags SwaggerElements.TAG_WELCOME)

  get("/hola", operation(getSimpleCheck)) {
    asyncResult("hola") { _ =>
      Task(hello)
    }
  }

  get("/hello", operation(getSimpleCheck)) {
    asyncResult("hello") { _ =>
      Task(hello)
    }
  }

  get("/ping", operation(getSimpleCheck)) {
    asyncResult("ping") { _ =>
      Task {
        Ok("pong")
      }
    }
  }

  get("/", operation(getSimpleCheck)) {
    asyncResult("root") { _ =>
      Task {
        Ok(Simple("Hallo, Hola, Hello, Salut, Hej, this is the Ubirch Identity Service."))
      }
    }
  }

  before() {
    contentType = formats("json")
  }

  notFound {
    asyncResult("not_found") { _ =>
      Task {
        logger.info("controller=InfoController route_not_found={} query_string={}", requestPath, request.getQueryString)
        NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
      }
    }
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
