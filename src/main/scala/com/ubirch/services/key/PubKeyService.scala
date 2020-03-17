package com.ubirch.services.key

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ PublicKey, PublicKeyByHwDeviceIdDAO, PublicKeyDAO }
import javax.inject.{ Inject, Singleton }
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }

import scala.util.control.NoStackTrace

trait PubKeyService {
  def create(publicKey: PublicKey): CancelableFuture[PublicKey]
  def get(hwDeviceId: String): CancelableFuture[Seq[PublicKey]]
}

@Singleton
class DefaultPubKeyService @Inject() (
    publicKeyDAO: PublicKeyDAO,
    publicKeyByHwDeviceIdDao: PublicKeyByHwDeviceIdDAO,
    pubKeyVerificationService: PubKeyVerificationService
)(implicit scheduler: Scheduler)
  extends PubKeyService with LazyLogging {

  def get(hwDeviceId: String): CancelableFuture[Seq[PublicKey]] = {
    publicKeyByHwDeviceIdDao
      .byHwDeviceId(hwDeviceId)
      .foldLeftL(Nil: Seq[PublicKey])((a, b) => a ++ Seq(b))
      .runToFuture
  }

  def create(publicKey: PublicKey): CancelableFuture[PublicKey] = {
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

    res.onErrorRecover {
      case KeyExists(publicKey) => publicKey
      case e: Throwable => throw e
    }

    res.runToFuture
  }

  private def earlyResponseIf(condition: Boolean)(response: Exception): Task[Unit] =
    if (condition) Task.raiseError(response) else Task.unit

  private case class KeyExists(publicKey: PublicKey) extends Exception with NoStackTrace

  private case class InvalidVerification(publicKey: PublicKey) extends Exception with NoStackTrace

  private case class InsertReturnsNone() extends Exception with NoStackTrace

}
