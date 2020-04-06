package com.ubirch.services.key

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.models._
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.formats.JsonConverterService
import io.prometheus.client.Counter
import javax.inject.{ Inject, Singleton }
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import org.apache.commons.codec.binary.Hex
import org.json4s.Formats

import scala.util.control.NoStackTrace
import scala.util.{ Failure, Success }

/**
  * Represents a PubKeyService to work with PublicKeys
  */
trait PubKeyService {
  def create(publicKey: PublicKey, rawMessage: String): CancelableFuture[PublicKey]
  def create(pm: ProtocolMessage, rawMsgPack: String): CancelableFuture[PublicKey]
  def getByPubKeyId(pubKeyId: String): CancelableFuture[Seq[PublicKey]]
  def getByHardwareId(hwDeviceId: String): CancelableFuture[Seq[PublicKey]]
  def delete(publicKeyDelete: PublicKeyDelete): CancelableFuture[Boolean]
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
    publicKeyByHwDeviceIdDao: PublicKeyRowByHwDeviceIdDAO,
    publicKeyByPubKeyIdDao: PublicKeyRowByPubKeyIdDAO,
    verification: PubKeyVerificationService,
    jsonConverterService: JsonConverterService
)(implicit scheduler: Scheduler, jsFormats: Formats)
  extends PubKeyService with LazyLogging {

  import DefaultPubKeyService._

  val service: String = config.getString(GenericConfPaths.NAME)

  val successCounter = Counter.build()
    .name("pubkey_management_success")
    .help("Represents the number of public key management successes")
    .labelNames("service", "method")
    .register()

  val errorCounter = Counter.build()
    .name("pubkey_management_failures")
    .help("Represents the number of public key management failures")
    .labelNames("service", "method")
    .register()

  def countWhen[T](method: String)(ft: T => Boolean)(cf: CancelableFuture[T]): CancelableFuture[T] = {

    def s = successCounter.labels(service, method).inc()
    def f = errorCounter.labels(service, method).inc()

    cf.onComplete {
      case Success(t) => if (ft(t)) s else f
      case Failure(_) => f
    }
    cf
  }

  def count[T](method: String)(cf: CancelableFuture[T]): CancelableFuture[T] = {
    cf.onComplete {
      case Success(_) => successCounter.labels(service, method).inc()
      case Failure(_) => errorCounter.labels(service, method).inc()
    }
    cf
  }

  def delete(publicKeyDelete: PublicKeyDelete): CancelableFuture[Boolean] = countWhen[Boolean]("delete")(t => t) {

    (for {
      _ <- Task.delay(logger.info("incoming_payload={}", publicKeyDelete.toString))
      maybeKey <- publicKeyDAO.byPubKeyId(publicKeyDelete.publicKey).headOptionL
      _ = if (maybeKey.isEmpty) logger.error("key_not_found={}", publicKeyDelete.publicKey)
      _ <- earlyResponseIf(maybeKey.isEmpty)(KeyNotExists(publicKeyDelete.publicKey))

      key = maybeKey.get
      pubKeyInfo = key.pubKeyInfoRow
      curve = verification.getCurve(pubKeyInfo.algorithm)
      verification <- Task.delay(verification.validateFromBase64(publicKeyDelete.publicKey, publicKeyDelete.signature, curve))
      _ = if (!verification) logger.error("invalid_signature_on_key_deletion={}", publicKeyDelete)
      _ <- earlyResponseIf(!verification)(InvalidVerification(PublicKey.fromPublicKeyRow(key)))

      deletion <- publicKeyDAO.delete(publicKeyDelete.publicKey).headOptionL
      _ = if (deletion.isEmpty) logger.error("failed_key_deletion={}", publicKeyDelete.toString)
      _ = if (deletion.isDefined) logger.info("key_deletion_succeeded={}", publicKeyDelete.toString)
      _ <- earlyResponseIf(deletion.isEmpty)(OperationReturnsNone("Delete"))

    } yield true).onErrorRecover {
      case KeyNotExists(_) => false
      case InvalidVerification(_) => false
      case OperationReturnsNone(_) => false
      case e: Throwable => throw e
    }.runToFuture

  }

  def getByPubKeyId(pubKeyId: String): CancelableFuture[Seq[PublicKey]] = count("get_by_pub_key") {
    (for {
      pubKeys <- publicKeyByPubKeyIdDao
        .byPubKeyId(pubKeyId)
        .map(PublicKey.fromPublicKeyRow)
        .foldLeftL(Nil: Seq[PublicKey])((a, b) => a ++ Seq(b))

      validPubKeys <- Task.delay(pubKeys.filter(verification.validateTime))
      _ = logger.info("keys_found={} valid_keys_found={} pub_key_id={}", pubKeys.size, validPubKeys.size, pubKeyId)

    } yield {
      validPubKeys
    }).runToFuture

  }

  def getByHardwareId(hwDeviceId: String): CancelableFuture[Seq[PublicKey]] = count("get_by_hardware_id") {
    (for {
      pubKeys <- publicKeyByHwDeviceIdDao
        .byHwDeviceId(hwDeviceId)
        .map(PublicKey.fromPublicKeyRow)
        .foldLeftL(Nil: Seq[PublicKey])((a, b) => a ++ Seq(b))

      validPubKeys <- Task.delay(pubKeys.filter(verification.validateTime))
      _ = logger.info("keys_found={} valid_keys_found={} hardware_id={}", pubKeys.size, validPubKeys.size, hwDeviceId)

    } yield {
      validPubKeys
    }).runToFuture

  }

  def create(publicKey: PublicKey, rawMessage: String): CancelableFuture[PublicKey] = count("create") {
    (for {
      maybeKey <- publicKeyDAO.byPubKeyId(publicKey.pubKeyInfo.pubKey).headOptionL
      _ = if (maybeKey.isDefined) logger.info("key_found={}", maybeKey.toString)
      _ <- earlyResponseIf(maybeKey.isDefined)(KeyExists(publicKey))

      verification <- Task.delay(verification.validate(publicKey))
      _ = if (!verification) logger.error("failed_verification={}", maybeKey.toString)
      _ <- earlyResponseIf(!verification)(InvalidVerification(publicKey))

      row <- Task(PublicKeyRow.fromPublicKeyAsJson(publicKey, rawMessage))
      res <- publicKeyDAO.insert(row).headOptionL
      _ = if (res.isEmpty) logger.error("failed_creation={} ", publicKey.toString)
      _ = if (res.isDefined) logger.info("creation_succeeded={}", publicKey.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("Json_Insert"))
    } yield publicKey).onErrorRecover {
      case KeyExists(publicKey) => publicKey
      case e: Throwable => throw e
    }.runToFuture

  }

  def create(pm: ProtocolMessage, rawMsgPack: String): CancelableFuture[PublicKey] = count("create_msg_pack") {
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
      _ = if (!verification) logger.error("failed_verification={}", publicKey.toString)
      _ <- earlyResponseIf(!verification)(InvalidVerification(publicKey))

      row <- Task(PublicKeyRow.fromPublicKeyAsMsgPack(publicKey, rawMsgPack))
      res <- publicKeyDAO.insert(row).headOptionL
      _ = if (res.isEmpty) logger.error("failed_creation={} ", publicKey.toString)
      _ = if (res.isDefined) logger.info("creation_succeeded={}", publicKey.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("Msg_Pack_Insert"))

    } yield publicKey).onErrorRecover {
      case KeyExists(publicKey) => publicKey
      case e: Throwable => throw e
    }.runToFuture

  }

  private def earlyResponseIf(condition: Boolean)(response: Exception): Task[Unit] =
    if (condition) Task.raiseError(response) else Task.unit

}

/**
  * Represents the companion object for the DefaultPubKeyService
  */
object DefaultPubKeyService {

  abstract class PubKeyServiceException(message: String) extends Exception(message) with NoStackTrace

  case class ParsingError(message: String) extends PubKeyServiceException(message)

  case class KeyExists(publicKey: PublicKey) extends PubKeyServiceException("Key provided already exits")

  case class InvalidVerification(publicKey: PublicKey) extends PubKeyServiceException("Invalid verification")

  case class OperationReturnsNone(message: String) extends PubKeyServiceException(message)

  case class KeyNotExists(publicKey: String) extends PubKeyServiceException("Key provided does not exist")
}
