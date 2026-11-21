package com.example

import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiChatRole
import com.example.data.local.AiChatSessionEntity
import com.example.data.local.AppSettings
import com.example.data.local.CategoryEntity
import com.example.data.local.CommandEntity
import com.example.data.local.CommandMatchType
import com.example.data.local.ContactRuleEntity
import com.example.data.local.MessageDirection
import com.example.data.local.MessageLogEntity
import com.example.data.local.ReplyStatus
import com.example.data.local.SmartActionEntity
import com.example.data.local.SmartActionType
import com.example.data.network.AiApiClient
import com.example.data.network.AiApiResult
import com.example.data.network.ChatMessageDto
import com.example.domain.CommandMatcher
import com.example.domain.NotificationDeduplicator
import com.example.domain.QuietHoursEvaluator
import com.example.domain.ReplyIntelligencePromptBuilder
import com.example.domain.ReplyPromptInput
import com.example.domain.SmartActionEvaluator
import com.example.domain.SmartActionOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReplyDomainTest {

    @Test
    fun deduplicatorBlocksDuplicatesAndSentEchoesWithinWindow() {
        val dedup = NotificationDeduplicator(windowMs = 30_000L)

        val first = dedup.isDuplicateAndRecord("com.whatsapp", "Sam", "Hello!", "k1", 1000L, nowMs = 1000L)
        val duplicate = dedup.isDuplicateAndRecord("com.whatsapp", "Sam", "Hello!", "k1", 1000L, nowMs = 5000L)
        val afterWindow = dedup.isDuplicateAndRecord("com.whatsapp", "Sam", "Hello!", "k2", 35_000L, nowMs = 35_000L)

        assertFalse(first)
        assertTrue(duplicate)
        assertFalse(afterWindow)

        dedup.recordSentReplyEcho("com.whatsapp", "Sam", "On my way!", nowMs = 40_000L)
        val echo = dedup.isDuplicateAndRecord("com.whatsapp", "Sam", "On my way!", "k3", 41_000L, nowMs = 41_000L)
        assertTrue(echo)
    }

    @Test
    fun commandMatcherRespectsExactKeywordAndPriority() {
        val commands = listOf(
            CommandEntity(1, "/hello", "Hello! How are you?", CommandMatchType.EXACT, isEnabled = true, priorityOverAi = true),
            CommandEntity(2, "busy", "I am currently busy.", CommandMatchType.KEYWORD, isEnabled = true, priorityOverAi = false),
            CommandEntity(3, "/disabled", "Nope", CommandMatchType.EXACT, isEnabled = false, priorityOverAi = true)
        )

        val exactMatch = CommandMatcher.findMatch("  /hello  ", commands)
        assertNotNull(exactMatch)
        assertEquals("Hello! How are you?", exactMatch?.command?.responseText)
        assertTrue(exactMatch?.isPriority == true)

        val keywordMatch = CommandMatcher.findMatch("Are you busy right now?", commands)
        assertNotNull(keywordMatch)
        assertEquals("I am currently busy.", keywordMatch?.command?.responseText)
        assertFalse(keywordMatch?.isPriority == true)

        assertNull(CommandMatcher.findMatch("/disabled", commands))
    }

    @Test
    fun smartActionEvaluatorRequiresExplicitConsentForLocationAndHandlesTimeDate() = runTest {
        val locationActionUnconsented = SmartActionEntity(
            id = 1,
            actionType = SmartActionType.LOCATION,
            title = "Location",
            description = "",
            triggerKeywords = "where are you",
            responseTemplate = "At {location}",
            isEnabled = true,
            requiresConfirmation = false,
            isConsentGranted = false
        )
        val timeAction = SmartActionEntity(
            id = 2,
            actionType = SmartActionType.TIME,
            title = "Time",
            description = "",
            triggerKeywords = "what time is it",
            responseTemplate = "It is {time}.",
            isEnabled = true,
            requiresConfirmation = false,
            isConsentGranted = true
        )

        val evaluator = SmartActionEvaluator(
            nowProvider = { 1_700_000_000_000L },
            locationResolver = { "Downtown Seattle" }
        )

        val locResult = evaluator.evaluate("Where are you?", listOf(locationActionUnconsented), hasLocationPermission = true)
        assertTrue(locResult is SmartActionOutcome.PendingApproval)

        val consentedAuto = locationActionUnconsented.copy(isConsentGranted = true, requiresConfirmation = false)
        val locAllowed = evaluator.evaluate("Where are you?", listOf(consentedAuto), hasLocationPermission = true)
        assertTrue(locAllowed is SmartActionOutcome.ImmediateReply)
        assertEquals("At Downtown Seattle", (locAllowed as SmartActionOutcome.ImmediateReply).replyText)

        val timeResult = evaluator.evaluate("Hey what time is it?", listOf(timeAction), hasLocationPermission = false)
        assertTrue(timeResult is SmartActionOutcome.ImmediateReply)
        assertTrue((timeResult as SmartActionOutcome.ImmediateReply).replyText.startsWith("It is "))
    }

    @Test
    fun promptBuilderIncludesAllContextWhenMemoryEnabledAndExcludesHistoryWhenDisabled() {
        val category = CategoryEntity(
            id = 4,
            name = "Business",
            instructions = "Be concise and polite.",
            replyStyle = "Professional",
            greeting = "Hello,",
            responseLanguage = "English"
        )
        val history = listOf(
            MessageLogEntity(
                id = 1,
                conversationKey = "com.whatsapp|jordan",
                packageName = "com.whatsapp",
                appDisplayName = "WhatsApp",
                senderName = "Jordan",
                senderIdentifier = "+1",
                messageText = "Did you review the invoice?",
                direction = MessageDirection.INCOMING,
                aiGeneratedReply = "Checking it now.",
                replyStatus = ReplyStatus.SENT,
                timestamp = 100L
            )
        )
        val session = AiChatSessionEntity(
            id = 1,
            title = "Project Launch",
            modelName = "llama-3.3-70b-versatile",
            personality = "Focused",
            responseStyle = "Direct",
            useAsAutoReplyContext = true
        )
        val sessionMsgs = listOf(
            AiChatMessageEntity(1, 1, AiChatRole.USER, "We launch on Friday.", 50L)
        )

        val withMemory = ReplyIntelligencePromptBuilder.buildMessages(
            ReplyPromptInput(
                senderName = "Jordan",
                appDisplayName = "WhatsApp",
                incomingMessage = "Any update?",
                category = category,
                contactRule = ContactRuleEntity("com.whatsapp|jordan", "Jordan", "com.whatsapp", 4, "Mention Q4", useContextualMemory = true),
                previousMessages = history,
                nonPriorityCommands = emptyList(),
                settings = AppSettings(),
                activeAiSession = session,
                activeAiSessionMessages = sessionMsgs,
                toneOverride = "Shorter and more concise"
            )
        )
        assertTrue(withMemory.first().content.contains("Mention Q4"))
        assertTrue(withMemory.first().content.contains("We launch on Friday."))
        assertTrue(withMemory.first().content.contains("Shorter and more concise"))
        assertTrue(withMemory.any { it.content == "Did you review the invoice?" })

        val withoutMemory = ReplyIntelligencePromptBuilder.buildMessages(
            ReplyPromptInput(
                senderName = "Jordan",
                appDisplayName = "WhatsApp",
                incomingMessage = "Any update?",
                category = category,
                contactRule = ContactRuleEntity("com.whatsapp|jordan", "Jordan", "com.whatsapp", 4, "", useContextualMemory = false),
                previousMessages = history,
                nonPriorityCommands = emptyList(),
                settings = AppSettings(),
                activeAiSession = null,
                activeAiSessionMessages = emptyList()
            )
        )
        assertFalse(withoutMemory.any { it.content == "Did you review the invoice?" })
    }

    @Test
    fun quietHoursEvaluatorHandlesDaytimeOvernightAndEqualWindows() {
        // Overnight 23 -> 7
        assertTrue(QuietHoursEvaluator.isHourInQuietWindow(23, 23, 7))
        assertTrue(QuietHoursEvaluator.isHourInQuietWindow(2, 23, 7))
        assertFalse(QuietHoursEvaluator.isHourInQuietWindow(7, 23, 7))
        assertFalse(QuietHoursEvaluator.isHourInQuietWindow(15, 23, 7))

        // Daytime 13 -> 15
        assertTrue(QuietHoursEvaluator.isHourInQuietWindow(13, 13, 15))
        assertTrue(QuietHoursEvaluator.isHourInQuietWindow(14, 13, 15))
        assertFalse(QuietHoursEvaluator.isHourInQuietWindow(15, 13, 15))
        assertFalse(QuietHoursEvaluator.isHourInQuietWindow(12, 13, 15))

        // Equal start & end -> disabled/empty window
        assertFalse(QuietHoursEvaluator.isHourInQuietWindow(10, 10, 10))
        assertFalse(QuietHoursEvaluator.isWithinQuietHours(enabled = false, startHour = 0, endHour = 23))
    }

    @Test
    fun dataSaverModeTrimsHistoryMessagesInPrompt() {
        val longHistory = (1..6).map { idx ->
            MessageLogEntity(
                id = idx.toLong(),
                conversationKey = "com.whatsapp|alex",
                packageName = "com.whatsapp",
                appDisplayName = "WhatsApp",
                senderName = "Alex",
                senderIdentifier = "+1",
                messageText = "Message $idx",
                direction = MessageDirection.INCOMING,
                aiGeneratedReply = "",
                replyStatus = ReplyStatus.RECEIVED,
                timestamp = idx.toLong() * 1000L
            )
        }
        val compactPrompt = ReplyIntelligencePromptBuilder.buildMessages(
            ReplyPromptInput(
                senderName = "Alex",
                appDisplayName = "WhatsApp",
                incomingMessage = "Latest?",
                category = null,
                contactRule = null,
                previousMessages = longHistory,
                nonPriorityCommands = emptyList(),
                settings = AppSettings(dataSaverMode = true),
                activeAiSession = null,
                activeAiSessionMessages = emptyList()
            )
        )
        assertFalse(compactPrompt.any { it.content == "Message 1" })
        assertTrue(compactPrompt.any { it.content == "Message 5" })
        assertTrue(compactPrompt.any { it.content == "Message 6" })
    }

    @Test
    fun apiClientRejectsInvalidKeysAndMalformedUrlsGracefully() = runTest {
        assertTrue(AiApiClient.isValidApiKeyHeader("gsk_validKey123"))
        assertFalse(AiApiClient.isValidApiKeyHeader("gsk_bad\nkey"))
        assertFalse(AiApiClient.isValidApiKeyHeader("gsk_•masked"))
        assertFalse(AiApiClient.isValidApiKeyHeader("   "))

        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            AiApiClient.normalizeEndpoint("https://api.groq.com/openai/v1/", "chat/completions")
        )
        assertNull(AiApiClient.normalizeEndpoint("ftp://invalid.example.com", "models"))
        assertNull(AiApiClient.normalizeEndpoint("not a url", "models"))

        val client = AiApiClient()
        val badKeyRes = client.generateCompletion(
            baseUrl = "https://api.groq.com/openai/v1/",
            apiKey = "gsk_bad\nline",
            model = "llama-3.3-70b-versatile",
            messages = listOf(ChatMessageDto("user", "hi")),
            temperature = 0.5f,
            maxTokens = 64
        )
        assertTrue(badKeyRes is AiApiResult.Error)

        val badModelsRes = client.fetchAvailableModels(
            baseUrl = "not-a-url",
            apiKey = "gsk_valid_123"
        )
        assertTrue(badModelsRes is AiApiResult.Error)
    }
}
