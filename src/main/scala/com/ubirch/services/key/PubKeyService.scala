package com.ubirch.services.key

import java.util.concurrent.TimeoutException

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.crypto.utils.Curve
import com.ubirch.crypto.{ GeneratorKeyFactory, PubKey }
import com.ubirch.models._
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.services.kafka.KeyAnchoring
import com.ubirch.util.TaskHelpers
import io.prometheus.client.Counter
import javax.inject.{ Inject, Singleton }
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import org.apache.commons.codec.binary.Hex
import org.apache.kafka.clients.producer.RecordMetadata
import org.json4s.Formats

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.language.postfixOps
import scala.util.Try
import scala.util.control.NoStackTrace

/**
  * Represents a PubKeyService to work with PublicKeys
  */
trait PubKeyService {
  def create(publicKey: PublicKey, rawMessage: String): CancelableFuture[PublicKey]
  def create(pm: ProtocolMessage, rawMsgPack: String): CancelableFuture[PublicKey]
  def getSome(take: Int = 1): CancelableFuture[Seq[PublicKey]]
  def getByPubKeyId(pubKeyId: String): CancelableFuture[Seq[PublicKey]]
  def getByHardwareId(hwDeviceId: String): CancelableFuture[Seq[PublicKey]]
  def delete(publicKeyDelete: PublicKeyDelete): CancelableFuture[Boolean]
  def recreatePublicKey(encoded: Array[Byte], curve: Curve): Try[PubKey]
}

/**
  * Represents a default implementation of the PubKeyService
  * @param config Represents a configuration object
  * @param publicKeyDAO Represents the DAO for basic pub key queries
  * @param publicKeyByHwDeviceIdDao Represents the DAO for publicKeyByHwDeviceId queries
  * @param publicKeyByPubKeyIdDao Represents the DAO for publicKeyByPubKeyId queries
  * @param verification Represents the verification component.
  * @param jsonConverterService Represents a json converter convenience
  * @param scheduler Represents the scheduler async processes
  * @param jsFormats Represents the json formats
  */
