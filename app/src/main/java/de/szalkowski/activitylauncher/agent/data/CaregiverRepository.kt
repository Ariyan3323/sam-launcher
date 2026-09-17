package de.szalkowski.activitylauncher.agent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Offline-first repository for caregiver conversation messages. */
class CaregiverRepository(private val dao: CaregiverDao) {
    val messages: Flow<List<CaregiverMessageEntity>> = dao.observeAll()

    suspend fun saveMessage(role: String, content: String, status: String = CaregiverMessageEntity.STATUS_SYNCED): Long =
        withContext(Dispatchers.IO) {
            dao.insert(
                CaregiverMessageEntity(
                    role = role,
                    content = content.trim(),
                    status = status,
                )
            )
        }

    suspend fun saveUserMessage(content: String): Long =
        saveMessage(ROLE_USER, content)

    suspend fun saveAssistantMessage(content: String, status: String = CaregiverMessageEntity.STATUS_SYNCED): Long =
        saveMessage(ROLE_ASSISTANT, content, status)

    suspend fun pendingMessages(): List<CaregiverMessageEntity> =
        withContext(Dispatchers.IO) {
            dao.getByStatus(CaregiverMessageEntity.STATUS_PENDING)
        }

    suspend fun markStatus(id: Long, status: String) =
        withContext(Dispatchers.IO) { dao.updateStatus(id, status) }

    suspend fun clearConversation() =
        withContext(Dispatchers.IO) { dao.deleteAll() }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}
