package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.crypto.utils.Curve

/**
  * Created by derMicha on 19/05/17.
  */
object PublicKeyUtil extends LazyLogging {

  final val ECDSA: String = "ecdsa-p256v1"
  final val EDDSA: String = "ed25519-sha-512"

  /**
    * Associate a string to a curve used by the crypto lib
    * @param curve the string representing the curve
    * @return the associated curve
    */
  def associateCurve(curve: String): Curve = {
    curve match {
      case ECDSA => Curve.PRIME256V1
      case _ => Curve.Ed25519
    }
  }

  def curveFromString(algorithm: String): Option[Curve] = algorithm match {
    case "ECC_ED25519" | "Ed25519" => Some(Curve.Ed25519)
    case "ECC_ECDSA" | "ecdsa-p256v1" | "ECDSA" | "SHA256withECDSA" => Some(Curve.PRIME256V1)
    case _ => None
  }

}
