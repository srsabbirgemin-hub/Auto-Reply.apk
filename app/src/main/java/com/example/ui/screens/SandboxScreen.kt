package com.example.ui.screens

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRose
import com.example.viewmodel.SandboxUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SandboxScreen(
    state: SandboxUiState,
    onRunSimulation: (String, String, String, Boolean, CategoryEntity?) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var senderName by remember { mutableStateOf("Alex") }
    var appName by remember { mutableStateOf("WhatsApp") }
    var sampleMessage by remember { mutableStateOf("") }
    var isGroupChat by remember { mutableStateOf(false) }
    var selectedCategory by remember(state.categories) {
        mutableStateOf(state.categories.firstOrNull { it.isDefault } ?: state.categories.firstOrNull())
    }
    val listState = rememberLazyListState()

    LaunchedEffect(state.lastTrace) {
        if (state.lastTrace != null) {
            listState.animateScrollToItem(2)
        }
    }

    val sampleApps = listOf("WhatsApp", "Messenger", "Telegram", "Instagram", "SMS")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reply Pipeline Sandbox") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("sandbox_back_button")) {
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
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .testTag("sandbox_lazy_column"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Surface(
                        color = StatusCyan.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Science, contentDescription = null, tint = StatusCyan)
                            Text(
                                text = "Dry-Run Sandbox: Test how your Commands, Smart Actions, Quiet Hours, Group Chat filter, and AI Category rules respond—without modifying real message history.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

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
                            OutlinedTextField(
                                value = senderName,
                                onValueChange = { senderName = it },
                                label = { Text("Simulated Sender") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Simulated App", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                sampleApps.forEach { app ->
                                    FilterChip(
                                        selected = appName == app,
                                        onClick = { appName = app },
                                        label = { Text(app) }
                                    )
                                }
                            }

                            Text("Category Rule", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.categories.filter { it.isEnabled }.forEach { cat ->
                                    FilterChip(
                                        selected = selectedCategory?.id == cat.id,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat.name) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Simulate Group Conversation", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Verify how your Group Chat safeguard handles this message",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isGroupChat,
                                    onCheckedChange = { isGroupChat = it }
                                )
                            }

                            OutlinedTextField(
                                value = sampleMessage,
                                onValueChange = { sampleMessage = it },
                                label = { Text("Sample Incoming Message") },
                                placeholder = { Text("Try '/hello', 'what time is it?', or 'Can we meet at 5?'") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sandbox_message_input")
                            )

                            Button(
                                onClick = {
                                    onRunSimulation(senderName, appName, sampleMessage, isGroupChat, selectedCategory)
                                },
                                enabled = !state.isRunning && sampleMessage.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sandbox_run_button")
                            ) {
                                if (state.isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Run Pipeline Dry-Run")
                                }
                            }
                        }
                    }
                }

                state.lastTrace?.let { trace ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sandbox_result_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Pipeline Decision", style = MaterialTheme.typography.titleMedium)
                                    StatusPill(
                                        label = trace.decisionSource,
                                        color = if (trace.error == null) StatusEmerald else StatusRose
                                    )
                                }
                                Text(
                                    text = "Category: ${trace.categoryUsed}" +
                                        if (trace.latencyMs > 0) " • ${trace.latencyMs} ms" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (trace.replyText.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = trace.replyText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                if (!trace.error.isNullOrBlank()) {
                                    Text(
                                        text = trace.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusRose
                                    )
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
