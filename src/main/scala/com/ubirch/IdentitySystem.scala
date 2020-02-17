package com.ubirch

import java.util.concurrent.CountDownLatch

import com.ubirch.services.kafka.Tiger
import javax.inject._

@Singleton
class IdentitySystem @Inject() (tiger: Tiger) {

  def start = {
    tiger.start()
    val cd = new CountDownLatch(1)
    cd.await()
  }

}
