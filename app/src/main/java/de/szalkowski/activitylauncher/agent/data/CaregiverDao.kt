package de.szalkowski.activitylauncher.agent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaregiverDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: CaregiverMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<CaregiverMessageEntity>)

    @Query("SELECT * FROM caregiver_messages ORDER BY timestamp ASC, id ASC")
    fun observeAll(): Flow<List<CaregiverMessageEntity>>

    @Query("SELECT * FROM caregiver_messages ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<CaregiverMessageEntity>

    @Query("SELECT * FROM caregiver_messages WHERE status = :status ORDER BY timestamp ASC, id ASC")
    suspend fun getByStatus(status: String): List<CaregiverMessageEntity>

    @Query("UPDATE caregiver_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM caregiver_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM caregiver_messages")
    suspend fun deleteAll()
}
