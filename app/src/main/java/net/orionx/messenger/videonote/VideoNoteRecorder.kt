package net.orionx.messenger.videonote

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Rational
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.delay

internal enum class VideoNoteCommand { LOCK, CANCEL, SEND, FLIP }

/**
 * Состояние записи кружка, общее для кнопки в композере и для оверлея с камерой.
 *
 * Жест удержания живёт в композере, а камера — в оверлее над лентой, поэтому команды
 * («отправить», «отменить», «зафиксировать», «сменить камеру») передаются через очередь.
 */
@Stable
class VideoNoteRecorderState {
    /** Идёт ли сеанс записи (показан оверлей). */
    var active by mutableStateOf(false)
        internal set

    /** Hands-free: палец можно отпустить, запись продолжается. */
    var locked by mutableStateOf(false)
        internal set

    var elapsedMs by mutableLongStateOf(0L)
        internal set

    /** 0..1 — насколько далеко утянули палец влево (отмена). */
    var cancelProgress by mutableFloatStateOf(0f)
        internal set

    /** 0..1 — насколько далеко утянули палец вверх (фиксация). */
    var lockProgress by mutableFloatStateOf(0f)
        internal set

    var cancelling by mutableStateOf(false)
        internal set

    /**
     * Очередь команд + счётчик как ключ эффекта.
     *
     * Через SharedFlow команда терялась бы, если её отправить раньше, чем оверлей успел
     * подписаться, — а это ровно случай «нажал и сразу отпустил»: запись осталась бы
     * висеть навсегда. Очередь переживает такую гонку: оверлей разберёт её при первой же
     * композиции.
     */
    private val queue = ArrayDeque<VideoNoteCommand>()

    internal var commandSeq by mutableIntStateOf(0)
        private set

    private fun submit(command: VideoNoteCommand) {
        synchronized(queue) { queue.addLast(command) }
        commandSeq++
    }

    internal fun drainCommands(): List<VideoNoteCommand> = synchronized(queue) {
        val pending = queue.toList()
        queue.clear()
        pending
    }

    val recordingReleasable: Boolean get() = active && !cancelling

    internal fun open() {
        synchronized(queue) { queue.clear() }
        active = true
        locked = false
        cancelling = false
        elapsedMs = 0L
        cancelProgress = 0f
        lockProgress = 0f
    }

    internal fun close() {
        synchronized(queue) { queue.clear() }
        active = false
        locked = false
        cancelling = false
        elapsedMs = 0L
        cancelProgress = 0f
        lockProgress = 0f
    }

    fun lock() {
        if (!recordingReleasable || locked) return
        locked = true
        cancelProgress = 0f
        lockProgress = 1f
        submit(VideoNoteCommand.LOCK)
    }

    fun cancel() {
        if (!active || cancelling) return
        cancelling = true
        submit(VideoNoteCommand.CANCEL)
    }

    fun send() {
        if (!recordingReleasable) return
        submit(VideoNoteCommand.SEND)
    }

    fun flipCamera() {
        if (active) submit(VideoNoteCommand.FLIP)
    }

    /** Палец двигается по экрану, кнопка удерживается. */
    fun onHoldDrag(dx: Float, dy: Float, cancelDistancePx: Float, lockDistancePx: Float) {
        if (!recordingReleasable || locked) return
        cancelProgress = (-dx / cancelDistancePx).coerceIn(0f, 1f)
        lockProgress = (-dy / lockDistancePx).coerceIn(0f, 1f)
        when {
            lockProgress >= 1f -> lock()
            cancelProgress >= 1f -> cancel()
        }
    }

    /** Палец отпущен: не зафиксировано и не отменено — значит отправляем. */
    fun onHoldRelease() {
        if (!recordingReleasable || locked) return
        send()
    }
}

@Composable
fun rememberVideoNoteRecorderState(): VideoNoteRecorderState = remember { VideoNoteRecorderState() }

/**
 * Оверлей записи: круглое превью камеры с кольцом прогресса поверх ленты чата.
 * Композер при этом остаётся на экране — именно поэтому жест «удерживать → отпустить»
 * доживает до отправки (раньше композер размонтировался и жест умирал вместе с ним).
 */
