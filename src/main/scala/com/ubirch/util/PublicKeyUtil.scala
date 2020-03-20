package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.crypto.utils.{ Curve, Hash }

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
      case "ecdsa-p256v1" => Curve.PRIME256V1
      case _ => Curve.Ed25519
    }
  }

  /**
    * Associate a string to a Hash algorithm used by the algorithm
    * @param curve the string representing the curve
    * @return the associated curve
    */
  def associateHash(curve: String): Hash = {
    curve match {
      case "ecdsa-p256v1" => Hash.SHA256
      case _ => Hash.SHA512
    }
  }

}
