package de.szalkowski.activitylauncher.agent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import de.szalkowski.activitylauncher.agent.data.AgentDatabase
import de.szalkowski.activitylauncher.agent.data.KnowledgeDao
import de.szalkowski.activitylauncher.agent.data.KnowledgeEntity
import de.szalkowski.activitylauncher.agent.tools.WebSearchTool

/**
 * Keyless knowledge helper. It is deliberately separate from the cloud SamAgent:
 * web access is invoked only by AssistantActivity after the user's web-help setting
 * has allowed it, while the database remains available for offline retrieval.
 */
class SamAgentEngine(private val context: Context) {
    private val webSearchTool = WebSearchTool()
    private val knowledgeDao: KnowledgeDao = AgentDatabase.get(context).knowledgeDao()
    private val longTermMemory = LongTermMemoryStore(context)

    suspend fun processQuery(userQuery: String, allowWeb: Boolean): EngineResponse {
        val query = userQuery.trim()
        if (query.isBlank()) return EngineResponse.Text("پرسش خالی است.")
        if (query.contains("تنظیمات", ignoreCase = true) || query.contains("settings", ignoreCase = true)) {
            return EngineResponse.Action("OPEN_SETTINGS")
        }

        // Never surface a stale error message cached from a previous network failure:
        // entries that contain provider or DNS error text are skipped, not shown.
        longTermMemory.search(query, 3)
            .firstOrNull { entry -> !looksLikeError(entry.content) }
            ?.let {
                return EngineResponse.Text("دانش ذخیره‌شده: ${it.content}")
            }
        knowledgeDao.findKnowledge(query)?.takeIf { it.isNotBlank() && !looksLikeError(it) }?.let {
            return EngineResponse.Text("دانش ذخیره‌شده: $it")
        }
        if (!allowWeb || !isNetworkUsable()) return EngineResponse.Text("")

        val fetched = webSearchTool.searchWeb(query)
        if (fetched.isBlank() || fetched.contains("انجام نشد") || looksLikeError(fetched)) {
            return EngineResponse.Text("")
        }
        knowledgeDao.saveKnowledge(KnowledgeEntity(topic = query, content = fetched))
        longTermMemory.remember("web_summary", query, fetched, "web-search")
        return EngineResponse.Text(fetched)
    }

    private fun hasValidatedInternet(): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivity.activeNetwork ?: return false
            connectivity.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        } else {
            @Suppress("DEPRECATION")
            connectivity.activeNetworkInfo?.isConnectedOrConnecting == true
        }
    }

    private fun isNetworkUsable(): Boolean {
        if (!hasValidatedInternet()) return false
        return runCatching { java.net.InetAddress.getByName("google.com").hostAddress != null }.getOrDefault(false)
    }

    companion object {
        val ERROR_MARKERS = listOf(
            "خطا", "خطای", "Unable to resolve", "No address associated",
            "Exception", "failed to connect", "timeout", "UnknownHost",
            "EXECUTION FAILED"
        )

        fun looksLikeError(text: String): Boolean {
            val t = text.lowercase()
            return ERROR_MARKERS.any { t.contains(it.lowercase()) }
        }
    }
}

sealed class EngineResponse {
    data class Text(val message: String) : EngineResponse()
    data class Action(val actionType: String) : EngineResponse()
}
