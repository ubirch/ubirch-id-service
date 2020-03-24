package com.ubirch.models

sealed trait Response

case class Simple(version: String, status: Symbol, message: String) extends Response

object Simple {
  def apply(message: String): Simple = new Simple("1.0", 'OK, message)
}

case class NOK(version: String, status: Symbol, errorType: Symbol, errorMessage: String) extends Response

object NOK {

  final val SERVER_ERROR = 'ServerError
  final val PARSING_ERROR = 'ParsingError
  final val NO_ROUTE_FOUND_ERROR = 'NoRouteFound
  final val PUBKEY_ERROR = 'PubkeyError
  final val DELETE_ERROR = 'DeleteError

  def apply(errorType: Symbol, errorMessage: String): NOK = new NOK("1.0", 'NOK, errorType, errorMessage)

  def serverError(errorMessage: String): NOK = NOK(SERVER_ERROR, errorMessage)
  def parsingError(errorMessage: String): NOK = NOK(PARSING_ERROR, errorMessage)
  def noRouteFound(errorMessage: String): NOK = NOK(NO_ROUTE_FOUND_ERROR, errorMessage)
  def pubKeyError(errorMessage: String): NOK = NOK(PUBKEY_ERROR, errorMessage)
  def deleteKeyError(errorMessage: String): NOK = NOK(DELETE_ERROR, errorMessage)

}

