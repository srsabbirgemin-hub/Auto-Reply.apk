package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReplySupportStatus {
    SUPPORTED,
    UNSUPPORTED_NO_REPLY,
    UNKNOWN_UNTIL_MESSAGE
}

enum class CommandMatchType {
    EXACT,
    KEYWORD
}

enum class SmartActionType {
    LOCATION,
    TIME,
    DATE,
    BUSY_MODE,
    CUSTOM
}

enum class MessageDirection {
    INCOMING,
    OUTGOING
}

enum class ReplyStatus {
    RECEIVED,
    SENT,
    COMMAND_SENT,
    SMART_ACTION_SENT,
    QUIET_HOURS_SENT,
    MANUAL_SENT,
    PENDING_APPROVAL,
    SKIPPED_DISABLED,
    SKIPPED_MUTED_CONTACT,
    SKIPPED_GROUP_CHAT,
    SKIPPED_COOLDOWN,
    UNSUPPORTED_NO_REPLY_ACTION,
    FAILED_NO_API_KEY,
    FAILED_API_ERROR
}

enum class AiChatRole {
    USER,
    ASSISTANT
}

@Entity(tableName = "supported_apps")
data class SupportedAppEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val isEnabled: Boolean,
    val isInstalled: Boolean,
    val replySupportStatus: ReplySupportStatus = ReplySupportStatus.UNKNOWN_UNTIL_MESSAGE,
    val categoryHint: String = "Messaging",
    val lastDetectedAt: Long = 0L
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val instructions: String,
    val replyStyle: String,
    val isEnabled: Boolean = true,
    val greeting: String = "",
    val responseLanguage: String = "Auto-detect",
    val isDefault: Boolean = false,
    val isCustom: Boolean = false,
    val colorHex: Long = 0xFF06B6D4
)

@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val triggerText: String,
    val responseText: String,
    val matchType: CommandMatchType = CommandMatchType.EXACT,
    val isEnabled: Boolean = true,
    val priorityOverAi: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "smart_actions")
data class SmartActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: SmartActionType,
    val title: String,
    val description: String,
    val triggerKeywords: String,
    val responseTemplate: String,
    val isEnabled: Boolean = false,
    val requiresConfirmation: Boolean = true,
    val isConsentGranted: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "message_logs")
data class MessageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationKey: String,
    val packageName: String,
    val appDisplayName: String,
    val senderName: String,
    val senderIdentifier: String,
    val messageText: String,
    val direction: MessageDirection,
    val aiGeneratedReply: String = "",
    val replyStatus: ReplyStatus,
    val errorDetail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val dedupHash: String = ""
)

@Entity(tableName = "contact_rules")
data class ContactRuleEntity(
    @PrimaryKey val conversationKey: String,
    val senderName: String,
    val packageName: String,
    val assignedCategoryId: Long? = null,
    val customInstructions: String = "",
    val useContextualMemory: Boolean = true,
    val isAutoReplyMuted: Boolean = false,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_chat_sessions")
data class AiChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val modelName: String,
    val personality: String,
    val responseStyle: String,
    val useAsAutoReplyContext: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_chat_messages")
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: AiChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
