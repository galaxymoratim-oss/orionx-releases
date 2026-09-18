package net.orionx.messenger

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The engine is a port of `calcKey` from the redesign canvas. Every expectation here was produced
 * by running the original JavaScript on the same key sequence, so a drift in the port fails here
 * instead of quietly changing what the calculator computes.
 */
class XCalcEngineTest {

    private fun run(keys: String): String {
        var state = XCalcSnapshot()
        keys.split(" ").forEach { state = XCalcEngine.key(state, it) }
        return "${state.current} | ${state.expression}"
    }

    @Test
    fun `adds and keeps the expression line`() {
        assertEquals("13 | 8 + 5 =", run("8 + 5 ="))
    }

    @Test
    fun `second equals repeats the last operation`() {
        assertEquals("18 | 13 + 5 =", run("8 + 5 = ="))
    }

    @Test
    fun `division by zero reports an error`() {
        assertEquals("ошибка | 7 / 0 =", run("7 ÷ 0 ="))
    }

    @Test
    fun `chained operators fold left, without precedence`() {
        assertEquals("20 | 5 × 4 =", run("2 + 3 × 4 ="))
        assertEquals("10 | 6 + 4 =", run("2 × 3 + 4 ="))
    }

    @Test
    fun `percent is plain when there is no pending operation`() {
        assertEquals("0.5 | ", run("5 0 %"))
    }

    @Test
    fun `percent takes a share of the accumulator inside an operation`() {
        assertEquals("20 | 200 +", run("2 0 0 + 1 0 %"))
    }

    @Test
    fun `decimals add exactly`() {
        assertEquals("3.75 | 1.5 + 2.25 =", run("1 . 5 + 2 . 2 5 ="))
    }

    @Test
    fun `sign flip and backspace`() {
        assertEquals("-9 | ", run("9 ±"))
        assertEquals("12 | ", run("1 2 3 ⌦"))
    }

    @Test
    fun `AC clears everything`() {
        assertEquals("0 | ", run("9 9 AC"))
    }

    @Test
    fun `repeating decimals are rounded to nine places, like the canvas`() {
        assertEquals("0.333333333 | 1 / 3 =", run("1 ÷ 3 ="))
    }

    @Test
    fun `a digit after a completed sum clears the expression line`() {
        assertEquals("9 | ", run("8 + 5 = 9"))
    }

    @Test
    fun `typing after a bare equals continues the number, as in the prototype`() {
        assertEquals("57 | ", run("5 = 7"))
    }
}
