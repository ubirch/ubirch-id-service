package com.ubirch.services.key

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ PublicKey, PublicKeyByHwDeviceIdDAO, PublicKeyDAO, PublicKeyDelete }
import javax.inject.{ Inject, Singleton }
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }

import scala.util.control.NoStackTrace

trait PubKeyService {
  def create(publicKey: PublicKey): CancelableFuture[PublicKey]
  def get(hwDeviceId: String): CancelableFuture[Seq[PublicKey]]
  def delete(publicKeyDelete: PublicKeyDelete): CancelableFuture[Boolean]
}

@Singleton
class DefaultPubKeyService @Inject() (
    publicKeyDAO: PublicKeyDAO,
    publicKeyByHwDeviceIdDao: PublicKeyByHwDeviceIdDAO,
    verification: PubKeyVerificationService
)(implicit scheduler: Scheduler)
  extends PubKeyService with LazyLogging {

  def delete(publicKeyDelete: PublicKeyDelete): CancelableFuture[Boolean] = {

    val res = for {
      maybeKey <- publicKeyDAO.byPubKey(publicKeyDelete.publicKey).headOptionL
      _ = if (maybeKey.isEmpty) logger.error("No key found with public key: " + publicKeyDelete.publicKey)
      _ <- earlyResponseIf(maybeKey.isDefined)(KeyNotExists(publicKeyDelete.publicKey))

      key = maybeKey.get
      pubKeyInfo = key.pubKeyInfo
      pubKey = pubKeyInfo.pubKey
      curve = verification.getCurve(pubKeyInfo.algorithm)
      verification <- Task.delay(verification.validateFromBase64(pubKey, publicKeyDelete.signature, curve))
      _ = if (!verification) logger.error("Unable to delete public key with invalid signature:" + publicKeyDelete)
      _ <- earlyResponseIf(!verification)(InvalidVerification(key))

      deletion <- publicKeyDAO.delete(publicKeyDelete.publicKey).headOptionL
      _ = if (deletion.isEmpty) logger.error("Deletion seems to have failed...for " + publicKeyDelete.toString)
      _ <- earlyResponseIf(deletion.isEmpty)(OperationReturnsNone())

    } yield {
      true
    }

    res.onErrorRecover {
      case KeyNotExists(publicKey) => false
      case InvalidVerification(_) => false
      case OperationReturnsNone() => false
      case e: Throwable => throw e
    }

    res.runToFuture

  }

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

      verification <- Task.delay(verification.validate(publicKey))
      _ = if (!verification) logger.error("Verification failed " + maybeKey)
      _ <- earlyResponseIf(!verification)(InvalidVerification(publicKey))

      res <- publicKeyDAO.insert(publicKey).headOptionL
      _ = if (res.isEmpty) logger.error("Creation seems to have failed...for " + publicKey.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone())
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

  abstract class PubKeyServiceException() extends Exception with NoStackTrace

  private case class KeyExists(publicKey: PublicKey) extends PubKeyServiceException

  private case class InvalidVerification(publicKey: PublicKey) extends PubKeyServiceException

  private case class OperationReturnsNone() extends PubKeyServiceException

  private case class KeyNotExists(publicKey: String) extends PubKeyServiceException

}
