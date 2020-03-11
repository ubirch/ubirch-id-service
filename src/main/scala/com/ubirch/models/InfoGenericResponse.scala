package com.ubirch.models

case class Info(name: String, description: String, version: String)
case class InfoGenericResponse(success: Boolean, message: String, data: Info) extends GenericResponseBase[Info]
