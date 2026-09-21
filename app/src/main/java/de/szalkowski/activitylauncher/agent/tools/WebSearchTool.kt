package de.szalkowski.activitylauncher.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/** Public web search without an API key; called only when the user allows web help. */
class WebSearchTool {
    suspend fun searchWeb(query: String): String = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext "پرسش خالی است."
        runCatching {
            val url = "https://html.duckduckgo.com/html/?q=${URLEncoder.encode(cleanQuery, "UTF-8")}"
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android) SamLauncher/2.1")
                .timeout(8_000)
                .get()
            val snippets = doc.select(".result__snippet")
                .take(3)
                .map { cleanSnippet(it.text()) }
                .filter { it.length > 20 }
                .distinct()
                .joinToString("\n") { "• $it" }
            snippets.ifBlank { "اطلاعات تازه‌ای در وب یافت نشد." }
        }.getOrElse {
            "جستجوی وب انجام نشد؛ می‌توانم بر اساس اطلاعات ذخیره‌شده و امکانات گوشی ادامه بدهم."
        }
    }

    private fun cleanSnippet(raw: String): String = raw
        .replace(Regex("\\b(likes?|comments?|shares?|views?|followers?)\\s*[:：]?\\s*[\\d,.]+\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(۲۰|۲۰۲[۰-۹]|202[0-9])[-/]\\d{1,2}[-/]\\d{1,2}\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '•', ':', '،')
}
