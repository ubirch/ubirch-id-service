package com.ubirch.util

import java.math.BigInteger
import java.util.UUID

import org.bouncycastle.asn1.x500.style.{ BCStyle, IETFUtils }
import org.bouncycastle.asn1.x500.{ RDN, X500Name }
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.{ DefaultAlgorithmNameFinder, DefaultSignatureAlgorithmIdentifierFinder }

import scala.util.Try

object CertUtil {

  //def CNAsUUID: X500Name => Try[UUID] = getCN _ andThen rdnToString andThen buildUUID

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
    nameFinder.getAlgorithmName(algorithmIdentifier)
  }

  def algorithmIdentifier(name: String): Try[AlgorithmIdentifier] = Try {
    val nameFinder = new DefaultSignatureAlgorithmIdentifierFinder()
    nameFinder.find(name)
  }

}
