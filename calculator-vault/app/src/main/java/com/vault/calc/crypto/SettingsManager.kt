package com.vault.calc.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores the unlock PIN and user settings in an EncryptedSharedPreferences file.
 *
 * The PIN itself is never stored in clear text: a random salt plus a PBKDF2
 * hash is kept, and the whole preferences file is additionally encrypted with a
 * Keystore-backed master key.
 */
class SettingsManager private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val FILE_NAME = "vault_settings"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_DECOY_RESULT = "decoy_result_enabled"
        private const val KEY_SECURE_WINDOW = "secure_window"

        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256

        @Volatile
        private var instance: SettingsManager? = null

        fun get(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }
        }

        private fun create(context: Context): SettingsManager {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return SettingsManager(prefs)
        }
    }

    val isPinConfigured: Boolean
        get() = prefs.contains(KEY_PIN_HASH)

    /** Whether the Recents/screenshot block is enabled for the vault screens. */
    var secureWindow: Boolean
        get() = prefs.getBoolean(KEY_SECURE_WINDOW, true)
        set(value) = prefs.edit().putBoolean(KEY_SECURE_WINDOW, value).apply()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = hashPin(pin, salt)
        return constantTimeEquals(expected, actual)
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}
