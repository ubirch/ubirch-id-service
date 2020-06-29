package com.ubirch.util

import io.prometheus.client.Counter
import monix.execution.CancelableFuture

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

trait ServiceMetrics {

  def service: String

  def successCounter: Counter

  def errorCounter: Counter

  def countWhen[T](method: String)(ft: T => Boolean)(cf: CancelableFuture[T])(implicit ec: ExecutionContext): CancelableFuture[T] = {

    def s(): Unit = successCounter.labels(service, method).inc()
    def f(): Unit = errorCounter.labels(service, method).inc()

    cf.onComplete {
      case Success(t) => if (ft(t)) s() else f()
      case Failure(_) => f()
    }
    cf
  }

  def count[T](method: String)(cf: CancelableFuture[T])(implicit ec: ExecutionContext): CancelableFuture[T] = {
    cf.onComplete {
      case Success(_) => successCounter.labels(service, method).inc()
      case Failure(_) => errorCounter.labels(service, method).inc()
    }
    cf
  }

}
