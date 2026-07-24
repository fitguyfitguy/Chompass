package app.chompass.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class ChatMessage(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val role: Role,
    val content: String,
    val attachmentImageBase64: String? = null,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now()
) {
    @Serializable
    enum class Role {
        @SerialName("user") USER,
        @SerialName("assistant") ASSISTANT
    }
}
