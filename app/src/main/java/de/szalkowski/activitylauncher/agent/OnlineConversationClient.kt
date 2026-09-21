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
    suspend fun answer(userText: String): String? = withContext(Dispatchers.IO) {
        val prompt = userText.trim()
        if (prompt.isBlank()) return@withContext null
        runCatching {
            val connection = (URL("https://text.pollinations.ai/openai").openConnection() as HttpURLConnection).apply {
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
                    put("content", "تو سام هستی؛ فارسی، صمیمی، دقیق و کوتاه پاسخ بده. برای درخواست‌های اجرایی فقط توضیح بده و اجرای کار را به لانچر بسپار.")
                }).put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }))
            }.toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = if (status in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else ""
            connection.disconnect()
            if (status !in 200..299) return@runCatching null
            parseResponse(response)
        }.getOrNull()
    }

    private fun parseResponse(body: String): String? = runCatching {
        val json = JSONObject(body)
        json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: json.optString("text").trim().takeIf { it.isNotBlank() }
            ?: body.trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}
