package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.ReplySupportStatus
import com.example.data.local.SupportedAppEntity
import com.example.ui.components.AppBadgeAvatar
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportedAppsScreen(
    apps: List<SupportedAppEntity>,
    isNotificationAccessGranted: Boolean,
    loadAppIcon: (String) -> Drawable?,
    onToggleApp: (String, Boolean) -> Unit,
    onRefreshApps: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supported Messaging Apps") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("supported_apps_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshApps) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh installed apps")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        color = if (isNotificationAccessGranted) {
                            StatusEmerald.copy(alpha = 0.12f)
                        } else {
                            StatusAmber.copy(alpha = 0.14f)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isNotificationAccessGranted) {
                                "Notification Listener is active. Only apps toggled ON below will receive automatic AI replies via Android's official notification reply action."
                            } else {
                                "Notification access is currently OFF. Enable it in Permissions Setup to detect messages from the selected apps below."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                items(apps, key = { it.packageName }) { app ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppBadgeAvatar(
                                    appName = app.displayName,
                                    iconDrawable = loadAppIcon(app.packageName),
                                    size = 46.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${app.categoryHint} • ${if (app.isInstalled) "Installed" else "Not Detected"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = app.isEnabled,
                                    onCheckedChange = { onToggleApp(app.packageName, it) },
                                    modifier = Modifier.testTag("app_toggle_${app.packageName}")
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(
                                    label = if (isNotificationAccessGranted) "Permission Ready" else "Permission Needed",
                                    color = if (isNotificationAccessGranted) StatusEmerald else StatusAmber
                                )
                                val (supportLabel, supportColor) = when (app.replySupportStatus) {
                                    ReplySupportStatus.SUPPORTED -> "Inline Reply Supported" to StatusCyan
                                    ReplySupportStatus.UNSUPPORTED_NO_REPLY -> "No Reply Action in Notification" to StatusAmber
                                    ReplySupportStatus.UNKNOWN_UNTIL_MESSAGE -> "Verifies on First Message" to MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                StatusPill(label = supportLabel, color = supportColor)
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
