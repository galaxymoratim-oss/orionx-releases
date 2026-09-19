package net.orionx.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Immutable calculator state. Keeping the state machine free of Compose means it can be exercised
 * directly against the behaviour of the canvas prototype.
 */
internal data class XCalcSnapshot(
    val current: String = "0",
    val expression: String = "",
    val accumulator: Double? = null,
    val operator: String? = null,
    val fresh: Boolean = true,
    val lastOperator: String? = null,
    val lastOperand: Double? = null,
)

/**
 * Ported from `calcKey` in the redesign canvas — including repeating the last operation on a
 * second `=`, and the running expression line that sits above the result.
 */
internal object XCalcEngine {

    const val ERROR = "ошибка"

    fun key(state: XCalcSnapshot, k: String): XCalcSnapshot {
        if (state.current == ERROR && k != "AC") return XCalcSnapshot()
        if (k == "AC") return XCalcSnapshot()

        if (k == "⌦") {
            val next = if (state.fresh || state.current.length <= 1 || state.current == "-0") "0" else state.current.dropLast(1)
            val value = if (next == "-") "0" else next
            return state.copy(current = value, fresh = value == "0")
        }

        if (k == "±") {
            val value = when {
                state.current.startsWith("-") -> state.current.drop(1)
                state.current == "0" -> "0"
                else -> "-" + state.current
            }
            return state.copy(current = value, fresh = state.current == "0")
        }

        if (k == "%") {
            val v = state.current.toDoubleOrNull() ?: 0.0
            val acc = state.accumulator
            val percent = if (state.operator != null && acc != null) acc * v / 100 else v / 100
            return state.copy(current = format(percent), fresh = false)
        }

        if (k == ".") {
            if (state.fresh) return state.copy(current = "0.", fresh = false)
            if (state.current.contains('.')) return state
            return state.copy(current = state.current + ".", fresh = false)
        }

        if (k.length == 1 && k[0].isDigit()) {
            val digitsOnly = state.current.replace("-", "").replace(".", "")
            val value = when {
                state.fresh || state.current == "0" -> k
                digitsOnly.length >= 12 -> state.current
                else -> state.current + k
            }
            // After «=» a fresh digit starts a new sum, so the old line does not hang around.
            val expression = if (state.operator == null && state.expression.endsWith("=")) "" else state.expression
            // Макет: пока пользователь набирает второй операнд, выражение живое — «8 + 5».
            val liveExpression =
                if (state.operator != null && !state.fresh)
                    format(state.accumulator ?: 0.0) + " " + glyph(state.operator) + " " + value
                else expression
            return state.copy(current = value, fresh = false, expression = liveExpression)
        }

        val b = state.current.toDoubleOrNull() ?: 0.0

        if (k == "=") {
            val op = state.operator
            if (op == null) {
                val lo = state.lastOperator
                val lb = state.lastOperand
                return if (lo != null && lb != null) {
                    val result = apply(b, lo, lb)
                    state.copy(
                        current = format(result),
                        expression = format(b) + " " + glyph(lo) + " " + format(lb) + " =",
                        fresh = true,
                    )
                } else {
                    state.copy(expression = format(b) + " =")
                }
            }
            val acc = state.accumulator ?: 0.0
            val result = apply(acc, op, b)
            return state.copy(
                current = format(result),
                expression = format(acc) + " " + glyph(op) + " " + format(b) + " =",
                accumulator = null,
                operator = null,
                fresh = true,
                lastOperator = op,
                lastOperand = b,
            )
        }

        val op = state.operator
        if (op != null && !state.fresh) {
            val acc = state.accumulator ?: 0.0
            val result = apply(acc, op, b)
            return state.copy(
                current = format(result),
                accumulator = result,
                operator = k,
                fresh = true,
                expression = format(result) + " " + glyph(k),
            )
        }
        return state.copy(
            accumulator = b,
            operator = k,
            fresh = true,
            expression = format(b) + " " + glyph(k),
        )
    }

