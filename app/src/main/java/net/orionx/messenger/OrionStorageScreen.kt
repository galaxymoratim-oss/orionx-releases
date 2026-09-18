package net.orionx.messenger

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.CloudSession
import net.orionx.messenger.cloud.SupabaseApi
import java.io.File

/*
 * The canvas `storage` screen — «данные и память».
 *
 * The canvas fills it with invented gigabytes. Here the numbers are measured off the
 * device: the app's own cache and files directories are walked and bucketed by file
 * type, so the bar and the legend describe what is actually on disk.
 */

private object XStgType {
    /** `font:800 19px/1;letter-spacing:-.01em` */
    val title = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = (-.01).em)
    /** `font:800 34px/1;letter-spacing:-.03em` — the total. */
    val total = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-.03).em)
    /** `font:700 12px/1` — «занято». */
    val totalCaption = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    /** `font:800 10px;letter-spacing:.16em;uppercase` — a card label. */
    val cardLabel = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = .16.em)
    /** `font:700 13px/1` — a legend name. */
    val legendName = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    /** `font:700 11px/1` — a legend percentage. */
    val legendPct = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    /** `font:700 12px/1` — a legend size. */
    val legendSize = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    /** `font:800 10.5px;letter-spacing:.12em;uppercase` — the clear button. */
    val button = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = .12.em)
    /** `font:700 13.5px/1` — the auto-delete row. */
    val row = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    /** `font:700 12px/1` — a row value. */
    val rowValue = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
}

/** `transition:width .68s cubic-bezier(.22,1,.28,1)` on the bar. */
private val XStgEasing = CubicBezierEasing(.22f, 1f, .28f, 1f)

/** The canvas' four buckets and their colours. «другое» uses `var(--mut2)`. */
private class XStorageBucket(val labelKey: String, val color: Color?, val bytes: Long)

/** `удалять медиа автоматически` cycles through these. */
private val XAutoDeleteOptions = listOf("stg_auto_never", "stg_auto_week", "stg_auto_month")

