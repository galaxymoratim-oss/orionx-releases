@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package net.orionx.messenger.videonote

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import net.orionx.messenger.R
import net.orionx.messenger.XText

private val VideoNoteAccent = Color(0xFF61D8FF)

/**
 * Один плеер на весь чат — как в Telegram.
 *
 * Ровно один кружок играет в любой момент, а его картинка может «переезжать» между
 * тремя поверхностями: пузырь в ленте, мини-плеер в углу (когда пузырь уехал за экран)
 * и полноэкранный просмотр. Поэтому переход между ними бесшовный, без перезапуска видео.
 */
@Stable
class VideoNotePlaybackController internal constructor(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    var activeId by mutableStateOf<String?>(null)
        private set
    var activeUrl by mutableStateOf("")
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var playing by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var muted by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1f)
        private set

    /** Пузырь отдаёт картинку полноэкранному просмотру, пока тот открыт. */
    var expanded by mutableStateOf(false)

    /** Вызывается, когда кружок доиграл до конца — чат включает следующий. */
    var onCompleted: ((String) -> Unit)? = null

    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    init {
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.volume = 1f
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) failed = false
                if (playbackState == Player.STATE_ENDED) {
                    playing = false
                    activeId?.let { finished -> onCompleted?.invoke(finished) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                playing = false
            }
        })
    }

    fun play(messageId: String, url: String) {
        if (url.isBlank()) return
        if (activeId == messageId) {
            resume()
            return
        }
        activeId = messageId
        activeUrl = url
        failed = false
        positionMs = 0L
        durationMs = 0L
        player.setMediaItem(MediaItem.fromUri(url))
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.playWhenReady = true
    }

    fun resume() {
        if (activeId == null) return
        if (durationMs > 0L && positionMs >= durationMs - 200L) player.seekTo(0L)
        player.playWhenReady = true
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun toggle() {
        if (player.playWhenReady && player.isPlaying) pause() else resume()
    }

    fun stop() {
        activeId = null
        activeUrl = ""
        expanded = false
        playing = false
        buffering = false
        positionMs = 0L
        durationMs = 0L
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
    }

    fun seekToFraction(fraction: Float) {
        if (durationMs <= 0L) return
        player.seekTo((fraction.coerceIn(0f, 1f) * durationMs).toLong())
        player.playWhenReady = true
    }

    fun toggleMuted() {
        muted = !muted
        player.volume = if (muted) 0f else 1f
    }

    fun cycleSpeed() {
        speed = when (speed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        player.setPlaybackSpeed(speed)
    }

    internal fun sync() {
        if (activeId == null) return
        positionMs = player.currentPosition.coerceAtLeast(0L)
        val d = player.duration
        if (d > 0L) durationMs = d
    }

    internal fun release() {
        onCompleted = null
        player.release()
    }
}

@Composable
fun rememberVideoNotePlaybackController(): VideoNotePlaybackController {
    val context = LocalContext.current
    val controller = remember { VideoNotePlaybackController(context) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    LaunchedEffect(controller) {
        while (true) {
            controller.sync()
            delay(50)
        }
    }
    return controller
}

/**
 * Размер кружка в ленте: `videoSize: playing ? 214px : 138px` на макетном экране шириной 366.
 * Держим те же доли ширины, чтобы кружок так же вырастал на время проигрывания.
 */
@Composable
fun videoNoteBubbleSize(playing: Boolean = false): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return if (playing) (width * .585f).coerceIn(190.dp, 268.dp)
    else (width * .377f).coerceIn(126.dp, 172.dp)
}

/**
 * Кружок в ленте чата.
 *
 * Пока он не играет — это статичная превьюшка первого кадра: ни одного ExoPlayer на
 * элемент списка, поэтому чат с десятками кружков листается без рывков.
 */
@Composable
fun VideoNoteBubble(
    messageId: String,
    url: String,
    durationMs: Long,
    unwatched: Boolean,
    controller: VideoNotePlaybackController,
    onPlayRequest: () -> Unit,
    onLongPress: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    /** Слева для чужого кружка, справа для своего — как `left:6px` / `right:6px` в макете. */
    footerAlignment: Alignment.Horizontal = Alignment.Start,
    footer: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val active = controller.activeId == messageId
    val playing = active && controller.playing
    val size by animateDpAsState(
        targetValue = videoNoteBubbleSize(playing),
        animationSpec = tween(420, easing = CubicBezierEasing(.33f, 1f, .68f, 1f)),
        label = "video-note-size",
    )
    val hostsSurface = active && !controller.expanded

    var thumbnail by remember(url) { mutableStateOf(VideoNoteThumbnails.cached(url)) }
    LaunchedEffect(url) {
        if (thumbnail == null) thumbnail = VideoNoteThumbnails.load(context, url)
    }

    // Через derivedStateOf подпись обновляется раз в секунду, а не 20 раз: иначе позиция
    // плеера дёргала бы рекомпозицию всего пузыря вместе с видеоповерхностью.
    // В макете в плашке идёт пройденное время, а до запуска — полная длительность.
    val durationLabel by remember(messageId, durationMs) {
        derivedStateOf {
            val shown = if (controller.activeId == messageId && controller.durationMs > 0L) {
                controller.positionMs.coerceAtLeast(0L)
            } else {
                durationMs
            }
            if (shown > 0L) VideoNote.formatDuration(shown) else ""
        }
    }

    // Внешний контейнер намеренно НЕ обрезается по кругу: подписи в углах квадрата
    // (время, галочки, точка «не просмотрено») иначе срезала бы круглая маска.
    Column(modifier, horizontalAlignment = footerAlignment) {
    Box(Modifier.size(size)) {
        // `inset:-6px;stroke-width:2;stroke:var(--txt)` — дуга идёт СНАРУЖИ кружка и только
        // пока он играет. В покое в макете нет ни дуги, ни дорожки под ней.
        if (playing) {
            val ringColor = XText
            Canvas(Modifier.matchParentSize()) {
                val stroke = 2.dp.toPx()
                val radius = this.size.minDimension / 2f + 6.dp.toPx()
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * controller.progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF1B1636), Color(0xFF0B0D1D))))
                .combinedClickable(
                    onClick = { if (active) controller.toggle() else onPlayRequest() },
                    onLongClick = onLongPress,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (hostsSurface) {
                VideoNoteSurface(controller.player, Modifier.matchParentSize())
            } else {
                thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }

            // Большого «play» посреди кружка в макете нет: состояние читается по плашке
            // снизу. В центре остаются только ошибка и буферизация.
            when {
                active && controller.failed -> VideoNoteGlyph(Icons.AutoMirrored.Outlined.OpenInNew, "Ошибка воспроизведения")
                active && controller.buffering && !controller.playing ->
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
            }

            if (active) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoNoteChipButton(
                        icon = if (controller.muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                        description = if (controller.muted) "Включить звук" else "Выключить звук",
                        onClick = { controller.toggleMuted() },
                    )
                    Spacer(Modifier.width(6.dp))
                    VideoNoteChipButton(
                        icon = Icons.Outlined.Fullscreen,
                        description = "Открыть во весь экран",
                        onClick = onExpand,
                    )
                }
            }

            // `left:50%;bottom:12px;padding:5px 10px;border-radius:999px;background:rgba(8,6,18,.62)`
            // — в плашке play/pause, время и точка «не просмотрено».
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x9E080612))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painterResource(if (playing) R.drawable.ic_ms_pause else R.drawable.ic_ms_play_arrow),
                    if (playing) "Пауза" else "Воспроизвести видеосообщение",
                    tint = Color(0xFFFCFBFF),
                    modifier = Modifier.size(13.dp),
                )
                if (durationLabel.isNotEmpty()) {
                    Text(
                        durationLabel,
                        color = Color(0xFFFCFBFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (unwatched && !active) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFCFBFF)))
                }
            }
        }


    }
    // `bottom:-24px` — время и галочка стоят под кружком, а не поверх него.
    footer?.let { content ->
        Box(Modifier.padding(top = 6.dp)) { content() }
    }
    }
}