    private fun apply(a: Double, op: String, b: Double): Double = when (op) {
        "+" -> a + b
        "−" -> a - b
        "×" -> a * b
        "÷" -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    private fun glyph(x: String): String = when (x) {
        "÷" -> "/"
        "−" -> "–"
        else -> x
    }

    fun format(n: Double): String {
        if (n.isNaN() || n.isInfinite()) return ERROR
        // Locale.US: в русской локали "%.5e" даёт «1,23456e», и запятая ломает движок.
        if (abs(n) >= 1e12) return "%.5e".format(java.util.Locale.US, n).replace("e+", "e")
        val rounded = (n * 1e9).roundToLong() / 1e9
        // JavaScript prints whole numbers without a trailing decimal; match that.
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}

/** Compose wrapper around [XCalcEngine]. */
@Stable
internal class XCalculatorState {
    private var snapshot by mutableStateOf(XCalcSnapshot())

    val current: String get() = snapshot.current
    val expression: String get() = snapshot.expression

    fun key(k: String) {
        snapshot = XCalcEngine.key(snapshot, k)
    }

    /** True while `k` is the pending operator — the canvas keeps that key lit. */
    fun isLiveOperator(k: String): Boolean = snapshot.operator == k
}

private enum class XCalcKind { DIGIT, OP, FN, EQ }

private data class XCalcKey(val label: String, val kind: XCalcKind = XCalcKind.DIGIT, val backspace: Boolean = false)

private val X_CALC_KEYS = listOf(
    XCalcKey("AC", XCalcKind.FN), XCalcKey("⌦", XCalcKind.FN, backspace = true),
    XCalcKey("%", XCalcKind.OP), XCalcKey("÷", XCalcKind.OP),
    XCalcKey("7"), XCalcKey("8"), XCalcKey("9"), XCalcKey("×", XCalcKind.OP),
    XCalcKey("4"), XCalcKey("5"), XCalcKey("6"), XCalcKey("−", XCalcKind.OP),
    XCalcKey("1"), XCalcKey("2"), XCalcKey("3"), XCalcKey("+", XCalcKind.OP),
    XCalcKey("±", XCalcKind.OP), XCalcKey("0"), XCalcKey("."), XCalcKey("=", XCalcKind.EQ),
)

/** «калькулятор» — the expression line, the result, and the 4-column keypad. */
@Composable
internal fun XCalculatorScreen(onBack: () -> Unit) {
    val state = remember { XCalculatorState() }
    // The canvas shrinks the result as it grows so long numbers stay on one line.
    val resultSize = when {
        state.current.length > 12 -> 30.sp
        state.current.length > 9 -> 38.sp
        state.current.length > 6 -> 48.sp
        else -> 60.sp
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        XScreenHeader(title = xSettingText("app_calc"), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                state.expression,
                style = XType.meta.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = .02.em),
                color = XMuted,
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                state.current,
                style = XType.metric.copy(fontSize = resultSize, letterSpacing = (-.035).em),
                color = XText,
                textAlign = TextAlign.End,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            userScrollEnabled = false,
        ) {
            items(X_CALC_KEYS) { key ->
                val live = key.kind == XCalcKind.OP && state.isLiveOperator(key.label)
                val filled = key.kind == XCalcKind.EQ || live
                val background = when {
                    filled -> XText
                    key.kind == XCalcKind.OP || key.kind == XCalcKind.FN -> XSurfaceRaised
                    else -> XSurface
                }
                val foreground = when {
                    filled -> XInverse
                    key.kind == XCalcKind.FN -> XMuted
                    else -> XText
                }
                val shape = RoundedCornerShape(20.dp)
                // Only the raised keys carry the drop shadow in the canvas.
                val base = if (key.kind == XCalcKind.OP || key.kind == XCalcKind.FN) Modifier else Modifier.xShadow(shape)
                Box(
                    base
                        .height(58.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(background)
                        .clickable { state.key(key.label) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (key.backspace) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Backspace,
                            xSettingText("calc_backspace"),
                            tint = foreground,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Text(
                            if (key.label == "÷") "/" else key.label,
                            style = XType.rowTitle.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = when {
                                    key.kind == XCalcKind.OP || key.kind == XCalcKind.EQ -> 26.sp
                                    key.label == "AC" -> 19.sp
                                    else -> 22.sp
                                },
                            ),
                            color = foreground,
                        )
                    }
                }
            }
        }
    }
}
