package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ContactRuleEntity
import com.example.data.local.DatabaseSeeder
import com.example.data.local.MessageDirection
import com.example.data.local.MessageLogEntity
import com.example.data.local.ReplyStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun readAppNameFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Smart AI Auto Reply", appName)
    }

    @Test
    fun databaseSeederPopulatesDefaultsAndLogsWork() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        DatabaseSeeder.seedDefaultsIfNeeded(db, setOf("com.whatsapp"))

        val categories = db.categoryDao().observeAll().first()
        val commands = db.commandDao().observeAll().first()
        val smartActions = db.smartActionDao().observeAll().first()
        val apps = db.supportedAppDao().observeAll().first()

        assertTrue(categories.size >= 8)
        assertTrue(commands.any { it.triggerText == "/hello" })
        assertTrue(smartActions.any { it.actionType.name == "LOCATION" && !it.isEnabled && !it.isConsentGranted })
        assertTrue(apps.any { it.packageName == "com.whatsapp" && it.isInstalled })

        db.contactRuleDao().upsert(
            ContactRuleEntity(
                conversationKey = "com.whatsapp|alex",
                senderName = "Alex",
                packageName = "com.whatsapp",
                isAutoReplyMuted = true
            )
        )
        assertTrue(db.contactRuleDao().getForContact("com.whatsapp|alex")?.isAutoReplyMuted == true)

        db.messageLogDao().insert(
            MessageLogEntity(
                conversationKey = "com.whatsapp|alex",
                packageName = "com.whatsapp",
                appDisplayName = "WhatsApp",
                senderName = "Alex",
                senderIdentifier = "+15550101",
                messageText = "Hey, are you free?",
                direction = MessageDirection.INCOMING,
                aiGeneratedReply = "I'll get back to you shortly!",
                replyStatus = ReplyStatus.SENT
            )
        )
        assertEquals(1, db.messageLogDao().observeTotalReceivedCount().first())
        db.close()
    }

    @Test
    fun homeDashboardRendersAndNavigatesBetweenTabs() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Smart AI Auto Reply").assertIsDisplayed()
        composeRule.onNodeWithTag("master_auto_reply_switch").assertIsDisplayed()

        // Navigate to Sandbox and dry-run /hello command
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("quick_sandbox"))
        composeRule.onNodeWithTag("quick_sandbox").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reply Pipeline Sandbox").assertIsDisplayed()
        composeRule.onNodeWithTag("sandbox_message_input").performScrollTo().performTextInput("/hello")
        composeRule.onNodeWithTag("sandbox_run_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag("sandbox_result_card")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sandbox_lazy_column")
            .performScrollToNode(hasTestTag("sandbox_result_card"))
        composeRule.onNodeWithTag("sandbox_result_card").assertIsDisplayed()
        composeRule.onNodeWithText("Hello! How are you?").assertIsDisplayed()
        composeRule.onNodeWithTag("sandbox_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_messages").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Message Monitor").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_categories").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reply Intelligence").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(androidx.compose.ui.test.hasText("AI API Configuration"))
        composeRule.onNodeWithText("AI API Configuration").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("test_api_connection_button"))
        composeRule.onNodeWithTag("test_api_connection_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("save_safeguards_button"))
        composeRule.onNodeWithTag("save_safeguards_button")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
