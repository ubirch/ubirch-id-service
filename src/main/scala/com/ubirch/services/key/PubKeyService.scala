package com.ubirch
package services.key

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.crypto.utils.Curve
import com.ubirch.crypto.{ GeneratorKeyFactory, PubKey }
import com.ubirch.models._
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.services.kafka.KeyAnchoring
import com.ubirch.util.TaskHelpers
import javax.inject.{ Inject, Singleton }
import monix.eval.Task
import org.apache.commons.codec.binary.Hex
import org.json4s.Formats

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

/**
  * Represents a PubKeyService to work with PublicKeys
  */
trait PubKeyService {
  def create(publicKey: PublicKey, rawMessage: String): Task[PublicKey]
  def create(pm: ProtocolMessage, rawMsgPack: String): Task[PublicKey]
  def getSome(take: Int = 1): Task[Seq[PublicKey]]
  def getByPubKeyId(pubKeyId: String): Task[Seq[PublicKey]]
  def getByHardwareId(hwDeviceId: String): Task[Seq[PublicKey]]
  def delete(publicKeyDelete: PublicKeyDelete): Task[Boolean]
  def recreatePublicKey(encoded: Array[Byte], curve: Curve): Try[PubKey]
  def createRow(publicKey: PublicKey, rawMsg: String): Task[(PublicKeyRow, Option[Unit])]
  def anchorAfter(timeout: FiniteDuration = 10 seconds)(fk: () => Task[PublicKey]): Task[PublicKey]
}

/**
  * Represents a default implementation of the PubKeyService
  * @param publicKeyDAO Represents the DAO for basic pub key queries
  * @param publicKeyByHwDeviceIdDao Represents the DAO for publicKeyByHwDeviceId queries
  * @param publicKeyByPubKeyIdDao Represents the DAO for publicKeyByPubKeyId queries
  * @param verification Represents the verification component.
  * @param jsonConverterService Represents a json converter convenience
  * @param jsFormats Represents the json formats
  */
