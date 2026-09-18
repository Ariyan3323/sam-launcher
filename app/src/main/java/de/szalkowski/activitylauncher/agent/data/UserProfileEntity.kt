package de.szalkowski.activitylauncher.agent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val category: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
