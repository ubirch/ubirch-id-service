package com.ubirch.services.key

import java.io.ByteArrayInputStream
import java.security.cert.{ CertificateFactory, X509Certificate }
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.PublicKeyInfo
import com.ubirch.util.{ CertUtil, PublicKeyUtil, TaskHelpers }
import javax.inject.{ Inject, Singleton }
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import org.bouncycastle.operator.ContentVerifierProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.bouncycastle.util.encoders.Base64

import scala.util.Try
import scala.util.control.NoStackTrace

trait CertService {
  def processCSR(csr: JcaPKCS10CertificationRequest): CancelableFuture[PublicKeyInfo]
  def extractCRS(request: Array[Byte]): Try[JcaPKCS10CertificationRequest]
}

@Singleton
class DefaultCertService @Inject() (pubKeyService: PubKeyService)(implicit scheduler: Scheduler) extends CertService with TaskHelpers with LazyLogging {

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
      pubKeyAsBase64 <- lift(Base64.toBase64String(pubKey.getPublicKey.getEncoded))(EncodingException("Error encoding key into base 64"))
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

  private def materializeCert(certBin: Array[Byte]): Either[Throwable, X509Certificate] = {
    (for {
      factory <- Try(CertificateFactory.getInstance("X.509"))
      cert <- Try(factory.generateCertificate(new ByteArrayInputStream(certBin)).asInstanceOf[X509Certificate])
    } yield {
      cert
    }).toEither
  }

}

object DefaultCertService {
  abstract class CertServiceException(message: String) extends Exception(message) with NoStackTrace

  case class InvalidVerification(csr: JcaPKCS10CertificationRequest) extends CertServiceException("Invalid verification")
  case class InvalidCN(csr: JcaPKCS10CertificationRequest) extends CertServiceException("Invalid Common Name")
  case class InvalidUUID(message: String) extends CertServiceException(message)
  case class UnknownSignatureAlgorithm(message: String) extends CertServiceException(message)
  case class UnknownCurve(message: String) extends CertServiceException(message)
  case class RecreationException(message: String) extends CertServiceException(message)
  case class EncodingException(message: String) extends CertServiceException(message)
}
