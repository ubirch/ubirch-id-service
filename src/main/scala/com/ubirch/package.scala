package com

import java.security.cert.X509Certificate

import com.ubirch.models.PublicKey
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest

import scala.util.control.NoStackTrace

package object ubirch {

  /**
    * Represents Generic Top Level Exception for the Id Service System
    * @param message Represents the error message.
    */

  abstract class IdentityServiceException(message: String) extends Exception(message) with NoStackTrace {
    val name: String = this.getClass.getCanonicalName
  }

  abstract class CertServiceException(message: String) extends IdentityServiceException(message)
  abstract class KeyAnchoringServiceException(message: String) extends IdentityServiceException(message)
  abstract class PubKeyServiceException(message: String) extends IdentityServiceException(message)

  case class NoContactPointsException(message: String) extends IdentityServiceException(message)
  case class NoKeyspaceException(message: String) extends IdentityServiceException(message)
  case class InvalidConsistencyLevel(message: String) extends IdentityServiceException(message)
  case class InvalidContactPointsException(message: String) extends IdentityServiceException(message)
  case class StoringException(message: String, reason: String) extends IdentityServiceException(message)

  case class NoCurveException(message: String) extends IdentityServiceException(message)
  case class OperationReturnsNone(message: String) extends IdentityServiceException(message)

  case class ParsingError(message: String) extends PubKeyServiceException(message)
  case class KeyExists(publicKey: PublicKey) extends PubKeyServiceException("Key provided already exits")
  case class InvalidKeyVerification(publicKey: PublicKey) extends PubKeyServiceException("Invalid verification")
  case class KeyNotExists(publicKey: String) extends PubKeyServiceException("Key provided does not exist")

  case class InvalidCSRVerification(csr: JcaPKCS10CertificationRequest) extends CertServiceException("Invalid CSR verification")
  case class InvalidCertVerification(csr: X509Certificate) extends CertServiceException("Invalid cert verification")
  case class InvalidCN(csr: JcaPKCS10CertificationRequest) extends CertServiceException("Invalid Common Name in CSR")
  case class InvalidCertCN(csr: X509Certificate) extends CertServiceException("Invalid Common Name in Cert")
  case class InvalidUUID(message: String) extends CertServiceException(message)
  case class UnknownSignatureAlgorithm(message: String) extends CertServiceException(message)
  case class UnknownCurve(message: String) extends CertServiceException(message)
  case class RecreationException(message: String) extends CertServiceException(message)
  case class EncodingException(message: String) extends CertServiceException(message)
  case class IdentityAlreadyExistsException(message: String) extends CertServiceException(message)
  case class IdentityNotFoundException(message: String) extends CertServiceException(message)

  case class FailedKafkaPublish(publicKey: PublicKey, maybeThrowable: Option[Throwable])
    extends KeyAnchoringServiceException(maybeThrowable.map(_.getMessage).getOrElse("Failed Publish"))

}
