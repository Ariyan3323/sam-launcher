package de.szalkowski.activitylauncher.agent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<UserProfileEntity>)

    @Query("SELECT * FROM user_profile ORDER BY category ASC, updatedAt DESC")
    fun observeAll(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profile ORDER BY category ASC, updatedAt DESC")
    suspend fun getAll(): List<UserProfileEntity>

    @Query("SELECT * FROM user_profile WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getByCategory(category: String): List<UserProfileEntity>

    @Query("DELETE FROM user_profile WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()
}
