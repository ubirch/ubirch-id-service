package com.ubirch.util

import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.{ KeyPair, KeyPairGenerator }
import java.util.{ Base64, UUID }

import com.ubirch.cert.BCCertGen
import com.ubirch.models.Identity
import org.apache.commons.codec.binary.Hex
import org.bouncycastle.asn1.x500.style.{ BCStyle, IETFUtils }
import org.bouncycastle.asn1.x500.{ RDN, X500Name, X500NameBuilder }
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jcajce.BCFKSLoadStoreParameter.SignatureAlgorithm
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.{ DefaultAlgorithmNameFinder, DefaultSignatureAlgorithmIdentifierFinder }
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder

import scala.util.Try

/**
  * A helper for daily operations on certs
  */
object CertUtil {

  def getCN(x500Name: X500Name): RDN = x500Name.getRDNs(BCStyle.CN)(0)

  def rdnToString(rdn: RDN): String = IETFUtils.valueToString(rdn.getFirst.getValue)

  def buildUUID(uuid: String): Try[UUID] = {
    Try(UUID.fromString(uuid)).recover {
      case _: IllegalArgumentException =>
        val UUID_RADIX = 16
        val UUID_MIDDLE = 16
        new UUID(
          new BigInteger(uuid.substring(0, UUID_MIDDLE), UUID_RADIX).longValue(),
          new BigInteger(uuid.substring(UUID_MIDDLE), UUID_RADIX).longValue()
        )
    }
  }

  def algorithmName(algorithmIdentifier: AlgorithmIdentifier): Try[String] = Try {
    val nameFinder = new DefaultAlgorithmNameFinder()
    val found = nameFinder.getAlgorithmName(algorithmIdentifier)
    //We do this to be able to recognize that no name was found.
    if (found == algorithmIdentifier.getAlgorithm.getId) throw new Exception("Didn't find a particular algorithm")
    else found
  }

  def algorithmIdentifier(name: String): Try[AlgorithmIdentifier] = Try {
    val nameFinder = new DefaultSignatureAlgorithmIdentifierFinder()
    nameFinder.find(name)
  }

  def createCert(uuid: UUID)(kpg: KeyPairGenerator, sigAlgo: SignatureAlgorithm = SignatureAlgorithm.SHA512withECDSA): (KeyPair, X509Certificate, Identity) = {
    val kp = kpg.generateKeyPair

    val xCert = BCCertGen.generate(
      kp.getPrivate,
      kp.getPublic,
      365,
      sigAlgo.toString,
      true,
      uuid.toString
    )

    val encodedCert: String = Hex.encodeHexString(xCert.getEncoded)

    (kp, xCert, Identity(
      id = Base64.getEncoder.encodeToString(kp.getPublic.getEncoded),
      ownerId = uuid.toString,
      category = "X.509",
      data = encodedCert,
      description = "This is a description for " + uuid
    ))

  }

  def createCSR(uuid: UUID)(keyPair: KeyPair, sigAlgo: SignatureAlgorithm = SignatureAlgorithm.SHA512withECDSA): PKCS10CertificationRequest = {

    val x500NameBld = new X500NameBuilder(BCStyle.INSTANCE)
      .addRDN(BCStyle.CN, uuid.toString)
      .addRDN(BCStyle.EmailAddress, "info@ubirch.com")
      .addRDN(BCStyle.C, "DE")
      .addRDN(BCStyle.ST, "Berlin")
      .addRDN(BCStyle.OU, "Security")
      .addRDN(BCStyle.O, "ubirch GmbH")

    val subject = x500NameBld.build

    val requestBuilder = new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic)

    val signer = new JcaContentSignerBuilder(sigAlgo.name()).setProvider(PublicKeyUtil.provider).build(keyPair.getPrivate)

    requestBuilder.build(signer)

  }

}
