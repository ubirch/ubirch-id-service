package com.ubirch

import java.nio.file.{ Files, Paths }

trait WithFixtures {

  def loadFixture(resource: String): Array[Byte] = {
    Files.readAllBytes(Paths.get(resource))
  }

}
