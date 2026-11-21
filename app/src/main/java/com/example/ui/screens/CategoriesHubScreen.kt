package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.data.local.CommandEntity
import com.example.data.local.CommandMatchType
import com.example.data.local.SmartActionEntity
import com.example.data.local.SmartActionType
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusIndigo
import com.example.viewmodel.AutomationUiState

enum class AutomationTab(val label: String) {
    CATEGORIES("Categories"),
    COMMANDS("Commands"),
    SMART_ACTIONS("Smart Actions")
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CategoriesHubScreen(
    state: AutomationUiState,
    selectedTab: AutomationTab,
    onSelectTab: (AutomationTab) -> Unit,
    onToggleCategory: (CategoryEntity, Boolean) -> Unit,
    onSetDefaultCategory: (CategoryEntity) -> Unit,
    onSaveCategory: (CategoryEntity?, String, String, String, String, String, Long) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onToggleCommand: (CommandEntity, Boolean) -> Unit,
    onSaveCommand: (CommandEntity?, String, String, CommandMatchType, Boolean) -> Unit,
    onDeleteCommand: (CommandEntity) -> Unit,
    onUpdateSmartAction: (SmartActionEntity) -> Unit,
    onEnableLocationConsent: (SmartActionEntity, Boolean) -> Unit,
    onDisableLocation: (SmartActionEntity) -> Unit,
    onAddCustomAction: (String, String, String, String, Boolean) -> Unit,
    onDeleteSmartAction: (SmartActionEntity) -> Unit,
    onRefreshLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    var editingCommand by remember { mutableStateOf<CommandEntity?>(null) }
    var showCommandDialog by remember { mutableStateOf(false) }

    var pendingLocationAction by remember { mutableStateOf<SmartActionEntity?>(null) }
    var showCustomActionDialog by remember { mutableStateOf(false) }
    var editingSmartAction by remember { mutableStateOf<SmartActionEntity?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onRefreshLocationPermission()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reply Intelligence",
                    style = MaterialTheme.typography.headlineMedium
                )
                Button(
                    onClick = {
                        when (selectedTab) {
                            AutomationTab.CATEGORIES -> {
                                editingCategory = null
                                showCategoryDialog = true
                            }
                            AutomationTab.COMMANDS -> {
                                editingCommand = null
                                showCommandDialog = true
                            }
                            AutomationTab.SMART_ACTIONS -> {
                                showCustomActionDialog = true
                            }
                        }
                    },
                    modifier = Modifier.testTag("automation_add_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                AutomationTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(tab.label) },
                        modifier = Modifier.testTag("automation_tab_${tab.name.lowercase()}")
                    )
                }
            }

