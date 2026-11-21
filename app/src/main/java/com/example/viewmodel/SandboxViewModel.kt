package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.CategoryEntity
import com.example.domain.DryRunTrace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SandboxUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val isRunning: Boolean = false,
    val lastTrace: DryRunTrace? = null
)

class SandboxViewModel(private val container: AppContainer) : ViewModel() {

    private val isRunning = MutableStateFlow(false)
    private val lastTrace = MutableStateFlow<DryRunTrace?>(null)

    val uiState: StateFlow<SandboxUiState> = combine(
        container.database.categoryDao().observeAll(),
        isRunning,
        lastTrace
    ) { categories, running, trace ->
        SandboxUiState(
            categories = categories,
            isRunning = running,
            lastTrace = trace
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SandboxUiState()
    )

    fun runSimulation(
        senderName: String,
        appDisplayName: String,
        incomingMessage: String,
        isGroupChat: Boolean,
        selectedCategory: CategoryEntity?
    ) {
        if (incomingMessage.isBlank()) return
        viewModelScope.launch {
            isRunning.value = true
            try {
                lastTrace.value = container.autoReplyEngine.runDryRunSimulation(
                    senderName = senderName,
                    appDisplayName = appDisplayName,
                    incomingMessage = incomingMessage,
                    isGroupChat = isGroupChat,
                    selectedCategory = selectedCategory
                )
            } finally {
                isRunning.value = false
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SandboxViewModel(container) as T
                }
            }
    }
}
