package com.ubirch.util

/**
  * Namespace that contains the exceptions of the system and a abstract
  * exception to create more in other components that use the core component.
  */
object Exceptions {

  /**
    * Represents Generic Top Level Exception for the Id Service System
    * @param message Represents the error message.
    */
  abstract class IdServiceException(message: String) extends Exception(message) {
    val name: String = this.getClass.getCanonicalName
  }

  /**
    * Represents an Exception that is thrown when the cluster service has not contacts points
    * configured in the configuration file or in the env.
    * @param message Represents the error message.
    */
  case class NoContactPointsException(message: String) extends IdServiceException(message)

  /**
    * Represents an Exception that is thrown when the connection service receives an empty keyspace
    * configured in the configuration file or set in the env.
    * @param message Represents the error message.
    */
  case class NoKeyspaceException(message: String) extends IdServiceException(message)

  /**
    * Represents an Exception that is thrown when the Consistency Level is invalid.
    * @param message Represents the error message.
    */
  case class InvalidConsistencyLevel(message: String) extends IdServiceException(message)

  /**
    * Represents an Exception that is thrown when the parsing of the contact points from a
    * string fail.
    * A correct string would look like: 127.0.0.1:9042, 127.0.0.2:9042
    * @param message Represents the error message.
    */
  case class InvalidContactPointsException(message: String) extends IdServiceException(message)

  /**
    * Represents an Exception that is thrown when Storing
    * @param message Represents the error message.
    * @param reason Represents the cause of the message
    */
  case class StoringException(message: String, reason: String) extends IdServiceException(message)

}
