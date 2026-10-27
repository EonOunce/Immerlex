package com.immerlex.core.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the API key in EncryptedSharedPreferences. If the device keystore
 * refuses to create the master key we keep the value in memory only and never
 * fall back to plaintext storage.
 */
class ApiKeyStore(context: Context) {

    private val store: EncryptedSharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "immerlex_secret",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as EncryptedSharedPreferences
    }.getOrNull()

    private var memoryKey: String? = null

    val isPersistent: Boolean get() = store != null

    fun key(): String = store?.getString(KEY, "").orEmpty().ifBlank { memoryKey.orEmpty() }

    fun save(value: String) {
        val trimmed = value.trim()
        memoryKey = trimmed
        store?.edit()?.putString(KEY, trimmed)?.apply()
    }

    private companion object {
        const val KEY = "api_key"
    }
}
