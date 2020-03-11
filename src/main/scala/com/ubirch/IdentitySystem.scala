package com.ubirch

import java.util.concurrent.CountDownLatch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.kafka.Tiger
import com.ubirch.services.rest.RestService
import javax.inject._

@Singleton
class IdentitySystem @Inject() (tiger: Tiger, restService: RestService) extends LazyLogging {

  def start = {

    restService.start

    tiger.start()
    val cd = new CountDownLatch(1)
    cd.await()
  }

}
