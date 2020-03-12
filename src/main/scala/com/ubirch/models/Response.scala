package com.ubirch.models

sealed trait Response

case class Simple(version: String, status: Symbol, message: String) extends Response

object Simple {
  def apply(message: String): Simple = new Simple("1.0", 'OK, message)
}

case class NOK(version: String, status: Symbol, errorType: Symbol, errorMessage: String) extends Response

object NOK {

  final val PARSING_ERROR = 'ParsingError
  final val NO_ROUTE_FOUND_ERROR = 'NoRouteFound
  final val PUBKEY_ERROR = 'PubkeyError

  def apply(errorType: Symbol, errorMessage: String): NOK = new NOK("1.0", 'NOK, errorType, errorMessage)

  def parsingError(errorMessage: String) = NOK(PARSING_ERROR, errorMessage)
  def noRouteFound(errorMessage: String) = NOK(NO_ROUTE_FOUND_ERROR, errorMessage)
  def pubKeyError(errorMessage: String) = NOK(PUBKEY_ERROR, errorMessage)

}

