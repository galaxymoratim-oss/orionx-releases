package net.orionx.messenger

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.orionx.messenger.music.MusicController
import net.orionx.messenger.music.MusicTrack
import net.orionx.messenger.music.formatTrackClock

private val XHaloEasing = CubicBezierEasing(.22f, 1f, .28f, 1f)

/**
 * halo — «капля / чёлка» из макета, единственная версия, без настроек.
 *
 * Свёрнутая: чёрная пилюля 132×34 r17 в статус-баре — обложка трека и эквалайзер,
 * пока играет музыка (в макете слои `islSmallOp` + `islMusicOp`). Без трека ничего
 * не рисуется: на реальном устройстве вырез камеры есть сам.
 * По тапу пилюля вырастает в карточку-плеер min(328px, 100%−32px) × 182px r40
 * с обложкой, названием, скраббером и prev/play/next/queue.
 */
@Composable
internal fun XHalo(
    music: MusicController,
    modifier: Modifier = Modifier,
) {
    val track = music.current ?: return

    var expanded by remember { mutableStateOf(false) }
    // Collapse on its own once playback stops, so the bar does not linger over the status bar.
    LaunchedEffect(music.playing) {
        if (!music.playing) {
            delay(4_000)
            if (!music.playing) expanded = false
        }
    }
    LaunchedEffect(expanded, music.playing) {
        while (expanded || music.playing) {
            music.syncPosition()
            delay(500)
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bigW = minOf(328.dp, maxWidth - 32.dp)
        val width by animateDpAsState(
            targetValue = if (expanded) bigW else 132.dp,
            animationSpec = tween(440, easing = XHaloEasing),
            label = "halo-width",
        )
        val height by animateDpAsState(
            targetValue = if (expanded) 182.dp else 34.dp,
            animationSpec = tween(440, easing = XHaloEasing),
            label = "halo-height",
        )

        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(width)
                    .height(height)
                    .clip(if (expanded) RoundedCornerShape(40.dp) else RoundedCornerShape(17.dp))
                    .background(Color(0xFF08080A))
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (expanded) {
                    XHaloBigPlayer(music, track)
                } else {
                    // Свёрнутая пилюля как в макете: обложка слева, эквалайзер справа.
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        XTrackArt(track, 18.dp, CircleShape)
                        if (music.playing) XHaloEq(12.dp)
                    }
                }
            }
        }
    }
}

/** Раскрытая карточка-плеер: обложка 46dp, название/исполнитель, скраббер, transport. */
@Composable
private fun XHaloBigPlayer(music: MusicController, track: MusicTrack) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XTrackArt(track, 46.dp, RoundedCornerShape(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    track.title,
                    style = XType.rowTitle.copy(fontSize = 14.sp, lineHeight = 16.sp),
                    color = Color(0xFFF2F1F5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = XType.meta.copy(fontSize = 11.sp, lineHeight = 13.sp),
                    color = Color(0x80F2F1F5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (music.playing) XHaloEq(16.dp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val duration = music.durationMs()
            val progress = if (duration > 0) (music.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x29F2F1F5)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFF2F1F5)),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatTrackClock(music.positionMs),
                    style = XType.meta.copy(fontSize = 9.sp, letterSpacing = .1.sp),
                    color = Color(0x80F2F1F5),
                )
                Text(
                    formatTrackClock(duration),
                    style = XType.meta.copy(fontSize = 9.sp, letterSpacing = .1.sp),
                    color = Color(0x80F2F1F5),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.SkipPrevious,
                null,
                tint = Color(0xFFF2F1F5),
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = music::previous),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF2F1F5))
                    .clickable(onClick = music::toggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (music.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    null,
                    tint = Color(0xFF101014),
                    modifier = Modifier.size(22.dp),
                )
            }
            Icon(
                Icons.Outlined.SkipNext,
                null,
                tint = Color(0xFFF2F1F5),
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = music::next),
            )
            Icon(
                Icons.Outlined.QueueMusic,
                null,
                tint = if (music.shuffle) Color(0xFFF2F1F5) else Color(0x80F2F1F5),
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = music::toggleShuffle),
            )
        }
    }
}

/** Четыре столбика эквалайзера (2.5dp), разбег 160 мс — как `@keyframes eq` в макете. */
@Composable
private fun XHaloEq(barHeight: Dp) {
    val transition = rememberInfiniteTransition(label = "halo-eq")
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(barHeight),
    ) {
        listOf(0, 160, 320, 480).forEach { delayMs ->
            val scale by transition.animateFloat(
                initialValue = .3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(410, easing = LinearEasing),
                    initialStartOffset = StartOffset(delayMs),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "halo-eq-$delayMs",
            )
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(barHeight * scale)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFF2F1F5)),
            )
        }
    }
}
