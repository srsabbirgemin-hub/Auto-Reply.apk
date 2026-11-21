package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.service.SmartReplyNotificationService
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    isNotificationAccessGranted: Boolean,
    isLocationPermissionGranted: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    onRefreshPermissions: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onRefreshPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions Setup") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("permissions_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Smart AI Auto Reply requests only the minimal Android permissions required for notification replies and optional smart actions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    PermissionCard(
                        icon = Icons.Filled.NotificationsActive,
                        title = "Notification Listener Access",
                        statusLabel = if (isNotificationAccessGranted) "Granted" else "Required",
                        isPositive = isNotificationAccessGranted,
                        description = "Allows Android's NotificationListenerService to read incoming messaging notifications from your selected apps and send replies using official inline RemoteInput actions.",
                        primaryActionLabel = if (isNotificationAccessGranted) "Open Listener Settings" else "Grant Notification Access",
                        onPrimaryAction = {
                            SmartReplyNotificationService.requestRebindIfPossible(context)
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        },
                        testTag = "open_notification_listener_settings_button"
                    )
                }

                item {
                    PermissionCard(
                        icon = Icons.Filled.LocationOn,
                        title = "Location Permission (Optional)",
                        statusLabel = if (isLocationPermissionGranted) "Granted" else "Optional",
                        isPositive = isLocationPermissionGranted,
                        description = "Used only if you explicitly enable and confirm the Location Smart Action to share your approximate city/area when someone asks where you are.",
                        primaryActionLabel = if (isLocationPermissionGranted) "Manage in App Settings" else "Allow Approximate Location",
                        onPrimaryAction = {
                            if (isLocationPermissionGranted) {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                    )
                                }
                            } else {
                                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        testTag = "request_location_permission_button"
                    )
                }

                item {
                    PermissionCard(
                        icon = Icons.Filled.Public,
                        title = "Internet Permission",
                        statusLabel = "Active (Standard)",
                        isPositive = true,
                        description = "Used solely to send prompts to your configured AI API provider (e.g., Groq or OpenAI-compatible endpoint) using your own encrypted API key.",
                        primaryActionLabel = "Network Settings",
                        onPrimaryAction = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            }
                        },
                        testTag = "open_network_settings_button"
                    )
                }

                item {
                    PermissionCard(
                        icon = Icons.Filled.BatteryChargingFull,
                        title = "Battery Optimization",
                        statusLabel = if (isBatteryOptimizationIgnored) "Unrestricted" else "System Managed",
                        isPositive = isBatteryOptimizationIgnored,
                        description = "Android may pause background notification listeners when battery saver or strict optimization is active. You can optionally set battery usage to Unrestricted.",
                        primaryActionLabel = "Open Battery Settings",
                        onPrimaryAction = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        },
                        testTag = "open_battery_settings_button"
                    )
                }

                item {
                    OutlinedButton(
                        onClick = onRefreshPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh Permission Status")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    statusLabel: String,
    isPositive: Boolean,
    description: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isPositive) StatusEmerald else StatusCyan
                    )
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                StatusPill(
                    label = statusLabel,
                    color = if (isPositive) StatusEmerald else StatusAmber
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            ) {
                Text(primaryActionLabel)
            }
        }
    }
}
