package com.ubirch.controllers

import javax.inject._
import org.scalatra.ScalatraServlet
import org.scalatra.swagger._

@Singleton
class ResourcesController @Inject() (val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase

object RestApiInfo extends ApiInfo(
  "Identity Service",
  "These are the available endpoints for querying the Identity Service",
  "https://ubirch.de",
  ContactInfo("Carlos Sanchez", "ubirch.com", "carlos.sanchez@ubirch.com"),
  LicenseInfo("Apache License, Version 2.0", "https://www.apache.org/licenses/LICENSE-2.0")
)
