package com.ubirch
package services.key

import java.io.ByteArrayInputStream
import java.security.cert.{ CertificateFactory, X509Certificate }
import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models._
import com.ubirch.util.{ CertUtil, Hasher, PublicKeyUtil, TaskHelpers }
import javax.inject.{ Inject, Singleton }
import monix.eval.Task
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
  def processCert(cert: X509Certificate): Task[PublicKeyInfo]
  def activateCert(activation: IdentityActivation): Task[PublicKeyInfo]
  def processCSR(csr: JcaPKCS10CertificationRequest): Task[PublicKeyInfo]
  def extractCRS(request: Array[Byte]): Try[JcaPKCS10CertificationRequest]
}

/**
  * Default implementation of a CertService
  *
  * @param pubKeyService Service for managing public keys
  * @param identitiesDAO DAO for the identities
  */
@Singleton
class DefaultCertService @Inject() (
    pubKeyService: PubKeyService,
    identitiesDAO: IdentitiesDAO,
    identitiesByStateDAO: IdentityByStateDAO
) extends CertService with TaskHelpers with LazyLogging {

  override def extractCert(request: Array[Byte]): Try[X509Certificate] = {
    for {
      cert <- materializeCert(request).toTry
      _ = logger.info("cert_extracted={}", cert.toString)
    } yield {
      cert
    }
  }

  override def processCert(cert: X509Certificate): Task[PublicKeyInfo] = {
    for {
      _ <- lift(cert.checkValidity())(InvalidCertVerification(cert))
      publicKey <- buildPublicKey(cert)

      data <- liftTry(Try(Hex.toHexString(cert.getEncoded)))(EncodingException("Error encoding data"))

      dataHashAsId = Hasher.hash(data)
      identityRow = IdentityRow(publicKey.pubKeyInfo.hwDeviceId, publicKey.pubKeyInfo.pubKeyId, dataHashAsId, "X.509", data, publicKey.pubKeyInfo.algorithm + " | " + cert.getSubjectX500Principal.toString)

      exists <- identitiesDAO.byOwnerIdAndIdentityIdAndDataId(identityRow.ownerId, identityRow.identityId, dataHashAsId).headOptionL
      _ <- earlyResponseIf(exists.isDefined)(IdentityAlreadyExistsException(publicKey.pubKeyInfo, identityRow.toString))

      res <- identitiesDAO.insertWithState(identityRow, X509Created).headOptionL
      _ = if (res.isEmpty) logger.error("failed_cert_creation={} ", identityRow.toString)
      _ = if (res.isDefined) logger.info("cert_creation_succeeded={}", identityRow.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("CERT_Insert"))

      _ <- pubKeyService.createRow(publicKey, data)
      _ <- pubKeyService.anchorAfter()(() => Task.delay(publicKey))

    } yield {
      publicKey.pubKeyInfo
    }
  }

  override def activateCert(activation: IdentityActivation): Task[PublicKeyInfo] = {
    for {

      maybeIdentity <- identitiesDAO.byOwnerIdAndIdentityIdAndDataId(activation.ownerId, activation.identityId, activation.dataHash).headOptionL
      _ <- earlyResponseIf(maybeIdentity.isEmpty)(IdentityNotFoundException(activation.toString))

      cert <- liftTry(extractCert(Hex.decode(maybeIdentity.get.data)))(EncodingException("Error building cert"))
      _ <- lift(cert.checkValidity())(InvalidCertVerification(cert))

      publicKey <- buildPublicKey(cert)

      keys <- pubKeyService.getByPubKeyId(activation.identityId)
      _ <- earlyResponseIf(keys.exists(_.pubKeyInfo.pubKeyId == activation.identityId))(IdentityAlreadyExistsException(publicKey.pubKeyInfo, activation.toString))

      data <- liftTry(Try(Hex.toHexString(cert.getEncoded)))(EncodingException("Error encoding data"))

      state = IdentityByStateRow.fromIdentityRow(maybeIdentity.get, CSRActivated)
      res <- identitiesByStateDAO.insert(state).headOptionL
      _ = if (res.isEmpty) logger.error("failed_state_creation={} ", state.toString)
      _ = if (res.isDefined) logger.info("state_creation_succeeded={}", state.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("CERT_Insert"))

      _ <- pubKeyService.createRow(publicKey, data)
      _ <- pubKeyService.anchorAfter()(() => Task.delay(publicKey))

    } yield {
      publicKey.pubKeyInfo
    }
  }

  override def processCSR(csr: JcaPKCS10CertificationRequest): Task[PublicKeyInfo] = {
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

      //We are using the identifiers are key to help narrow the search as the csrs themselves might changes as the signing key could be different
      identifiers = CertUtil.identifiers(csr.getSubject).map { case (a, b) => s"$a=$b" }.mkString(",")
      dataId = Hasher.hash(identifiers)
      identityRow = IdentityRow(uuid.toString, pubKeyAsBase64, dataId, "CSR", data, curve + " | " + identifiers)

      pubkeyInfo = PublicKeyInfo(curve.name(), new Date(), uuid.toString, pubKeyAsBase64, pubKeyAsBase64, None, new Date())

      maybeIdentityRow <- identitiesDAO.byOwnerIdAndIdentityIdAndDataId(identityRow.ownerId, identityRow.identityId, identityRow.dataId).headOptionL
      _ <- earlyResponseIf(maybeIdentityRow.isDefined)(IdentityAlreadyExistsException(pubkeyInfo, identityRow.toString))

      res <- identitiesDAO.insertWithState(identityRow, CSRCreated).headOptionL
      _ = if (res.isEmpty) logger.error("failed_csr_creation={} ", identityRow.toString)
      _ = if (res.isDefined) logger.info("csr_creation_succeeded={}", identityRow.toString)
      _ <- earlyResponseIf(res.isEmpty)(OperationReturnsNone("CSR_Insert"))

    } yield {
      pubkeyInfo
    })
      .onErrorRecover {
        case IdentityAlreadyExistsException(publicKey, _) => publicKey
        case e: Throwable => throw e
      }

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

