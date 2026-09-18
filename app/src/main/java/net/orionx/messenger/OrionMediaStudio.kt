@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package net.orionx.messenger

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class OrionEditedMedia(
    val file: File,
    val displayName: String,
    val mimeType: String,
)

private enum class OrionCropPreset(val label: String, val ratio: Float?) {
    ORIGINAL("Оригинал", null),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    STORY("9:16", 9f / 16f),
    LANDSCAPE("16:9", 16f / 9f),
}

private enum class PhotoTool { DRAW, CROP, EMOJI }
private enum class OrionViewerKind { IMAGE, VIDEO, AUDIO, FILE }

private data class NormalizedStroke(val points: List<Offset>)
private data class EmojiObject(val value: String, val position: Offset)

private val StudioBackground = Color(0xFF030512)
private val StudioSurface = Color(0xF20B0E22)
private val StudioSoft = Color(0xE8121630)
private val StudioBorder = Color(0xFF302B5B)
private val StudioPurple = Color(0xFF733DFF)
private val StudioViolet = Color(0xFF9A5BFF)
private val StudioText = Color(0xFFF7F4FF)
private val StudioMuted = Color(0xFFA39DB8)
private val StudioGreen = Color(0xFF37D39A)

@Composable
internal fun OrionPhotoEditorDialog(
    source: Uri,
    onDismiss: () -> Unit,
    onEdited: (OrionEditedMedia) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var tool by remember { mutableStateOf(PhotoTool.DRAW) }
    var crop by remember { mutableStateOf(OrionCropPreset.ORIGINAL) }
    var strokes by remember { mutableStateOf<List<NormalizedStroke>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var emojis by remember { mutableStateOf<List<EmojiObject>>(emptyList()) }
    var selectedEmoji by remember { mutableStateOf("✨") }
    var exporting by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        bitmap = runCatching { withContext(Dispatchers.IO) { decodeBitmap(context, source) } }
            .onFailure { onError("Не удалось открыть фотографию") }
            .getOrNull()
    }

    Dialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !exporting, dismissOnClickOutside = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(StudioBackground).statusBarsPadding().navigationBarsPadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, enabled = !exporting) {
                        Icon(Icons.Outlined.Close, "Закрыть", tint = StudioText)
                    }
                    Text("Редактирование фото", color = StudioText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Surface(
                        onClick = {
                            val sourceBitmap = bitmap
                            if (sourceBitmap != null && !exporting) {
                                exporting = true
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.Default) {
                                            exportEditedPhoto(context, sourceBitmap, crop, strokes, currentStroke, emojis)
                                        }
                                    }.onSuccess(onEdited)
                                        .onFailure { onError("Не удалось сохранить отредактированное фото") }
                                    exporting = false
                                }
                            }
                        },
                        enabled = bitmap != null && !exporting,
                        color = StudioPurple,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (exporting) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Outlined.Check, "Готово", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }

                val targetRatio = crop.ratio ?: bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().aspectRatio(targetRatio.coerceIn(.45f, 2.2f)).clip(RoundedCornerShape(24.dp))
                            .background(StudioSoft).border(1.dp, StudioBorder, RoundedCornerShape(24.dp)),
                    ) {
                        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                        bitmap?.let { image ->
                            Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = StudioPurple)
                        }

                        Canvas(
                            Modifier.fillMaxSize()
                                .pointerInput(tool, selectedEmoji, widthPx, heightPx) {
                                    if (tool == PhotoTool.DRAW) {
                                        detectDragGestures(
                                            onDragStart = { start -> currentStroke = listOf(Offset(start.x / widthPx, start.y / heightPx)) },
                                            onDrag = { change, _ ->
                                                currentStroke = currentStroke + Offset(change.position.x / widthPx, change.position.y / heightPx)
                                            },
                                            onDragEnd = {
                                                if (currentStroke.size > 1) strokes = strokes + NormalizedStroke(currentStroke)
                                                currentStroke = emptyList()
                                            },
                                            onDragCancel = { currentStroke = emptyList() },
                                        )
                                    }
                                }
                                .pointerInput(tool, selectedEmoji, widthPx, heightPx) {
                                    if (tool == PhotoTool.EMOJI) {
                                        detectTapGestures { point ->
                                            emojis = emojis + EmojiObject(selectedEmoji, Offset(point.x / widthPx, point.y / heightPx))
                                        }
                                    }
                                },
                        ) {
                            val allStrokes = strokes.map { it.points } + listOf(currentStroke).filter { it.isNotEmpty() }
                            allStrokes.forEach { normalized ->
                                for (i in 1 until normalized.size) {
                                    drawLine(
                                        StudioViolet,
                                        start = Offset(normalized[i - 1].x * size.width, normalized[i - 1].y * size.height),
                                        end = Offset(normalized[i].x * size.width, normalized[i].y * size.height),
                                        strokeWidth = 9f,
                                        cap = StrokeCap.Round,
                                    )
                                }
                            }
                            emojis.forEach { objectItem ->
                                drawContext.canvas.nativeCanvas.drawText(
                                    objectItem.value,
                                    objectItem.position.x * size.width,
                                    objectItem.position.y * size.height,
                                    Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = min(size.width, size.height) * .11f },
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = StudioSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StudioToolButton(Icons.Outlined.Draw, "Рисовать", tool == PhotoTool.DRAW) { tool = PhotoTool.DRAW }
                            StudioToolButton(Icons.Outlined.Crop, "Кадрировать", tool == PhotoTool.CROP) { tool = PhotoTool.CROP }
                            StudioToolButton(Icons.Outlined.EmojiEmotions, "Объекты", tool == PhotoTool.EMOJI) { tool = PhotoTool.EMOJI }
                            StudioToolButton(Icons.Outlined.Undo, "Очистить", false) {
                                strokes = emptyList(); currentStroke = emptyList(); emojis = emptyList()
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (tool == PhotoTool.CROP) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                OrionCropPreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = crop == preset,
                                        onClick = { crop = preset },
                                        label = { Text(preset.label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StudioPurple, selectedLabelColor = Color.White),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        } else if (tool == PhotoTool.EMOJI) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf("✨", "❤️", "🚀", "😂", "🔥", "👍").forEach { emoji ->
                                    Surface(
                                        color = if (selectedEmoji == emoji) StudioPurple.copy(alpha = .45f) else StudioSoft,
                                        shape = CircleShape,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedEmoji == emoji) StudioViolet else StudioBorder),
                                        modifier = Modifier.size(46.dp),
                                        onClick = { selectedEmoji = emoji },
                                    ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 23.sp) } }
                                }
                            }
                            Text("Выберите объект и коснитесь фотографии, чтобы добавить его.", color = StudioMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Text("Проведите пальцем по фотографии. Кнопка «Очистить» удаляет все правки.", color = StudioMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
        Surface(
            onClick = onClick,
            color = if (selected) StudioPurple.copy(alpha = .45f) else StudioSoft,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) StudioViolet else StudioBorder),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.size(48.dp),
        ) { Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = if (selected) Color.White else StudioMuted) } }
        Text(label, color = if (selected) StudioViolet else StudioMuted, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun OrionVideoEditorDialog(
    source: Uri,
    onDismiss: () -> Unit,
    onEdited: (OrionEditedMedia) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    var durationMs by remember(source) { mutableLongStateOf(1L) }
    var trim by remember(source) { mutableStateOf(0f..1f) }
    var crop by remember { mutableStateOf(OrionCropPreset.ORIGINAL) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }

    // Player is hosted here so playback controls can live BELOW the video instead of
    // being drawn on top of it (the default PlayerView controller covers the frame).
    val player = remember(source) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(source)); prepare(); playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(200)
        }
    }

    LaunchedEffect(source) {
        durationMs = withContext(Dispatchers.IO) { readMediaDuration(context, source).coerceAtLeast(1_000L) }
        trim = 0f..(durationMs / 1000f)
    }
    DisposableEffect(Unit) { onDispose { runCatching { transformer?.cancel() } } }

    Dialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !exporting, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxSize().background(StudioBackground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, enabled = !exporting) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", tint = StudioText) }
                Column(Modifier.weight(1f)) {
                    Text("Редактирование видео", color = StudioText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Обрезка и кадрирование", color = StudioMuted, fontSize = 11.sp)
                }
                IconButton(onClick = onDismiss, enabled = !exporting) { Icon(Icons.Outlined.Close, "Закрыть", tint = StudioMuted) }
            }

            Surface(
                color = Color.Black,
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp),
            ) {
                OrionVideoPreview(player, crop.ratio)
            }

            // Compact transport bar — sits under the video, never over it.
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(color = StudioPurple, shape = CircleShape, onClick = {
                    if (player.isPlaying) player.pause() else {
                        if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) player.seekTo(0)
                        player.play()
                    }
                }, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Text(formatStudioTime(positionMs), color = StudioMuted, fontSize = 11.sp)
                Slider(
                    value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { positionMs = it.toLong() },
                    onValueChangeFinished = { player.seekTo(positionMs) },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(thumbColor = StudioViolet, activeTrackColor = StudioViolet, inactiveTrackColor = StudioBorder),
                    modifier = Modifier.weight(1f),
                )
                Text(formatStudioTime(durationMs), color = StudioMuted, fontSize = 11.sp)
            }

            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ContentCut, null, tint = StudioViolet, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Обрезка", color = StudioText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${formatStudioTime((trim.start * 1000).toLong())} – ${formatStudioTime((trim.endInclusive * 1000).toLong())}", color = StudioViolet, fontSize = 12.sp)
                    }
                    RangeSlider(
                        value = trim,
                        onValueChange = { range ->
                            val totalSeconds = max(1f, durationMs / 1000f)
                            val minGap = min(1f, totalSeconds)
                            val start = range.start.coerceIn(0f, (totalSeconds - minGap).coerceAtLeast(0f))
                            val end = range.endInclusive.coerceIn(start + minGap, totalSeconds)
                            trim = start..end
                        },
                        valueRange = 0f..max(1f, durationMs / 1000f),
                        enabled = !exporting,
                    )
                    Text("Кадрирование", color = StudioText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OrionCropPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = crop == preset,
                                onClick = { crop = preset },
                                label = { Text(preset.label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StudioPurple, selectedLabelColor = Color.White),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (exporting) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { exportProgress / 100f },
                            color = StudioViolet,
                            trackColor = StudioBorder,
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        )
                        Text("Подготовка видео: $exportProgress%", color = StudioMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            player.pause()
                            exporting = true
                            exportProgress = 0
                            val output = File(context.cacheDir, "edited_video_${System.currentTimeMillis()}.mp4")
                            val clipping = MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs((trim.start * 1000).toLong())
                                .setEndPositionMs((trim.endInclusive * 1000).toLong())
                                .build()
                            val item = MediaItem.Builder().setUri(source).setClippingConfiguration(clipping).build()
                            val videoEffects = crop.ratio?.let {
                                listOf(Presentation.createForAspectRatio(it, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
                            }.orEmpty()
                            val edited = EditedMediaItem.Builder(item).setEffects(Effects(emptyList(), videoEffects)).build()
                            val built = Transformer.Builder(context)
                                .addListener(object : Transformer.Listener {
                                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                        exporting = false
                                        onEdited(OrionEditedMedia(output, "video_${System.currentTimeMillis()}.mp4", "video/mp4"))
                                    }

                                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                        exporting = false
                                        output.delete()
                                        onError("Не удалось обработать видео: ${exportException.message ?: "ошибка кодека"}")
                                    }
                                }).build()
                            transformer = built
                            runCatching { built.start(edited, output.absolutePath) }
                                .onFailure {
                                    exporting = false
                                    output.delete()
                                    onError("Не удалось начать обработку видео")
                                }
                        },
                        enabled = !exporting && durationMs > 1,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioPurple),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (exporting) "Обработка…" else "Готово", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    LaunchedEffect(exporting, transformer) {
        val active = transformer ?: return@LaunchedEffect
        val holder = ProgressHolder()
        while (exporting) {
            val state = runCatching { active.getProgress(holder) }.getOrDefault(Transformer.PROGRESS_STATE_UNAVAILABLE)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) exportProgress = holder.progress.coerceIn(0, 100)
            delay(300)
        }
    }
}

