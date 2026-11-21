package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

data class ExtractedNotificationMessage(
    val notificationKey: String,
    val packageName: String,
    val senderName: String,
    val senderIdentifier: String,
    val conversationKey: String,
    val messageText: String,
    val postTimeMs: Long,
    val isGroupConversation: Boolean,
    val replyPendingIntent: PendingIntent?,
    val remoteInputs: Array<RemoteInput>?,
    val primaryRemoteInputKey: String?
) {
    val supportsInlineReply: Boolean
        get() = replyPendingIntent != null && !remoteInputs.isNullOrEmpty() && !primaryRemoteInputKey.isNullOrBlank()
}

object NotificationReplyExtractor {

    fun extract(sbn: StatusBarNotification, ownPackageName: String): ExtractedNotificationMessage? {
        val packageName = sbn.packageName ?: return null
        if (packageName == ownPackageName) return null

        val notification = sbn.notification ?: return null
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return null
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null

        val extras = notification.extras ?: return null
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)

        val latestMessagingMsg = messagingStyle?.messages?.lastOrNull()
        val rawSender = latestMessagingMsg?.person?.name?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
            ?: return null

        // Ignore self-echo messages in MessagingStyle when sender equals user display name
        val selfUserName = messagingStyle?.user?.name?.toString()?.trim()
        if (!selfUserName.isNullOrBlank() && rawSender.equals(selfUserName, ignoreCase = true)) {
            return null
        }

        val rawText = latestMessagingMsg?.text?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: return null

        // Ignore generic summary strings like "3 new messages"
        if (isSummaryPlaceholder(rawText)) return null

        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val isExtraGroup = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        } else {
            false
        }
        val isGroup = (messagingStyle?.isGroupConversation == true) ||
            isExtraGroup ||
            (!conversationTitle.isNullOrBlank() && !conversationTitle.equals(rawSender, ignoreCase = true))

        val identifier = conversationTitle?.takeIf { it.isNotBlank() && it != rawSender }
            ?: sbn.tag?.takeIf { it.isNotBlank() }
            ?: packageName

        val conversationKey = "${packageName}|${(conversationTitle?.takeIf { isGroup } ?: rawSender).lowercase()}"

        val replyPair = findReplyAction(notification)

        return ExtractedNotificationMessage(
            notificationKey = sbn.key ?: "${packageName}_${sbn.id}",
            packageName = packageName,
            senderName = rawSender,
            senderIdentifier = identifier,
            conversationKey = conversationKey,
            messageText = rawText,
            postTimeMs = sbn.postTime,
            isGroupConversation = isGroup,
            replyPendingIntent = replyPair?.first,
            remoteInputs = replyPair?.second,
            primaryRemoteInputKey = replyPair?.second?.firstOrNull()?.resultKey
        )
    }

    private fun findReplyAction(notification: Notification): Pair<PendingIntent, Array<RemoteInput>>? {
        val candidateActions = mutableListOf<Notification.Action>()
        notification.actions?.let { candidateActions.addAll(it) }

        val wearableActions = Notification.WearableExtender(notification).actions
        if (wearableActions.isNotEmpty()) {
            candidateActions.addAll(wearableActions)
        }

        // Prefer semantic reply action first
        val sortedActions = candidateActions.sortedByDescending { action ->
            var score = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY
            ) {
                score += 10
            }
            val title = action.title?.toString()?.lowercase().orEmpty()
            if (title.contains("reply") || title.contains("answer") || title.contains("message")) {
                score += 5
            }
            score
        }

        for (action in sortedActions) {
            val inputs = action.remoteInputs
            val intent = action.actionIntent
            if (intent != null && !inputs.isNullOrEmpty() && inputs.any { it.allowFreeFormInput }) {
                return intent to inputs
            }
        }
        return null
    }

    private fun isSummaryPlaceholder(text: String): Boolean {
        val lower = text.lowercase()
        return lower.matches(Regex("^\\d+\\s+new\\s+messages?$")) ||
            lower == "checking for new messages" ||
            lower == "checking for messages"
    }
}
