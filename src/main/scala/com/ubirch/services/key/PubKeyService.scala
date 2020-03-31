package com.ubirch.services.key

import java.util.Base64

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.models._
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.protocol.codec.UUIDUtil
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.util.DateUtil
import io.prometheus.client.Counter
import javax.inject.{ Inject, Singleton }
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import org.apache.commons.codec.binary.Hex
import org.json4s.Formats
import org.json4s.JsonAST.{ JInt, JString }

import scala.util.control.NoStackTrace
import scala.util.{ Failure, Success }

trait PubKeyService {
  def create(publicKey: PublicKey, rawMessage: String): CancelableFuture[PublicKey]
  def create(pm: ProtocolMessage, rawMsgPack: String): CancelableFuture[PublicKey]
  def getByPubKeyId(pubKeyId: String): CancelableFuture[Seq[PublicKey]]
  def getByHardwareId(hwDeviceId: String): CancelableFuture[Seq[PublicKey]]
  def delete(publicKeyDelete: PublicKeyDelete): CancelableFuture[Boolean]
}

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
      maybeKey <- publicKeyDAO.byPubKeyId(publicKeyDelete.publicKey).headOptionL
      _ = if (maybeKey.isEmpty) logger.error("No key found with public key: " + publicKeyDelete.publicKey)
      _ <- earlyResponseIf(maybeKey.isEmpty)(KeyNotExists(publicKeyDelete.publicKey))

      key = maybeKey.get
      pubKeyInfo = key.pubKeyInfoRow
      curve = verification.getCurve(pubKeyInfo.algorithm)
      verification <- Task.delay(verification.validateFromBase64(publicKeyDelete.publicKey, publicKeyDelete.signature, curve))
      _ = if (!verification) logger.error("Unable to delete public key with invalid signature: " + publicKeyDelete)
      _ <- earlyResponseIf(!verification)(InvalidVerification(PublicKey.fromPublicKeyRow(key)))

      deletion <- publicKeyDAO.delete(publicKeyDelete.publicKey).headOptionL
      _ = if (deletion.isEmpty) logger.error("Deletion seems to have failed...for " + publicKeyDelete.toString)
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
      _ = logger.info(s"Found ${pubKeys.size} results for: pubKeyId=$pubKeyId")

      validPubKeys <- Task.delay(pubKeys.filter(verification.validateTime))
      _ = logger.info(s"Valid keys ${validPubKeys.size} results for: pubKeyId=$pubKeyId")

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
      _ = logger.info(s"Found ${pubKeys.size} results for: hardwareId=$hwDeviceId")

      validPubKeys <- Task.delay(pubKeys.filter(verification.validateTime))
      _ = logger.info(s"Valid keys ${validPubKeys.size} results for: hardwareId=$hwDeviceId")

    } yield {
      validPubKeys
    }).runToFuture

  }

  def create(publicKey: PublicKey, rawMessage: String): CancelableFuture[PublicKey] = count("create") {
    (for {
      maybeKey <- publicKeyDAO.byPubKeyId(publicKey.pubKeyInfo.pubKey).headOptionL
      _ = if (maybeKey.isDefined) logger.info("Key found is " + maybeKey)
      _ <- earlyResponseIf(maybeKey.isDefined)(KeyExists(publicKey))

      verification <- Task.delay(verification.validate(publicKey))
      _ = if (!verification) logger.error("Verification failed " + maybeKey)
      _ <- earlyResponseIf(!verification)(InvalidVerification(publicKey))

      row <- Task(PublicKeyRow.fromPublicKeyAsJson(publicKey, rawMessage))
      res <- publicKeyDAO.insert(row).headOptionL
      _ = if (res.isEmpty) logger.error("Creation seems to have failed...for " + publicKey.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("Insert"))
    } yield publicKey).onErrorRecover {
      case KeyExists(publicKey) => publicKey
      case e: Throwable => throw e
    }.runToFuture

  }

  def create(pm: ProtocolMessage, rawMsgPack: String): CancelableFuture[PublicKey] = count("create_msgpack") {
    (for {

      text <- Task(pm.getPayload.toString)
      payloadJValue <- Task.fromTry {
        jsonConverterService.toJValue(text).toTry
      }
      _ = logger.info("protocol_message_payload {}", payloadJValue.toString)
      pubKeyInfo <- Task(payloadJValue).map { jv =>
        def formatter(time: Long) = DateUtil.ISOFormatter.print(time.toLong * 1000)
        jv.mapField {
          case ("algorithm", JString(value)) => ("algorithm", JString(new String(Base64.getDecoder.decode(value))))
          case ("hwDeviceId", JString(value)) => ("hwDeviceId", JString(UUIDUtil.bytesToUUID(Base64.getDecoder.decode(value)).toString))
          case ("created", JInt(num)) => ("created", JString(formatter(num.toLong)))
          case ("validNotAfter", JInt(num)) => ("validNotAfter", JString(formatter(num.toLong)))
          case ("validNotBefore", JInt(num)) => ("validNotBefore", JString(formatter(num.toLong)))
          case x => x
        }.extract[PublicKeyInfo]
      }

      maybeKey <- publicKeyDAO.byPubKeyId(pubKeyInfo.pubKey).headOptionL
      _ = if (maybeKey.isDefined) logger.info("Key found is " + maybeKey)

      publicKey <- Task.delay(PublicKey(pubKeyInfo, Hex.encodeHexString(pm.getSignature)))
      _ <- earlyResponseIf(maybeKey.isDefined)(KeyExists(publicKey))

      verification <- Task.delay(verification.validate(publicKey.pubKeyInfo, pm))
      _ = if (!verification) logger.error("Verification failed " + maybeKey)
      _ <- earlyResponseIf(!verification)(InvalidVerification(publicKey))

      row <- Task(PublicKeyRow.fromPublicKeyAsMsgPack(publicKey, rawMsgPack))
      res <- publicKeyDAO.insert(row).headOptionL
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
