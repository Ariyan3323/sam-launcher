package de.szalkowski.activitylauncher.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
        runCatching {
            val connection = (URL(freeEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val body = JSONObject().apply {
                put("model", "openai")
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "system")
                    put("content", "تو سام هستی؛ یک رفیق باهوش، گرم و دلسوز. فارسی را طبیعی، روان و بدون غلط املایی بنویس و قبل از ارسال یک بار املا و نیم‌فاصله‌ها را بازبینی کن. لحن محاوره‌ای و دوستانه داشته باش، نه کتابی و اداری. پاسخ معمولی را کوتاه و مستقیم در یک تا سه جمله بده. جدول، تیتر، شماره‌گذاری، بولت، JSON و Markdown پیچیده نساز. اگر پرسش نیاز به تحلیل دارد، واضح و انسانی جمع‌بندی کن. درباره فرمان‌های گوشی نگو نمی‌توانی؛ این فرمان‌ها پیش از رسیدن به تو اجرا می‌شوند.")
                }).put(JSONObject().apply {
                    put("role", "user")
                    put("content", if (researchContext.isNullOrBlank()) prompt else
                        "پرسش کاربر: $prompt\n\nیافته‌های وب برای بررسی و جمع‌بندی:\n$researchContext\n\nیافته‌ها را با دانسته‌های خودت یکپارچه کن. پاسخ را روان و انسانی در چند پاراگراف کوتاه بده؛ نتیجه اصلی را اول بگو، منبع یا بازه زمانی را فقط وقتی لازم است اشاره کن، و اگر شواهد متناقض است خیلی ساده توضیح بده. جدول و گزارش خشک نساز.")
                }))
            }.toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = if (status in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else ""
            connection.disconnect()
            if (status !in 200..299 || response.contains("doesn't have enough credits", ignoreCase = true)) {
                return@runCatching null
            }
            parseResponse(response)?.let(PersianResponsePolisher::clean)
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
