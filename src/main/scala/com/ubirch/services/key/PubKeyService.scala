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

  import DefaultPubKeyService._

  def delete(publicKeyDelete: PublicKeyDelete): CancelableFuture[Boolean] = {

    (for {
      maybeKey <- publicKeyDAO.byPubKey(publicKeyDelete.pubKey).headOptionL
      _ = if (maybeKey.isEmpty) logger.error("No key found with public key: " + publicKeyDelete.pubKey)
      _ <- earlyResponseIf(maybeKey.isEmpty)(KeyNotExists(publicKeyDelete.pubKey))

      key = maybeKey.get
      pubKeyInfo = key.pubKeyInfo
      pubKey = pubKeyInfo.pubKey
      curve = verification.getCurve(pubKeyInfo.algorithm)
      verification <- Task.delay(verification.validateFromBase64(pubKey, publicKeyDelete.signature, curve))
      _ = if (!verification) logger.error("Unable to delete public key with invalid signature: " + publicKeyDelete)
      _ <- earlyResponseIf(!verification)(InvalidVerification(key))

      deletion <- publicKeyDAO.delete(publicKeyDelete.pubKey).headOptionL
      _ = if (deletion.isEmpty) logger.error("Deletion seems to have failed...for " + publicKeyDelete.toString)
      _ <- earlyResponseIf(deletion.isEmpty)(OperationReturnsNone("Delete"))

    } yield true).onErrorRecover {
      case KeyNotExists(_) => false
      case InvalidVerification(_) => false
      case OperationReturnsNone(_) => false
      case e: Throwable => throw e
    }.runToFuture

  }

  def get(hwDeviceId: String): CancelableFuture[Seq[PublicKey]] = {
    (for {
      pubKeys <- publicKeyByHwDeviceIdDao
        .byHwDeviceId(hwDeviceId)
        .foldLeftL(Nil: Seq[PublicKey])((a, b) => a ++ Seq(b))
      _ = logger.info(s"Found ${pubKeys.size} results for: hardwareId=$hwDeviceId")

      validPubKeys <- Task.delay(pubKeys.filter(verification.validateTime))
      _ = logger.info(s"Valid keys ${pubKeys.size} results for: hardwareId=$hwDeviceId")

    } yield {
      validPubKeys
    }).runToFuture

  }

  def create(publicKey: PublicKey): CancelableFuture[PublicKey] = {
    (for {
      maybeKey <- publicKeyDAO.byPubKey(publicKey.pubKeyInfo.pubKey).headOptionL
      _ = if (maybeKey.isDefined) logger.info("Key found is " + maybeKey)
      _ <- earlyResponseIf(maybeKey.isDefined)(KeyExists(publicKey))

      verification <- Task.delay(verification.validate(publicKey))
      _ = if (!verification) logger.error("Verification failed " + maybeKey)
      _ <- earlyResponseIf(!verification)(InvalidVerification(publicKey))

      res <- publicKeyDAO.insert(publicKey).headOptionL
      _ = if (res.isEmpty) logger.error("Creation seems to have failed...for " + publicKey.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("Insert"))
    } yield publicKey).onErrorRecover {
      case KeyExists(publicKey) => publicKey
      case e: Throwable => throw e
    }.runToFuture
  }

  private def earlyResponseIf(condition: Boolean)(response: Exception): Task[Unit] =
    if (condition) Task.raiseError(response) else Task.unit

}

object DefaultPubKeyService {

  abstract class PubKeyServiceException(message: String) extends Exception(message) with NoStackTrace

  case class KeyExists(publicKey: PublicKey) extends PubKeyServiceException("Key provided already exits")

  case class InvalidVerification(publicKey: PublicKey) extends PubKeyServiceException("Invalid verification")

  case class OperationReturnsNone(message: String) extends PubKeyServiceException(message)

  case class KeyNotExists(publicKey: String) extends PubKeyServiceException("Key provided does not exist")
}
