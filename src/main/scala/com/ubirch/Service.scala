package com.ubirch

object Service extends Boot(List(new Binder)) {
  def main(args: Array[String]): Unit = {
    get[IdentitySystem].start
  }
}
