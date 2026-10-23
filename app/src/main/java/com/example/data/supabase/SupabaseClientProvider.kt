package com.example.data.supabase

import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.tasks.await

/**
 * Supabase Data API client that reuses Firebase Auth during the staged migration.
 * Firebase must be configured as a Supabase third-party provider and the ID token
 * must contain role=authenticated. Only a publishable key is allowed in this client.
 */
object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL is missing. Set it in .env or .env.example."
        }
        require(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "SUPABASE_PUBLISHABLE_KEY is missing. Set it in .env or .env.example."
        }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            accessToken = {
                Firebase.auth.currentUser
                    ?.getIdToken(false)
                    ?.await()
                    ?.token
            }
            install(Postgrest)
        }
    }
}