/**
 * Мини-плеер: кружок уехал за границы экрана, а видео продолжает играть в углу.
 * Телеграмное поведение — прокрутка чата не обрывает воспроизведение.
 */
@Composable
fun VideoNoteMiniPlayer(
    controller: VideoNotePlaybackController,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .combinedClickable(onClick = onExpand, onLongClick = { controller.toggle() }),
            contentAlignment = Alignment.Center,
        ) {
            if (!controller.expanded) VideoNoteSurface(controller.player, Modifier.matchParentSize())
            Canvas(Modifier.matchParentSize().padding(2.dp)) {
                drawVideoNoteRing(
                    progress = controller.progress,
                    trackColor = Color.White.copy(alpha = .22f),
                    progressColor = VideoNoteAccent,
                    strokeDp = 2.5f,
                )
            }
            if (!controller.playing) VideoNoteGlyph(Icons.Outlined.PlayArrow, "Продолжить", padding = 8.dp, size = 20.dp)
        }
        Surface(
            onClick = onClose,
            color = Color.Black.copy(alpha = .70f),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopEnd).size(26.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Закрыть мини-плеер", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
internal fun VideoNoteSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            val view = android.view.LayoutInflater
                .from(context)
                .inflate(R.layout.view_video_note_player, null) as PlayerView
            view.clipToOutline = true
            view.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(target: android.view.View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, target.width, target.height)
                }
            }
            view.player = player
            view
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

@Composable
internal fun VideoNoteGlyph(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    padding: Dp = 11.dp,
    size: Dp = 30.dp,
) {
    Surface(color = Color.Black.copy(alpha = .42f), shape = CircleShape) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.padding(padding).size(size))
    }
}

@Composable
private fun VideoNoteChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Black.copy(alpha = .48f),
        shape = CircleShape,
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

internal fun DrawScope.drawVideoNoteRing(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    strokeDp: Float = 3f,
) {
    val stroke = strokeDp.dp.toPx()
    val arcSize = Size(size.width - stroke, size.height - stroke)
    val topLeft = Offset(stroke / 2f, stroke / 2f)
    drawArc(
        color = trackColor,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    if (progress > 0f) {
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Тап по кольцу = перемотка (как в полноэкранном просмотре Telegram). */
internal fun Modifier.videoNoteSeekRing(controller: VideoNotePlaybackController): Modifier =
    pointerInput(controller) {
        detectTapGestures { offset ->
            val cx = size.width / 2f
            val cy = size.height / 2f
            val dx = offset.x - cx
            val dy = offset.y - cy
            val outer = minOf(size.width, size.height) / 2f
            val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
            if (distance > outer * .72f) {
                var fromTop = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 90.0
                if (fromTop < 0.0) fromTop += 360.0
                controller.seekToFraction((fromTop / 360.0).toFloat())
            } else {
                controller.toggle()
            }
        }
    }

