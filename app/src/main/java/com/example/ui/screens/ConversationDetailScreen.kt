package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.data.local.ContactRuleEntity
import com.example.data.local.MessageDirection
import com.example.data.local.MessageLogEntity
import com.example.data.local.ReplyStatus
import com.example.ui.components.ReplyStatusBadge
import com.example.ui.components.formatShortTimestamp
import com.example.ui.theme.StatusAmber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationDetailScreen(
    conversationKey: String,
    logs: List<MessageLogEntity>,
    categories: List<CategoryEntity>,
    contactRule: ContactRuleEntity?,
    canReplyInline: Boolean,
    isDrafting: Boolean,
    onBack: () -> Unit,
    onDraftAiReply: (senderName: String, appName: String, latestIncoming: String, toneOverride: String?, (String) -> Unit) -> Unit,
    onSendReply: (packageName: String, appName: String, senderName: String, text: String, sourceMessageId: Long?) -> Unit,
    onSaveContactRule: (senderName: String, packageName: String, categoryId: Long?, customInstructions: String, useMemory: Boolean, isMuted: Boolean) -> Unit,
    onDeleteConversation: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    val sortedLogs = remember(logs) { logs.sortedBy { it.timestamp } }
    val firstMeta = sortedLogs.firstOrNull()
    val senderName = contactRule?.senderName ?: firstMeta?.senderName ?: "Conversation"
    val appName = firstMeta?.appDisplayName ?: "Messaging"
    val packageName = contactRule?.packageName ?: firstMeta?.packageName ?: ""
    val latestIncoming = sortedLogs.lastOrNull { it.direction == MessageDirection.INCOMING }

    var replyInput by remember(conversationKey) { mutableStateOf("") }
    var editingSourceMsgId by remember(conversationKey) { mutableStateOf<Long?>(null) }
    var selectedTone by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showContactRuleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(senderName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = buildString {
                                append(appName)
                                if (contactRule?.isAutoReplyMuted == true) {
                                    append(" • Auto-Reply Muted")
                                } else if (canReplyInline) {
                                    append(" • Inline Reply Active")
                                } else {
                                    append(" • View Only")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("conversation_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (sortedLogs.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val transcript = sortedLogs.joinToString("\n") { msg ->
                                    val prefix = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.senderName
                                    "[${formatShortTimestamp(msg.timestamp)}] $prefix: ${msg.messageText}"
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Conversation with $senderName ($appName)")
                                    putExtra(Intent.EXTRA_TEXT, transcript)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, "Export Conversation"))
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Export conversation")
                        }
                    }
                    IconButton(
                        onClick = { showContactRuleDialog = true },
                        modifier = Modifier.testTag("contact_rules_button")
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = "Contact settings & memory")
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("delete_conversation_button")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete conversation")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                if (!canReplyInline) {
                    Surface(
                        color = StatusAmber.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No active Android notification reply handle for this chat right now. New incoming messages from reply-supported apps will restore inline reply capability.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedLogs, key = { it.id }) { msg ->
                        val isOutgoing = msg.direction == MessageDirection.OUTGOING
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOutgoing) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = msg.messageText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isOutgoing) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )

                                    if (!isOutgoing && msg.aiGeneratedReply.isNotBlank() &&
                                        msg.replyStatus == ReplyStatus.PENDING_APPROVAL
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Pending Draft: ${msg.aiGeneratedReply}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            replyInput = msg.aiGeneratedReply
                                                            editingSourceMsgId = msg.id
                                                        }
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Edit,
                                                            contentDescription = "Edit draft",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Edit")
                                                    }
                                                    if (canReplyInline) {
                                                        Button(
                                                            onClick = {
                                                                onSendReply(
                                                                    packageName,
                                                                    appName,
                                                                    senderName,
                                                                    msg.aiGeneratedReply,
                                                                    msg.id
                                                                )
                                                            }
                                                        ) {
                                                            Text("Approve & Send")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (msg.errorDetail.isNotBlank()) {
                                        Text(
                                            text = msg.errorDetail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ReplyStatusBadge(status = msg.replyStatus)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatShortTimestamp(msg.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Composer for Manual Reply & Editing AI Drafts
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LazyRow(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                OutlinedButton(
                                    onClick = {
                                        val promptMsg = latestIncoming?.messageText ?: sortedLogs.lastOrNull()?.messageText.orEmpty()
                                        if (promptMsg.isNotBlank()) {
                                            onDraftAiReply(senderName, appName, promptMsg, selectedTone) { draft ->
                                                replyInput = draft
                                                editingSourceMsgId = latestIncoming?.id
                                            }
                                        }
                                    },
                                    enabled = !isDrafting && sortedLogs.isNotEmpty(),
                                    modifier = Modifier.testTag("draft_ai_reply_button")
                                ) {
                                    if (isDrafting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = "Generate AI draft",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isDrafting) "Drafting..." else "AI Draft")
                                }
                            }
                            val tones = listOf(null to "Default", "Concise & short (1 sentence)" to "Shorter", "Extra polite & formal" to "Polite", "Warm & casual" to "Casual")
                            items(tones) { (tonePrompt, label) ->
                                FilterChip(
                                    selected = selectedTone == tonePrompt,
                                    onClick = { selectedTone = tonePrompt },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = replyInput,
                                onValueChange = { replyInput = it },
                                placeholder = { Text("Type or edit reply before sending...") },
                                maxLines = 3,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manual_reply_input")
                            )
                            Button(
                                onClick = {
                                    val textToSend = replyInput
                                    onSendReply(packageName, appName, senderName, textToSend, editingSourceMsgId)
                                    replyInput = ""
                                    editingSourceMsgId = null
                                },
                                enabled = canReplyInline && replyInput.isNotBlank(),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("send_manual_reply_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Conversation?") },
            text = { Text("This removes all locally stored message history with $senderName.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteConversation()
                        onBack()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showContactRuleDialog) {
        var selectedCategoryId by remember(contactRule) { mutableStateOf(contactRule?.assignedCategoryId) }
        var customInstructions by remember(contactRule) { mutableStateOf(contactRule?.customInstructions.orEmpty()) }
        var useMemory by remember(contactRule) { mutableStateOf(contactRule?.useContextualMemory ?: true) }
        var isMuted by remember(contactRule) { mutableStateOf(contactRule?.isAutoReplyMuted ?: false) }

        AlertDialog(
            onDismissRequest = { showContactRuleDialog = false },
            title = { Text("Contact Rules • $senderName") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Category Assignment", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("Default") }
                        )
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customInstructions,
                        onValueChange = { customInstructions = it },
                        label = { Text("Contact-specific AI instructions") },
                        placeholder = { Text("e.g., Address formally, never share weekend schedule") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Contextual Memory", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Include past messages with this contact in AI prompts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useMemory,
                            onCheckedChange = { useMemory = it },
                            modifier = Modifier.testTag("contact_memory_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mute Auto-Reply", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Never send automatic replies to this contact",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMuted,
                            onCheckedChange = { isMuted = it },
                            modifier = Modifier.testTag("contact_mute_switch")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveContactRule(
                            senderName,
                            packageName,
                            selectedCategoryId,
                            customInstructions,
                            useMemory,
                            isMuted
                        )
                        showContactRuleDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showContactRuleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
