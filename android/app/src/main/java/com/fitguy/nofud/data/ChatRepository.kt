package com.fitguy.nofud.data

import com.fitguy.nofud.models.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Coach chat conversation history. Port of iOS ChatStore.
 * The full history stays visible to the UI; the LLM payload is capped
 * at the last 20 messages elsewhere (ChatService).
 */
class ChatRepository(private val prefs: PreferencesStore) {
    val messages: Flow<List<ChatMessage>> = prefs.chatHistory

    suspend fun append(message: ChatMessage) {
        val current = prefs.chatHistory.first()
        prefs.setChatHistory(current + message)
    }

    suspend fun replaceAll(messages: List<ChatMessage>) {
        prefs.setChatHistory(messages)
    }

    suspend fun clear() {
        prefs.setChatHistory(emptyList())
    }

    /** Last N messages for LLM payload context, trimmed to keep token cost flat. */
    suspend fun contextMessages(limit: Int = 20): List<ChatMessage> =
        prefs.chatHistory.first().takeLast(limit)
}
