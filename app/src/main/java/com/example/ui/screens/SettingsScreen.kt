package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AiProviderType
import com.example.data.local.ApiConnectionStatus
import com.example.data.local.ThemeMode
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRose
import com.example.viewmodel.SettingsUiState
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSaveAiConfig: (
        AiProviderType,
        String,
        String,
        Float,
        Int,
        String?,
        String,
        String,
        Int
    ) -> Unit,
    onTestConnection: (
        AiProviderType,
        String,
        String,
        String?,
        Float,
        Int,
        String,
        String,
        Int
    ) -> Unit,
    onFetchModels: (String, String?) -> Unit,
    onClearApiKey: () -> Unit,
    onSaveSafeguards: (Int, Boolean, Boolean, Int, Int, String, Boolean) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onOpenSupportedApps: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSandbox: () -> Unit,
    onDeleteAllHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var provider by remember(state.settings.apiProvider) { mutableStateOf(state.settings.apiProvider) }
    var baseUrl by remember(state.settings.apiBaseUrl) { mutableStateOf(state.settings.apiBaseUrl) }
    var modelName by remember(state.settings.modelName) { mutableStateOf(state.settings.modelName) }
    var newApiKeyInput by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var temperature by remember(state.settings.temperature) { mutableFloatStateOf(state.settings.temperature) }
    var maxTokens by remember(state.settings.maxResponseTokens) { mutableIntStateOf(state.settings.maxResponseTokens) }
    var personality by remember(state.settings.globalPersonality) { mutableStateOf(state.settings.globalPersonality) }
    var responseStyle by remember(state.settings.globalResponseStyle) { mutableStateOf(state.settings.globalResponseStyle) }
    var delaySeconds by remember(state.settings.replyDelaySeconds) { mutableIntStateOf(state.settings.replyDelaySeconds) }
    var cooldownSeconds by remember(state.settings.cooldownSeconds) { mutableIntStateOf(state.settings.cooldownSeconds) }
    var replyToGroupChats by remember(state.settings.replyToGroupChats) { mutableStateOf(state.settings.replyToGroupChats) }
    var quietHoursEnabled by remember(state.settings.quietHoursEnabled) { mutableStateOf(state.settings.quietHoursEnabled) }
    var quietStartHour by remember(state.settings.quietHoursStartHour) { mutableIntStateOf(state.settings.quietHoursStartHour) }
    var quietEndHour by remember(state.settings.quietHoursEndHour) { mutableIntStateOf(state.settings.quietHoursEndHour) }
    var quietReplyText by remember(state.settings.quietHoursReplyText) { mutableStateOf(state.settings.quietHoursReplyText) }
    var dataSaverMode by remember(state.settings.dataSaverMode) { mutableStateOf(state.settings.dataSaverMode) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    val availableModels = remember(provider, state.discoveredModels) {
        (state.discoveredModels + provider.freeModels).distinct()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("settings_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }

            // Quick Access Links: Supported Apps & Permissions
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenSupportedApps)
                                .padding(16.dp)
                                .testTag("settings_supported_apps_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Filled.Apps, contentDescription = null, tint = StatusCyan)
                                Column {
                                    Text("Supported Messaging Apps", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${state.supportedApps.count { it.isEnabled }} enabled • WhatsApp, Messenger, Telegram, SMS & more",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenPermissions)
                                .padding(16.dp)
                                .testTag("settings_permissions_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = if (state.isNotificationAccessGranted) StatusEmerald else StatusAmber
                                )
                                Column {
                                    Text("Permissions & Background Access", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (state.isNotificationAccessGranted) "Notification Listener active" else "Action needed: Notification access",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenSandbox)
                                .padding(16.dp)
                                .testTag("settings_sandbox_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Filled.Science, contentDescription = null, tint = StatusCyan)
                                Column {
                                    Text("Reply Pipeline Sandbox", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Dry-run test commands, smart actions, quiet hours & AI",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open")
                        }
                    }
                }
            }

            // AI API Configuration
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("AI API Configuration", style = MaterialTheme.typography.titleLarge)
                            val (label, color) = when (state.settings.apiConnectionStatus) {
                                ApiConnectionStatus.CONNECTED -> "Connected" to StatusEmerald
                                ApiConnectionStatus.ERROR -> "Error" to StatusRose
                                ApiConnectionStatus.UNCONFIGURED -> "Not Set" to StatusAmber
                            }
                            StatusPill(label = label, color = color)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Keystore protected",
                                tint = StatusEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "API key encrypted locally via Android Keystore (AES-256-GCM)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text("AI Provider", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AiProviderType.entries.forEach { item ->
                                FilterChip(
                                    selected = provider == item,
                                    onClick = {
                                        provider = item
                                        baseUrl = item.defaultBaseUrl
                                        modelName = item.defaultModel
                                    },
                                    label = { Text(item.displayName) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = newApiKeyInput,
                            onValueChange = { newApiKeyInput = it },
                            label = {
                                Text(
                                    if (state.settings.hasApiKey) {
                                        "API Key (Saved: ${state.settings.maskedApiKey})"
                                    } else {
                                        "API Key"
                                    }
                                )
                            },
                            placeholder = { Text(provider.keyHint) },
                            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle key visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input")
                        )

                        if (state.settings.hasApiKey) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onClearApiKey) {
                                    Text("Remove Stored Key", color = StatusRose)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("API Base URL") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_base_url_input")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = modelName,
                                onValueChange = { modelName = it },
                                label = { Text("Model Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("api_model_input")
                            )
                            OutlinedButton(
                                onClick = { onFetchModels(baseUrl, newApiKeyInput.ifBlank { null }) },
                                enabled = !state.isFetchingModels,
                                modifier = Modifier.testTag("fetch_models_button")
                            ) {
                                if (state.isFetchingModels) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Fetch models")
                                }
                            }
                        }

                        if (availableModels.isNotEmpty()) {
                            Text("Available Models", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableModels.take(10).forEach { model ->
                                    FilterChip(
                                        selected = modelName == model,
                                        onClick = { modelName = model },
                                        label = {
                                            Text(
                                                model,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Temperature & Max Tokens
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Temperature", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    String.format(Locale.US, "%.2f", temperature),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Slider(
                                value = temperature,
                                onValueChange = { temperature = it },
                                valueRange = 0f..1.5f
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Max Response Length", style = MaterialTheme.typography.labelLarge)
                                Text("$maxTokens tokens", style = MaterialTheme.typography.labelSmall)
                            }
                            Slider(
                                value = maxTokens.toFloat(),
                                onValueChange = { maxTokens = it.roundToInt() },
                                valueRange = 50f..1000f
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Natural Reply Delay", style = MaterialTheme.typography.labelLarge)
                                Text("${delaySeconds}s", style = MaterialTheme.typography.labelSmall)
                            }
                            Slider(
                                value = delaySeconds.toFloat(),
                                onValueChange = { delaySeconds = it.roundToInt() },
                                valueRange = 0f..10f,
                                steps = 9
                            )
                        }

                        OutlinedTextField(
                            value = personality,
                            onValueChange = { personality = it },
                            label = { Text("Global AI Personality") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = responseStyle,
                            onValueChange = { responseStyle = it },
                            label = { Text("Global Reply Style") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (state.settings.apiLastError.isNotBlank()) {
                            Text(
                                text = "Last Error: ${state.settings.apiLastError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRose
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onSaveAiConfig(
                                        provider,
                                        baseUrl,
                                        modelName,
                                        temperature,
                                        maxTokens,
                                        newApiKeyInput.ifBlank { null },
                                        personality,
                                        responseStyle,
                                        delaySeconds
                                    )
                                    newApiKeyInput = ""
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_ai_settings_button")
                            ) {
                                Text("Save")
                            }
                            Button(
                                onClick = {
                                    onTestConnection(
                                        provider,
                                        baseUrl,
                                        modelName,
                                        newApiKeyInput.ifBlank { null },
                                        temperature,
                                        maxTokens,
                                        personality,
                                        responseStyle,
                                        delaySeconds
                                    )
                                },
                                enabled = !state.isTestingConnection,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_api_connection_button")
                            ) {
                                if (state.isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Test API Connection")
                                }
                            }
                        }
                    }
                }
            }

            // Smart Safeguards & Quiet Hours
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Smart Safeguards & Quiet Hours", style = MaterialTheme.typography.titleMedium)

                        Text("Per-Contact Reply Cooldown", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0 to "Off", 30 to "30s", 60 to "1 min", 300 to "5 min", 900 to "15 min").forEach { (sec, label) ->
                                FilterChip(
                                    selected = cooldownSeconds == sec,
                                    onClick = { cooldownSeconds = sec },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Reply to Group Chats", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Disabled by default to prevent replying in noisy group threads",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = replyToGroupChats,
                                onCheckedChange = { replyToGroupChats = it },
                                modifier = Modifier.testTag("settings_group_chats_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Quiet Hours Schedule", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Send an away note between ${quietStartHour}:00 and ${quietEndHour}:00",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = quietHoursEnabled,
                                onCheckedChange = { quietHoursEnabled = it },
                                modifier = Modifier.testTag("settings_quiet_hours_switch")
                            )
                        }

                        if (quietHoursEnabled) {
                            Column {
                                Text("Start Hour: ${quietStartHour}:00", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = quietStartHour.toFloat(),
                                    onValueChange = { quietStartHour = it.roundToInt() },
                                    valueRange = 0f..23f,
                                    steps = 22
                                )
                                Text("End Hour: ${quietEndHour}:00", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = quietEndHour.toFloat(),
                                    onValueChange = { quietEndHour = it.roundToInt() },
                                    valueRange = 0f..23f,
                                    steps = 22
                                )
                            }
                            if (quietStartHour == quietEndHour) {
                                Text(
                                    text = "Start and end hours match, so Quiet Hours will stay inactive until they differ.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusAmber
                                )
                            }
                            OutlinedTextField(
                                value = quietReplyText,
                                onValueChange = { quietReplyText = it },
                                label = { Text("Quiet Hours Auto-Reply") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Data Saver Mode", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Trims AI context & caps reply length (~1 KB per AI reply; ~0.2 MB per 150+ replies). Local commands use 0 KB.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = dataSaverMode,
                                onCheckedChange = { dataSaverMode = it },
                                modifier = Modifier.testTag("settings_data_saver_switch")
                            )
                        }

                        Button(
                            onClick = {
                                onSaveSafeguards(
                                    cooldownSeconds,
                                    replyToGroupChats,
                                    quietHoursEnabled,
                                    quietStartHour,
                                    quietEndHour,
                                    quietReplyText,
                                    dataSaverMode
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_safeguards_button")
                        ) {
                            Text("Save Safeguards")
                        }
                    }
                }
            }

            // Appearance
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Theme", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = state.settings.themeMode == mode,
                                    onClick = { onSetThemeMode(mode) },
                                    label = {
                                        Text(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> "System"
                                                ThemeMode.LIGHT -> "Light"
                                                ThemeMode.DARK -> "Dark"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Privacy & Local Data Management
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Privacy & Local Data", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "All chat logs, contact rules, and AI Chat sessions stay exclusively on this device. No accounts, no cloud tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showWipeConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRose),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_all_history_button")
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete All Local History")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            title = { Text("Delete All Local History?") },
            text = {
                Text("This permanently erases all logged messages, contact rules, and AI Chat conversations from your device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllHistory()
                        showWipeConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRose)
                ) {
                    Text("Erase Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}
