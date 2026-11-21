package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ApiConnectionStatus
import com.example.ui.components.AppBadgeAvatar
import com.example.ui.components.ReplyStatusBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.formatShortTimestamp
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusIndigo
import com.example.ui.theme.StatusRose
import com.example.viewmodel.DashboardUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: DashboardUiState,
    loadAppIcon: (String) -> Drawable?,
    onToggleAutoReply: (Boolean) -> Unit,
    onToggleBusyMode: (Boolean) -> Unit,
    onOpenSupportedApps: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenCommands: () -> Unit,
    onOpenSmartActions: () -> Unit,
    onOpenSandbox: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Smart AI Auto Reply",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Local-first notification intelligence • Zero login required",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Hero Master Control Card
            item {
                val isActive = state.settings.isAutoReplyEnabled
                val gradientColors = if (isActive) {
                    listOf(
                        Color(0xFF0A3A4A),
                        Color(0xFF0E2942)
                    )
                } else {
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                }
                val contentColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .background(Brush.linearGradient(gradientColors))
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isActive) StatusCyan.copy(alpha = 0.22f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = "Auto Reply engine status",
                                            tint = if (isActive) StatusCyan else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (isActive) "Auto Reply Active" else "Auto Reply Paused",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = contentColor
                                        )
                                        Text(
                                            text = if (isActive) {
                                                "Monitoring ${state.enabledAppsCount} enabled apps"
                                            } else {
                                                "Turn ON to reply automatically"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = contentColor.copy(alpha = 0.78f)
                                        )
                                    }
                                }

                                Switch(
                                    checked = isActive,
                                    onCheckedChange = onToggleAutoReply,
                                    modifier = Modifier.testTag("master_auto_reply_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val (apiLabel, apiColor) = when (state.settings.apiConnectionStatus) {
                                    ApiConnectionStatus.CONNECTED ->
                                        "API Connected (${state.settings.apiLastLatencyMs}ms)" to StatusEmerald
                                    ApiConnectionStatus.ERROR -> "API Error" to StatusRose
                                    ApiConnectionStatus.UNCONFIGURED -> "API Key Needed" to StatusAmber
                                }
                                StatusPill(
                                    label = apiLabel,
                                    color = apiColor,
                                    modifier = Modifier.clickable(onClick = onOpenAiSettings)
                                )
                                StatusPill(
                                    label = if (state.permissions.notificationAccessGranted) {
                                        "Listener Ready"
                                    } else {
                                        "Listener Off"
                                    },
                                    color = if (state.permissions.notificationAccessGranted) StatusEmerald else StatusAmber,
                                    modifier = Modifier.clickable(onClick = onOpenPermissions)
                                )
                            }
                        }
                    }
                }
            }

            // Notification Permission Alert Banner
            item {
                AnimatedVisibility(visible = !state.permissions.notificationAccessGranted) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = StatusAmber.copy(alpha = 0.14f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = "Permission required",
                                tint = StatusAmber
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notification Access Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Grant Notification Listener permission so incoming messages can be detected.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = onOpenPermissions,
                                colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                                modifier = Modifier.testTag("home_grant_permission_button")
                            ) {
                                Text("Setup", color = Color.Black)
                            }
                        }
                    }
                }
            }

            // Pending Approval Callout
            if (state.pendingApprovalCount > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = StatusCyan.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenMessages)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${state.pendingApprovalCount} message(s) awaiting approval",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Tap to review location/action drafts before sending.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Review pending messages"
                            )
                        }
                    }
                }
            }

            // 2x2 Metrics Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Enabled Apps",
                            value = "${state.enabledAppsCount}/${state.totalAppsCount}",
                            subtitle = "Tap to select apps",
                            icon = Icons.Filled.Apps,
                            accent = StatusCyan,
                            onClick = onOpenSupportedApps,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("metric_enabled_apps")
                        )
                        MetricCard(
                            title = "Total Received",
                            value = state.totalReceived.toString(),
                            subtitle = "Incoming messages",
                            icon = Icons.Filled.NotificationsActive,
                            accent = StatusIndigo,
                            onClick = onOpenMessages,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("metric_total_received")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Total Replies",
                            value = state.totalReplies.toString(),
                            subtitle = "Dispatched replies",
                            icon = Icons.Filled.MarkChatRead,
                            accent = StatusEmerald,
                            onClick = onOpenMessages,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("metric_total_replies")
                        )
                        MetricCard(
                            title = "Busy Mode",
                            value = if (state.isBusyModeActive) "ON" else "OFF",
                            subtitle = if (state.isBusyModeActive) "Auto-informing contacts" else "Tap to activate",
                            icon = Icons.Filled.DoNotDisturbOn,
                            accent = if (state.isBusyModeActive) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onToggleBusyMode(!state.isBusyModeActive) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("metric_busy_mode")
                        )
                    }
                }
            }

            // Quick Actions Rows
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Quick Controls",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickChipCard(
                            label = "Apps",
                            icon = Icons.Filled.Apps,
                            onClick = onOpenSupportedApps,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_supported_apps")
                        )
                        QuickChipCard(
                            label = "Commands",
                            icon = Icons.Filled.Terminal,
                            onClick = onOpenCommands,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_commands")
                        )
                        QuickChipCard(
                            label = "Actions",
                            icon = Icons.Filled.Bolt,
                            onClick = onOpenSmartActions,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_smart_actions")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickChipCard(
                            label = "Reply Sandbox",
                            icon = Icons.Filled.Science,
                            onClick = onOpenSandbox,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_sandbox")
                        )
                        QuickChipCard(
                            label = "Permissions",
                            icon = Icons.Filled.Security,
                            onClick = onOpenPermissions,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_permissions")
                        )
                    }
                }
            }

            if (state.totalReplies > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Reply Breakdown", style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("AI: ${state.replyBreakdown.aiCount}", style = MaterialTheme.typography.labelMedium, color = StatusEmerald)
                                Text("Cmd: ${state.replyBreakdown.commandCount}", style = MaterialTheme.typography.labelMedium, color = StatusIndigo)
                                Text("Action: ${state.replyBreakdown.smartActionCount}", style = MaterialTheme.typography.labelMedium, color = StatusCyan)
                                Text("Quiet: ${state.replyBreakdown.quietHoursCount}", style = MaterialTheme.typography.labelMedium, color = StatusAmber)
                                Text("Manual: ${state.replyBreakdown.manualCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Recent Conversations Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Conversations",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onOpenMessages) {
                        Text("View All")
                    }
                }
            }

            if (state.recentConversations.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MarkChatRead,
                                contentDescription = "No conversations yet",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No detected messages yet",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Enable Notification Access, select your messaging apps, and add an AI API key in Settings. Real incoming messages will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.recentConversations, key = { it.conversationKey }) { convo ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenConversation(convo.conversationKey) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppBadgeAvatar(
                                appName = convo.appDisplayName,
                                iconDrawable = loadAppIcon(convo.packageName)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = convo.senderName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatShortTimestamp(convo.lastMessage.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = convo.lastMessage.messageText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = convo.appDisplayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    ReplyStatusBadge(status = convo.lastMessage.replyStatus)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickChipCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
