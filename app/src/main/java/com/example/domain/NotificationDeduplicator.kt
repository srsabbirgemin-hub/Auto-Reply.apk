package com.example.domain

import java.security.MessageDigest

class NotificationDeduplicator(
    private val windowMs: Long = 30_000L,
    private val maxEntries: Int = 250
) {
    private val seenKeys = LinkedHashMap<String, Long>(maxEntries, 0.75f, true)
    private val recentSentEchoes = LinkedHashMap<String, Long>(maxEntries, 0.75f, true)

    @Synchronized
    fun isDuplicateAndRecord(
        packageName: String,
        sender: String,
        messageText: String,
        notificationKey: String,
        postTimeMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        pruneExpired(nowMs)

        val normalizedText = messageText.trim()
        // Suppress echo of our own recently dispatched reply
        val echoKey = buildEchoKey(packageName, sender, normalizedText)
        val echoTimestamp = recentSentEchoes[echoKey]
        if (echoTimestamp != null && nowMs - echoTimestamp <= windowMs) {
            return true
        }

        val contentHash = computeContentHash(packageName, sender, normalizedText)
        val lastSeenContent = seenKeys[contentHash]
        if (lastSeenContent != null && nowMs - lastSeenContent <= windowMs) {
            return true
        }

        val sbnEventKey = "$notificationKey|$postTimeMs|$contentHash"
        if (seenKeys.containsKey(sbnEventKey)) {
            return true
        }

        seenKeys[contentHash] = nowMs
        seenKeys[sbnEventKey] = nowMs
        trimToCapacity(seenKeys)
        return false
    }

    @Synchronized
    fun recordSentReplyEcho(
        packageName: String,
        sender: String,
        replyText: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val echoKey = buildEchoKey(packageName, sender, replyText.trim())
        recentSentEchoes[echoKey] = nowMs
        trimToCapacity(recentSentEchoes)
    }

    fun computeContentHash(packageName: String, sender: String, messageText: String): String {
        val raw = "${packageName.trim()}|${sender.trim().lowercase()}|${messageText.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun buildEchoKey(packageName: String, sender: String, text: String): String {
        return "${packageName.trim()}|${sender.trim().lowercase()}|${text.trim()}"
    }

    private fun pruneExpired(nowMs: Long) {
        val seenIter = seenKeys.entries.iterator()
        while (seenIter.hasNext()) {
            if (nowMs - seenIter.next().value > windowMs) {
                seenIter.remove()
            }
        }
        val echoIter = recentSentEchoes.entries.iterator()
        while (echoIter.hasNext()) {
            if (nowMs - echoIter.next().value > windowMs) {
                echoIter.remove()
            }
        }
    }

    private fun trimToCapacity(map: LinkedHashMap<String, Long>) {
        while (map.size > maxEntries) {
            val eldest = map.entries.iterator().next().key
            map.remove(eldest)
        }
    }
}
