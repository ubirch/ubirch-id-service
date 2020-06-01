package com.ubirch.services.key

import java.io.ByteArrayInputStream
import java.security.cert.{ CertificateFactory, X509Certificate }

import com.typesafe.scalalogging.LazyLogging
import org.bouncycastle.operator.ContentVerifierProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest

import scala.util.Try

trait CertService {
  def extractCRS(request: Array[Byte]): Try[Option[JcaPKCS10CertificationRequest]]
}

class DefaultCertService extends CertService with LazyLogging {

  def extractCRS(request: Array[Byte]): Try[Option[JcaPKCS10CertificationRequest]] = {
    for {
      csr <- materializeCRS(request).toTry
      isValid <- Try(verifyCSR(csr))
    } yield {
      if (isValid) Option(csr) else None
    }
  }

  def verifyCSR(request: Array[Byte]): Boolean = {
    val provider = new org.bouncycastle.jce.provider.BouncyCastleProvider()
    val jcaRequest = new JcaPKCS10CertificationRequest(request).setProvider(provider)
    verifyCSR(jcaRequest)
  }

  def verifyCSR(jcaRequest: JcaPKCS10CertificationRequest): Boolean = {
    val provider = new org.bouncycastle.jce.provider.BouncyCastleProvider()
    val key = jcaRequest.getPublicKey
    val verifierProvider: ContentVerifierProvider =
      new JcaContentVerifierProviderBuilder()
        .setProvider(provider).build(key)

    jcaRequest.isSignatureValid(verifierProvider)
  }

  def materializeCRS(request: Array[Byte]): Either[Throwable, JcaPKCS10CertificationRequest] = {
    (for {
      provider <- Try(new org.bouncycastle.jce.provider.BouncyCastleProvider())
      cert <- Try(new JcaPKCS10CertificationRequest(request).setProvider(provider))
    } yield {
      cert
    }).toEither
  }

  def materializeCert(certBin: Array[Byte]): Either[Throwable, X509Certificate] = {
    (for {
      factory <- Try(CertificateFactory.getInstance("X.509"))
      cert <- Try(factory.generateCertificate(new ByteArrayInputStream(certBin)).asInstanceOf[X509Certificate])
    } yield {
      cert
    }).toEither
  }

}