@Singleton
class DefaultPubKeyService @Inject() (
    config: Config,
    publicKeyDAO: PublicKeyRowDAO,
    publicKeyByHwDeviceIdDao: PublicKeyRowByOwnerIdDAO,
    publicKeyByPubKeyIdDao: PublicKeyRowByPubKeyIdDAO,
    verification: PubKeyVerificationService,
    jsonConverterService: JsonConverterService,
    keyAnchoring: KeyAnchoring
)(implicit scheduler: Scheduler, jsFormats: Formats)
  extends PubKeyService with TaskHelpers with ServiceMetrics with LazyLogging {

  import DefaultPubKeyService._

  val service: String = config.getString(GenericConfPaths.NAME)

  val successCounter: Counter = Counter.build()
    .name("pubkey_management_success")
    .help("Represents the number of public key management successes")
    .labelNames("service", "method")
    .register()

  val errorCounter: Counter = Counter.build()
    .name("pubkey_management_failures")
    .help("Represents the number of public key management failures")
    .labelNames("service", "method")
    .register()

  def delete(publicKeyDelete: PublicKeyDelete): CancelableFuture[Boolean] = countWhen[Boolean]("delete")(t => t) {

    (for {
      _ <- Task.delay(logger.info("incoming_payload={}", publicKeyDelete.toString))
      maybeKey <- publicKeyDAO.byPubKeyId(publicKeyDelete.publicKey).headOptionL
      _ = if (maybeKey.isEmpty) logger.error("key_not_found={}", publicKeyDelete.publicKey)
      _ <- earlyResponseIf(maybeKey.isEmpty)(KeyNotExists(publicKeyDelete.publicKey))

      key = maybeKey.get
      pubKeyInfo = key.pubKeyInfoRow
      curve <- Task.fromTry(verification.getCurve(pubKeyInfo.algorithm))
      verification <- Task.delay(verification.validateFromBase64(publicKeyDelete.publicKey, publicKeyDelete.signature, curve))
      _ = if (!verification) logger.error("invalid_signature_on_key_deletion={}", publicKeyDelete)
      _ <- earlyResponseIf(!verification)(InvalidVerification(PublicKey.fromPublicKeyRow(key)))

      deletion <- del(publicKeyDelete.publicKey)

    } yield deletion).onErrorRecover {
      case KeyNotExists(_) => false
      case InvalidVerification(_) => false
      case OperationReturnsNone(_) => false
      case e: Throwable => throw e
    }.runToFuture

  }

  def getSome(take: Int): CancelableFuture[Seq[PublicKey]] = count("get_some") {
    (for {
      pubKeys <- publicKeyDAO
        .getSome(take)
        .map(PublicKey.fromPublicKeyRow)
        .foldLeftL(Nil: Seq[PublicKey])((a, b) => a ++ Seq(b))

    } yield {
      pubKeys
    }).runToFuture

  }

  def getByPubKeyId(pubKeyId: String): CancelableFuture[Seq[PublicKey]] = count("get_by_pub_key") {
    (for {
      pubKeys <- publicKeyByPubKeyIdDao
        .byPubKeyId(pubKeyId)
        .map(PublicKey.fromPublicKeyRow)
        .toListL

      validPubKeys <- Task.delay(pubKeys.filter(verification.validateTime)).map(sort)
      _ = logger.info("keys_found={} valid_keys_found={} pub_key_id={}", pubKeys.size, validPubKeys.size, pubKeyId)

    } yield {
      validPubKeys
    }).runToFuture

  }

  def getByHardwareId(hwDeviceId: String): CancelableFuture[Seq[PublicKey]] = count("get_by_hardware_id") {
    getByHardwareIdAsTask(hwDeviceId).runToFuture
  }

  def getByHardwareIdAsTask(hwDeviceId: String): Task[Seq[PublicKey]] = {
    for {
      pubKeys <- publicKeyByHwDeviceIdDao
        .byOwnerId(hwDeviceId)
        .map(PublicKey.fromPublicKeyRow)
        .toListL

      validPubKeys <- Task.delay(pubKeys.filter(verification.validateTime)).map(sort)

      _ = logger.info("keys_found={} valid_keys_found={} hardware_id={}", pubKeys.size, validPubKeys.size, hwDeviceId)

    } yield {
      validPubKeys
    }
  }

  def create(publicKey: PublicKey, rawMessage: String): CancelableFuture[PublicKey] = count("create") {
    createAndPublish(() => createFromJson(publicKey, rawMessage)).runToFuture
  }

  def create(pm: ProtocolMessage, rawMsgPack: String): CancelableFuture[PublicKey] = count("create_msg_pack") {
    createAndPublish(() => createFromMsgPack(pm, rawMsgPack)).runToFuture
  }

  private def createFromJson(publicKey: PublicKey, rawMessage: String): Task[PublicKey] = {
    (for {
      maybePrevKey <- getByHardwareIdAsTask(publicKey.pubKeyInfo.hwDeviceId).map(_.headOption)
      _ = if (publicKey.prevSignature.isDefined && maybePrevKey.isEmpty) logger.info("with_prev_sig={} prev_key={}", publicKey.prevSignature, maybePrevKey.toString)
      _ <- earlyResponseIf(publicKey.prevSignature.exists(_.isEmpty) && maybePrevKey.isDefined)(InvalidVerification(publicKey))
      _ = if (maybePrevKey.isDefined) logger.info("key_found={}", maybePrevKey.toString)

      prevSignatureVerification <- Task.delay(maybePrevKey.forall(x => verification.validate(x, publicKey)))
      _ = if (!prevSignatureVerification) logger.error("failed_prev_verification_for={}", publicKey.toString)
      _ <- earlyResponseIf(!prevSignatureVerification)(InvalidVerification(publicKey))

      verification <- Task.delay(verification.validate(publicKey))
      _ = if (!verification) logger.error("failed_verification_for={}", publicKey.toString)
      _ <- earlyResponseIf(!verification)(InvalidVerification(publicKey))

      row <- Task(PublicKeyRow.fromPublicKeyAsJson(publicKey, rawMessage))
      _ <- createRow(row)

    } yield publicKey).onErrorRecover {
      case KeyExists(publicKey) => publicKey
      case e: Throwable => throw e
    }

  }

  private def createFromMsgPack(pm: ProtocolMessage, rawMsgPack: String): Task[PublicKey] = {
    (for {

      text <- Task(pm.getPayload.toString)
      payloadJValue <- Task.fromTry {
        jsonConverterService.toJValue(text).toTry
      }
      _ = logger.info("protocol_message_payload={}", payloadJValue.toString)
      pubKeyInfo <- Task(payloadJValue).map { jv =>
        PublicKeyInfo
          .checkPubKeyId(PublicKeyInfo.fixValuesFomMsgPack(jv))
          .extract[PublicKeyInfo]
      }.onErrorRecover {
        case e: Exception => throw ParsingError(e.getMessage)
      }

      maybeKey <- publicKeyDAO.byPubKeyId(pubKeyInfo.pubKey).headOptionL
      _ = if (maybeKey.isDefined) logger.info("key_found={} ", maybeKey)

      publicKey <- Task.delay(PublicKey(pubKeyInfo, Hex.encodeHexString(pm.getSignature)))
      _ <- earlyResponseIf(maybeKey.isDefined)(KeyExists(publicKey))

      verification <- Task.delay(verification.validate(publicKey.pubKeyInfo, pm))
      _ = if (!verification) logger.error("failed_verification_for={}", publicKey.toString)
      _ <- earlyResponseIf(!verification)(InvalidVerification(publicKey))

      row <- Task(PublicKeyRow.fromPublicKeyAsMsgPack(publicKey, rawMsgPack))
      _ <- createRow(row)

    } yield publicKey)
      .onErrorRecover {
        case KeyExists(publicKey) => publicKey
        case e: Throwable => throw e
      }
  }

  def createAndPublish(fk: () => Task[PublicKey]): Task[PublicKey] = {
    (for {
      publicKey <- fk()
      _ <- publish(publicKey)
    } yield publicKey)
      .onErrorRecoverWith {
        case e @ FailedKafkaPublish(publicKey, _) =>
          //We try and delete the key and still fail
          del(publicKey.pubKeyInfo.pubKeyId)
            .flatMap(_ => Task.raiseError(e))
            .onErrorHandleWith(_ => Task.raiseError(e))

      }
  }

  private def publish(publicKey: PublicKey, timeout: FiniteDuration = 10 seconds): Task[(RecordMetadata, PublicKey)] = {
    for {
      maybeRM <- keyAnchoring.anchorKeyAsOpt(publicKey)
        .timeoutWith(timeout, FailedKafkaPublish(publicKey, Option(new TimeoutException(s"failed_publish_timeout=${timeout.toString()}"))))
        .onErrorHandleWith(e => Task.raiseError(FailedKafkaPublish(publicKey, Option(e))))
      _ = if (maybeRM.isEmpty) logger.error("failed_publish={}", publicKey.toString)
      _ = if (maybeRM.isDefined) logger.info("publish_succeeded_for={}", publicKey.pubKeyInfo.pubKeyId)
      _ <- earlyResponseIf(maybeRM.isEmpty)(FailedKafkaPublish(publicKey, None))
    } yield {
      (maybeRM.get, publicKey)
    }
  }

  def createRow(publicKeyRow: PublicKeyRow): Task[Option[Unit]] = {
    for {
      res <- publicKeyDAO.insert(publicKeyRow).headOptionL
      _ = if (res.isEmpty) logger.error("failed_creation={} ", publicKeyRow.toString)
      _ = if (res.isDefined) logger.info("creation_succeeded={}", publicKeyRow.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("Core_Insert"))
    } yield {
      res
    }
  }

  private def del(pubKeyId: String): Task[Boolean] = {
    for {
      deletion <- publicKeyDAO.delete(pubKeyId).headOptionL
      _ = if (deletion.isEmpty) logger.error("failed_key_deletion={}", pubKeyId)
      _ = if (deletion.isDefined) logger.info("key_deletion_succeeded={}", pubKeyId)
    } yield {
      deletion.isDefined
    }
  }

  def recreatePublicKey(encoded: Array[Byte], curve: Curve): Try[PubKey] = {

    def recreate(bytes: Array[Byte]) = Try(GeneratorKeyFactory.getPubKey(bytes, curve))

    val bytesLength = encoded.length
    for {
      bytesToUse <- Try {
        //We are slicing as only the latest 64 bytes are needed to recreate the key.
        if (curve == Curve.PRIME256V1) encoded.slice(bytesLength - 64, bytesLength) else encoded
      }
      pubKey <- recreate(bytesToUse)
    } yield {
      pubKey
    }

  }

  def sort(publicKeys: Seq[PublicKey]): Seq[PublicKey] = {
    publicKeys
      .sortWith { (a, b) => a.pubKeyInfo.created.after(b.pubKeyInfo.created) }
      .sortWith { (a, _) => a.prevSignature.isDefined }
  }

}

/**
  * Represents the companion object for the DefaultPubKeyService
  */
object DefaultPubKeyService {

  abstract class PubKeyServiceException(message: String) extends Exception(message) with NoStackTrace

  case class ParsingError(message: String) extends PubKeyServiceException(message)
  case class KeyExists(publicKey: PublicKey) extends PubKeyServiceException("Key provided already exits")
  case class InvalidVerification(publicKey: PublicKey) extends PubKeyServiceException("Invalid verification")
  case class FailedKafkaPublish(publicKey: PublicKey, maybeThrowable: Option[Throwable]) extends PubKeyServiceException(maybeThrowable.map(_.getMessage).getOrElse("Failed Publish"))
  case class OperationReturnsNone(message: String) extends PubKeyServiceException(message)
  case class KeyNotExists(publicKey: String) extends PubKeyServiceException("Key provided does not exist")

}
