package net.orionx.messenger

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Stopwatch laps, newest first, the way the canvas lists them. */
internal data class XLap(val number: Int, val totalMs: Long, val splitMs: Long)

/** `12:34` plus the hundredths shown in the smaller muted type. */
private fun clockMain(ms: Long): String {
    val total = ms / 1000
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes >= 60) {
        "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun clockCentis(ms: Long): String = ".%02d".format((ms % 1000) / 10)

/** `00:05:00` for the countdown face. */
private fun countdownText(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%02d:%02d:%02d".format(total / 3600, (total / 60) % 60, total % 60)
}

@Composable
internal fun XTimerScreen(
    notes: XNotesStore,
    onBack: () -> Unit,
) {
    var stopwatchTab by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        XScreenHeader(title = xSettingText("app_timer"), onBack = onBack)
        Column(
            Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // «секундомер / таймер» — the canvas uses a slightly rounder segmented control here.
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(XSurfaceRaised).padding(5.dp),
            ) {
                XSegmentedTabs(
                    labels = listOf(xSettingText("stopwatch"), xSettingText("app_timer")),
                    selected = if (stopwatchTab) 0 else 1,
                    onSelect = { stopwatchTab = it == 0 },
                    modifier = Modifier.height(38.dp),
                )
            }
            if (stopwatchTab) XStopwatch(notes) else XCountdown()
        }
    }
}

/** The stopwatch face, its laps, and «сохранить круг в блокнот». */
@Composable
private fun XStopwatch(notes: XNotesStore) {
    var running by rememberSaveable { mutableStateOf(false) }
    var accumulated by rememberSaveable { mutableLongStateOf(0L) }
    // startedAt — снапшот elapsedRealtime, его нельзя сохранять между процессами:
    // после восстановления отсчёт ребейсится от текущего времени.
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var laps by remember { mutableStateOf<List<XLap>>(emptyList()) }
    var pendingLap by remember { mutableStateOf<XLap?>(null) }
    var savedToast by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (running && startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
        while (running) {
            elapsed = accumulated + (SystemClock.elapsedRealtime() - startedAt)
            delay(31)
        }
        if (!running) elapsed = accumulated
    }

    LaunchedEffect(savedToast) {
        if (savedToast) {
            delay(2200)
            savedToast = false
        }
    }

    val lapWord = xSettingText("lap")
    val stopwatchWord = xSettingText("stopwatch")

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                XTimerRing(progress = (elapsed % 60_000L) / 60_000f) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            clockMain(elapsed),
                            style = XType.metric.copy(fontSize = 48.sp, letterSpacing = (-.045).em),
                            color = XText,
                        )
                        Text(
                            clockCentis(elapsed),
                            style = XType.metric.copy(fontSize = 24.sp, letterSpacing = (-.03).em),
                            color = XMuted,
                        )
                    }
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(laps, key = { it.number }) { lap ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(XSurface)
                            .clickable { pendingLap = lap }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "${xSettingText("lap")} ${lap.number}",
                            style = XType.rowTitle.copy(fontSize = 13.sp),
                            color = XText,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            clockMain(lap.splitMs) + clockCentis(lap.splitMs),
                            style = XType.meta.copy(fontWeight = FontWeight.Bold),
                            color = XMuted,
                        )
                        Text(
                            clockMain(lap.totalMs) + clockCentis(lap.totalMs),
                            style = XType.rowTitle.copy(fontSize = 13.sp),
                            color = XText,
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                XTimerButton(
                    label = if (running) xSettingText("lap") else xSettingText("reset_action"),
                    filled = false,
                    modifier = Modifier.weight(1f),
                ) {
                    if (running) {
                        val previous = laps.firstOrNull()?.totalMs ?: 0L
                        laps = listOf(XLap(laps.size + 1, elapsed, elapsed - previous)) + laps
                    } else {
                        accumulated = 0L
                        elapsed = 0L
                        laps = emptyList()
                    }
                }
                XTimerButton(
                    label = if (running) xSettingText("pause_action") else xSettingText("start_action"),
                    filled = !running,
                    modifier = Modifier.weight(1.3f),
                ) {
                    if (running) {
                        accumulated += SystemClock.elapsedRealtime() - startedAt
                        elapsed = accumulated
                        running = false
                    } else {
                        startedAt = SystemClock.elapsedRealtime()
                        running = true
                    }
                }
            }
        }

        pendingLap?.let { lap ->
            XLapNoteDialog(
                lap = lap,
                onDismiss = { pendingLap = null },
                onSave = { title ->
                    notes.add(
                        title = title.ifBlank { "$lapWord ${lap.number}" },
                        text = "$lapWord ${lap.number} — ${clockMain(lap.totalMs)}${clockCentis(lap.totalMs)}",
                        source = stopwatchWord,
                    )
                    pendingLap = null
                    savedToast = true
                },
            )
        }

        if (savedToast) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(XText)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(xSettingText("lap_saved"), style = XType.rowTitle.copy(fontSize = 12.5.sp), color = XInverse)
            }
        }
    }
}

