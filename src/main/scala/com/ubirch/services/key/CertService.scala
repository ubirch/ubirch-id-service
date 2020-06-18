package com.ubirch
package services.key

import java.io.ByteArrayInputStream
import java.security.cert.{ CertificateFactory, X509Certificate }
import java.util.Date

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.models._
import com.ubirch.util.{ CertUtil, Hasher, PublicKeyUtil, TaskHelpers }
import io.prometheus.client.Counter
import javax.inject.{ Inject, Singleton }
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.operator.ContentVerifierProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.bouncycastle.util.encoders.{ Base64, Hex }

import scala.util.Try

/**
  * Basic description of what a CertService is
  */
trait CertService {
  def extractCert(request: Array[Byte]): Try[X509Certificate]
  def processCert(cert: X509Certificate): CancelableFuture[PublicKeyInfo]
  def activateCert(activation: IdentityActivation): CancelableFuture[PublicKeyInfo]
  def processCSR(csr: JcaPKCS10CertificationRequest): CancelableFuture[PublicKeyInfo]
  def extractCRS(request: Array[Byte]): Try[JcaPKCS10CertificationRequest]
}

/**
  * Default implementation of a CertService
  *
  * @param config Represents a config object
  * @param pubKeyService Service for managing public keys
  * @param identitiesDAO DAO for the identities
  * @param scheduler Executor Scheduler.
  */
