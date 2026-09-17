package de.szalkowski.activitylauncher.agent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A locally persisted caregiver/assistant message for offline-first conversation recovery. */
@Entity(tableName = "caregiver_messages")
data class CaregiverMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val role: String,
    val content: String,
    val status: String = STATUS_PENDING,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SYNCED = "synced"
        const val STATUS_FAILED = "failed"
    }
}