@Composable
fun VideoNoteRecorderOverlay(
    state: VideoNoteRecorderState,
    onCaptured: (File, Long) -> Unit,
    onCancelled: () -> Unit,
    onTooShort: () -> Unit,
    onError: (String) -> Unit,
    /**
     * Высота строки ввода под оверлеем. В макете кружок и пилюли расставлены от низа
     * экрана (`top:40%`, `bottom:92px`), а оверлей живёт над лентой, поэтому недостающую
     * часть экрана он добирает этим отступом.
     */
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(target: android.view.View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, target.width, target.height)
                }
            }
        }
    }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                )
            )
            .build()
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var cancelRequested by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, lensFacing) {
        cameraReady = false
        torchOn = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val capture = VideoCapture.Builder(recorder)
                    // Отправляем то, что человек видит в превью: селфи-камера зеркалится.
                    .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                    .build()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                // Квадратный ViewPort: кадрирование камеры совпадает с кругом на экране.
                val viewPort = ViewPort.Builder(
                    Rational(1, 1),
                    previewView.display?.rotation ?: android.view.Surface.ROTATION_0,
                ).setScaleType(ViewPort.FILL_CENTER).build()
                provider.unbindAll()
                camera = runCatching {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        UseCaseGroup.Builder()
                            .addUseCase(preview)
                            .addUseCase(capture)
                            .setViewPort(viewPort)
                            .build(),
                    )
                }.getOrElse {
                    // Квадратное кадрирование поддержано не везде. Кружок всё равно
                    // получится квадратным — его дожмёт VideoNoteEncoder, — так что
                    // лучше снимать как есть, чем не открыть камеру вовсе.
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                }
                videoCapture = capture
                cameraReady = true
            }.onFailure {
                camera = null
                cameraReady = false
                onError("Не удалось запустить камеру")
            }
        }, executor)
        onDispose {
            runCatching { camera?.cameraControl?.enableTorch(false) }
            camera = null
            runCatching { if (future.isDone) future.get().unbindAll() }
        }
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        if (recording != null) return
        runCatching {
            val dir = File(context.cacheDir, "video_notes").apply { mkdirs() }
            val file = File(dir, "vnote_raw_${System.currentTimeMillis()}.mp4")
            recordedFile = file
            val prepared = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
            val withAudio = if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) prepared.withAudioEnabled() else prepared
            // Persistent: запись переживает пересборку камеры, поэтому её можно
            // переключать с фронтальной на основную прямо во время съёмки.
            recording = withAudio.asPersistentRecording().start(executor) { event ->
                if (event !is VideoRecordEvent.Finalize) return@start
                val file0 = recordedFile
                val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                recording = null
                recordedFile = null
                when {
                    cancelRequested -> {
                        file0?.delete()
                        onCancelled()
                    }
                    file0 == null || !file0.exists() || file0.length() == 0L -> {
                        file0?.delete()
                        if (event.hasError()) onError("Не удалось записать видеосообщение") else onTooShort()
                    }
                    durationMs < VideoNote.MIN_DURATION_MS -> {
                        file0.delete()
                        onTooShort()
                    }
                    else -> onCaptured(file0, durationMs)
                }
            }
        }.onFailure {
            recording = null
            recordedFile?.delete()
            recordedFile = null
            onError("Не удалось начать запись видеосообщения")
        }
    }

    LaunchedEffect(cameraReady) {
        if (cameraReady && !started) {
            started = true
            startRecording()
        }
    }

    // Таймер и авто-стоп на 60 секундах (дальше — сразу отправка, как в Telegram).
    LaunchedEffect(recording) {
        val active = recording ?: return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        while (recording === active) {
            state.elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            if (state.elapsedMs >= VideoNote.MAX_DURATION_MS) {
                active.stop()
                break
            }
            delay(50)
        }
    }

    LaunchedEffect(state.commandSeq) {
        state.drainCommands().forEach { command ->
            when (command) {
                VideoNoteCommand.LOCK -> Unit
                VideoNoteCommand.FLIP -> if (cameraReady) {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                }
                VideoNoteCommand.CANCEL -> {
                    cancelRequested = true
                    val active = recording
                    if (active != null) active.stop() else onCancelled()
                }
                VideoNoteCommand.SEND -> {
                    val active = recording
                    // Камера ещё не успела начать запись — значит записывать было нечего.
                    if (active != null) active.stop() else onTooShort()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Persistent-запись не останавливается сама при отвязке камеры. Если оверлей
            // уходит с незавершённой записью (ушли из чата), это отмена: файл удаляем,
            // иначе он навсегда осел бы в кэше.
            val unfinished = recording
            if (unfinished != null) {
                cancelRequested = true
                runCatching { unfinished.stop() }
            }
        }
    }

    val pulse = rememberInfiniteTransition(label = "video-note-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(760, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "video-note-pulse-scale",
    )
    // `eq .9s ease-in-out infinite` — красная точка рядом со временем.
    val dotPulse by pulse.animateFloat(
        initialValue = .35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "video-note-dot",
    )

    // ВНИМАНИЕ: elapsedMs и cancelProgress читаются только внутри лямбд отрисовки и
    // graphicsLayer. Прочитай их на уровне composable — и всё дерево с превью камеры
    // начнёт рекомпозироваться 20 раз в секунду.
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Затемняем чат и глотаем касания ленты, чтобы она не прокручивалась под кружком.
        Box(
            Modifier
                .matchParentSize()
                .background(Color(0x99080612))
                .pointerInput(Unit) { detectTapGestures { } }
        )

        // `width:316px;height:316px;left:50%;top:40%` — кружок сидит чуть выше центра экрана,
        // поэтому считаем от полной высоты, а не от высоты ленты.
        val screenHeight = maxHeight + bottomInset
        val circleSize = minOf(maxWidth * .84f, screenHeight * .62f, 316.dp)
        val circleOffset = screenHeight * .4f - maxHeight / 2
        // `bottom:92px` — тоже от низа экрана.
        val pillBottom = (92.dp - bottomInset).coerceAtLeast(12.dp)

        Box(
            Modifier
                .align(Alignment.Center)
                .offset(y = circleOffset)
                .size(circleSize + 14.dp)
                .graphicsLayer {
                    val cancelShift = state.cancelProgress
                    val scale = if (state.cancelling) .92f else pulseScale
                    scaleX = scale
                    scaleY = scale
                    translationX = -cancelShift * 90f
                    alpha = 1f - cancelShift * .45f
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        // Второй палец: двойной тап по кружку переключает камеру — как в Telegram.
                        detectTapGestures(onDoubleTap = { state.flipCamera() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())

                if (!cameraReady) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                }
            }

            // Кольцо таймера в макете тонкое (1.7 px) и без подложки — одна белая дуга.
            Canvas(Modifier.matchParentSize()) {
                val progress = (state.elapsedMs.toFloat() / VideoNote.MAX_DURATION_MS).coerceIn(0f, 1f)
                val stroke = 1.7.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                drawArc(
                    color = Color(0xFFF2F1F5),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }

            // `left:14px;bottom:14px;padding:6px 11px;border-radius:999px` — таймер записи.
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 21.dp, bottom = 21.dp)
                    .clip(CircleShape)
                    .background(Color(0x9E080612))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer { val p = dotPulse; scaleX = p; scaleY = p }
                        .clip(CircleShape)
                        .background(Color(0xFFFF5C76)),
                )
                Text(
                    videoNoteElapsedLabel(state.elapsedMs),
                    color = Color(0xFFFCFBFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            if (state.locked) {
                // Внутри круглой маски углы обрезаются — значок держим у верхнего центра.
                Surface(
                    color = Color.Black.copy(alpha = .55f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(30.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Lock, "Запись зафиксирована", tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
            }

            if (state.cancelling || state.cancelProgress > .55f) {
                Surface(color = Color.Black.copy(alpha = .62f), shape = CircleShape) {
                    Column(
                        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.Delete, null, tint = Color(0xFFFF5C76), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (state.cancelling) "Отмена…" else "Отпустите — отмена",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Управление камерой — горизонтальная пилюля слева снизу, как в макете:
        // `left:16px;bottom:92px;padding:5px 6px;border-radius:999px;background:rgba(28,26,44,.72)`.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = pillBottom)
                .clip(CircleShape)
                .background(Color(0xB81C1A2C))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            VideoNoteRoundButton(
                icon = Icons.Outlined.Cameraswitch,
                description = "Сменить камеру",
                enabled = cameraReady,
                onClick = { state.flipCamera() },
            )
            VideoNoteRoundButton(
                icon = if (torchOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                description = "Вспышка",
                enabled = cameraReady && lensFacing == CameraSelector.LENS_FACING_BACK,
                highlighted = torchOn,
                onClick = {
                    val next = !torchOn
                    runCatching { camera?.cameraControl?.enableTorch(next) }.onSuccess { torchOn = next }
                },
            )
            VideoNoteRoundButton(
                icon = Icons.Outlined.Lock,
                description = "Зафиксировать запись",
                enabled = !state.locked,
                highlighted = state.locked,
                iconSize = 18.dp,
                onClick = { state.lock() },
            )
        }

        // `right:16px;bottom:92px;width:44px;border-radius:22px` — подсказка «смахни вверх,
        // чтобы зафиксировать». Длинной строки-подписи на артборде нет: что делать дальше,
        // говорит полоса записи внизу.
        if (!state.locked && !state.cancelling) {
            val bob = rememberInfiniteTransition(label = "video-note-lock-hint")
            // `swu 1.4s`: 0%,100% {translateY(0);opacity:.8}  50% {translateY(-5px);opacity:1}
            val bobPhase by bob.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "video-note-lock-hint-phase",
            )
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = pillBottom)
                    .graphicsLayer {
                        translationY = -5.dp.toPx() * bobPhase
                        alpha = .8f + .2f * bobPhase
                    }
                    .width(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xB81C1A2C))
                    .padding(top = 10.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    "Смахните вверх, чтобы зафиксировать запись",
                    tint = Color(0xFFFCFBFF),
                    modifier = Modifier.size(19.dp),
                )
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    null,
                    tint = Color(0xFFFCFBFF).copy(alpha = .55f),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** `width:34px;height:34px;border-radius:50%` внутри пилюли управления. */
/** ММ:СС для таймера в углу кружка. */
private fun videoNoteElapsedLabel(elapsedMs: Long): String {
    val total = (elapsedMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun VideoNoteRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    highlighted: Boolean = false,
    iconSize: Dp = 19.dp,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (highlighted) Color(0xFFF04452).copy(alpha = .82f) else Color.Transparent,
        shape = CircleShape,
        modifier = Modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                description,
                tint = if (enabled || highlighted) Color(0xFFFCFBFF) else Color.White.copy(alpha = .32f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
