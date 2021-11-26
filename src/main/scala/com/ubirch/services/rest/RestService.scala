package com.ubirch.services.rest

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.HttpServerConfPaths
import com.ubirch.services.lifeCycle.Lifecycle
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.server.{ Handler, HttpConnectionFactory, Server }
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

import javax.inject._
import scala.concurrent.Future

/**
  * Represents the basic component for supporting scalatra
  * @param config the configuration object
  * @param lifecycle the life cycle object
  */
class RestService @Inject() (config: Config, lifecycle: Lifecycle) extends LazyLogging {

  val serverPort: Int = config.getInt(HttpServerConfPaths.PORT)
  val swaggerPath: String = config.getString(HttpServerConfPaths.SWAGGER_PATH)

  def disableServerVersionHeader(server: Server): Unit = {
    server.getConnectors.foreach { connector =>
      connector.getConnectionFactories
        .stream()
        .filter(cf => cf.isInstanceOf[HttpConnectionFactory])
        .forEach(cf => cf.asInstanceOf[HttpConnectionFactory].getHttpConfiguration.setSendServerVersion(false))
    }
  }

  def start(): Unit = {
    val server = initializeServer
    startServer(server)
    addShutdownHook(server)
  }

  private def initializeServer: Server = {
    val server = createServer
    disableServerVersionHeader(server)
    val contexts = createContextsOfTheServer
    server.setHandler(contexts)
    server
  }

  private def createServer = new Server(serverPort)

  private def createContextsOfTheServer = {
    val contextRestApi: WebAppContext = createContextScalatraApi
    val contextSwaggerUi: WebAppContext = createContextSwaggerUi
    initialiseContextHandlerCollection(Array(contextRestApi, contextSwaggerUi))
  }

  private def initialiseContextHandlerCollection(contexts: Array[Handler]): ContextHandlerCollection = {
    val contextCollection = new ContextHandlerCollection()
    contextCollection.setHandlers(contexts)
    contextCollection
  }

  private def createContextScalatraApi: WebAppContext = {
    val contextRestApi = new WebAppContext()
    contextRestApi.setContextPath("/")
    contextRestApi.setResourceBase("src/main/scala")
    contextRestApi.addEventListener(new ScalatraListener)
    contextRestApi.addServlet(classOf[DefaultServlet], "/")
    contextRestApi.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    contextRestApi
  }

  private def createContextSwaggerUi: WebAppContext = {
    val contextSwaggerUi = new WebAppContext()
    contextSwaggerUi.setContextPath("/docs")
    contextSwaggerUi.setResourceBase(swaggerPath)
    contextSwaggerUi
  }

  private def startServer(server: Server, withJoin: Boolean = false): Unit =
    try {
      server.start()
      if (withJoin) server.join()
    } catch {
      case e: Exception =>
        logger.error("Error starting service with message=" + e.getMessage)
        System.exit(-1)
    }

  private def addShutdownHook(server: Server): Unit = {
    lifecycle.addStopHook { () =>
      logger.info("Shutting down Restful Service")
      Future.successful(server.stop())
    }
  }

}
