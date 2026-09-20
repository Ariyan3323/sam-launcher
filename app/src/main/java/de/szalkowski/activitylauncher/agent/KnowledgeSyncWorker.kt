package de.szalkowski.activitylauncher.agent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.szalkowski.activitylauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Imports signed-by-transport knowledge packages when connectivity is available.
 * The feed is optional and configured with SAM_KNOWLEDGE_FEED_URL at build time.
 * Expected JSON: [{"topic":"...","content":"...","source":"..."}]
 */
class KnowledgeSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.SAM_KNOWLEDGE_FEED_URL.trim()
        if (endpoint.isBlank()) return@withContext Result.success()
        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }
            val status = connection.responseCode
            val body = if (status in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
            connection.disconnect()
            if (status !in 200..299) return@withContext Result.retry()
            importPackage(body)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun importPackage(body: String): Boolean {
        val items = JSONArray(body)
        val memory = LongTermMemoryStore(applicationContext)
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            memory.remember(
                kind = item.optString("kind", "scientific_summary"),
                topic = item.optString("topic").trim(),
                content = item.optString("content").trim(),
                source = item.optString("source", "knowledge-feed"),
            )
        }
        return true
    }
}
