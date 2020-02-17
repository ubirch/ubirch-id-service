package com.ubirch

object Service extends Boot(List(new IdServiceBinder)) {
  def main(args: Array[String]): Unit = {
    println(" Started ")
  }
}
