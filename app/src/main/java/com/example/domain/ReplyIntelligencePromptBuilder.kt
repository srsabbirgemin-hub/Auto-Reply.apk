package com.example.domain

import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiChatRole
import com.example.data.local.AiChatSessionEntity
import com.example.data.local.AppSettings
import com.example.data.local.CategoryEntity
import com.example.data.local.CommandEntity
import com.example.data.local.ContactRuleEntity
import com.example.data.local.MessageDirection
import com.example.data.local.MessageLogEntity
import com.example.data.network.ChatMessageDto

data class ReplyPromptInput(
    val senderName: String,
    val appDisplayName: String,
    val incomingMessage: String,
    val category: CategoryEntity?,
    val contactRule: ContactRuleEntity?,
    val previousMessages: List<MessageLogEntity>,
    val nonPriorityCommands: List<CommandEntity>,
    val settings: AppSettings,
    val activeAiSession: AiChatSessionEntity?,
    val activeAiSessionMessages: List<AiChatMessageEntity>,
    val toneOverride: String? = null
)

object ReplyIntelligencePromptBuilder {

    fun buildMessages(input: ReplyPromptInput): List<ChatMessageDto> {
        val systemBuilder = StringBuilder()
        systemBuilder.appendLine("You are an intelligent messaging auto-reply assistant helping ${input.senderName}'s contact on ${input.appDisplayName}.")
        systemBuilder.appendLine("Core behavior rules:")
        systemBuilder.appendLine("- Write natural, human-like, context-aware replies. Avoid robotic phrasing, cliché filler, or unnecessarily long paragraphs.")
        systemBuilder.appendLine("- Match the language of the incoming message unless a specific response language is configured below.")
        systemBuilder.appendLine("- Never fabricate personal facts, schedules, prices, or commitments you do not know.")
        systemBuilder.appendLine("- If asked directly whether you are an AI or automated assistant, answer honestly rather than pretending otherwise.")
        if (!input.toneOverride.isNullOrBlank()) {
            systemBuilder.appendLine("- Requested tone override for this draft: ${input.toneOverride}.")
        }
        systemBuilder.appendLine()

        // Personality & style
        systemBuilder.appendLine("User-defined personality: ${input.settings.globalPersonality}")
        systemBuilder.appendLine("Preferred response style: ${input.settings.globalResponseStyle}")

        // Category rules
        input.category?.let { cat ->
            systemBuilder.appendLine()
            systemBuilder.appendLine("Active Category: ${cat.name}")
            if (cat.instructions.isNotBlank()) {
                systemBuilder.appendLine("Category AI instructions: ${cat.instructions}")
            }
            if (cat.replyStyle.isNotBlank()) {
                systemBuilder.appendLine("Category reply style: ${cat.replyStyle}")
            }
            if (cat.greeting.isNotBlank()) {
                systemBuilder.appendLine("Optional greeting style when appropriate: ${cat.greeting}")
            }
            if (cat.responseLanguage.isNotBlank() && !cat.responseLanguage.equals("Auto-detect", ignoreCase = true)) {
                systemBuilder.appendLine("Target response language: ${cat.responseLanguage}")
            } else {
                systemBuilder.appendLine("Target response language: Match incoming message language.")
            }
        }

        // Contact-specific instructions
        input.contactRule?.let { rule ->
            if (rule.customInstructions.isNotBlank()) {
                systemBuilder.appendLine()
                systemBuilder.appendLine("Contact-specific instructions for ${rule.senderName}: ${rule.customInstructions}")
            }
        }

        // Non-priority custom commands as reference hints
        if (input.nonPriorityCommands.isNotEmpty()) {
            systemBuilder.appendLine()
            systemBuilder.appendLine("Reference command templates (use when relevant):")
            input.nonPriorityCommands.take(6).forEach { cmd ->
                systemBuilder.appendLine("- Trigger '${cmd.triggerText}': ${cmd.responseText}")
            }
        }

        // Pinned AI Chat Assistant context
        val maxSessionMsgs = if (input.settings.dataSaverMode) 2 else 6
        if (input.activeAiSession != null && input.activeAiSessionMessages.isNotEmpty()) {
            systemBuilder.appendLine()
            systemBuilder.appendLine("Context from user's pinned AI Chat session ('${input.activeAiSession.title}'):")
            if (input.activeAiSession.personality.isNotBlank()) {
                systemBuilder.appendLine("Session personality: ${input.activeAiSession.personality}")
            }
            input.activeAiSessionMessages
                .sortedBy { it.timestamp }
                .takeLast(maxSessionMsgs)
                .forEach { msg ->
                    val label = if (msg.role == AiChatRole.USER) "User" else "Assistant"
                    val limit = if (input.settings.dataSaverMode) 140 else 280
                    systemBuilder.appendLine("$label: ${msg.content.take(limit)}")
                }
        }

        val chatMessages = mutableListOf<ChatMessageDto>()
        chatMessages.add(ChatMessageDto(role = "system", content = systemBuilder.toString().trim()))

        // Include previous conversation history only if contextual memory is enabled for this contact
        val memoryEnabled = input.contactRule?.useContextualMemory ?: true
        val maxHistoryMsgs = if (input.settings.dataSaverMode) 2 else 8
        if (memoryEnabled && input.previousMessages.isNotEmpty()) {
            input.previousMessages
                .sortedBy { it.timestamp }
                .takeLast(maxHistoryMsgs)
                .forEach { log ->
                    if (log.direction == MessageDirection.INCOMING) {
                        chatMessages.add(ChatMessageDto(role = "user", content = log.messageText))
                        if (log.aiGeneratedReply.isNotBlank() &&
                            (log.replyStatus.name.endsWith("SENT"))
                        ) {
                            chatMessages.add(ChatMessageDto(role = "assistant", content = log.aiGeneratedReply))
                        }
                    } else {
                        chatMessages.add(ChatMessageDto(role = "assistant", content = log.messageText))
                    }
                }
        }

        chatMessages.add(
            ChatMessageDto(
                role = "user",
                content = "Incoming message from ${input.senderName} via ${input.appDisplayName}:\n${input.incomingMessage}"
            )
        )
        return chatMessages
    }
}
