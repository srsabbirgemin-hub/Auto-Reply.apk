package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.CategoryEntity
import com.example.data.local.ContactRuleEntity
import com.example.data.local.MessageDirection
import com.example.data.local.MessageLogEntity
import com.example.data.local.ReplyStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MessageFilterMode(val label: String) {
    ALL("All"),
    INCOMING("Incoming"),
    OUTGOING("Replies"),
    PENDING("Needs Action")
}

data class ConversationSummary(
    val conversationKey: String,
    val senderName: String,
    val senderIdentifier: String,
    val packageName: String,
    val appDisplayName: String,
    val latestMessage: MessageLogEntity,
    val totalMessages: Int,
    val hasPendingApproval: Boolean,
    val canReplyInline: Boolean,
    val assignedCategoryName: String?
)

data class MessagesUiState(
    val searchQuery: String = "",
    val selectedAppPackage: String? = null,
    val filterMode: MessageFilterMode = MessageFilterMode.ALL,
    val availableAppFilters: List<Pair<String, String>> = emptyList(),
    val conversations: List<ConversationSummary> = emptyList(),
    val allLogs: List<MessageLogEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val contactRules: Map<String, ContactRuleEntity> = emptyMap(),
    val replyableKeys: Set<String> = emptySet(),
    val isDrafting: Boolean = false,
    val statusBanner: String? = null
)

