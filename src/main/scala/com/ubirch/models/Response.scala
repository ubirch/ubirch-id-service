package com.ubirch.models

sealed trait Response {
  val version: String
  val status: Symbol
}

case class OK(version: String, status: Symbol, message: String) extends Response

object OK {
  def apply(message: String): OK = new OK("1.0", 'OK, message)
}

case class NOK(version: String, status: Symbol, errorType: Symbol, errorMessage: String) extends Response

object NOK {

  final val PARSING_ERROR = 'ParsingError
  final val NO_ROUTE_FOUND_ERROR = 'NoRouteFound

  def apply(errorType: Symbol, errorMessage: String): NOK = new NOK("1.0", 'NOK, errorType, errorMessage)

  def parsingError(errorMessage: String) = NOK(PARSING_ERROR, errorMessage)
  def noRouteFound(errorMessage: String) = NOK(NO_ROUTE_FOUND_ERROR, errorMessage)

}

