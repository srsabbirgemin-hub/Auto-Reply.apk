package com.example.domain

import com.example.data.local.SmartActionEntity
import com.example.data.local.SmartActionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SmartActionOutcome {
    data class ImmediateReply(
        val action: SmartActionEntity,
        val replyText: String
    ) : SmartActionOutcome()

    data class PendingApproval(
        val action: SmartActionEntity,
        val suggestedReplyText: String,
        val reason: String
    ) : SmartActionOutcome()
}

class SmartActionEvaluator(
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val locationResolver: suspend () -> String? = { null }
) {
    suspend fun evaluate(
        incomingText: String,
        actions: List<SmartActionEntity>,
        hasLocationPermission: Boolean
    ): SmartActionOutcome? {
        val enabledActions = actions.filter { it.isEnabled }.sortedBy { it.sortOrder }
        if (enabledActions.isEmpty()) return null

        // Check Time, Date, Location, Custom keyword-triggered actions before general Busy Mode
        val specificActions = enabledActions.filter { it.actionType != SmartActionType.BUSY_MODE }
        for (action in specificActions) {
            if (!matchesKeywords(incomingText, action.triggerKeywords)) continue

            when (action.actionType) {
                SmartActionType.TIME -> {
                    val timeText = formatLocalTime(nowProvider())
                    val reply = action.responseTemplate.replace("{time}", timeText)
                    return SmartActionOutcome.ImmediateReply(action, reply)
                }
                SmartActionType.DATE -> {
                    val dateText = formatLocalDate(nowProvider())
                    val reply = action.responseTemplate.replace("{date}", dateText)
                    return SmartActionOutcome.ImmediateReply(action, reply)
                }
                SmartActionType.LOCATION -> {
                    // Strict privacy gate: NEVER share location without explicit consent AND Android permission
                    if (!action.isConsentGranted || !hasLocationPermission) {
                        return SmartActionOutcome.PendingApproval(
                            action = action,
                            suggestedReplyText = "I can share my approximate location shortly.",
                            reason = if (!action.isConsentGranted) {
                                "Location sharing consent has not been confirmed."
                            } else {
                                "Location permission is not granted."
                            }
                        )
                    }
                    val resolvedLocation = locationResolver()
                    val draft = if (!resolvedLocation.isNullOrBlank()) {
                        action.responseTemplate.replace("{location}", resolvedLocation)
                    } else {
                        "Location currently unavailable; please approve or type your reply."
                    }
                    return if (action.requiresConfirmation || resolvedLocation.isNullOrBlank()) {
                        SmartActionOutcome.PendingApproval(
                            action = action,
                            suggestedReplyText = draft,
                            reason = "Awaiting your explicit approval before sharing location."
                        )
                    } else {
                        SmartActionOutcome.ImmediateReply(action, draft)
                    }
                }
                SmartActionType.CUSTOM -> {
                    return if (action.requiresConfirmation) {
                        SmartActionOutcome.PendingApproval(
                            action = action,
                            suggestedReplyText = action.responseTemplate,
                            reason = "Custom action '${action.title}' requires your approval."
                        )
                    } else {
                        SmartActionOutcome.ImmediateReply(action, action.responseTemplate)
                    }
                }
                SmartActionType.BUSY_MODE -> Unit
            }
        }

        val busyAction = enabledActions.firstOrNull { it.actionType == SmartActionType.BUSY_MODE }
        if (busyAction != null) {
            return SmartActionOutcome.ImmediateReply(busyAction, busyAction.responseTemplate)
        }

        return null
    }

    private fun matchesKeywords(incomingText: String, rawKeywords: String): Boolean {
        val text = incomingText.trim().lowercase(Locale.getDefault())
        if (text.isBlank()) return false
        val tokens = rawKeywords.split(",")
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        if (tokens.contains("*")) return true
        return tokens.any { keyword -> text.contains(keyword) }
    }

    private fun formatLocalTime(timestampMs: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    private fun formatLocalDate(timestampMs: Long): String {
        val sdf = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}
