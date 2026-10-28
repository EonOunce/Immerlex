package com.immerlex.core.secret

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 一台机器上一个加密偏好文件，每类密钥各占一个键（AI key、坚果云应用密码）。
 * 系统 keystore 建不出主密钥时只在内存里留一份，绝不退回明文存储。
 */
class SecretStore(context: Context, private val key: String) {

    private val store: EncryptedSharedPreferences? = runCatching {
        EncryptedSharedPreferences.create(
            context,
            FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as EncryptedSharedPreferences
    }.getOrNull()

    private var memoryValue: String? = null

    val isPersistent: Boolean get() = store != null

    fun value(): String = store?.getString(key, "").orEmpty().ifBlank { memoryValue.orEmpty() }

    fun save(raw: String) {
        val trimmed = raw.trim()
        memoryValue = trimmed
        store?.edit()?.putString(key, trimmed)?.apply()
    }

    private companion object {
        const val FILE = "immerlex_secret"
    }
}
