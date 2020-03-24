import com.ubirch.Service
import com.ubirch.controllers.{ KeyController, ResourcesController }
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle {

  lazy val keyController = Service.get[KeyController]
  lazy val resourceController = Service.get[ResourcesController]

  override def init(context: ServletContext) {

    context.setInitParameter("org.scalatra.cors.preflightMaxAge", "5")
    context.setInitParameter("org.scalatra.cors.allowCredentials", "false")

    context.mount(
      handler = keyController,
      urlPattern = "/api/keyService",
      name = "Keys"
    )
    context.mount(
      handler = resourceController,
      urlPattern = "/api-docs",
      name = "Resources"
    )
  }
}

