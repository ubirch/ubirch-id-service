package com.ubirch.services.rest

import com.google.inject.Provider
import com.ubirch.controllers.RestApiInfo
import javax.inject._
import org.scalatra.swagger.Swagger

/**
  * Represents the Swagger Provider for the system
  */
@Singleton
class SwaggerProvider extends Provider[Swagger] {
  lazy val swagger = new Swagger(Swagger.SpecVersion, "1.3.0", RestApiInfo)
  override def get(): Swagger = swagger
}
