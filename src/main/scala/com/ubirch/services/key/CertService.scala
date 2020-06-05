package com.ubirch.services.key

import java.io.ByteArrayInputStream
import java.security.cert.{ CertificateFactory, X509Certificate }
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ IdentitiesDAO, Identity, IdentityRow, PublicKeyInfo }
import com.ubirch.util.{ CertUtil, PublicKeyUtil, TaskHelpers }
import javax.inject.{ Inject, Singleton }
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.operator.ContentVerifierProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.bouncycastle.util.encoders.{ Base64, Hex }

import scala.util.Try
import scala.util.control.NoStackTrace

/**
  * Basic description of what a CertService is
  */
trait CertService {
  def extractCert(request: Array[Byte]): Try[X509Certificate]
  def processCert(cert: X509Certificate): CancelableFuture[PublicKeyInfo]
  def processCSR(csr: JcaPKCS10CertificationRequest): CancelableFuture[PublicKeyInfo]
  def extractCRS(request: Array[Byte]): Try[JcaPKCS10CertificationRequest]
}

/**
  * Default implementation of a CertService
  * @param pubKeyService Service for managing public keys
  * @param identitiesDAO DAO for the identities
  * @param scheduler Executor Scheduler.
  */
@Singleton
class DefaultCertService @Inject() (pubKeyService: PubKeyService, identitiesDAO: IdentitiesDAO)(implicit scheduler: Scheduler) extends CertService with TaskHelpers with LazyLogging {

  import DefaultCertService._

  override def processCSR(csr: JcaPKCS10CertificationRequest): CancelableFuture[PublicKeyInfo] = {
    (for {
      verification <- Task.delay(verifyCSR(csr))
      _ = if (!verification) logger.error("failed_verification_for={}", csr.toString)
      _ <- earlyResponseIf(!verification)(InvalidVerification(csr))

      cn <- lift(CertUtil.getCN(csr.getSubject))(InvalidCN(csr))
      cnAsString <- lift(CertUtil.rdnToString(cn))(InvalidCN(csr))
      uuid <- liftTry(CertUtil.buildUUID(cnAsString))(InvalidUUID(cnAsString))

      alg <- lift(CertUtil.algorithmName(csr.getSignatureAlgorithm)
        .getOrElse(csr.getPublicKey.getAlgorithm))(UnknownSignatureAlgorithm("Unknown Algorithm"))
      curve <- liftTry(PublicKeyUtil.associateCurve(alg))(UnknownCurve("Unknown curve for " + alg))

      pubKey <- liftTry(pubKeyService.recreatePublicKey(csr.getPublicKey.getEncoded, curve))(RecreationException("Error recreating pubkey"))
      pubKeyAsBase64 <- liftTry(Try(Base64.toBase64String(pubKey.getPublicKey.getEncoded)))(EncodingException("Error encoding key into base 64"))

      data_id <- liftTry(Try(Hex.toHexString(csr.getEncoded)))(EncodingException("Error encoding data_id"))

      identity = Identity(uuid.toString, "CSR", data_id, "This is a description")
      identityRow = IdentityRow.fromIdentity(identity)

      exists <- identitiesDAO.byIdAndDataId(identityRow.id, identityRow.data_id).headOptionL
      _ <- earlyResponseIf(exists.isDefined)(IdentityAlreadyExistsException(cnAsString))

      res <- identitiesDAO.insert(IdentityRow.fromIdentity(identity)).headOptionL
      _ = if (res.isEmpty) logger.error("failed_creation={} ", identityRow.toString)
      _ = if (res.isDefined) logger.info("creation_succeeded={}", identityRow.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("CSR_Insert"))

    } yield {
      PublicKeyInfo(alg, new Date(), uuid.toString, pubKeyAsBase64, pubKeyAsBase64, None, new Date())
    }).runToFuture

  }

  def extractCRS(request: Array[Byte]): Try[JcaPKCS10CertificationRequest] = {
    for {
      csr <- materializeCRS(request).toTry
      _ = logger.info("crs_extracted={}", csr.getSubject.toString)
    } yield {
      csr
    }
  }

  private def verifyCSR(request: Array[Byte]): Boolean = {
    val provider = new org.bouncycastle.jce.provider.BouncyCastleProvider()
    val jcaRequest = new JcaPKCS10CertificationRequest(request).setProvider(provider)
    verifyCSR(jcaRequest)
  }

