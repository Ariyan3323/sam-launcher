package de.szalkowski.activitylauncher.agent.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "agent_knowledge")
data class KnowledgeEntity(
    @PrimaryKey val topic: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface KnowledgeDao {
    @Query("SELECT content FROM agent_knowledge WHERE topic LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 1")
    suspend fun findKnowledge(query: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveKnowledge(knowledge: KnowledgeEntity)
}
