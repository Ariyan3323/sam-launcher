package de.szalkowski.activitylauncher.agent

import android.content.Context
import java.util.Locale

class PrivacyGuard(context: Context) {
    companion object {
        private const val PREFS = "sam_privacy"
        private const val PRIVACY = "privacy_mode"
        private const val GUEST = "guest_mode"
        private val GUEST_ALLOWLIST = listOf(
            "settings", "تنظیمات", "calculator", "ماشین حساب", "clock", "ساعت",
            "camera", "دوربین", "browser", "مرورگر", "chrome", "phone", "تلفن"
        )
    }

    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val privacyMode: Boolean get() = preferences.getBoolean(PRIVACY, false)
    val guestMode: Boolean get() = preferences.getBoolean(GUEST, false)

    fun setPrivacyMode(enabled: Boolean) {
        preferences.edit().putBoolean(PRIVACY, enabled).apply()
    }

    fun setGuestMode(enabled: Boolean) {
        preferences.edit().putBoolean(GUEST, enabled).apply()
    }

    fun canPersistPersonalMemory(): Boolean = !privacyMode && !guestMode

    fun canOpenApp(query: String): Boolean {
        if (!guestMode) return true
        val normalized = query.lowercase(Locale.ROOT)
        return GUEST_ALLOWLIST.any { normalized.contains(it) }
    }

    fun status(): String = when {
        guestMode -> "مهمان"
        privacyMode -> "حریم خصوصی"
        else -> "عادی"
    }
}
