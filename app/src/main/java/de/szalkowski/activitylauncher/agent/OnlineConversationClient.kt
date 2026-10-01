package de.szalkowski.activitylauncher.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Connected chat fallback for natural conversation when no user provider key is configured.
 * Phone actions never use this client; they remain on the native command router.
 */
class OnlineConversationClient {
    private val freeEndpoint = "https://text.pollinations.ai/"

    suspend fun answer(userText: String): String? = withContext(Dispatchers.IO) {
        answer(userText, null)
    }

    suspend fun answer(userText: String, researchContext: String?): String? = withContext(Dispatchers.IO) {
        val prompt = userText.trim()
        if (prompt.isBlank()) return@withContext null
        
        // Try JSON POST first
        val postResult = runCatching {
            val connection = (URL(freeEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val body = JSONObject().apply {
                put("model", "openai")
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "system")
                    put("content", "تو سام هستی؛ دستیار هوشمند و رفیق صمیمی. فارسی را فوق‌العاده روان، طبیعی و صمیمی صحبت کن. در ۲ الی ۴ جمله پاسخ شفاف، عمیق و دوستانه بده. هیچ قالب خشک، جدول یا متون رباتی نده.")
                }).put(JSONObject().apply {
                    put("role", "user")
                    put("content", if (researchContext.isNullOrBlank()) prompt else
                        "پرسش کاربر: $prompt\n\nاطلاعات مرتبط:\n$researchContext\n\nپاسخ را خلاصه و دوستانه بگو.")
                }))
            }.toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = if (status in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else ""
            connection.disconnect()
            if (status in 200..299 && !response.contains("doesn't have enough credits", ignoreCase = true)) {
                parseResponse(response)?.let(PersianResponsePolisher::clean)
            } else null
        }.getOrNull()

        if (!postResult.isNullOrBlank()) return@withContext postResult

        // Secondary fallback: Direct GET query
        runCatching {
            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
            val getUrl = "https://text.pollinations.ai/$encodedPrompt?model=openai"
            val conn = (URL(getUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 25_000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val status = conn.responseCode
            val response = if (status in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else ""
            conn.disconnect()
            if (status in 200..299 && response.isNotBlank()) {
                PersianResponsePolisher.clean(response.trim())
            } else null
        }.getOrNull()
    }

    private fun parseResponse(body: String): String? = runCatching {
        val trimmed = body.trim()
        if (trimmed.isBlank() || trimmed.contains("doesn't have enough credits", ignoreCase = true)) return@runCatching null
        if (!trimmed.startsWith("{")) return@runCatching trimmed
        val json = JSONObject(trimmed)
        json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: json.optString("text").trim().takeIf { it.isNotBlank() }
            ?: trimmed.takeIf { it.isNotBlank() }
    }.getOrNull()
}
