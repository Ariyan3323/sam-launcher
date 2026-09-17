package de.szalkowski.activitylauncher.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, device-only cache of user-approved general Q&A for offline retrieval. */
class OfflineKnowledgeStore(context: Context) {
    private val prefs = context.getSharedPreferences("sam_offline_knowledge", Context.MODE_PRIVATE)
    private val keyAlias = "sam_offline_knowledge_v1"
    private val storageKey = "entries"
    private val maxEntries = 80

    @Synchronized
    fun save(question: String, answer: String): Boolean {
        val q = question.trim().replace(Regex("\\s+"), " ").take(180)
        val a = answer.trim().take(1200)
        if (q.isBlank() || a.isBlank()) return false
        val entries = read().filterNot { it.optString("q").equals(q, ignoreCase = true) }.toMutableList()
        entries.add(0, JSONObject().apply { put("q", q); put("a", a); put("at", System.currentTimeMillis()) })
        val payload = JSONArray(entries.take(maxEntries)).toString()
        prefs.edit().putString(storageKey, encrypt(payload)).apply()
        return true
    }

    @Synchronized
    fun search(question: String, limit: Int = 2): List<String> {
        val terms = normalize(question).split(" ").filter { it.length >= 3 }.toSet()
        if (terms.isEmpty()) return emptyList()
        return read().mapNotNull { entry ->
            val candidate = normalize(entry.optString("q"))
            val score = terms.count(candidate::contains)
            if (score > 0) score to entry.optString("a") else null
        }.sortedByDescending { it.first }.take(limit).map { it.second }
    }

    @Synchronized
    fun clear() { prefs.edit().remove(storageKey).apply() }
    fun size(): Int = read().size

    private fun read(): List<JSONObject> {
        val raw = prefs.getString(storageKey, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(decrypt(raw))
            List(json.length()) { json.optJSONObject(it) }.filterNotNull()
        }.getOrDefault(emptyList())
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("\\s+"), " ").trim()

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(StandardCharsets.UTF_8)
    }.getOrDefault("")
}
