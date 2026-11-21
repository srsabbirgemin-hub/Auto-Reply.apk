package com.example.service

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class NotificationReplyHandle(
    val conversationKey: String,
    val packageName: String,
    val senderName: String,
    val pendingIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>,
    val resultKey: String,
    val updatedAt: Long = System.currentTimeMillis()
)

class ActiveReplyActionRegistry {
    private val handles = ConcurrentHashMap<String, NotificationReplyHandle>()
    private val _replyableKeys = MutableStateFlow<Set<String>>(emptySet())
    val replyableKeys: StateFlow<Set<String>> = _replyableKeys.asStateFlow()

    fun register(handle: NotificationReplyHandle) {
        handles[handle.conversationKey] = handle
        _replyableKeys.value = handles.keys.toSet()
    }

    fun hasActiveHandle(conversationKey: String): Boolean = handles.containsKey(conversationKey)

    fun sendReply(context: Context, conversationKey: String, replyText: String): Result<Unit> {
        val handle = handles[conversationKey]
            ?: return Result.failure(IllegalStateException("No active notification reply session for this contact."))

        return runCatching {
            val fillInIntent = Intent()
            val bundle = Bundle()
            for (remoteInput in handle.remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(handle.remoteInputs, fillInIntent, bundle)
            handle.pendingIntent.send(context, 0, fillInIntent)
        }.recoverCatching { err ->
            if (err is PendingIntent.CanceledException) {
                handles.remove(conversationKey)
                _replyableKeys.value = handles.keys.toSet()
                throw IllegalStateException("Notification session expired. Wait for a new incoming message to reply.")
            }
            throw err
        }
    }

    fun clear() {
        handles.clear()
        _replyableKeys.value = emptySet()
    }
}
