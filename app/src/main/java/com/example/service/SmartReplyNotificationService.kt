package com.example.service

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.SmartAutoReplyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SmartReplyNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val extracted = NotificationReplyExtractor.extract(notification, packageName) ?: return
        val app = application as? SmartAutoReplyApplication ?: return

        serviceScope.launch {
            runCatching {
                app.container.autoReplyEngine.processIncomingNotification(extracted)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun requestRebindIfPossible(context: Context) {
            runCatching {
                requestRebind(ComponentName(context, SmartReplyNotificationService::class.java))
            }
        }
    }
}
