package net.orionx.messenger.security

/** Transport-safe encrypted message envelope. */
data class EncryptedPayload(
    val algorithm: String,
    val keyVersion: Int,
    val nonceBase64: String,
    val ciphertextBase64: String,
    val associatedDataBase64: String,
)
