package com.ubirch.services.key

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ PublicKey, PublicKeyDAO }
import javax.inject.{ Inject, Singleton }
import monix.eval.Task

import scala.concurrent.ExecutionContext
import scala.util.control.NoStackTrace

@Singleton
class PubKeyService @Inject() (publicKeyDAO: PublicKeyDAO, pubKeyVerificationService: PubKeyVerificationService)(implicit ec: ExecutionContext) extends LazyLogging {

  implicit private val scheduler = monix.execution.Scheduler(ec)

  private def earlyResponseIf(condition: Boolean)(response: Exception): Task[Unit] =
    if (condition) Task.raiseError(response) else Task.unit

  private def finalize(response: Task[PublicKey]) = response.onErrorRecover {
    case KeyExists(publicKey) => publicKey
    case e: Throwable => throw e
  }

  case class KeyExists(publicKey: PublicKey) extends Exception with NoStackTrace
  case class InvalidVerification(publicKey: PublicKey) extends Exception with NoStackTrace
  case class InsertReturnsNone() extends Exception with NoStackTrace

  def process(publicKey: PublicKey) = {
    val res = for {
      maybeKey <- publicKeyDAO.byPubKey(publicKey.pubKeyInfo.pubKey).headOptionL
      _ = if (maybeKey.isDefined) logger.info("Key found is " + maybeKey)
      _ <- earlyResponseIf(maybeKey.isDefined)(KeyExists(publicKey))

      verification <- Task.delay(pubKeyVerificationService.validateSignature(publicKey))
      _ = if (!verification) logger.info("Verification failed " + maybeKey)
      _ <- earlyResponseIf(!verification)(InvalidVerification(publicKey))

      res <- publicKeyDAO.insert(publicKey).headOptionL
      _ = if (res.isEmpty) logger.info("Creation seems to have failed...for " + publicKey.toString)
      _ <- earlyResponseIf(res.isEmpty)(InsertReturnsNone())
    } yield {
      publicKey
    }

    finalize(res).runToFuture
  }

}