@Composable
internal fun XStorageScreen(
    api: SupabaseApi,
    session: CloudSession,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var buckets by remember { mutableStateOf<List<XStorageBucket>>(emptyList()) }
    var cleared by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var autoDelete by remember(context) { mutableIntStateOf(xLoadAutoDelete(context)) }

    suspend fun measure() {
        buckets = withContext(Dispatchers.IO) { xMeasureStorage(context) }
    }

    LaunchedEffect(Unit) {
        measure()
        // `setTimeout(… , 90)` — the canvas lets the card land, then grows the bar.
        delay(90)
        revealed = true
    }

    val total = buckets.sumOf { it.bytes }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            XCircleButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = tr("back"),
                size = 42.dp,
                iconSize = 20.dp,
                onClick = onBack,
            )
            Text(xSettingText("data_row"), style = XStgType.title, color = XText, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // `border-radius:28px;padding:22px 20px`
            Column(
                Modifier
                    .fillMaxWidth()
                    .xShadow(RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(XSurface)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(xFormatStorage(total), style = XStgType.total, color = XText)
                    Text(
                        xSettingText("stg_used"),
                        style = XStgType.totalCaption,
                        color = XMuted2,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }

                // `height:12px;gap:3px;border-radius:999px` over an `--sf2` track.
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(XSurfaceRaised),
                ) {
                    val trackWidth = maxWidth
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        buckets.forEachIndexed { index, bucket ->
                            val share = if (total == 0L) 0f else bucket.bytes.toFloat() / total
                            val grown by animateFloatAsState(
                                targetValue = if (revealed) share else 0f,
                                animationSpec = tween(680, delayMillis = index * 95, easing = XStgEasing),
                                label = "stg-seg",
                            )
                            Box(
                                Modifier
                                    .width(trackWidth * grown)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(bucket.color ?: XMuted2),
                            )
                        }
                    }
                }

                // Legend rows rise 12px into place, staggered by 85 ms.
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    buckets.forEachIndexed { index, bucket ->
                        val appear by animateFloatAsState(
                            targetValue = if (revealed) 1f else 0f,
                            animationSpec = tween(460, delayMillis = index * 85, easing = XStgEasing),
                            label = "stg-legend",
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp)
                                .graphicsLayer {
                                    alpha = appear
                                    translationY = (1f - appear) * 12.dp.toPx()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(bucket.color ?: XMuted2),
                            )
                            Text(
                                xSettingText(bucket.labelKey),
                                style = XStgType.legendName,
                                color = XText,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (total == 0L) "0%" else "${(bucket.bytes * 100 / total)}%",
                                style = XStgType.legendPct,
                                color = XMuted3,
                            )
                            Text(
                                if (bucket.bytes == 0L) xSettingText("stg_emptied") else xFormatStorage(bucket.bytes),
                                style = XStgType.legendSize,
                                color = XMuted,
                                textAlign = TextAlign.End,
                                modifier = Modifier.widthIn(min = 56.dp),
                            )
                        }
                    }
                }
            }

            // `кэш`
            Column(
                Modifier
                    .fillMaxWidth()
                    .xShadow(RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(XSurface)
                    .padding(20.dp),
            ) {
                Text(
                    xSettingText("stg_cache").uppercase(),
                    style = XStgType.cardLabel,
                    color = XMuted2,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                // `height:48px;border-radius:999px;background:var(--txt)`
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(XText)
                        .xPressable(pressedScale = .98f, enabled = !busy) {
                            busy = true
                            scope.launch {
                                xClearAppCache(context)
                                cleared = true
                                measure()
                                busy = false
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Outlined.CleaningServices, null, tint = XInverse, modifier = Modifier.size(18.dp))
                    Text(xSettingText("stg_clear").uppercase(), style = XStgType.button, color = XInverse)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .xPressable {
                            autoDelete = (autoDelete + 1) % XAutoDeleteOptions.size
                            xSaveAutoDelete(context, autoDelete)
                        }
                        .padding(top = 16.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Outlined.AutoDelete, null, tint = XText, modifier = Modifier.size(20.dp))
                    Text(xSettingText("stg_auto"), style = XStgType.row, color = XText, modifier = Modifier.weight(1f))
                    Text(xSettingText(XAutoDeleteOptions[autoDelete]), style = XStgType.rowValue, color = XMuted)
                }
            }

            if (cleared) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = XGreen, modifier = Modifier.size(18.dp))
                    Text(xSettingText("stg_cleared_note"), style = XStgType.rowValue, color = XMuted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Measurement
// ---------------------------------------------------------------------------

private val XImageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
private val XVideoExt = setOf("mp4", "mkv", "webm", "3gp", "mov")
private val XAudioExt = setOf("m4a", "ogg", "opus", "mp3", "aac", "wav", "amr")
private val XStickerExt = setOf("tgs", "webp_sticker", "json")

/**
 * Walks the app's own storage and buckets it the way the canvas labels it. Nothing
 * outside the app is touched or measured.
 */
private fun xMeasureStorage(context: Context): List<XStorageBucket> {
    var media = 0L
    var voice = 0L
    var stickers = 0L
    var other = 0L

    fun walk(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { walk(it) }
            return
        }
        val size = file.length()
        val ext = file.extension.lowercase()
        when {
            ext in XImageExt || ext in XVideoExt -> media += size
            ext in XAudioExt -> voice += size
            ext in XStickerExt -> stickers += size
            else -> other += size
        }
    }

    listOfNotNull(context.cacheDir, context.filesDir, context.externalCacheDir)
        .forEach { runCatching { walk(it) } }

    return listOf(
        XStorageBucket("stg_media", Color(0xFFFFB48A), media),
        XStorageBucket("stg_voice", Color(0xFF8FA8FF), voice),
        XStorageBucket("stg_stickers", Color(0xFFE39BD8), stickers),
        XStorageBucket("stg_other", null, other),
    )
}

private suspend fun xClearAppCache(context: Context) = withContext(Dispatchers.IO) {
    listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { root ->
        root.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
    }
    XImageCache.clear()
}

/** «4,6 гб» / «812 мб» — the canvas writes sizes with a comma. */
internal fun xFormatStorage(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 ->
        "%.1f гб".format(bytes / (1024f * 1024f * 1024f)).replace('.', ',')
    bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} мб"
    bytes >= 1024L -> "${bytes / 1024} кб"
    else -> "$bytes б"
}

private fun xLoadAutoDelete(context: Context): Int =
    context.getSharedPreferences("orionx_redesign", Context.MODE_PRIVATE)
        .getInt("stg_auto_delete", 0).coerceIn(0, XAutoDeleteOptions.lastIndex)

private fun xSaveAutoDelete(context: Context, mode: Int) {
    context.getSharedPreferences("orionx_redesign", Context.MODE_PRIVATE)
        .edit().putInt("stg_auto_delete", mode).apply()
}
