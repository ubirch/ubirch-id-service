package com.ubirch

import java.util.concurrent.Executors

import monix.execution.Scheduler
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers, WordSpec }

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutor, Future }

trait TestBase
  extends WordSpec
  with ScalaFutures
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with MustMatchers {

  implicit lazy val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))

  implicit lazy val scheduler: Scheduler = monix.execution.Scheduler(ec)

  def await[T](future: Future[T]): T = await(future, Duration.Inf)

  def await[T](future: Future[T], atMost: Duration): T = Await.result(future, atMost)

}
