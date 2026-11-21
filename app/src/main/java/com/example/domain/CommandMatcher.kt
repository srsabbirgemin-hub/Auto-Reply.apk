package com.example.domain

import com.example.data.local.CommandEntity
import com.example.data.local.CommandMatchType

data class CommandMatchResult(
    val command: CommandEntity,
    val isPriority: Boolean
)

object CommandMatcher {
    fun findMatch(incomingText: String, commands: List<CommandEntity>): CommandMatchResult? {
        val normalizedIncoming = incomingText.trim()
        if (normalizedIncoming.isEmpty()) return null

        val enabled = commands.filter { it.isEnabled }
            .sortedWith(
                compareByDescending<CommandEntity> { it.priorityOverAi }
                    .thenBy { it.matchType == CommandMatchType.KEYWORD } // Exact before keyword
                    .thenByDescending { it.triggerText.length }
            )

        for (command in enabled) {
            val trigger = command.triggerText.trim()
            if (trigger.isEmpty()) continue

            val matched = when (command.matchType) {
                CommandMatchType.EXACT -> normalizedIncoming.equals(trigger, ignoreCase = true)
                CommandMatchType.KEYWORD -> containsKeyword(normalizedIncoming, trigger)
            }
            if (matched) {
                return CommandMatchResult(command = command, isPriority = command.priorityOverAi)
            }
        }
        return null
    }

    private fun containsKeyword(text: String, keyword: String): Boolean {
        val escaped = Regex.escape(keyword)
        val pattern = if (keyword.first().isLetterOrDigit() && keyword.last().isLetterOrDigit()) {
            Regex("\\b$escaped\\b", RegexOption.IGNORE_CASE)
        } else {
            Regex(escaped, RegexOption.IGNORE_CASE)
        }
        return pattern.containsMatchIn(text)
    }
}