@Composable
private fun OrionVideoPreview(player: ExoPlayer, ratio: Float?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = if (ratio == null) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            update = { it.resizeMode = if (ratio == null) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM },
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
        )
    }
}

@Composable
internal fun OrionMediaPlayerDialog(
    url: String,
    title: String,
    mimeType: String,
    senderName: String = "",
    sentAt: String = "",
    onDismiss: () -> Unit,
    onForward: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val normalizedMime = mimeType.substringBefore(';').trim().lowercase()
    val normalizedTitle = title.substringBefore('?').substringBefore('#').lowercase()
    val normalizedUrlName = remember(url) {
        runCatching { Uri.parse(url).lastPathSegment.orEmpty().substringBefore('?').substringBefore('#').lowercase() }
            .getOrDefault("")
    }
    val viewerKind = remember(normalizedMime, normalizedTitle, normalizedUrlName) {
        detectViewerKind(normalizedMime, normalizedTitle, normalizedUrlName)
    }
    val isImage = viewerKind == OrionViewerKind.IMAGE
    val isVideo = viewerKind == OrionViewerKind.VIDEO
    val isAudio = viewerKind == OrionViewerKind.AUDIO
    val isPlayable = viewerKind == OrionViewerKind.VIDEO || viewerKind == OrionViewerKind.AUDIO
    var remoteImage by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val player = remember(url, isPlayable) {
        if (!isPlayable) null else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = isVideo
        }
    }
    var playing by remember(url) { mutableStateOf(isVideo) }
    var position by remember(url) { mutableLongStateOf(0L) }
    var duration by remember(url) { mutableLongStateOf(1L) }

    DisposableEffect(player) { onDispose { player?.release() } }
    LaunchedEffect(url, isImage) {
        if (isImage) {
            remoteImage = runCatching {
                withContext(Dispatchers.IO) {
                    java.net.URL(url).openStream().use { input -> BitmapFactory.decodeStream(input)?.asImageBitmap() }
                }
            }.getOrNull()
        }
    }
    LaunchedEffect(player) {
        val active = player ?: return@LaunchedEffect
        while (true) {
            playing = active.isPlaying
            position = active.currentPosition.coerceAtLeast(0L)
            duration = active.duration.takeIf { it > 0 } ?: 1L
            delay(250)
        }
    }

    fun saveMedia() {
        runCatching {
            val safeName = title.ifBlank {
                when {
                    isImage -> "orionx_photo_${System.currentTimeMillis()}.jpg"
                    isVideo -> "orionx_video_${System.currentTimeMillis()}.mp4"
                    isAudio -> "orionx_audio_${System.currentTimeMillis()}.m4a"
                    else -> "orionx_file_${System.currentTimeMillis()}"
                }
            }.replace(Regex("[^A-Za-zА-Яа-я0-9._-]"), "_").takeLast(100)
            val directory = when {
                isImage -> Environment.DIRECTORY_PICTURES
                isVideo -> Environment.DIRECTORY_MOVIES
                isAudio -> Environment.DIRECTORY_MUSIC
                else -> Environment.DIRECTORY_DOWNLOADS
            }
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(safeName)
                .setDescription("Сохранение из OrionX")
                .setMimeType(inferredMediaMime(normalizedMime, normalizedTitle, normalizedUrlName))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(directory, "OrionX/$safeName")
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(context, "Файл сохраняется", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Не удалось сохранить файл", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareMedia() {
        runCatching {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = inferredMediaMime(normalizedMime, normalizedTitle, normalizedUrlName)
                    .takeUnless { it == "application/octet-stream" }
                    ?: "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
                putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "Медиа из OrionX" })
            }
            context.startActivity(Intent.createChooser(share, "Отправить"))
        }.onFailure {
            Toast.makeText(context, "Не удалось открыть меню отправки", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(if (isImage || isVideo) Color.Black else StudioBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", tint = StudioText)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            senderName.ifBlank {
                                when {
                                    isImage -> "Фотография"
                                    isVideo -> "Видео"
                                    isAudio -> "Аудио"
                                    else -> "Файл"
                                }
                            },
                            color = StudioText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (sentAt.isNotBlank()) Text(sentAt, color = StudioMuted, fontSize = 11.sp)
                    }
                }

                when {
                    isImage -> {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            when (val image = remoteImage) {
                                null -> CircularProgressIndicator(color = StudioViolet)
                                else -> Image(
                                    image,
                                    contentDescription = title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    isVideo -> {
                        Surface(
                            color = Color.Black,
                            shape = RoundedCornerShape(22.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            AndroidView(
                                factory = { viewContext ->
                                    PlayerView(viewContext).apply {
                                        this.player = player
                                        useController = false
                                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        OrionPlaybackControls(player, playing, position, duration)
                    }
                    isAudio -> {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(
                                color = StudioSoft.copy(alpha = .90f),
                                shape = RoundedCornerShape(34.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                            ) {
                                Column(Modifier.padding(horizontal = 22.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier.size(150.dp).clip(CircleShape).background(StudioPurple.copy(alpha = .17f)).border(2.dp, StudioViolet.copy(alpha = .55f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Outlined.GraphicEq, null, tint = StudioViolet, modifier = Modifier.size(86.dp))
                                    }
                                    Spacer(Modifier.height(22.dp))
                                    Text(title.ifBlank { "Аудиосообщение" }, color = StudioText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(8.dp))
                                    Text("OrionX Music", color = StudioViolet, fontSize = 12.sp)
                                    Spacer(Modifier.height(18.dp))
                                    OrionPlaybackControls(player, playing, position, duration)
                                }
                            }
                        }
                    }
                    else -> {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(
                                color = StudioSoft.copy(alpha = .92f),
                                shape = RoundedCornerShape(30.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            ) {
                                Column(Modifier.padding(horizontal = 22.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)).background(StudioPurple.copy(alpha = .16f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Outlined.Description, null, tint = StudioViolet, modifier = Modifier.size(58.dp))
                                    }
                                    Spacer(Modifier.height(20.dp))
                                    Text(title.ifBlank { "Файл" }, color = StudioText, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Документ OrionX", color = StudioMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = StudioSurface.copy(alpha = .97f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OrionMediaAction(Icons.Outlined.Download, "Сохранить", ::saveMedia)
                        onForward?.let { forward -> OrionMediaAction(Icons.Outlined.Forward, "Переслать", forward) }
                        OrionMediaAction(Icons.Outlined.Share, "Отправить", ::shareMedia)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrionPlaybackControls(
    player: ExoPlayer?,
    playing: Boolean,
    position: Long,
    duration: Long,
) {
    val active = player ?: return
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Slider(
            value = dragFraction ?: (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
            onValueChange = { fraction -> dragFraction = fraction },
            onValueChangeFinished = {
                dragFraction?.let { active.seekTo((duration * it).toLong()) }
                dragFraction = null
            },
            colors = SliderDefaults.colors(thumbColor = StudioViolet, activeTrackColor = StudioPurple, inactiveTrackColor = StudioBorder),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatStudioTime(position), color = StudioMuted, fontSize = 11.sp)
            Text(formatStudioTime(duration), color = StudioMuted, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { active.seekTo((position - 10_000).coerceAtLeast(0L)) }) {
                Icon(Icons.Outlined.Replay10, "Назад 10 секунд", tint = StudioMuted, modifier = Modifier.size(28.dp))
            }
            Surface(
                onClick = { if (active.isPlaying) active.pause() else active.play() },
                color = StudioPurple,
                shape = CircleShape,
                modifier = Modifier.size(66.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Пауза" else "Воспроизвести", tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
            IconButton(onClick = { active.seekTo((position + 10_000).coerceAtMost(duration)) }) {
                Icon(Icons.Outlined.Forward10, "Вперёд 10 секунд", tint = StudioMuted, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun RowScope.OrionMediaAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, title, tint = StudioViolet, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, color = StudioText, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any(::endsWith)

private fun detectViewerKind(
    normalizedMime: String,
    normalizedTitle: String,
    normalizedUrlName: String,
): OrionViewerKind {
    val names = listOf(normalizedTitle, normalizedUrlName)
    fun anyName(vararg suffixes: String) = names.any { name -> name.endsWithAny(*suffixes) }

    // File extension wins over a stale or incorrect backend MIME value. This prevents a .png
    // attachment from ever being routed into OrionX Music/Video just because old metadata says audio/*.
    return when {
        anyName(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif") -> OrionViewerKind.IMAGE
        anyName(".mp4", ".webm", ".mkv", ".mov", ".3gp", ".m4v") -> OrionViewerKind.VIDEO
        anyName(".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".flac") -> OrionViewerKind.AUDIO
        normalizedMime.startsWith("image/") -> OrionViewerKind.IMAGE
        normalizedMime.startsWith("video/") -> OrionViewerKind.VIDEO
        normalizedMime.startsWith("audio/") -> OrionViewerKind.AUDIO
        else -> OrionViewerKind.FILE
    }
}

private fun inferredMediaMime(
    normalizedMime: String,
    normalizedTitle: String,
    normalizedUrlName: String = "",
): String {
    val names = listOf(normalizedTitle, normalizedUrlName)
    fun anyName(vararg suffixes: String) = names.any { name -> name.endsWithAny(*suffixes) }
    return when {
        anyName(".jpg", ".jpeg") -> "image/jpeg"
        anyName(".png") -> "image/png"
        anyName(".webp") -> "image/webp"
        anyName(".gif") -> "image/gif"
        anyName(".heic", ".heif") -> "image/heic"
        anyName(".mp4", ".m4v", ".mov") -> "video/mp4"
        anyName(".webm") -> "video/webm"
        anyName(".mp3") -> "audio/mpeg"
        anyName(".m4a", ".aac") -> "audio/mp4"
        anyName(".ogg") -> "audio/ogg"
        anyName(".opus") -> "audio/opus"
        normalizedMime.isNotBlank() && normalizedMime != "application/octet-stream" -> normalizedMime
        else -> "application/octet-stream"
    }
}

private suspend fun decodeBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ -> decoder.isMutableRequired = true }
    } else {
        context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) } ?: error("Empty image")
    }
}

private fun exportEditedPhoto(
    context: Context,
    source: Bitmap,
    crop: OrionCropPreset,
    strokes: List<NormalizedStroke>,
    currentStroke: List<Offset>,
    emojis: List<EmojiObject>,
): OrionEditedMedia {
    val target = centerCropBitmap(source, crop.ratio)
    val mutable = target.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(mutable)
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(154, 91, 255)
        style = Paint.Style.STROKE
        strokeWidth = min(mutable.width, mutable.height) * .014f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val all = strokes.map { it.points } + listOf(currentStroke).filter { it.isNotEmpty() }
    all.forEach { points ->
        for (index in 1 until points.size) {
            canvas.drawLine(
                points[index - 1].x * mutable.width,
                points[index - 1].y * mutable.height,
                points[index].x * mutable.width,
                points[index].y * mutable.height,
                linePaint,
            )
        }
    }
    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = min(mutable.width, mutable.height) * .12f
        textAlign = Paint.Align.CENTER
    }
    emojis.forEach { canvas.drawText(it.value, it.position.x * mutable.width, it.position.y * mutable.height, emojiPaint) }
    val output = File(context.cacheDir, "edited_photo_${System.currentTimeMillis()}.png")
    FileOutputStream(output).use { mutable.compress(Bitmap.CompressFormat.PNG, 96, it) }
    if (target !== source) target.recycle()
    if (mutable !== target) mutable.recycle()
    return OrionEditedMedia(output, "photo_${System.currentTimeMillis()}.png", "image/png")
}

private fun centerCropBitmap(source: Bitmap, targetRatio: Float?): Bitmap {
    val ratio = targetRatio ?: return source
    val sourceRatio = source.width.toFloat() / source.height.toFloat()
    val cropWidth: Int
    val cropHeight: Int
    if (sourceRatio > ratio) {
        cropHeight = source.height
        cropWidth = (cropHeight * ratio).toInt().coerceAtMost(source.width)
    } else {
        cropWidth = source.width
        cropHeight = (cropWidth / ratio).toInt().coerceAtMost(source.height)
    }
    val left = (source.width - cropWidth) / 2
    val top = (source.height - cropHeight) / 2
    return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
}

private fun readMediaDuration(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } finally {
        retriever.release()
    }
}

private fun formatStudioTime(milliseconds: Long): String {
    val total = (milliseconds.coerceAtLeast(0L) / 1000L)
    return "%02d:%02d".format(total / 60L, total % 60L)
}
