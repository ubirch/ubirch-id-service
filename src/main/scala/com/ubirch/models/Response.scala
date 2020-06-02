package com.ubirch.models

/**
  * Represents a simple Response object. Used for HTTP responses.
  */
sealed trait Response[T] {
  val version: String
  val status: T
}
object Response {
  val version = "1.0"
}

/**
  * Represents an OK Response object
  * @param version the version of the response
  * @param status the status of the response. OK
  * @param message the message of the response
  */
case class Simple(version: String, status: Symbol, message: String) extends Response[Symbol]

/**
  * Companion object for the Simple response
  */
object Simple {
  def apply(message: String): Simple = new Simple(Response.version, 'OK, message)
}

/**
  *  Represents an Error Response.
  * @param version the version of the response
  * @param status the status of the response. NOK
  * @param errorType the error type
  * @param errorMessage the message for the response
  */
case class NOK(version: String, status: Symbol, errorType: Symbol, errorMessage: String) extends Response[Symbol]

/**
  * Companion object for the NOK response
  */
object NOK {

  final val SERVER_ERROR = 'ServerError
  final val PARSING_ERROR = 'ParsingError
  final val NO_ROUTE_FOUND_ERROR = 'NoRouteFound
  final val PUBKEY_ERROR = 'PubkeyError
  final val CRS_ERROR = 'CertRequestError
  final val DELETE_ERROR = 'DeleteError

  def apply(errorType: Symbol, errorMessage: String): NOK = new NOK(Response.version, 'NOK, errorType, errorMessage)

  def serverError(errorMessage: String): NOK = NOK(SERVER_ERROR, errorMessage)
  def parsingError(errorMessage: String): NOK = NOK(PARSING_ERROR, errorMessage)
  def noRouteFound(errorMessage: String): NOK = NOK(NO_ROUTE_FOUND_ERROR, errorMessage)
  def pubKeyError(errorMessage: String): NOK = NOK(PUBKEY_ERROR, errorMessage)
  def crsError(errorMessage: String): NOK = NOK(CRS_ERROR, errorMessage)
  def deleteKeyError(errorMessage: String): NOK = NOK(DELETE_ERROR, errorMessage)

}

/**
  * Represents an OK Response object.
  * This is just a convenience object to be compatible with
  * one client that expects "messages" instead of "message"
  * This response is meant only for the deep check used by other systems
  * and added to keep backwards compatibility
  *
  * @param version the version of the response
  * @param status the status of the response. True/False
  * @param messages the messages of the response
  */
case class BooleanListResponse(version: String, status: Boolean, messages: List[String]) extends Response[Boolean]

/**
  * Companion object for the Simple response
  */
object BooleanListResponse {
  def OK(message: String): BooleanListResponse = new BooleanListResponse(Response.version, status = true, List(message))
  def NOK(message: String): BooleanListResponse = BooleanListResponse(Response.version, status = false, List(message))
}

