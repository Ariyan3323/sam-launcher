package de.szalkowski.activitylauncher.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores AI credentials locally using an Android Keystore AES-GCM key. */
class SecureAiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("sam_ai_secure", Context.MODE_PRIVATE)
    private val alias = "sam_ai_credentials_v1"

    fun get(provider: String): String = decrypt(prefs.getString("key_$provider", null))

    fun set(provider: String, value: String) {
        if (value.isBlank()) prefs.edit().remove("key_$provider").apply()
        else prefs.edit().putString("key_$provider", encrypt(value.trim())).apply()
    }

    fun remove(provider: String) = prefs.edit().remove("key_$provider").apply()
    fun getBaseUrl(): String = prefs.getString("base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
    fun setBaseUrl(value: String) = prefs.edit().putString("base_url", value.trim().trimEnd('/')).apply()
    fun getModel(): String = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
    fun setModel(value: String) = prefs.edit().putString("model", value.trim()).apply()
    fun getProvider(): String = prefs.getString("provider", "openai") ?: "openai"
    fun setProvider(value: String) = prefs.edit().putString("provider", value).apply()
    fun getMode(): String = prefs.getString("mode", "local") ?: "local"
    fun setMode(value: String) = prefs.edit().putString("mode", value).apply()

    private fun key(): SecretKey {
        val existing = runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        }.getOrNull()
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return runCatching {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }
}

fun Context.secureAiSettings(): SecureAiSettings = SecureAiSettings(this)
