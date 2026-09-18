package net.orionx.messenger.videonote

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Полноэкранный просмотр кружка. Использует тот же плеер, что и лента, поэтому
 * разворот происходит без перезапуска: видео продолжает идти с того же места.
 */
@Composable
fun VideoNoteViewerDialog(
    controller: VideoNotePlaybackController,
    senderName: String,
    sentAt: String,
    onDismiss: () -> Unit,
    onForward: (() -> Unit)? = null,
) {
    DisposableEffect(controller) {
        controller.expanded = true
        onDispose { controller.expanded = false }
    }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .88f))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val target = minOf(maxWidth * .92f, 400.dp)
            val circleSize by animateDpAsState(
                targetValue = if (entered) target else videoNoteBubbleSize(),
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "video-note-expand",
            )

            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = Color(0xF20E0F14),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(58.dp).padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                senderName.ifBlank { "Видеосообщение" },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            if (sentAt.isNotBlank()) {
                                Text(sentAt, color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
                            }
                        }
                        Surface(
                            onClick = { controller.cycleSpeed() },
                            color = Color.White.copy(alpha = .10f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                if (controller.speed == 1f) "1×" else "${controller.speed}×",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, "Закрыть", tint = Color.White.copy(alpha = .70f))
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Box(
                    Modifier
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = .28f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    VideoNoteSurface(controller.player, Modifier.matchParentSize())
                    Box(Modifier.matchParentSize().videoNoteSeekRing(controller))
                    Canvas(Modifier.matchParentSize().padding(4.dp)) {
                        drawVideoNoteRing(
                            progress = controller.progress,
                            trackColor = Color.White.copy(alpha = .22f),
                            progressColor = Color.White,
                            strokeDp = 4f,
                        )
                    }
                    when {
                        controller.failed -> Surface(color = Color.Black.copy(alpha = .62f), shape = CircleShape) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                "Не удалось воспроизвести",
                                tint = Color.White,
                                modifier = Modifier.padding(17.dp).size(32.dp),
                            )
                        }
                        controller.buffering && !controller.playing ->
                            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                        !controller.playing -> VideoNoteGlyph(Icons.Outlined.PlayArrow, "Продолжить", padding = 15.dp, size = 38.dp)
                    }

                    Surface(
                        color = Color.Black.copy(alpha = .68f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    ) {
                        VideoNotePositionLabel(controller)
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    Modifier.padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { controller.toggleMuted() },
                        color = Color.White.copy(alpha = .10f),
                        shape = CircleShape,
                        modifier = Modifier.size(50.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (controller.muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                                "Звук",
                                tint = Color.White,
                            )
                        }
                    }
                    Surface(
                        onClick = { controller.toggle() },
                        color = Color(0xFF733DFF),
                        shape = CircleShape,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (controller.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                "Воспроизведение",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    if (onForward != null) {
                        Surface(
                            onClick = onForward,
                            color = Color.White.copy(alpha = .10f),
                            shape = CircleShape,
                            modifier = Modifier.size(50.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Outlined.Forward, "Переслать", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Позиция вынесена в отдельный composable: она меняется 20 раз в секунду и не должна
 * тянуть за собой рекомпозицию всего диалога вместе с видеоповерхностью.
 */
@Composable
private fun VideoNotePositionLabel(controller: VideoNotePlaybackController) {
    val label by remember(controller) {
        derivedStateOf {
            "${VideoNote.formatDuration(controller.positionMs)} / ${VideoNote.formatDuration(controller.durationMs)}"
        }
    }
    Text(
        label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
