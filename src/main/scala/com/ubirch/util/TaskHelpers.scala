package com.ubirch.util

import monix.eval.Task

import scala.util.Try

/**
  * A simple helper for making tasks easier to deal with for when wrting for
  * comprehensions
  */
trait TaskHelpers {

  def escape[A](task: Task[A])(f: Throwable): Task[A] =
    Task.defer {
      task.onErrorHandleWith(_ => Task.raiseError(f))
    }

  def lift[A](block: => A)(f: Throwable): Task[A] =
    Task.defer {
      Task.delay(block).onErrorHandleWith(_ => Task.raiseError(f))
    }

  def liftTry[A](block: => Try[A])(f: Throwable): Task[A] =
    Task.defer {
      Task.fromTry(block).onErrorHandleWith(_ => Task.raiseError(f))
    }

  def earlyResponseIf(condition: Boolean)(response: Exception): Task[Unit] =
    if (condition) Task.raiseError(response) else Task.unit

}
