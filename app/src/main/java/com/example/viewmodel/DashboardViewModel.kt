package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.AppSettings
import com.example.data.local.MessageDirection
import com.example.data.local.MessageLogEntity
import com.example.data.local.SmartActionType
import com.example.data.local.SupportedAppEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionSnapshot(
    val notificationAccessGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false
)

data class ConversationPreview(
    val conversationKey: String,
    val senderName: String,
    val packageName: String,
    val appDisplayName: String,
    val lastMessage: MessageLogEntity,
    val messageCount: Int
)

data class ReplyBreakdown(
    val aiCount: Int = 0,
    val commandCount: Int = 0,
    val smartActionCount: Int = 0,
    val quietHoursCount: Int = 0,
    val manualCount: Int = 0
)

data class DashboardUiState(
    val settings: AppSettings = AppSettings(),
    val enabledAppsCount: Int = 0,
    val totalAppsCount: Int = 0,
    val enabledApps: List<SupportedAppEntity> = emptyList(),
    val totalReceived: Int = 0,
    val totalReplies: Int = 0,
    val replyBreakdown: ReplyBreakdown = ReplyBreakdown(),
    val isBusyModeActive: Boolean = false,
    val recentConversations: List<ConversationPreview> = emptyList(),
    val pendingApprovalCount: Int = 0,
    val permissions: PermissionSnapshot = PermissionSnapshot()
)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val permissionsFlow = MutableStateFlow(readPermissions())

    private val baseStatsFlow = combine(
        container.preferencesRepository.settingsFlow,
        container.database.supportedAppDao().observeAll(),
        container.database.messageLogDao().observeTotalReceivedCount(),
        container.database.messageLogDao().observeTotalRepliesCount()
    ) { settings, apps, received, replies ->
        Tuple4(settings, apps, received, replies)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        baseStatsFlow,
        container.database.smartActionDao().observeAll(),
        container.database.messageLogDao().observeAll(),
        permissionsFlow
    ) { (settings, apps, received, replies), actions, logs, perms ->
        val enabledApps = apps.filter { it.isEnabled }
        val busyActive = actions.any { it.actionType == SmartActionType.BUSY_MODE && it.isEnabled }
        val pendingCount = logs.count { it.replyStatus.name == "PENDING_APPROVAL" }
        val outgoingLogs = logs.filter { it.direction == MessageDirection.OUTGOING }
        val breakdown = ReplyBreakdown(
            aiCount = outgoingLogs.count { it.replyStatus.name == "SENT" },
            commandCount = outgoingLogs.count { it.replyStatus.name == "COMMAND_SENT" },
            smartActionCount = outgoingLogs.count { it.replyStatus.name == "SMART_ACTION_SENT" },
            quietHoursCount = outgoingLogs.count { it.replyStatus.name == "QUIET_HOURS_SENT" },
            manualCount = outgoingLogs.count { it.replyStatus.name == "MANUAL_SENT" }
        )
        val recent = logs
            .groupBy { it.conversationKey }
            .mapNotNull { (key, entries) ->
                val sorted = entries.sortedByDescending { it.timestamp }
                val latest = sorted.firstOrNull() ?: return@mapNotNull null
                val latestIncoming = sorted.firstOrNull { it.direction == MessageDirection.INCOMING } ?: latest
                ConversationPreview(
                    conversationKey = key,
                    senderName = latestIncoming.senderName,
                    packageName = latest.packageName,
                    appDisplayName = latest.appDisplayName,
                    lastMessage = latest,
                    messageCount = entries.size
                )
            }
            .sortedByDescending { it.lastMessage.timestamp }
            .take(5)

        DashboardUiState(
            settings = settings,
            enabledAppsCount = enabledApps.size,
            totalAppsCount = apps.size,
            enabledApps = enabledApps,
            totalReceived = received,
            totalReplies = replies,
            replyBreakdown = breakdown,
            isBusyModeActive = busyActive,
            recentConversations = recent,
            pendingApprovalCount = pendingCount,
            permissions = perms
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun refreshPermissions() {
        permissionsFlow.value = readPermissions()
    }

    fun setAutoReplyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setAutoReplyEnabled(enabled)
        }
    }

    fun setBusyModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val busy = container.database.smartActionDao().getByType(SmartActionType.BUSY_MODE)
            if (busy != null) {
                container.database.smartActionDao().update(busy.copy(isEnabled = enabled))
            }
        }
    }

    private fun readPermissions(): PermissionSnapshot {
        return PermissionSnapshot(
            notificationAccessGranted = container.appPackageInspector.isNotificationListenerEnabled(),
            locationPermissionGranted = container.locationHelper.hasCoarseLocationPermission(),
            batteryOptimizationIgnored = container.appPackageInspector.isIgnoringBatteryOptimizations()
        )
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DashboardViewModel(container) as T
                }
            }
    }
}
