package com.ubirch

import com.github.nosan.embedded.cassandra.commons.ClassPathResource
import com.github.nosan.embedded.cassandra.cql.{ CqlScript, ResourceCqlScript, StringCqlScript }

object EmbeddedCassandra {

  def truncateScript: StringCqlScript = {
    new StringCqlScript("truncate identity_system.identities; truncate identity_system.identities_by_state; truncate identity_system.keys;")
  }

  def creationScripts: Seq[CqlScript] = List(
    new StringCqlScript("drop keyspace IF EXISTS identity_system;"),
    new StringCqlScript("CREATE KEYSPACE identity_system WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"),
    new StringCqlScript("USE identity_system;"),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v1_Identity_Column_Family.cql")),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v2_Key_Column_Family.cql")),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v3_Materialized_view_for_hardware_search.cql")),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v4_Materialized_view_for_pub_key_id_search.cql")),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v5_Rebuilding_identities_table.cql")),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v6_Renaming_column_names.cql")),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v7_identity_states.cql")),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v8_previous_signature_column.cql")),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v9_Modifying_identity_family_column.cql")),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v10_Adding_prevPubKeyId_column.cql")),
    new ResourceCqlScript(new ClassPathResource("db/migrations/v11_Adding_revoke_column.cql"))
  )
}
