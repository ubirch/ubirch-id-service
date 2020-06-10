package com.ubirch

import com.google.inject.{ Guice, Injector, Module }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.lifeCycle.JVMHook
import com.ubirch.services.metrics.PrometheusMetrics

import scala.reflect._
import scala.util.Try

/**
  * Helper to manage Guice Injection.
  */
abstract class InjectorHelper(val modules: List[Module]) extends LazyLogging {

  import InjectorHelper._

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

object InjectorHelper {
  /**
    * Represents an Exception for when injecting a component
    * @param message Represents the error message
    */
  case class InjectionException(message: String) extends Exception(message)

  /**
    * Represents an Exception for when creating an injector a component
    * @param message Represents the error message
    */
  case class InjectorCreationException(message: String) extends Exception(message)
}

/**
  * Util that integrates an elegant way to add shut down hooks to the JVM.
  */
trait WithJVMHooks {

  _: InjectorHelper =>

  private def bootJVMHook(): JVMHook = get[JVMHook]

  bootJVMHook()

}

/**
  * Util that integrates an elegant way to add shut down hooks to the JVM.
  */
trait WithPrometheusMetrics {

  _: InjectorHelper =>

  get[PrometheusMetrics]

}

/**
  * Represents an assembly for the boot process
  * @param modules It is the modules of the system
  */
abstract class Boot(modules: List[Module]) extends InjectorHelper(modules) with WithJVMHooks with WithPrometheusMetrics {
  def *[T](block: => T): Unit =
    try { block } catch {
      case e: Exception =>
        logger.error("Exiting after exception found = {}", e.getMessage)
        Thread.sleep(5000)
        sys.exit(1)
    }
}
