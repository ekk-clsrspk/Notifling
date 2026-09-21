package com.notifling

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Persistent storage for the pre-shared key + last status. Key survives reboot. */
class KeyStore(ctx: Context) {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx, "notifling", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            // Fallback for devices without keystore: plain prefs (LAN key only).
            ctx.getSharedPreferences("notifling", Context.MODE_PRIVATE)
        }
    }

    fun getKey(): String = prefs.getString(KEY, "") ?: ""
    fun saveKey(v: String) { prefs.edit().putString(KEY, v.trim()).apply() }
    fun hasKey(): Boolean = getKey().isNotEmpty()

    fun getLastStatus(): String = prefs.getString(STATUS, "idle") ?: "idle"
    fun setLastStatus(v: String) { prefs.edit().putString(STATUS, v).apply() }

    companion object {
        private const val KEY = "auth_key"
        private const val STATUS = "last_status"
    }
}