@Singleton
class DefaultCertService @Inject() (
    config: Config,
    pubKeyService: PubKeyService,
    identitiesDAO: IdentitiesDAO,
    identitiesByStateDAO: IdentityByStateDAO
)(implicit scheduler: Scheduler) extends CertService with TaskHelpers with ServiceMetrics with LazyLogging {

  val service: String = config.getString(GenericConfPaths.NAME)

  val successCounter: Counter = Counter.build()
    .name("cert_management_success")
    .help("Represents the number of cert key management successes")
    .labelNames("service", "method")
    .register()

  val errorCounter: Counter = Counter.build()
    .name("cert_management_failures")
    .help("Represents the number of cert management failures")
    .labelNames("service", "method")
    .register()

  override def extractCert(request: Array[Byte]): Try[X509Certificate] = {
    for {
      cert <- materializeCert(request).toTry
      _ = logger.info("crs_extracted={}", cert.toString)
    } yield {
      cert
    }
  }

  override def processCert(cert: X509Certificate): CancelableFuture[PublicKeyInfo] = count("process_cert_x509") {
    (for {
      _ <- lift(cert.checkValidity())(InvalidCertVerification(cert))
      publicKey <- buildPublicKey(cert)

      data <- liftTry(Try(Hex.toHexString(cert.getEncoded)))(EncodingException("Error encoding data"))

      identity = Identity(Hasher.hash(data), publicKey.pubKeyInfo.hwDeviceId, "X.509", data, publicKey.pubKeyInfo.pubKeyId)
      identityRow = IdentityRow.fromIdentity(identity)

      exists <- identitiesDAO.byOwnerIdAndIdentityId(identityRow.ownerId, identityRow.identityId).headOptionL
      _ <- earlyResponseIf(exists.isDefined)(IdentityAlreadyExistsException(identity.toString))

      res <- identitiesDAO.insertWithState(IdentityRow.fromIdentity(identity), X509Created).headOptionL
      _ = if (res.isEmpty) logger.error("failed_creation={} ", identityRow.toString)
      _ = if (res.isDefined) logger.info("creation_succeeded={}", identityRow.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("CERT_Insert"))

      _ <- pubKeyService.createRow(publicKey, data)
      _ <- pubKeyService.anchorAfter()(() => Task.delay(publicKey))

    } yield {
      publicKey.pubKeyInfo
    }).runToFuture
  }

  override def activateCert(activation: IdentityActivation): CancelableFuture[PublicKeyInfo] = count("activate_cert_x509") {
    (for {

      maybeIdentity <- identitiesDAO.byOwnerIdAndIdentityId(activation.ownerId, activation.identityId).headOptionL
      _ <- earlyResponseIf(maybeIdentity.isEmpty)(IdentityNotFoundException(activation.toString))

      cert <- liftTry(extractCert(Hex.decode(maybeIdentity.get.data)))(EncodingException("Error building cert"))
      _ <- lift(cert.checkValidity())(InvalidCertVerification(cert))

      publicKey <- buildPublicKey(cert)

      data <- liftTry(Try(Hex.toHexString(cert.getEncoded)))(EncodingException("Error encoding data"))

      res <- identitiesByStateDAO.insert(IdentityByStateRow.fromIdentityRow(maybeIdentity.get, CSRActivated)).headOptionL
      _ = if (res.isEmpty) logger.error("failed_creation={} ", maybeIdentity.toString)
      _ = if (res.isDefined) logger.info("creation_succeeded={}", maybeIdentity.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("CERT_Insert"))

      _ <- pubKeyService.createRow(publicKey, data)
      _ <- pubKeyService.anchorAfter()(() => Task.delay(publicKey))

    } yield {
      publicKey.pubKeyInfo
    }).runToFuture
  }

  override def processCSR(csr: JcaPKCS10CertificationRequest): CancelableFuture[PublicKeyInfo] = count("process_csr") {
    (for {
      verification <- Task.delay(verifyCSR(csr))
      _ = if (!verification) logger.error("failed_verification_for={}", csr.toString)
      _ <- earlyResponseIf(!verification)(InvalidCSRVerification(csr))

      cn <- lift(CertUtil.getCN(csr.getSubject))(InvalidCN(csr))
      cnAsString <- lift(CertUtil.rdnToString(cn))(InvalidCN(csr))
      uuid <- liftTry(CertUtil.buildUUID(cnAsString))(InvalidUUID(cnAsString))

      alg <- lift(CertUtil.algorithmName(csr.getSignatureAlgorithm)
        .getOrElse(csr.getPublicKey.getAlgorithm))(UnknownSignatureAlgorithm("Unknown Algorithm"))
      curve <- liftTry(PublicKeyUtil.associateCurve(alg))(UnknownCurve("Unknown curve for " + alg))

      pubKey <- liftTry(pubKeyService.recreatePublicKey(csr.getPublicKey.getEncoded, curve))(RecreationException("Error recreating pubkey"))
      pubKeyAsBase64 <- liftTry(Try(Base64.toBase64String(pubKey.getRawPublicKey)))(EncodingException("Error encoding key into base 64"))

      data <- liftTry(Try(Hex.toHexString(csr.getEncoded)))(EncodingException("Error encoding data_id"))

      identity = Identity(Hasher.hash(data), uuid.toString, "CSR", data, pubKeyAsBase64)
      identityRow = IdentityRow.fromIdentity(identity)

      exists <- identitiesDAO.byOwnerIdAndIdentityId(identityRow.ownerId, identityRow.identityId).headOptionL
      _ <- earlyResponseIf(exists.isDefined)(IdentityAlreadyExistsException(identity.toString))

      res <- identitiesDAO.insertWithState(IdentityRow.fromIdentity(identity), CSRCreated).headOptionL
      _ = if (res.isEmpty) logger.error("failed_creation={} ", identityRow.toString)
      _ = if (res.isDefined) logger.info("creation_succeeded={}", identityRow.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("CSR_Insert"))

    } yield {
      PublicKeyInfo(alg, new Date(), uuid.toString, pubKeyAsBase64, pubKeyAsBase64, None, new Date())
    }).runToFuture

  }

  override def extractCRS(request: Array[Byte]): Try[JcaPKCS10CertificationRequest] = {
    for {
      csr <- materializeCRS(request).toTry
      _ = logger.info("crs_extracted={}", csr.getSubject.toString)
    } yield {
      csr
    }
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

  private def buildPublicKey(cert: X509Certificate): Task[PublicKey] = {
    for {
      _ <- lift(cert.checkValidity())(InvalidCertVerification(cert))

      cn <- lift(CertUtil.getCN(new JcaX509CertificateHolder(cert).getSubject))(InvalidCertCN(cert))
      cnAsString <- lift(CertUtil.rdnToString(cn))(InvalidCertCN(cert))
      uuid <- liftTry(CertUtil.buildUUID(cnAsString))(InvalidUUID(cnAsString))

      alg = cert.getSigAlgName
      curve <- liftTry(PublicKeyUtil.associateCurve(alg))(UnknownCurve("Unknown curve for " + alg))

      pubKey <- liftTry(pubKeyService.recreatePublicKey(cert.getPublicKey.getEncoded, curve))(RecreationException("Error recreating pubkey"))
      pubKeyAsBase64 <- liftTry(Try(Base64.toBase64String(pubKey.getRawPublicKey)))(EncodingException("Error encoding key into base 64"))

      pubKeyInfo = PublicKeyInfo(curve.name(), new Date(), uuid.toString, pubKeyAsBase64, pubKeyAsBase64, Option(cert.getNotAfter), cert.getNotBefore)
      publicKey = PublicKey(pubKeyInfo, Hex.toHexString(cert.getSignature))

    } yield {
      publicKey
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

