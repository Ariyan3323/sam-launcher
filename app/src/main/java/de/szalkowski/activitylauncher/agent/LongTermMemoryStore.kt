package de.szalkowski.activitylauncher.agent

import android.content.Context
import de.szalkowski.activitylauncher.agent.data.AgentDatabase
import de.szalkowski.activitylauncher.agent.data.LongTermMemoryEntity

/** Durable, device-only memory used by Sam in both connected and disconnected sessions. */
class LongTermMemoryStore(context: Context) {
    private val dao = AgentDatabase.get(context.applicationContext).longTermMemoryDao()

    suspend fun remember(kind: String, topic: String, content: String, source: String = "agent") {
        val cleanTopic = topic.trim().take(240)
        val cleanContent = content.trim().take(12_000)
        if (cleanTopic.isBlank() || cleanContent.isBlank()) return
        dao.insert(
            LongTermMemoryEntity(
                kind = kind,
                topic = cleanTopic,
                content = cleanContent,
                source = source,
            )
        )
    }

    suspend fun search(query: String, limit: Int = 6): List<LongTermMemoryEntity> =
        query.trim().takeIf { it.isNotBlank() }?.let { dao.search(it, limit) }.orEmpty()

    suspend fun latest(limit: Int = 20): List<LongTermMemoryEntity> = dao.latest(limit)

    suspend fun count(): Int = dao.count()
}