  private def verifyCSR(jcaRequest: JcaPKCS10CertificationRequest): Boolean = {
    val provider = new org.bouncycastle.jce.provider.BouncyCastleProvider()
    val key = jcaRequest.getPublicKey
    val verifierProvider: ContentVerifierProvider =
      new JcaContentVerifierProviderBuilder()
        .setProvider(provider).build(key)

    jcaRequest.isSignatureValid(verifierProvider)
  }

  private def materializeCRS(request: Array[Byte]): Either[Throwable, JcaPKCS10CertificationRequest] = {
    (for {
      provider <- Try(new org.bouncycastle.jce.provider.BouncyCastleProvider())
      cert <- Try(new JcaPKCS10CertificationRequest(request).setProvider(provider))
    } yield {
      cert
    }).toEither
  }

  override def processCert(cert: X509Certificate): CancelableFuture[PublicKeyInfo] = {
    (for {
      _ <- lift(cert.checkValidity())(InvalidCertVerification(cert))

      cn <- lift(CertUtil.getCN(new JcaX509CertificateHolder(cert).getSubject))(InvalidCertCN(cert))
      cnAsString <- lift(CertUtil.rdnToString(cn))(InvalidCertCN(cert))
      uuid <- liftTry(CertUtil.buildUUID(cnAsString))(InvalidUUID(cnAsString))

      alg = cert.getSigAlgName
      curve <- liftTry(PublicKeyUtil.associateCurve(alg))(UnknownCurve("Unknown curve for " + alg))

      pubKey <- liftTry(pubKeyService.recreatePublicKey(cert.getPublicKey.getEncoded, curve))(RecreationException("Error recreating pubkey"))
      pubKeyAsBase64 <- liftTry(Try(Base64.toBase64String(pubKey.getPublicKey.getEncoded)))(EncodingException("Error encoding key into base 64"))

      data_id <- liftTry(Try(Hex.toHexString(cert.getEncoded)))(EncodingException("Error encoding data_id"))

      identity = Identity(uuid.toString, "X509", data_id, "This is a description")
      identityRow = IdentityRow.fromIdentity(identity)

      exists <- identitiesDAO.byIdAndDataId(identityRow.id, identityRow.data_id).headOptionL
      _ <- earlyResponseIf(exists.isDefined)(IdentityAlreadyExistsException(cnAsString))

      res <- identitiesDAO.insert(IdentityRow.fromIdentity(identity)).headOptionL
      _ = if (res.isEmpty) logger.error("failed_creation={} ", identityRow.toString)
      _ = if (res.isDefined) logger.info("creation_succeeded={}", identityRow.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("CERT_Insert"))

    } yield {
      PublicKeyInfo(alg, new Date(), uuid.toString, pubKeyAsBase64, pubKeyAsBase64, Option(cert.getNotAfter), cert.getNotBefore)
    }).runToFuture
  }

  def extractCert(request: Array[Byte]): Try[X509Certificate] = {
    for {
      cert <- materializeCert(request).toTry
      _ = logger.info("crs_extracted={}", cert.toString)
    } yield {
      cert
    }
  }

  private def materializeCert(certBin: Array[Byte]): Either[Throwable, X509Certificate] = {
    (for {
      factory <- Try(CertificateFactory.getInstance("X.509"))
      cert <- Try(factory.generateCertificate(new ByteArrayInputStream(certBin)).asInstanceOf[X509Certificate])
    } yield {
      cert
    }).toEither
  }

}

/**
  * Convinience object for the Cert Service
  */
object DefaultCertService {

  abstract class CertServiceException(message: String) extends Exception(message) with NoStackTrace

  case class InvalidVerification(csr: JcaPKCS10CertificationRequest) extends CertServiceException("Invalid CSR verification")

  case class InvalidCertVerification(csr: X509Certificate) extends CertServiceException("Invalid cert verification")
  case class InvalidCN(csr: JcaPKCS10CertificationRequest) extends CertServiceException("Invalid Common Name in CSR")
  case class InvalidCertCN(csr: X509Certificate) extends CertServiceException("Invalid Common Name in Cert")
  case class InvalidUUID(message: String) extends CertServiceException(message)
  case class UnknownSignatureAlgorithm(message: String) extends CertServiceException(message)
  case class UnknownCurve(message: String) extends CertServiceException(message)
  case class RecreationException(message: String) extends CertServiceException(message)
  case class EncodingException(message: String) extends CertServiceException(message)
  case class IdentityAlreadyExistsException(message: String) extends CertServiceException(message)
  case class OperationReturnsNone(message: String) extends CertServiceException(message)
}

