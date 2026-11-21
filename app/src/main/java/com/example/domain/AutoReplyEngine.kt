package com.example.domain

import android.content.Context
import com.example.data.local.AiChatDao
import com.example.data.local.ApiConnectionStatus
import com.example.data.local.CategoryDao
import com.example.data.local.CategoryEntity
import com.example.data.local.CommandDao
import com.example.data.local.CommandEntity
import com.example.data.local.ContactRuleDao
import com.example.data.local.ContactRuleEntity
import com.example.data.local.MessageDirection
import com.example.data.local.MessageLogDao
import com.example.data.local.MessageLogEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.ReplyStatus
import com.example.data.local.ReplySupportStatus
import com.example.data.local.SmartActionDao
import com.example.data.local.SupportedAppDao
import com.example.data.local.SupportedAppEntity
import com.example.data.network.AiApiClient
import com.example.data.network.AiApiResult
import com.example.service.ActiveReplyActionRegistry
import com.example.service.ExtractedNotificationMessage
import com.example.service.NotificationReplyHandle
import kotlinx.coroutines.delay

data class DryRunTrace(
    val decisionSource: String,
    val replyText: String,
    val categoryUsed: String,
    val latencyMs: Long = 0L,
    val error: String? = null
)

class AutoReplyEngine(
    private val context: Context,
    private val supportedAppDao: SupportedAppDao,
    private val categoryDao: CategoryDao,
    private val commandDao: CommandDao,
    private val smartActionDao: SmartActionDao,
    private val messageLogDao: MessageLogDao,
    private val contactRuleDao: ContactRuleDao,
    private val aiChatDao: AiChatDao,
    private val preferencesRepository: PreferencesRepository,
    private val aiApiClient: AiApiClient,
    private val deduplicator: NotificationDeduplicator,
    private val smartActionEvaluator: SmartActionEvaluator,
    private val locationHelper: LocationHelper,
    private val appPackageInspector: AppPackageInspector,
    private val replyRegistry: ActiveReplyActionRegistry
) {

    suspend fun processIncomingNotification(extracted: ExtractedNotificationMessage) {
        // 1. Deduplicate sliding window + echo check
        if (deduplicator.isDuplicateAndRecord(
                packageName = extracted.packageName,
                sender = extracted.senderName,
                messageText = extracted.messageText,
                notificationKey = extracted.notificationKey,
                postTimeMs = extracted.postTimeMs
            )
        ) {
            return
        }

        val dedupHash = deduplicator.computeContentHash(
            extracted.packageName,
            extracted.senderName,
            extracted.messageText
        )
        if (messageLogDao.hasRecentDedupHash(dedupHash, System.currentTimeMillis() - 30_000L)) {
            return
        }

        // 2. Register or update app metadata & reply support status
        val existingApp = supportedAppDao.getByPackage(extracted.packageName)
        val appDisplayName = existingApp?.displayName
            ?: appPackageInspector.getAppLabel(extracted.packageName, extracted.packageName)

        val supportStatus = if (extracted.supportsInlineReply) {
            ReplySupportStatus.SUPPORTED
        } else {
            ReplySupportStatus.UNSUPPORTED_NO_REPLY
        }

        if (existingApp == null) {
            supportedAppDao.upsert(
                SupportedAppEntity(
                    packageName = extracted.packageName,
                    displayName = appDisplayName,
                    isEnabled = false,
                    isInstalled = true,
                    replySupportStatus = supportStatus,
                    categoryHint = "Detected App",
                    lastDetectedAt = System.currentTimeMillis()
                )
            )
        } else {
            supportedAppDao.updateReplySupport(
                packageName = extracted.packageName,
                status = supportStatus,
                timestamp = System.currentTimeMillis()
            )
        }

        // Store live RemoteInput reply handle if available (for manual or edited replies later)
        if (extracted.supportsInlineReply &&
            extracted.replyPendingIntent != null &&
            extracted.remoteInputs != null &&
            extracted.primaryRemoteInputKey != null
        ) {
            replyRegistry.register(
                NotificationReplyHandle(
                    conversationKey = extracted.conversationKey,
                    packageName = extracted.packageName,
                    senderName = extracted.senderName,
                    pendingIntent = extracted.replyPendingIntent,
                    remoteInputs = extracted.remoteInputs,
                    resultKey = extracted.primaryRemoteInputKey
                )
            )
        }

        // Ensure contact rule exists
        val contactRule = contactRuleDao.getForContact(extracted.conversationKey) ?: ContactRuleEntity(
            conversationKey = extracted.conversationKey,
            senderName = extracted.senderName,
            packageName = extracted.packageName,
            assignedCategoryId = null,
            customInstructions = "",
            useContextualMemory = true,
            isAutoReplyMuted = false
        ).also { contactRuleDao.upsert(it) }

        val settings = preferencesRepository.getSettingsOnce()
        val isAppEnabled = existingApp?.isEnabled ?: false

        // 3. Check Master ON/OFF and per-app toggle
        if (!settings.isAutoReplyEnabled || !isAppEnabled) {
            logIncomingOnly(extracted, appDisplayName, ReplyStatus.SKIPPED_DISABLED, dedupHash)
            return
        }

        // 3b. Check per-contact mute rule
        if (contactRule.isAutoReplyMuted) {
            logIncomingOnly(
                extracted,
                appDisplayName,
                ReplyStatus.SKIPPED_MUTED_CONTACT,
                dedupHash,
                "Auto-reply is muted for ${extracted.senderName}."
            )
            return
        }

        // 3c. Check group chat handling preference
        if (extracted.isGroupConversation && !settings.replyToGroupChats) {
            logIncomingOnly(
                extracted,
                appDisplayName,
                ReplyStatus.SKIPPED_GROUP_CHAT,
                dedupHash,
                "Group chat auto-replies are turned off in Settings."
            )
            return
        }

        // 4. Check inline notification reply capability
        if (!extracted.supportsInlineReply) {
            logIncomingOnly(
                extracted,
                appDisplayName,
                ReplyStatus.UNSUPPORTED_NO_REPLY_ACTION,
                dedupHash,
                "This notification does not expose an Android RemoteInput reply action."
            )
            return
        }

        // 4b. Check per-contact cooldown safeguard
        if (settings.cooldownSeconds > 0) {
            val lastOutgoing = messageLogDao.getLastOutgoingTimestamp(extracted.conversationKey)
            if (lastOutgoing != null && (System.currentTimeMillis() - lastOutgoing) < settings.cooldownSeconds * 1000L) {
                logIncomingOnly(
                    extracted,
                    appDisplayName,
                    ReplyStatus.SKIPPED_COOLDOWN,
                    dedupHash,
                    "Skipped due to ${settings.cooldownSeconds}s per-contact cooldown."
                )
                return
            }
        }

        // 5. Evaluate Priority Custom Commands
        val enabledCommands = commandDao.getEnabledOnce()
        val commandMatch = CommandMatcher.findMatch(extracted.messageText, enabledCommands)
        if (commandMatch != null && commandMatch.isPriority) {
            dispatchReplyAndRecord(
                extracted = extracted,
                appDisplayName = appDisplayName,
                replyText = commandMatch.command.responseText,
                successStatus = ReplyStatus.COMMAND_SENT,
                dedupHash = dedupHash,
                delaySeconds = settings.replyDelaySeconds
            )
            return
        }

        // 6. Evaluate Smart Automatic Actions
        val enabledActions = smartActionDao.getEnabledOnce()
        val actionOutcome = smartActionEvaluator.evaluate(
            incomingText = extracted.messageText,
            actions = enabledActions,
            hasLocationPermission = locationHelper.hasCoarseLocationPermission()
        )
        when (actionOutcome) {
            is SmartActionOutcome.ImmediateReply -> {
                dispatchReplyAndRecord(
                    extracted = extracted,
                    appDisplayName = appDisplayName,
                    replyText = actionOutcome.replyText,
                    successStatus = ReplyStatus.SMART_ACTION_SENT,
                    dedupHash = dedupHash,
                    delaySeconds = settings.replyDelaySeconds
                )
                return
            }
            is SmartActionOutcome.PendingApproval -> {
                messageLogDao.insert(
                    MessageLogEntity(
                        conversationKey = extracted.conversationKey,
                        packageName = extracted.packageName,
                        appDisplayName = appDisplayName,
                        senderName = extracted.senderName,
                        senderIdentifier = extracted.senderIdentifier,
                        messageText = extracted.messageText,
                        direction = MessageDirection.INCOMING,
                        aiGeneratedReply = actionOutcome.suggestedReplyText,
                        replyStatus = ReplyStatus.PENDING_APPROVAL,
                        errorDetail = actionOutcome.reason,
                        timestamp = extracted.postTimeMs,
                        dedupHash = dedupHash
                    )
                )
                return
            }
            null -> Unit
        }

        // 6b. Evaluate Quiet Hours Schedule
        if (QuietHoursEvaluator.isWithinQuietHours(
                enabled = settings.quietHoursEnabled,
                startHour = settings.quietHoursStartHour,
                endHour = settings.quietHoursEndHour
            )
        ) {
            dispatchReplyAndRecord(
                extracted = extracted,
                appDisplayName = appDisplayName,
                replyText = settings.quietHoursReplyText,
                successStatus = ReplyStatus.QUIET_HOURS_SENT,
                dedupHash = dedupHash,
                delaySeconds = settings.replyDelaySeconds
            )
            return
        }

        // 7. Generate AI Response using full Reply Intelligence
        val apiKey = preferencesRepository.getDecryptedApiKey().orEmpty()
        if (apiKey.isBlank()) {
            logIncomingOnly(
                extracted,
                appDisplayName,
                ReplyStatus.FAILED_NO_API_KEY,
                dedupHash,
                "Configure your AI API key in Settings to enable AI replies."
            )
            return
        }

        val promptMessages = buildPromptMessagesForConversation(
            conversationKey = extracted.conversationKey,
            senderName = extracted.senderName,
            appDisplayName = appDisplayName,
            incomingMessage = extracted.messageText,
            contactRule = contactRule,
            nonPriorityCommands = enabledCommands.filter { !it.priorityOverAi }
        )

        val effectiveMaxTokens = if (settings.dataSaverMode) {
            settings.maxResponseTokens.coerceAtMost(110)
        } else {
            settings.maxResponseTokens
        }
        when (val result = aiApiClient.generateCompletion(
            baseUrl = settings.apiBaseUrl,
            apiKey = apiKey,
            model = settings.modelName,
            messages = promptMessages,
            temperature = settings.temperature,
            maxTokens = effectiveMaxTokens
        )) {
            is AiApiResult.Success -> {
                preferencesRepository.updateConnectionStatus(
                    ApiConnectionStatus.CONNECTED,
                    result.latencyMs,
                    ""
                )
                dispatchReplyAndRecord(
                    extracted = extracted,
                    appDisplayName = appDisplayName,
                    replyText = result.data,
                    successStatus = ReplyStatus.SENT,
                    dedupHash = dedupHash,
                    delaySeconds = settings.replyDelaySeconds
                )
            }
            is AiApiResult.Error -> {
                preferencesRepository.updateConnectionStatus(
                    ApiConnectionStatus.ERROR,
                    0L,
                    result.message
                )
                logIncomingOnly(
                    extracted,
                    appDisplayName,
                    ReplyStatus.FAILED_API_ERROR,
                    dedupHash,
                    result.message
                )
            }
        }
    }

    suspend fun runDryRunSimulation(
        senderName: String,
        appDisplayName: String,
        incomingMessage: String,
        isGroupChat: Boolean,
        selectedCategory: CategoryEntity?
    ): DryRunTrace {
        val settings = preferencesRepository.getSettingsOnce()
        val categoryLabel = selectedCategory?.name ?: "Default"

        if (isGroupChat && !settings.replyToGroupChats) {
            return DryRunTrace(
                decisionSource = "Skipped (Group Chat Safeguard)",
                replyText = "",
                categoryUsed = categoryLabel,
                error = "Group chat auto-replies are currently disabled in Settings."
            )
        }

        val enabledCommands = commandDao.getEnabledOnce()
        val commandMatch = CommandMatcher.findMatch(incomingMessage, enabledCommands)
        if (commandMatch != null && commandMatch.isPriority) {
            return DryRunTrace(
                decisionSource = "Priority Command (${commandMatch.command.triggerText})",
                replyText = commandMatch.command.responseText,
                categoryUsed = categoryLabel
            )
        }

        val enabledActions = smartActionDao.getEnabledOnce()
        when (val actionOutcome = smartActionEvaluator.evaluate(
            incomingText = incomingMessage,
            actions = enabledActions,
            hasLocationPermission = locationHelper.hasCoarseLocationPermission()
        )) {
            is SmartActionOutcome.ImmediateReply -> {
                return DryRunTrace(
                    decisionSource = "Smart Action (${actionOutcome.action.title})",
                    replyText = actionOutcome.replyText,
                    categoryUsed = categoryLabel
                )
            }
            is SmartActionOutcome.PendingApproval -> {
                return DryRunTrace(
                    decisionSource = "Pending User Approval (${actionOutcome.action.title})",
                    replyText = actionOutcome.suggestedReplyText,
                    categoryUsed = categoryLabel,
                    error = actionOutcome.reason
                )
            }
            null -> Unit
        }

        if (QuietHoursEvaluator.isWithinQuietHours(
                enabled = settings.quietHoursEnabled,
                startHour = settings.quietHoursStartHour,
                endHour = settings.quietHoursEndHour
            )
        ) {
            return DryRunTrace(
                decisionSource = "Quiet Hours Auto-Reply",
                replyText = settings.quietHoursReplyText,
                categoryUsed = categoryLabel
            )
        }

        val apiKey = preferencesRepository.getDecryptedApiKey().orEmpty()
        if (apiKey.isBlank()) {
            return DryRunTrace(
                decisionSource = "AI Engine",
                replyText = "",
                categoryUsed = categoryLabel,
                error = "No API key configured in Settings."
            )
        }

        val activeCategory = selectedCategory
            ?: categoryDao.getDefaultCategory()
            ?: categoryDao.getEnabledOnce().firstOrNull()
        val activeSession = aiChatDao.getActiveContextSession()
        val sessionMsgs = activeSession?.let { aiChatDao.getRecentMessages(it.id, 6) }.orEmpty()

        val promptMessages = ReplyIntelligencePromptBuilder.buildMessages(
            ReplyPromptInput(
                senderName = senderName.ifBlank { "Contact" },
                appDisplayName = appDisplayName,
                incomingMessage = incomingMessage,
                category = activeCategory,
                contactRule = null,
                previousMessages = emptyList(),
                nonPriorityCommands = enabledCommands.filter { !it.priorityOverAi },
                settings = settings,
                activeAiSession = activeSession,
                activeAiSessionMessages = sessionMsgs
            )
        )

        val effectiveMaxTokens = if (settings.dataSaverMode) {
            settings.maxResponseTokens.coerceAtMost(110)
        } else {
            settings.maxResponseTokens
        }
        return when (val res = aiApiClient.generateCompletion(
            baseUrl = settings.apiBaseUrl,
            apiKey = apiKey,
            model = settings.modelName,
            messages = promptMessages,
            temperature = settings.temperature,
            maxTokens = effectiveMaxTokens
        )) {
            is AiApiResult.Success -> {
                preferencesRepository.updateConnectionStatus(
                    ApiConnectionStatus.CONNECTED,
                    res.latencyMs,
                    ""
                )
                DryRunTrace(
                    decisionSource = "AI Reply (${settings.modelName})",
                    replyText = res.data,
                    categoryUsed = activeCategory?.name ?: categoryLabel,
                    latencyMs = res.latencyMs
                )
            }
            is AiApiResult.Error -> {
                preferencesRepository.updateConnectionStatus(
                    ApiConnectionStatus.ERROR,
                    0L,
                    res.message
                )
                DryRunTrace(
                    decisionSource = "AI Reply Failed",
                    replyText = "",
                    categoryUsed = activeCategory?.name ?: categoryLabel,
                    error = res.message
                )
            }
        }
    }

    suspend fun draftAiReplyForConversation(
        conversationKey: String,
        senderName: String,
        appDisplayName: String,
        latestIncomingMessage: String,
        toneOverride: String? = null
    ): Result<String> {
        val settings = preferencesRepository.getSettingsOnce()
        val apiKey = preferencesRepository.getDecryptedApiKey().orEmpty()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("API key is not configured in Settings."))
        }
        val contactRule = contactRuleDao.getForContact(conversationKey)
        val nonPriorityCommands = commandDao.getEnabledOnce().filter { !it.priorityOverAi }
        val promptMessages = buildPromptMessagesForConversation(
            conversationKey = conversationKey,
            senderName = senderName,
            appDisplayName = appDisplayName,
            incomingMessage = latestIncomingMessage,
            contactRule = contactRule,
            nonPriorityCommands = nonPriorityCommands,
            toneOverride = toneOverride
        )
        val effectiveMaxTokens = if (settings.dataSaverMode) {
            settings.maxResponseTokens.coerceAtMost(110)
        } else {
            settings.maxResponseTokens
        }
        return when (val result = aiApiClient.generateCompletion(
            baseUrl = settings.apiBaseUrl,
            apiKey = apiKey,
            model = settings.modelName,
            messages = promptMessages,
            temperature = settings.temperature,
            maxTokens = effectiveMaxTokens
        )) {
            is AiApiResult.Success -> {
                preferencesRepository.updateConnectionStatus(
                    ApiConnectionStatus.CONNECTED,
                    result.latencyMs,
                    ""
                )
                Result.success(result.data)
            }
            is AiApiResult.Error -> {
                preferencesRepository.updateConnectionStatus(
                    ApiConnectionStatus.ERROR,
                    0L,
                    result.message
                )
                Result.failure(IllegalStateException(result.message))
            }
        }
    }

    suspend fun sendManualOrEditedReply(
        conversationKey: String,
        packageName: String,
        appDisplayName: String,
        senderName: String,
        replyText: String,
        sourceMessageId: Long? = null
    ): Result<Unit> {
        val cleanText = replyText.trim()
        if (cleanText.isBlank()) {
            return Result.failure(IllegalArgumentException("Reply text cannot be empty."))
        }

        val sendResult = replyRegistry.sendReply(context, conversationKey, cleanText)
        if (sendResult.isFailure) {
            return sendResult
        }

        deduplicator.recordSentReplyEcho(packageName, senderName, cleanText)
        if (sourceMessageId != null) {
            messageLogDao.getById(sourceMessageId)?.let { existing ->
                messageLogDao.update(
                    existing.copy(
                        aiGeneratedReply = cleanText,
                        replyStatus = ReplyStatus.MANUAL_SENT,
                        errorDetail = ""
                    )
                )
            }
        }
        messageLogDao.insert(
            MessageLogEntity(
                conversationKey = conversationKey,
                packageName = packageName,
                appDisplayName = appDisplayName,
                senderName = senderName,
                senderIdentifier = packageName,
                messageText = cleanText,
                direction = MessageDirection.OUTGOING,
                aiGeneratedReply = cleanText,
                replyStatus = ReplyStatus.MANUAL_SENT,
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    private suspend fun logIncomingOnly(
        extracted: ExtractedNotificationMessage,
        appDisplayName: String,
        status: ReplyStatus,
        dedupHash: String,
        errorDetail: String = ""
    ) {
        messageLogDao.insert(
            MessageLogEntity(
                conversationKey = extracted.conversationKey,
                packageName = extracted.packageName,
                appDisplayName = appDisplayName,
                senderName = extracted.senderName,
                senderIdentifier = extracted.senderIdentifier,
                messageText = extracted.messageText,
                direction = MessageDirection.INCOMING,
                aiGeneratedReply = "",
                replyStatus = status,
                errorDetail = errorDetail,
                timestamp = extracted.postTimeMs,
                dedupHash = dedupHash
            )
        )
    }

    private suspend fun buildPromptMessagesForConversation(
        conversationKey: String,
        senderName: String,
        appDisplayName: String,
        incomingMessage: String,
        contactRule: ContactRuleEntity?,
        nonPriorityCommands: List<CommandEntity>,
        toneOverride: String? = null
    ) = run {
        val settings = preferencesRepository.getSettingsOnce()
        val category = contactRule?.assignedCategoryId?.let { categoryDao.getById(it) }
            ?: categoryDao.getDefaultCategory()
            ?: categoryDao.getEnabledOnce().firstOrNull()

        val activeCategory = category?.takeIf { it.isEnabled }
        val previousMessages = if (contactRule?.useContextualMemory != false) {
            messageLogDao.getRecentForConversation(conversationKey, 8)
        } else {
            emptyList()
        }
        val activeSession = aiChatDao.getActiveContextSession()
        val activeSessionMsgs = activeSession?.let { aiChatDao.getRecentMessages(it.id, 6) }.orEmpty()

        ReplyIntelligencePromptBuilder.buildMessages(
            ReplyPromptInput(
                senderName = senderName,
                appDisplayName = appDisplayName,
                incomingMessage = incomingMessage,
                category = activeCategory,
                contactRule = contactRule,
                previousMessages = previousMessages,
                nonPriorityCommands = nonPriorityCommands,
                settings = settings,
                activeAiSession = activeSession,
                activeAiSessionMessages = activeSessionMsgs,
                toneOverride = toneOverride
            )
        )
    }

    private suspend fun dispatchReplyAndRecord(
        extracted: ExtractedNotificationMessage,
        appDisplayName: String,
        replyText: String,
        successStatus: ReplyStatus,
        dedupHash: String,
        delaySeconds: Int
    ) {
        if (delaySeconds > 0) {
            delay(delaySeconds * 1000L)
        }

        val sendResult = replyRegistry.sendReply(context, extracted.conversationKey, replyText)
        if (sendResult.isSuccess) {
            deduplicator.recordSentReplyEcho(extracted.packageName, extracted.senderName, replyText)
            messageLogDao.insert(
                MessageLogEntity(
                    conversationKey = extracted.conversationKey,
                    packageName = extracted.packageName,
                    appDisplayName = appDisplayName,
                    senderName = extracted.senderName,
                    senderIdentifier = extracted.senderIdentifier,
                    messageText = extracted.messageText,
                    direction = MessageDirection.INCOMING,
                    aiGeneratedReply = replyText,
                    replyStatus = successStatus,
                    timestamp = extracted.postTimeMs,
                    dedupHash = dedupHash
                )
            )
            messageLogDao.insert(
                MessageLogEntity(
                    conversationKey = extracted.conversationKey,
                    packageName = extracted.packageName,
                    appDisplayName = appDisplayName,
                    senderName = extracted.senderName,
                    senderIdentifier = extracted.senderIdentifier,
                    messageText = replyText,
                    direction = MessageDirection.OUTGOING,
                    aiGeneratedReply = replyText,
                    replyStatus = successStatus,
                    timestamp = System.currentTimeMillis()
                )
            )
        } else {
            logIncomingOnly(
                extracted,
                appDisplayName,
                ReplyStatus.FAILED_API_ERROR,
                dedupHash,
                sendResult.exceptionOrNull()?.localizedMessage
                    ?: "Failed to dispatch notification reply intent."
            )
        }
    }
}
