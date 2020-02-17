package com.ubirch

import com.google.inject.{ Guice, Injector, Module }
import com.typesafe.scalalogging.LazyLogging

import scala.reflect._
import scala.util.Try

case class InjectionException(message: String) extends Exception(message)

case class InjectorCreationException(message: String) extends Exception(message)

/**
  * Helper to manage Guice Injection.
  */
abstract class InjectorHelper(val modules: List[Module]) extends LazyLogging {

  private val injector: Injector = {
    try {
      Guice.createInjector(modules: _*)
    } catch {
      case e: Exception =>
        logger.error("Error Creating Injector: {} ", e.getMessage)
        throw InjectorCreationException(e.getMessage)
    }
  }

  def getAsOption[T](implicit ct: ClassTag[T]): Option[T] = Option(get(ct))

  def get[T](implicit ct: ClassTag[T]): T = get(ct.runtimeClass.asInstanceOf[Class[T]])

  def get[T](clazz: Class[T]): T = {
    try {
      injector.getInstance(clazz)
    } catch {
      case e: Exception =>
        logger.error("Error Injecting: {} ", e.getMessage)
        throw InjectionException(e.getMessage)
    }
  }

  def getAsTry[T](implicit ct: ClassTag[T]): Try[T] = Try(get(ct))

}

abstract class Boot(modules: List[Module]) extends InjectorHelper(modules) {
  def *[T](block: => T): Unit =
    try { block } catch {
      case e: Exception =>
        logger.error("Exiting after exception found = {}", e.getMessage)
        Thread.sleep(5000)
        sys.exit(1)
    }
}

object Boot {
  type Factory = Boot
}
