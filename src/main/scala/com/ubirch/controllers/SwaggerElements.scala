package com.ubirch.controllers

object SwaggerElements {
  val NOT_AUTHORIZED_CODE_401 = 401
  val OK_CODE_200 = 200
  val ERROR_REQUEST_CODE_400 = 400

  val TAG_KEY_SERVICE = "key-service"
  val TAG_KEY_REGISTRY = "key registry"
  val TAG_MSG_PACK = "MessagePack"

  val ERROR_RESPONSE = """      version: '1.0'
                         |      status: NOK
                         |      errorType: FindTrustedError
                         |      message: failed to query trusted keys"""
}
