package de.szalkowski.activitylauncher.agent.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(
    tableName = "agent_long_term_memory",
    indices = [Index(value = ["topic"]), Index(value = ["kind"])]
)
data class LongTermMemoryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val topic: String,
    val content: String,
    val source: String = "agent",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface LongTermMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LongTermMemoryEntity): Long

    @Query("SELECT * FROM agent_long_term_memory WHERE topic LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 6): List<LongTermMemoryEntity>

    @Query("SELECT * FROM agent_long_term_memory ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun latest(limit: Int = 20): List<LongTermMemoryEntity>

    @Query("SELECT COUNT(*) FROM agent_long_term_memory")
    suspend fun count(): Int
}
