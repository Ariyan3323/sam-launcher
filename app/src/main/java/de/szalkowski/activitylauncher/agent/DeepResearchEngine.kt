package de.szalkowski.activitylauncher.agent

import de.szalkowski.activitylauncher.agent.tools.WebSearchTool
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Collects several independent web-search angles, then asks the online model to synthesize them. */
class DeepResearchEngine {
    private val search = WebSearchTool()
    private val online = OnlineConversationClient()

    suspend fun answer(query: String): String? = coroutineScope {
        val clean = query.trim()
        if (clean.isBlank()) return@coroutineScope null
        val queries = listOf(
            clean,
            "$clean latest news facts",
            "$clean causes consequences expert analysis",
        ).distinct()
        val results = queries.map { variant -> async { search.searchWeb(variant) } }.awaitAll()
        val sources = results
            .filter { it.isNotBlank() && !it.startsWith("جستجوی وب انجام نشد") }
            .mapIndexed { index, text -> "منبع/جست‌وجوی ${index + 1}:\n$text" }
            .joinToString("\n\n")
            .takeIf { it.isNotBlank() }
            ?: return@coroutineScope null
        online.answer(clean, sources)
    }
}
