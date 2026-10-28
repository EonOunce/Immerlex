package com.immerlex.core.ai

import android.content.Context
import com.immerlex.core.secret.SecretStore

/**
 * Stores the API key in EncryptedSharedPreferences. If the device keystore
 * refuses to create the master key we keep the value in memory only and never
 * fall back to plaintext storage.
 */
class ApiKeyStore(context: Context) {

    private val store = SecretStore(context, KEY)

    val isPersistent: Boolean get() = store.isPersistent

    fun key(): String = store.value()

    fun save(value: String) = store.save(value)

    private companion object {
        const val KEY = "api_key"
    }
}