/** The countdown tab: ring, custom hh:mm:ss, presets, reset / start. */
@Composable
private fun XCountdown() {
    val context = LocalContext.current
    var target by rememberSaveable { mutableLongStateOf(300_000L) }
    var left by rememberSaveable { mutableLongStateOf(300_000L) }
    var running by rememberSaveable { mutableStateOf(false) }
    var endAt by rememberSaveable { mutableLongStateOf(0L) }
    var hh by remember { mutableStateOf("") }
    var mm by remember { mutableStateOf("") }
    var ss by remember { mutableStateOf("") }

    LaunchedEffect(running) {
        while (running) {
            val remaining = endAt - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                left = 0
                running = false
                vibrateDone(context)
                break
            }
            left = remaining
            delay(100)
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Как в макете: панели («своё время» и пресеты) выезжают по нажатию на круг.
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.clickable(enabled = !running) { settingsOpen = !settingsOpen }) {
                XTimerRing(progress = if (target > 0) (left.toFloat() / target).coerceIn(0f, 1f) else 0f) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            countdownText(left),
                            style = XType.metric.copy(fontSize = 44.sp, letterSpacing = (-.045).em),
                            color = XText,
                        )
                        Text(
                            if (running) xSettingText("timer_running") else xSettingText("timer_pick"),
                            style = XType.meta.copy(fontWeight = FontWeight.Bold, letterSpacing = .02.em),
                            color = XMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = settingsOpen,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .xShadow(RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(XSurface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            Text(
                xSettingText("custom_time").uppercase(),
                style = XType.sectionLabel.copy(fontSize = 10.sp, lineHeight = 13.sp),
                color = XMuted,
                modifier = Modifier.width(46.dp),
            )
            XTimeField(hh, { hh = it }, "00", Modifier.weight(1f))
            Text(":", style = XType.rowTitle.copy(fontSize = 15.sp), color = XMuted)
            XTimeField(mm, { mm = it }, "05", Modifier.weight(1f))
            Text(":", style = XType.rowTitle.copy(fontSize = 15.sp), color = XMuted)
            XTimeField(ss, { ss = it }, "00", Modifier.weight(1f))
            Box(
                Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(XText)
                    .clickable {
                        val ms = (hh.toIntOrNull() ?: 0).coerceAtMost(99) * 3_600_000L +
                            (mm.toIntOrNull() ?: 0).coerceAtMost(59) * 60_000L +
                            (ss.toIntOrNull() ?: 0).coerceAtMost(59) * 1_000L
                        if (ms > 0) {
                            target = ms
                            left = ms
                            running = false
                            hh = ""; mm = ""; ss = ""
                            settingsOpen = false
                        }
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(xSettingText("apply_action"), style = XType.rowTitle.copy(fontSize = 11.5.sp), color = XInverse)
            }
        }

        FlowRowPresets(
            presets = listOf(
                xSettingText("min_1") to 60_000L,
                xSettingText("min_3") to 180_000L,
                xSettingText("min_5") to 300_000L,
                xSettingText("min_10") to 600_000L,
                xSettingText("min_25") to 1_500_000L,
            ),
            selected = if (running) -1L else target,
            onPick = {
                target = it
                left = it
                running = false
                settingsOpen = false
            },
        )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            XTimerButton(xSettingText("reset_action"), filled = false, modifier = Modifier.weight(1f)) {
                running = false
                left = target
            }
            XTimerButton(
                label = when {
                    running -> xSettingText("pause_action")
                    left in 1 until target -> xSettingText("resume_action")
                    else -> xSettingText("start_action")
                },
                filled = !running,
                modifier = Modifier.weight(1.3f),
            ) {
                if (running) {
                    running = false
                } else {
                    val start = if (left > 0) left else target
                    if (start > 0) {
                        endAt = SystemClock.elapsedRealtime() + start
                        left = start
                        running = true
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowRowPresets(presets: List<Pair<String, Long>>, selected: Long, onPick: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        presets.forEach { (label, value) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) XText else XSurfaceRaised)
                    .clickable { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = XType.rowTitle.copy(fontSize = 12.5.sp),
                    color = if (active) XInverse else XText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun XTimeField(value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Box(
        modifier.height(42.dp).clip(RoundedCornerShape(14.dp)).background(XSurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = XType.rowTitle.copy(fontSize = 17.sp), color = XMuted)
        }
        BasicTextField(
            value = value,
            onValueChange = { onValue(it.filter(Char::isDigit).take(2)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = XType.rowTitle.copy(fontSize = 17.sp, color = XText, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(XAccent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun XTimerButton(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(56.dp)
            .then(if (filled) Modifier.xShadow(RoundedCornerShape(20.dp), 14.dp) else Modifier)
            .clip(RoundedCornerShape(20.dp))
            .background(if (filled) XText else XSurfaceRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = XType.rowTitle.copy(fontSize = 13.sp, letterSpacing = .02.em),
            color = if (filled) XInverse else XMuted,
        )
    }
}

/** The 288 dp ring: a track plus a gradient arc, drawn from the top like the canvas SVG. */
@Composable
private fun XTimerRing(progress: Float, content: @Composable () -> Unit) {
    Box(Modifier.size(288.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = XSurfaceRaised,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke),
            )
            if (progress > 0f) {
                drawArc(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF8FB4FF),
                            .45f to Color(0xFFB79CFF),
                            1f to Color(0xFFFF9FC4),
                        ),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
private fun XLapNoteDialog(lap: XLap, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .xShadow(RoundedCornerShape(26.dp), 24.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(XSurface)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    xSettingText("note_title_prompt"),
                    style = XType.screenTitle.copy(fontSize = 16.sp, letterSpacing = (-.02).em),
                    color = XText,
                )
                Text(
                    "${xSettingText("lap")} ${lap.number} — ${clockMain(lap.totalMs)}${clockCentis(lap.totalMs)}",
                    style = XType.meta.copy(fontSize = 11.5.sp),
                    color = XMuted,
                )
            }
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(XSurfaceRaised)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (title.isEmpty()) {
                    Text(xSettingText("note_title_example"), style = XType.rowTitle.copy(fontSize = 14.sp), color = XMuted)
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = XType.rowTitle.copy(fontSize = 14.sp, color = XText),
                    cursorBrush = SolidColor(XAccent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(18.dp)).background(XSurfaceRaised)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text(xSettingText("cancel_action"), style = XType.rowTitle.copy(fontSize = 12.5.sp), color = XMuted) }
                Box(
                    Modifier.weight(1.4f).height(50.dp).clip(RoundedCornerShape(18.dp)).background(XText)
                        .clickable { onSave(title) },
                    contentAlignment = Alignment.Center,
                ) { Text(xSettingText("save_action"), style = XType.rowTitle.copy(fontSize = 12.5.sp), color = XInverse) }
            }
        }
    }
}

/** A short buzz when the countdown reaches zero. */
private fun vibrateDone(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 140, 220), -1))
    }
}
