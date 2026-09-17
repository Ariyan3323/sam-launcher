package de.szalkowski.activitylauncher.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.szalkowski.activitylauncher.agent.data.AppDatabase
import de.szalkowski.activitylauncher.agent.data.CaregiverMessageEntity
import de.szalkowski.activitylauncher.agent.data.CaregiverRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CaregiverConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CaregiverRepository(AppDatabase.get(application).caregiverDao())

    val messages: StateFlow<List<CaregiverMessageEntity>> = repository.messages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = emptyList(),
    )

    fun saveUserMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch { repository.saveUserMessage(content) }
    }

    fun saveAssistantMessage(content: String, status: String = CaregiverMessageEntity.STATUS_SYNCED) {
        if (content.isBlank()) return
        viewModelScope.launch { repository.saveAssistantMessage(content, status) }
    }
}
