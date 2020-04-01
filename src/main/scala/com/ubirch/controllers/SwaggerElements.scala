package com.ubirch.controllers

object SwaggerElements {
  val NOT_AUTHORIZED_CODE_401 = 401
  val OK_CODE_200 = 200
  val ERROR_REQUEST_CODE_400 = 400

  val TAG_KEY_SERVICE = "key-service"
  val TAG_KEY_REGISTRY = "key registry"
  val TAG_MSG_PACK = "MessagePack"
  val TAG_WELCOME = "Welcome"
  val TAG_HEALTH = "Health"

  val ERROR_RESPONSE: String = "version: '1.0' status: NOK\n" + "errorType: FindTrustedError\n" + "message: failed to query trusted keys"
}
