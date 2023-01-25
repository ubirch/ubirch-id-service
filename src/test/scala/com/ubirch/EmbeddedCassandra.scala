package com.ubirch

import com.github.nosan.embedded.cassandra.commons.ClassPathResource
import com.github.nosan.embedded.cassandra.cql.{ CqlScript, ResourceCqlScript, StringCqlScript }

object EmbeddedCassandra {

  def truncateScript: StringCqlScript = {
    new StringCqlScript("truncate identity_system.identities; truncate identity_system.identities_by_state; truncate identity_system.keys;")
  }

  def creationScripts: Seq[CqlScript] = List(
    new ResourceCqlScript(new ClassPathResource("db/schemaOverview.cql"))
  )
}
