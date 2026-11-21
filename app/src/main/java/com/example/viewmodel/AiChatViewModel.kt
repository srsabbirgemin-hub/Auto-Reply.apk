package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiChatRole
import com.example.data.local.AiChatSessionEntity
import com.example.data.local.ApiConnectionStatus
import com.example.data.local.AppSettings
import com.example.data.network.AiApiResult
import com.example.data.network.ChatMessageDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiChatUiState(
    val sessions: List<AiChatSessionEntity> = emptyList(),
    val activeSession: AiChatSessionEntity? = null,
    val messages: List<AiChatMessageEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isSending: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(private val container: AppContainer) : ViewModel() {

    private val aiChatDao = container.database.aiChatDao()
    private val selectedSessionId = MutableStateFlow<Long?>(null)
    private val isSending = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val resolvedSessionId = combine(
        aiChatDao.observeSessions(),
        selectedSessionId
    ) { sessions, selectedId ->
        selectedId?.takeIf { id -> sessions.any { it.id == id } } ?: sessions.firstOrNull()?.id
    }.distinctUntilChanged()

    private val messagesFlow = resolvedSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else aiChatDao.observeMessages(id)
    }

    val uiState: StateFlow<AiChatUiState> = combine(
        aiChatDao.observeSessions(),
        resolvedSessionId,
        messagesFlow,
        container.preferencesRepository.settingsFlow,
        combine(isSending, errorMessage) { sending, err -> sending to err }
    ) { sessions, activeId, messages, settings, (sending, err) ->
        val active = sessions.firstOrNull { it.id == activeId } ?: sessions.firstOrNull()
        AiChatUiState(
            sessions = sessions,
            activeSession = active,
            messages = messages,
            settings = settings,
            isSending = sending,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiChatUiState()
    )

    fun selectSession(sessionId: Long) {
        selectedSessionId.value = sessionId
        errorMessage.value = null
    }

    fun createNewSession(
        title: String = "New Assistant Chat",
        personality: String? = null,
        responseStyle: String? = null,
        modelName: String? = null
    ) {
        viewModelScope.launch {
            val settings = container.preferencesRepository.getSettingsOnce()
            val id = aiChatDao.insertSession(
                AiChatSessionEntity(
                    title = title.ifBlank { "New Assistant Chat" },
                    modelName = modelName ?: settings.modelName,
                    personality = personality ?: settings.globalPersonality,
                    responseStyle = responseStyle ?: settings.globalResponseStyle,
                    useAsAutoReplyContext = false
                )
            )
            selectedSessionId.value = id
            errorMessage.value = null
        }
    }

    fun updateSessionConfig(
        session: AiChatSessionEntity,
        title: String,
        modelName: String,
        personality: String,
        responseStyle: String
    ) {
        viewModelScope.launch {
            aiChatDao.updateSession(
                session.copy(
                    title = title.trim().ifBlank { session.title },
                    modelName = modelName.trim().ifBlank { session.modelName },
                    personality = personality.trim(),
                    responseStyle = responseStyle.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleUseAsAutoReplyContext(session: AiChatSessionEntity, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                aiChatDao.setExclusiveContextSession(session.id)
            } else {
                aiChatDao.clearAllContextSessions()
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            aiChatDao.deleteMessagesForSession(sessionId)
            aiChatDao.deleteSessionById(sessionId)
            if (selectedSessionId.value == sessionId) {
                selectedSessionId.value = null
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    fun sendMessage(userText: String) {
        val cleanText = userText.trim()
        if (cleanText.isBlank()) return

        viewModelScope.launch {
            errorMessage.value = null
            val settings = container.preferencesRepository.getSettingsOnce()
            val apiKey = container.preferencesRepository.getDecryptedApiKey().orEmpty()
            if (apiKey.isBlank()) {
                errorMessage.value = "Please configure your AI API key in Settings first."
                return@launch
            }

            var session = uiState.value.activeSession
            if (session == null) {
                val newId = aiChatDao.insertSession(
                    AiChatSessionEntity(
                        title = cleanText.take(36),
                        modelName = settings.modelName,
                        personality = settings.globalPersonality,
                        responseStyle = settings.globalResponseStyle
                    )
                )
                selectedSessionId.value = newId
                session = aiChatDao.getSessionById(newId) ?: return@launch
            }

            aiChatDao.insertMessage(
                AiChatMessageEntity(
                    sessionId = session.id,
                    role = AiChatRole.USER,
                    content = cleanText
                )
            )

            // Update default title on first user prompt
            if (session.title == "New Assistant Chat") {
                aiChatDao.updateSession(
                    session.copy(
                        title = cleanText.take(36),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            isSending.value = true
            try {
                val historyLimit = if (settings.dataSaverMode) 6 else 14
                val history = aiChatDao.getRecentMessages(session.id, historyLimit).sortedBy { it.timestamp }
                val dtoList = mutableListOf<ChatMessageDto>()
                dtoList.add(
                    ChatMessageDto(
                        role = "system",
                        content = "You are a helpful AI assistant inside Smart AI Auto Reply. " +
                            "Personality: ${session.personality}. Response style: ${session.responseStyle}. " +
                            "Keep responses clear, accurate, and natural."
                    )
                )
                history.forEach { msg ->
                    dtoList.add(
                        ChatMessageDto(
                            role = if (msg.role == AiChatRole.USER) "user" else "assistant",
                            content = msg.content
                        )
                    )
                }

                val effectiveMaxTokens = if (settings.dataSaverMode) {
                    settings.maxResponseTokens.coerceAtMost(180)
                } else {
                    settings.maxResponseTokens.coerceAtLeast(350)
                }
                when (val res = container.aiApiClient.generateCompletion(
                    baseUrl = settings.apiBaseUrl,
                    apiKey = apiKey,
                    model = session.modelName.ifBlank { settings.modelName },
                    messages = dtoList,
                    temperature = settings.temperature,
                    maxTokens = effectiveMaxTokens
                )) {
                    is AiApiResult.Success -> {
                        container.preferencesRepository.updateConnectionStatus(
                            ApiConnectionStatus.CONNECTED,
                            res.latencyMs,
                            ""
                        )
                        aiChatDao.insertMessage(
                            AiChatMessageEntity(
                                sessionId = session.id,
                                role = AiChatRole.ASSISTANT,
                                content = res.data
                            )
                        )
                        aiChatDao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
                    }
                    is AiApiResult.Error -> {
                        container.preferencesRepository.updateConnectionStatus(
                            ApiConnectionStatus.ERROR,
                            0L,
                            res.message
                        )
                        errorMessage.value = res.message
                    }
                }
            } finally {
                isSending.value = false
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AiChatViewModel(container) as T
                }
            }
    }
}