@Singleton
class DefaultPubKeyService @Inject() (
    publicKeyDAO: PublicKeyRowDAO,
    publicKeyByHwDeviceIdDao: PublicKeyRowByOwnerIdDAO,
    publicKeyByPubKeyIdDao: PublicKeyRowByPubKeyIdDAO,
    verification: PubKeyVerificationService,
    jsonConverterService: JsonConverterService,
    keyAnchoring: KeyAnchoring
)(implicit jsFormats: Formats) extends PubKeyService with TaskHelpers with LazyLogging {

  def delete(publicKeyDelete: PublicKeyDelete): Task[Boolean] = {

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
      _ <- earlyResponseIf(!verification)(InvalidKeyVerification(PublicKey.fromPublicKeyRow(key)))

      deletion <- del(publicKeyDelete.publicKey)

    } yield deletion).onErrorRecover {
      case KeyNotExists(_) => false
      case InvalidKeyVerification(_) => false
      case OperationReturnsNone(_) => false
      case e: Throwable => throw e
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

  def getSome(take: Int): Task[Seq[PublicKey]] = {
    for {
      pubKeys <- publicKeyDAO
        .getSome(take)
        .map(PublicKey.fromPublicKeyRow)
        .toListL

    } yield {
      pubKeys
    }
  }

  def getByPubKeyId(pubKeyId: String): Task[Seq[PublicKey]] = {
    for {
      pubKeys <- publicKeyByPubKeyIdDao
        .byPubKeyId(pubKeyId)
        .map(PublicKey.fromPublicKeyRow)
        .toListL

      validPubKeys <- Task.delay(pubKeys.filter(verification.validateTime)).map(sort)
      _ = logger.info("keys_found={} valid_keys_found={} pub_key_id={}", pubKeys.size, validPubKeys.size, pubKeyId)

    } yield {
      validPubKeys
    }
  }

  def getByHardwareId(hwDeviceId: String): Task[Seq[PublicKey]] = {
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

  def sort(publicKeys: Seq[PublicKey]): Seq[PublicKey] = {
    publicKeys
      .sortWith { (a, b) => a.pubKeyInfo.created.after(b.pubKeyInfo.created) }
      .sortWith { (a, _) => a.prevSignature.isDefined }
  }

  def create(publicKey: PublicKey, rawMessage: String): Task[PublicKey] = {
    anchorAfter()(() => createFromJson(publicKey, rawMessage))
  }

  private def createFromJson(publicKey: PublicKey, rawMessage: String): Task[PublicKey] = {
    (for {
      //Check if already exists
      maybePubKeyExists <- getByPubKeyId(publicKey.pubKeyInfo.pubKeyId).map(_.headOption)
      _ = if (maybePubKeyExists.isDefined) logger.info("key_found={}", maybePubKeyExists.toString)
      _ <- earlyResponseIf(maybePubKeyExists.map(_.pubKeyInfo).contains(publicKey.pubKeyInfo))(KeyExists(publicKey))


      maybePrevKey <- getByPubKeyId(publicKey.pubKeyInfo.prevPubKeyId.getOrElse("")).map(_.headOption)
      _ = if (maybePrevKey.isDefined) logger.info("prev_key_found={}", maybePrevKey.toString)

      initialVerification = !(
        publicKey.prevSignature.exists(_.nonEmpty) && maybePrevKey.isDefined &&
        publicKey.pubKeyInfo.prevPubKeyId.exists(_.nonEmpty) &&
        Option(publicKey.pubKeyInfo.hwDeviceId) == maybePrevKey.map(_.pubKeyInfo.hwDeviceId)
        )

      _ = if (initialVerification) logger.error("failed_init_verification_for={}", publicKey.toString)
      _ <- earlyResponseIf(initialVerification)(InvalidKeyVerification(publicKey))

      prevSignatureVerification <- Task.delay(maybePrevKey.forall(verification.validate(_, publicKey)))
      _ = if (!prevSignatureVerification) logger.error("failed_prev_verification_for={}", publicKey.toString)
      _ <- earlyResponseIf(!prevSignatureVerification)(InvalidKeyVerification(publicKey))

      maybeKey <- publicKeyDAO.byPubKeyId(publicKey.pubKeyInfo.pubKey)
        .headOptionL
        .map(_.filter(x => verification.validateTime(PublicKey.fromPublicKeyRow(x))))
      _ = if (maybeKey.isDefined) logger.info("key_found={}", maybeKey.toString)
      _ <- earlyResponseIf(maybeKey.isDefined)(KeyExists(publicKey))

      verification <- Task.delay(verification.validate(publicKey))
      _ = if (!verification) logger.error("failed_verification_for={}", publicKey.toString)
      _ <- earlyResponseIf(!verification)(InvalidKeyVerification(publicKey))

      _ <- createRow(publicKey, rawMessage)

    } yield publicKey).onErrorRecover {
      case KeyExists(publicKey) => publicKey
      case e: Throwable => throw e
    }

  }

  def create(pm: ProtocolMessage, rawMsgPack: String): Task[PublicKey] = {
    anchorAfter()(() => createFromMsgPack(pm, rawMsgPack))
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

      maybeKey <- publicKeyDAO.byPubKeyId(pubKeyInfo.pubKey)
        .headOptionL
        .map(_.filter(x => verification.validateTime(PublicKey.fromPublicKeyRow(x))))
      _ = if (maybeKey.isDefined) logger.info("key_found={} ", maybeKey)

      publicKey <- Task.delay(PublicKey(pubKeyInfo, Hex.encodeHexString(pm.getSignature)))
      _ <- earlyResponseIf(maybeKey.isDefined)(KeyExists(publicKey))

      verification <- Task.delay(verification.validate(publicKey.pubKeyInfo, pm))
      _ = if (!verification) logger.error("failed_verification_for={}", publicKey.toString)
      _ <- earlyResponseIf(!verification)(InvalidKeyVerification(publicKey))

      _ <- createRow(publicKey, rawMsgPack)

    } yield publicKey)
      .onErrorRecover {
        case KeyExists(publicKey) => publicKey
        case e: Throwable => throw e
      }
  }

  def createRow(publicKey: PublicKey, rawMsg: String): Task[(PublicKeyRow, Option[Unit])] = {
    for {
      row <- Task(PublicKeyRow.fromPublicKeyAsMsgPack(publicKey, rawMsg))
      res <- createRow(row)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("PubKey_Insert"))
    } yield {
      (row, res)
    }
  }

  def createRow(publicKeyRow: PublicKeyRow): Task[Option[Unit]] = {
    for {
      res <- publicKeyDAO.insert(publicKeyRow).headOptionL
      _ = if (res.isEmpty) logger.error("failed_key_creation={} ", publicKeyRow.toString)
      _ = if (res.isDefined) logger.info("key_creation_succeeded={}", publicKeyRow.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("Core_Insert"))
    } yield {
      res
    }
  }

  def anchorAfter(timeout: FiniteDuration = 10 seconds)(fk: () => Task[PublicKey]): Task[PublicKey] = {
    (for {
      publicKey <- fk()
      _ <- keyAnchoring.anchorKey(publicKey, timeout)
    } yield publicKey)
      .onErrorRecoverWith {
        case e @ FailedKafkaPublish(publicKey, _) =>
          //We try and delete the key and still fail
          del(publicKey.pubKeyInfo.pubKeyId)
            .flatMap(_ => Task.raiseError(e))
            .onErrorHandleWith(_ => Task.raiseError(e))

      }
  }

  def recreatePublicKey(encoded: Array[Byte], curve: Curve): Try[PubKey] = {
    require(encoded.nonEmpty, "zero bytes found")

    def recreate(bytes: Array[Byte]) = Try(GeneratorKeyFactory.getPubKey(bytes, curve))

    val bytesLength = encoded.length
    for {
      bytesToUse <- Try {
        //We are slicing as only the latest 64 bytes are needed to recreate the key.
        if (curve == Curve.PRIME256V1) encoded.slice(bytesLength - 64, bytesLength)
        else if (curve == Curve.Ed25519) encoded.slice(bytesLength - 32, bytesLength)
        else encoded
      }
      pubKey <- recreate(bytesToUse)
    } yield {
      pubKey
    }

  }

}
