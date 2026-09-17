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

    suspend fun processQuery(userQuery: String, allowWeb: Boolean): EngineResponse {
        val query = userQuery.trim()
        if (query.isBlank()) return EngineResponse.Text("پرسش خالی است.")
        if (query.contains("تنظیمات", ignoreCase = true) || query.contains("settings", ignoreCase = true)) {
            return EngineResponse.Action("OPEN_SETTINGS")
        }

        knowledgeDao.findKnowledge(query)?.takeIf { it.isNotBlank() }?.let {
            return EngineResponse.Text("دانش آفلاین: $it")
        }
        if (!allowWeb) return EngineResponse.Text("برای جستجوی وب باید اجازهٔ کمک وب را در تنظیمات فعال کنی.")
        if (!hasValidatedInternet()) {
            return EngineResponse.Text("اینترنت در دسترس نیست؛ پاسخ محلی سام فعال است.")
        }

        val fetched = webSearchTool.searchWeb(query)
        if (fetched.isNotBlank() && !fetched.contains("انجام نشد")) {
            knowledgeDao.saveKnowledge(KnowledgeEntity(topic = query, content = fetched))
        }
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
}

sealed class EngineResponse {
    data class Text(val message: String) : EngineResponse()
    data class Action(val actionType: String) : EngineResponse()
}
