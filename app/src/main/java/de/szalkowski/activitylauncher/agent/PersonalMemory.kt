package de.szalkowski.activitylauncher.agent

import android.content.Context
import org.json.JSONArray

/** Small on-device memory store. It never syncs facts to a server. */
class PersonalMemory(context: Context) {
    companion object {
        private const val PREFS = "sam_personal_memory"
        private const val KEY_FACTS = "facts"
        private const val MAX_FACTS = 30
    }

    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
        preferences.edit().remove(KEY_FACTS).apply()
    }

    private fun readFacts(): List<String> {
        val raw = preferences.getString(KEY_FACTS, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            List(json.length()) { index -> json.optString(index).trim() }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}
