package de.szalkowski.activitylauncher.agent

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Locale

object SemanticAppSearch {
    private val aliases = mapOf(
        "عکس" to listOf("photo", "gallery", "image", "تصویر", "گالری"),
        "ویرایش عکس" to listOf("photo", "editor", "picsart", "تصویر", "ادیت"),
        "قبض" to listOf("bill", "payment", "wallet", "بانک", "پرداخت"),
        "پرداخت" to listOf("payment", "wallet", "bank", "بانک", "پرداخت"),
        "نقشه" to listOf("map", "navigation", "نقشه", "مسیریابی"),
        "موسیقی" to listOf("music", "spotify", "podcast", "موسیقی"),
        "پیام" to listOf("message", "sms", "chat", "پیام"),
        "مرورگر" to listOf("browser", "chrome", "firefox", "مرورگر"),
        "عکس ها" to listOf("photo", "gallery", "image", "تصویر", "گالری")
    )

    fun findMatches(context: Context, query: String, limit: Int = 12): List<ApplicationInfo> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return emptyList()
        val terms = aliases.entries.firstOrNull { normalized.contains(it.key) }
            ?.value.orEmpty() + normalized.split(Regex("\\s+"))
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.MATCH_ALL)
            .map { app -> app to score(pm, app, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun score(pm: PackageManager, app: ApplicationInfo, terms: List<String>): Int {
        val label = app.loadLabel(pm).toString().lowercase(Locale.ROOT)
        val packageName = app.packageName.lowercase(Locale.ROOT)
        return terms.fold(0) { total, term ->
            total + when {
                label == term || packageName == term -> 10
                label.contains(term) -> 5
                packageName.contains(term) -> 3
                else -> 0
            }
        }
    }
}
