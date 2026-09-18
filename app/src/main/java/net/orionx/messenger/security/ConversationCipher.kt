package net.orionx.messenger.security

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM payload primitive used after a conversation key has been established.
 * This class deliberately does not implement identity verification or group key exchange.
 */
class ConversationCipher {
    private val random = SecureRandom()

    fun encrypt(
        key: SecretKey,
        plaintext: String,
        associatedData: String,
        keyVersion: Int,
    ): EncryptedPayload {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val aad = associatedData.toByteArray(StandardCharsets.UTF_8)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return EncryptedPayload(
            algorithm = "AES-256-GCM",
            keyVersion = keyVersion,
            nonceBase64 = nonce.b64(),
            ciphertextBase64 = ciphertext.b64(),
            associatedDataBase64 = aad.b64(),
        )
    }

    fun decrypt(key: SecretKey, payload: EncryptedPayload): String {
        require(payload.algorithm == "AES-256-GCM") { "Unsupported algorithm" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, payload.nonceBase64.decodeB64()),
        )
        cipher.updateAAD(payload.associatedDataBase64.decodeB64())
        return String(cipher.doFinal(payload.ciphertextBase64.decodeB64()), StandardCharsets.UTF_8)
    }

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decodeB64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
}
