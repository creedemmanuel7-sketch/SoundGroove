package com.credo.soundgroove.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stockage clé/valeur chiffré (AES-256-GCM + Android Keystore).
 *
 * Contexte 2026 : `EncryptedSharedPreferences` (Jetpack Security Crypto) est **deprecated**.
 * La voie officielle recommandée pour le long terme est Jetpack DataStore +
 * `androidx.datastore:datastore-tink` ([AeadSerializer]). Tant que datastore-tink
 * reste en alpha / adoption limitée, ce helper Keystore fournit un fichier dédié
 * pour secrets / tokens sans dépendre de security-crypto.
 *
 * Usage : uniquement pour données sensibles (tokens, PIN persistés, etc.).
 * Les prefs UX (`soundgroove_prefs`) restent en clair — pas de secrets aujourd’hui.
 *
 * Exclure [PREFS_NAME] des backups (voir `backup_rules.xml` / `data_extraction_rules.xml`).
 */
object SecurePrefs {
    const val PREFS_NAME = "soundgroove_secure_prefs"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "soundgroove_secure_prefs_aes"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun putString(context: Context, key: String, value: String?) {
        val editor = prefs(context).edit()
        if (value == null) {
            editor.remove(key).apply()
            return
        }
        editor.putString(key, encrypt(value)).apply()
    }

    fun getString(context: Context, key: String, default: String? = null): String? {
        val cipherText = prefs(context).getString(key, null) ?: return default
        return runCatching { decrypt(cipherText) }.getOrDefault(default)
    }

    fun remove(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(1 + iv.size + encrypted.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(payload)
        val ivLen = buffer.get().toInt() and 0xff
        require(ivLen == IV_BYTES) { "IV invalide" }
        val iv = ByteArray(ivLen)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
