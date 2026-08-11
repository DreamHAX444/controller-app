@file:Suppress("DEPRECATION")
package com.aistudio.missioncontrol.pxytwe.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Owns PIN storage. EncryptedSharedPreferences create() takes 50-500 ms
 * on cold start — previously called from the constructor on the
 * Composable's main-thread composition. Now:
 *  - prefs is `by lazy` so the constructor is trivial and only the
 *    first method call pays for crypto;
 *  - all methods are `suspend` and dispatch the I/O onto
 *    [Dispatchers.IO], so Composable callers must wrap them in a
 *    coroutine (LaunchedEffect / viewModelScope).
 *
 * Ponytail: keep the API close—same names, same signatures pending
 * `suspend`. Add per-method scope if any operation needs to share
 * state across instances.
 */
@Suppress("DEPRECATION")
class SecurityManager(context: Context) {
    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun isPinSet(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains("master_pin_hash")
    }

    suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString("master_pin_hash", hashPin(pin)).apply()
    }

    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        prefs.getString("master_pin_hash", null)?.let { it == hashPin(pin) } ?: false
    }
}
