package com.ubirch

/**
  * Represents a bootable service object that starts the identity system
  */
object Service extends Boot(List(new Binder)) {
  def main(args: Array[String]): Unit = * {
    get[IdentitySystem].start
  }
}
