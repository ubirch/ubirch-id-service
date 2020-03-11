import com.ubirch.Service
import com.ubirch.controllers.{ IdentityController, InfoController, ResourcesController }
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle {

  lazy val infoController = Service.get[InfoController]
  lazy val identityController = Service.get[IdentityController]
  lazy val resourceController = Service.get[ResourcesController]

  override def init(context: ServletContext) {

    context.initParameters("org.scalatra.cors.preflightMaxAge") = "5"
    context.initParameters("org.scalatra.cors.allowCredentials") = "false"

    context.mount(
      handler = infoController,
      urlPattern = "/",
      name = "Info"
    )
    context.mount(
      handler = identityController,
      urlPattern = "/identities",
      name = "Identities"
    )
    context.mount(
      handler = resourceController,
      urlPattern = "/api-docs",
      name = "Resources"
    )
  }
}

