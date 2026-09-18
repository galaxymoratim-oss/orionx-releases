package net.orionx.messenger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * The encoder is a port of `qr.js` from the redesign canvas. These hashes come from running the
 * original JavaScript on the same strings, so a drift in the port shows up here rather than as a
 * QR code nobody can scan.
 */
class OrionQrEncoderTest {

    private fun fingerprint(text: String): String {
        val code = OrionQrEncoder.build(text)
        val flat = code.modules.joinToString("") { row -> row.joinToString("") { if (it) "1" else "0" } }
        val digest = MessageDigest.getInstance("SHA-1").digest(flat.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    @Test
    fun `matches the canvas encoder for a 25-module code`() {
        assertEquals(25, OrionQrEncoder.build("orionx.me/@alex").size)
        assertEquals("e729c073e86cc870", fingerprint("orionx.me/@alex"))
    }

    @Test
    fun `matches the canvas encoder for a longer handle`() {
        assertEquals("ab0e7b781d2accad", fingerprint("orionx.me/@ezra99"))
    }

    @Test
    fun `drops to version 1 for a short handle`() {
        assertEquals(21, OrionQrEncoder.build("orionx.me/@a").size)
        assertEquals("7fb00f4c06d6a607", fingerprint("orionx.me/@a"))
    }

    @Test
    fun `first rows carry the finder pattern and timing row`() {
        val code = OrionQrEncoder.build("orionx.me/@alex")
        val rows = code.modules.take(3).map { row -> row.joinToString("") { if (it) "1" else "0" } }
        assertEquals("1111111010000100101111111", rows[0])
        assertEquals("1000001001111011101000001", rows[1])
        assertEquals("1011101010111110101011101", rows[2])
    }
}
