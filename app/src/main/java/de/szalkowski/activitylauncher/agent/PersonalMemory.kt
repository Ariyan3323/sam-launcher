package de.szalkowski.activitylauncher.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device durable memory store for user profile, name, preferences, and facts.
 * Persists in SharedPreferences so data is retained across app restarts and reboots.
 */
class PersonalMemory(context: Context) {
    companion object {
        private const val PREFS = "sam_personal_memory"
        private const val KEY_FACTS = "facts"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PREFERENCES = "user_prefs"
        private const val MAX_FACTS = 50
    }

    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun setUserName(name: String) {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isNotBlank()) {
            preferences.edit().putString(KEY_USER_NAME, clean).apply()
            remember("نام کاربر: $clean")
        }
    }

    @Synchronized
    fun getUserName(): String? = preferences.getString(KEY_USER_NAME, null)?.takeIf { it.isNotBlank() }

    @Synchronized
    fun rememberPreference(key: String, value: String) {
        val current = readPreferences().toMutableMap()
        current[key.trim()] = value.trim()
        val json = JSONObject(current as Map<*, *>).toString()
        preferences.edit().putString(KEY_PREFERENCES, json).apply()
        remember("$key: $value")
    }

    @Synchronized
    fun getPreference(key: String): String? = readPreferences()[key.trim()]

    @Synchronized
    fun allPreferences(): Map<String, String> = readPreferences()

    @Synchronized
    fun remember(fact: String): Boolean {
        val normalized = fact.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return false
        val facts = readFacts().filterNot { it.equals(normalized, ignoreCase = true) }.toMutableList()
        facts.add(0, normalized)
        preferences.edit().putString(KEY_FACTS, JSONArray(facts.take(MAX_FACTS)).toString()).apply()
        return true
    }

    @Synchronized
    fun facts(): List<String> = readFacts()

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun readFacts(): List<String> {
        val raw = preferences.getString(KEY_FACTS, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            List(json.length()) { index -> json.optString(index).trim() }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun readPreferences(): Map<String, String> {
        val raw = preferences.getString(KEY_PREFERENCES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { k -> map[k] = json.optString(k) }
            map
        }.getOrDefault(emptyMap())
    }
}
