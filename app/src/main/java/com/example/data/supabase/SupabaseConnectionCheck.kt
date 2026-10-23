package com.example.data.supabase

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

/**
 * Read-only connection smoke test. An empty list is valid when no communities
 * have been migrated yet. It intentionally inserts no sample rows.
 */
object SupabaseConnectionCheck {
    @Serializable
    data class CommunityName(
        val id: String,
        val name: String,
    )

    suspend fun readSampleCommunities(): List<CommunityName> =
        SupabaseClientProvider.client
            .from("communities")
            .select(columns = Columns.list("id, name"))
            .decodeList<CommunityName>()
}