class MessagesViewModel(private val container: AppContainer) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedAppPackage = MutableStateFlow<String?>(null)
    private val filterMode = MutableStateFlow(MessageFilterMode.ALL)
    private val isDrafting = MutableStateFlow(false)
    private val statusBanner = MutableStateFlow<String?>(null)

    private val filterState = combine(
        searchQuery,
        selectedAppPackage,
        filterMode,
        isDrafting,
        statusBanner
    ) { query, appPkg, mode, drafting, banner ->
        FilterBundle(query, appPkg, mode, drafting, banner)
    }

    val uiState: StateFlow<MessagesUiState> = combine(
        container.database.messageLogDao().observeAll(),
        container.database.categoryDao().observeAll(),
        container.database.contactRuleDao().observeAll(),
        container.replyRegistry.replyableKeys,
        filterState
    ) { logs, categories, rules, replyable, filters ->
        val rulesMap = rules.associateBy { it.conversationKey }
        val categoryMap = categories.associateBy { it.id }

        val appFilters = logs
            .map { it.packageName to it.appDisplayName }
            .distinctBy { it.first }
            .sortedBy { it.second }

        val filteredLogs = logs.filter { log ->
            val matchesApp = filters.appPkg == null || log.packageName == filters.appPkg
            val matchesMode = when (filters.mode) {
                MessageFilterMode.ALL -> true
                MessageFilterMode.INCOMING -> log.direction == MessageDirection.INCOMING
                MessageFilterMode.OUTGOING -> log.direction == MessageDirection.OUTGOING
                MessageFilterMode.PENDING -> log.replyStatus == ReplyStatus.PENDING_APPROVAL ||
                    log.replyStatus == ReplyStatus.FAILED_API_ERROR ||
                    log.replyStatus == ReplyStatus.FAILED_NO_API_KEY
            }
            val q = filters.query.trim()
            val matchesSearch = q.isEmpty() ||
                log.senderName.contains(q, ignoreCase = true) ||
                log.senderIdentifier.contains(q, ignoreCase = true) ||
                log.messageText.contains(q, ignoreCase = true) ||
                log.aiGeneratedReply.contains(q, ignoreCase = true) ||
                log.appDisplayName.contains(q, ignoreCase = true)
            matchesApp && matchesMode && matchesSearch
        }

        val conversations = filteredLogs
            .groupBy { it.conversationKey }
            .mapNotNull { (key, groupLogs) ->
                val sortedDesc = groupLogs.sortedByDescending { it.timestamp }
                val latest = sortedDesc.firstOrNull() ?: return@mapNotNull null
                val rule = rulesMap[key]
                val catName = rule?.assignedCategoryId?.let { categoryMap[it]?.name }
                ConversationSummary(
                    conversationKey = key,
                    senderName = latest.senderName,
                    senderIdentifier = latest.senderIdentifier,
                    packageName = latest.packageName,
                    appDisplayName = latest.appDisplayName,
                    latestMessage = latest,
                    totalMessages = groupLogs.size,
                    hasPendingApproval = groupLogs.any { it.replyStatus == ReplyStatus.PENDING_APPROVAL },
                    canReplyInline = replyable.contains(key),
                    assignedCategoryName = catName
                )
            }
            .sortedByDescending { it.latestMessage.timestamp }

        MessagesUiState(
            searchQuery = filters.query,
            selectedAppPackage = filters.appPkg,
            filterMode = filters.mode,
            availableAppFilters = appFilters,
            conversations = conversations,
            allLogs = filteredLogs,
            categories = categories,
            contactRules = rulesMap,
            replyableKeys = replyable,
            isDrafting = filters.drafting,
            statusBanner = filters.banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MessagesUiState()
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSelectedAppPackage(packageName: String?) {
        selectedAppPackage.value = packageName
    }

    fun setFilterMode(mode: MessageFilterMode) {
        filterMode.value = mode
    }

    fun clearBanner() {
        statusBanner.value = null
    }

    fun deleteConversation(conversationKey: String) {
        viewModelScope.launch {
            container.database.messageLogDao().deleteConversation(conversationKey)
            statusBanner.value = "Conversation history deleted."
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            container.database.messageLogDao().deleteById(id)
        }
    }

    fun saveContactRule(
        conversationKey: String,
        senderName: String,
        packageName: String,
        assignedCategoryId: Long?,
        customInstructions: String,
        useContextualMemory: Boolean,
        isAutoReplyMuted: Boolean
    ) {
        viewModelScope.launch {
            container.database.contactRuleDao().upsert(
                ContactRuleEntity(
                    conversationKey = conversationKey,
                    senderName = senderName,
                    packageName = packageName,
                    assignedCategoryId = assignedCategoryId,
                    customInstructions = customInstructions.trim(),
                    useContextualMemory = useContextualMemory,
                    isAutoReplyMuted = isAutoReplyMuted,
                    lastUpdatedAt = System.currentTimeMillis()
                )
            )
            statusBanner.value = if (isAutoReplyMuted) {
                "Auto-reply muted for $senderName."
            } else {
                "Saved contact settings for $senderName."
            }
        }
    }

    fun draftAiReply(
        conversationKey: String,
        senderName: String,
        appDisplayName: String,
        latestIncomingText: String,
        toneOverride: String? = null,
        onDraftReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            isDrafting.value = true
            try {
                val result = container.autoReplyEngine.draftAiReplyForConversation(
                    conversationKey = conversationKey,
                    senderName = senderName,
                    appDisplayName = appDisplayName,
                    latestIncomingMessage = latestIncomingText,
                    toneOverride = toneOverride
                )
                result.onSuccess { draft ->
                    onDraftReady(draft)
                }.onFailure { err ->
                    statusBanner.value = err.localizedMessage ?: "Failed to generate AI draft."
                }
            } finally {
                isDrafting.value = false
            }
        }
    }

    fun sendManualReply(
        conversationKey: String,
        packageName: String,
        appDisplayName: String,
        senderName: String,
        replyText: String,
        sourceMessageId: Long? = null,
        onSent: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = container.autoReplyEngine.sendManualOrEditedReply(
                conversationKey = conversationKey,
                packageName = packageName,
                appDisplayName = appDisplayName,
                senderName = senderName,
                replyText = replyText,
                sourceMessageId = sourceMessageId
            )
            result.onSuccess {
                statusBanner.value = "Reply sent via $appDisplayName."
                onSent()
            }.onFailure { err ->
                statusBanner.value = err.localizedMessage
                    ?: "Could not send reply. Ensure the notification is still active."
            }
        }
    }

    private data class FilterBundle(
        val query: String,
        val appPkg: String?,
        val mode: MessageFilterMode,
        val drafting: Boolean,
        val banner: String?
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MessagesViewModel(container) as T
                }
            }
    }
}