            when (selectedTab) {
                AutomationTab.CATEGORIES -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.categories, key = { it.id }) { cat ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Category,
                                                contentDescription = cat.name,
                                                tint = Color(cat.colorHex)
                                            )
                                            Column {
                                                Text(cat.name, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "${cat.replyStyle} • ${cat.responseLanguage}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = cat.isEnabled,
                                            onCheckedChange = { onToggleCategory(cat, it) }
                                        )
                                    }
                                    Text(
                                        text = cat.instructions,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (cat.greeting.isNotBlank()) {
                                        Text(
                                            text = "Greeting: \"${cat.greeting}\"",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (cat.isDefault) {
                                            StatusPill(label = "Default Fallback", color = StatusEmerald)
                                        } else {
                                            TextButton(onClick = { onSetDefaultCategory(cat) }) {
                                                Text("Set as Default")
                                            }
                                        }
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    editingCategory = cat
                                                    showCategoryDialog = true
                                                }
                                            ) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit category")
                                            }
                                            if (cat.isCustom) {
                                                IconButton(onClick = { onDeleteCategory(cat) }) {
                                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete category")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }

                AutomationTab.COMMANDS -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.commands, key = { it.id }) { cmd ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Terminal,
                                                contentDescription = cmd.triggerText,
                                                tint = StatusIndigo
                                            )
                                            Text(
                                                text = cmd.triggerText,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Switch(
                                            checked = cmd.isEnabled,
                                            onCheckedChange = { onToggleCommand(cmd, it) }
                                        )
                                    }
                                    Text(
                                        text = cmd.responseText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            StatusPill(
                                                label = if (cmd.matchType == CommandMatchType.EXACT) "Exact Match" else "Keyword",
                                                color = StatusCyan
                                            )
                                            if (cmd.priorityOverAi) {
                                                StatusPill(label = "Priority over AI", color = StatusIndigo)
                                            }
                                        }
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    editingCommand = cmd
                                                    showCommandDialog = true
                                                }
                                            ) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit command")
                                            }
                                            IconButton(onClick = { onDeleteCommand(cmd) }) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete command")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }

                AutomationTab.SMART_ACTIONS -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.smartActions, key = { it.id }) { action ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            val icon = when (action.actionType) {
                                                SmartActionType.LOCATION -> Icons.Filled.LocationOn
                                                SmartActionType.TIME, SmartActionType.DATE -> Icons.Filled.Schedule
                                                else -> Icons.Filled.Bolt
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = action.title,
                                                tint = if (action.actionType == SmartActionType.LOCATION) StatusAmber else StatusCyan
                                            )
                                            Column {
                                                Text(action.title, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    action.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = action.isEnabled,
                                            onCheckedChange = { wantEnabled ->
                                                if (action.actionType == SmartActionType.LOCATION) {
                                                    if (wantEnabled) {
                                                        pendingLocationAction = action
                                                    } else {
                                                        onDisableLocation(action)
                                                    }
                                                } else {
                                                    onUpdateSmartAction(action.copy(isEnabled = wantEnabled))
                                                }
                                            },
                                            modifier = Modifier.testTag("smart_action_switch_${action.actionType.name.lowercase()}")
                                        )
                                    }

                                    Text(
                                        text = "Response: ${action.responseTemplate}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (action.actionType == SmartActionType.LOCATION) {
                                                StatusPill(
                                                    label = if (state.hasLocationPermission) "Permission Granted" else "Location Permission Needed",
                                                    color = if (state.hasLocationPermission) StatusEmerald else StatusAmber
                                                )
                                                if (action.requiresConfirmation) {
                                                    StatusPill(label = "Confirm Before Sending", color = StatusCyan)
                                                }
                                            } else if (action.triggerKeywords != "*") {
                                                StatusPill(label = "Keywords: ${action.triggerKeywords.take(24)}", color = StatusCyan)
                                            }
                                        }
                                        Row {
                                            IconButton(onClick = { editingSmartAction = action }) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit action template")
                                            }
                                            if (action.actionType == SmartActionType.CUSTOM) {
                                                IconButton(onClick = { onDeleteSmartAction(action) }) {
                                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete custom action")
                                                }
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
        }
    }

    // Category Dialog
    if (showCategoryDialog) {
        var name by remember(editingCategory) { mutableStateOf(editingCategory?.name.orEmpty()) }
        var instructions by remember(editingCategory) { mutableStateOf(editingCategory?.instructions.orEmpty()) }
        var style by remember(editingCategory) { mutableStateOf(editingCategory?.replyStyle ?: "Balanced & Natural") }
        var greeting by remember(editingCategory) { mutableStateOf(editingCategory?.greeting.orEmpty()) }
        var language by remember(editingCategory) { mutableStateOf(editingCategory?.responseLanguage ?: "Auto-detect") }

        val languages = listOf("Auto-detect", "English", "Spanish", "French", "German", "Arabic", "Hindi", "Bengali", "Portuguese")

        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text(if (editingCategory == null) "New Category" else "Edit Category") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("Custom AI Instructions") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = style,
                        onValueChange = { style = it },
                        label = { Text("Reply Style") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = greeting,
                        onValueChange = { greeting = it },
                        label = { Text("Custom Greeting (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Response Language", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        languages.forEach { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { language = lang },
                                label = { Text(lang) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCategory(
                            editingCategory,
                            name,
                            instructions,
                            style,
                            greeting,
                            language,
                            editingCategory?.colorHex ?: 0xFF06B6D4
                        )
                        showCategoryDialog = false
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Command Dialog
    if (showCommandDialog) {
        var trigger by remember(editingCommand) { mutableStateOf(editingCommand?.triggerText.orEmpty()) }
        var response by remember(editingCommand) { mutableStateOf(editingCommand?.responseText.orEmpty()) }
        var matchType by remember(editingCommand) { mutableStateOf(editingCommand?.matchType ?: CommandMatchType.EXACT) }
        var priority by remember(editingCommand) { mutableStateOf(editingCommand?.priorityOverAi ?: true) }

        AlertDialog(
            onDismissRequest = { showCommandDialog = false },
            title = { Text(if (editingCommand == null) "New Custom Command" else "Edit Command") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = trigger,
                        onValueChange = { trigger = it },
                        label = { Text("Trigger (e.g., /hello or price?)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = response,
                        onValueChange = { response = it },
                        label = { Text("Command Response") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = matchType == CommandMatchType.EXACT,
                            onClick = { matchType = CommandMatchType.EXACT },
                            label = { Text("Exact Match") }
                        )
                        FilterChip(
                            selected = matchType == CommandMatchType.KEYWORD,
                            onClick = { matchType = CommandMatchType.KEYWORD },
                            label = { Text("Keyword Match") }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = priority, onCheckedChange = { priority = it })
                        Text("Priority over AI responses", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCommand(editingCommand, trigger, response, matchType, priority)
                        showCommandDialog = false
                    },
                    enabled = trigger.isNotBlank() && response.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommandDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Explicit Location Consent & Confirmation Dialog (REQUIRED PRIVACY SAFEGUARD)
    pendingLocationAction?.let { locAction ->
        var requirePerMessageConfirmation by remember(locAction) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { pendingLocationAction = null },
            title = { Text("Confirm Location Sharing") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "When someone asks where you are, this action reads your approximate device location (coarse area) only when triggered. Location is never collected or shared in the background.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = requirePerMessageConfirmation,
                            onCheckedChange = { requirePerMessageConfirmation = it }
                        )
                        Text(
                            "Require my manual approval before sending each location reply (Recommended)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!state.hasLocationPermission) {
                        Text(
                            "Android approximate location permission is not granted yet. Tapping Confirm will request it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusAmber
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEnableLocationConsent(locAction, requirePerMessageConfirmation)
                        if (!state.hasLocationPermission) {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                        pendingLocationAction = null
                    },
                    modifier = Modifier.testTag("confirm_location_consent_button")
                ) {
                    Text("Confirm & Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLocationAction = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Smart Action Template Dialog
    editingSmartAction?.let { action ->
        var template by remember(action) { mutableStateOf(action.responseTemplate) }
        var keywords by remember(action) { mutableStateOf(action.triggerKeywords) }
        AlertDialog(
            onDismissRequest = { editingSmartAction = null },
            title = { Text("Edit ${action.title}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (action.actionType != SmartActionType.BUSY_MODE) {
                        OutlinedTextField(
                            value = keywords,
                            onValueChange = { keywords = it },
                            label = { Text("Trigger Keywords (comma-separated)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = { Text("Response Template") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSmartAction(action.copy(triggerKeywords = keywords, responseTemplate = template))
                        editingSmartAction = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSmartAction = null }) { Text("Cancel") }
            }
        )
    }

    // Custom Smart Action Dialog
    if (showCustomActionDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var keywords by remember { mutableStateOf("") }
        var template by remember { mutableStateOf("") }
        var requiresConfirm by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCustomActionDialog = false },
            title = { Text("New Custom Smart Action") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Action Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("Trigger Keywords (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = { Text("Predefined Response") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = requiresConfirm, onCheckedChange = { requiresConfirm = it })
                        Text("Ask approval before sending", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCustomAction(title, desc, keywords, template, requiresConfirm)
                        showCustomActionDialog = false
                    },
                    enabled = title.isNotBlank() && keywords.isNotBlank() && template.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomActionDialog = false }) { Text("Cancel") }
            }
        )
    }
}
