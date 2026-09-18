package de.szalkowski.activitylauncher.agent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Local long-term user profile storage with bounded prompt projection. */
class UserProfileRepository(private val dao: UserProfileDao) {
    val profiles: Flow<List<UserProfileEntity>> = dao.observeAll()

    suspend fun save(key: String, value: String, category: String = key): Boolean =
        withContext(Dispatchers.IO) {
            val cleanKey = key.trim().take(MAX_KEY_LENGTH)
            val cleanValue = value.trim().take(MAX_VALUE_LENGTH)
            val cleanCategory = category.trim().take(MAX_CATEGORY_LENGTH)
            if (cleanKey.isBlank() || cleanValue.isBlank() || cleanCategory.isBlank()) return@withContext false
            dao.upsert(UserProfileEntity(cleanKey, cleanValue, cleanCategory))
            true
        }

    suspend fun find(key: String): UserProfileEntity? =
        withContext(Dispatchers.IO) { dao.findByKey(key.trim()) }

    suspend fun all(): List<UserProfileEntity> =
        withContext(Dispatchers.IO) { dao.getAll() }

    suspend fun delete(key: String) =
        withContext(Dispatchers.IO) { dao.deleteByKey(key.trim()) }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.deleteAll() }

    suspend fun promptSummary(maxLength: Int = MAX_PROMPT_LENGTH): String = withContext(Dispatchers.IO) {
        val summary = dao.getAll()
            .asSequence()
            .sortedByDescending { it.updatedAt }
            .map { "${it.category}/${it.key}: ${it.value}" }
            .joinToString("؛ ")
        summary.take(maxLength)
    }

    companion object {
        private const val MAX_KEY_LENGTH = 80
        private const val MAX_VALUE_LENGTH = 500
        private const val MAX_CATEGORY_LENGTH = 80
        private const val MAX_PROMPT_LENGTH = 1_500
    }
}
