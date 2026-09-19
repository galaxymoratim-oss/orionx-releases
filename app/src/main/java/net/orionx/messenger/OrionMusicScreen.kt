package net.orionx.messenger

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.orionx.messenger.music.MusicController
import net.orionx.messenger.music.MusicLibrary
import net.orionx.messenger.music.MusicRepeat
import net.orionx.messenger.music.MusicTrack
import net.orionx.messenger.music.formatTrackClock

/**
 * «музыка» from the canvas: a player card (74 dp art, title, like, scrubber, transport row)
 * over the device's own library list.
 */
@Composable
internal fun XMusicScreen(controller: MusicController, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, MusicLibrary.permission()) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(MusicLibrary.permission()) }

    LaunchedEffect(granted) {
        if (granted && controller.tracks.isEmpty()) {
            val loaded = withContext(Dispatchers.IO) { MusicLibrary.load(context) }
            controller.replaceLibrary(loaded)
        }
    }

    // ExoPlayer has no position callback, so the scrubber polls while this screen is on top.
    LaunchedEffect(controller.playing) {
        while (true) {
            controller.syncPosition()
            delay(500)
        }
    }

    val track = controller.current
    // Макет: transition:width .3s linear — полоска скользит между опросами позиции.
    val animatedProgress by animateFloatAsState(
        targetValue = if (controller.durationMs() > 0)
            (controller.positionMs.toFloat() / controller.durationMs()).coerceIn(0f, 1f)
        else 0f,
        animationSpec = tween(300, easing = LinearEasing),
        label = "music-progress",
    )
    val duration = controller.durationMs()
    val progress = if (duration > 0) (controller.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        XScreenHeader(title = xSettingText("app_music"), onBack = onBack)

        XCard(modifier = Modifier.padding(horizontal = 18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    XTrackArt(track, 74.dp, RoundedCornerShape(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            track?.title ?: xSettingText("music_nothing"),
                            style = XType.screenTitle.copy(fontSize = 18.sp, letterSpacing = (-.02).em),
                            color = XText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track?.artist ?: xSettingText("music_pick_hint"),
                            style = XType.meta.copy(fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = XMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val isLiked = track != null && track.id in controller.liked
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(XSurfaceRaised)
                            .clickable(enabled = track != null) { controller.toggleLike() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            xSettingText("like_action"),
                            // В макете неактивный лайк — приглушённый --mut.
                            tint = if (isLiked) XRed else XMuted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    if (size.width > 0) controller.seekTo(offset.x / size.width)
                                }
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(XSurfaceRaised)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF8FB4FF), Color(0xFFFF9FC4)))),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            formatTrackClock(controller.positionMs),
                            style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = XMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatTrackClock(duration),
                            style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = XMuted,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XMusicGhostButton(
                        icon = Icons.Outlined.Shuffle,
                        active = controller.shuffle,
                        description = xSettingText("shuffle_action"),
                        onClick = controller::toggleShuffle,
                    )
                    XMusicPlateButton(Icons.Outlined.SkipPrevious, xSettingText("prev_action"), controller::previous)
                    Box(
                        Modifier
                            .size(60.dp)
                            .xShadow(RoundedCornerShape(22.dp), 16.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(XText)
                            .clickable { controller.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (controller.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            xSettingText("play_action"),
                            tint = XInverse,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    XMusicPlateButton(Icons.Outlined.SkipNext, xSettingText("next_action"), controller::next)
                    XMusicGhostButton(
                        icon = Icons.Outlined.RepeatOne,
                        active = controller.repeat == MusicRepeat.ONE,
                        description = xSettingText("repeat_action"),
                        onClick = controller::toggleRepeat,
                    )
                }
            }
        }

        XCard(
            modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 20.dp).weight(1f),
            shape = XShape.card,
        ) {
            if (!granted) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        xSettingText("music_no_permission"),
                        style = XType.bodyStrong,
                        color = XMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (controller.tracks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        xSettingText("music_empty"),
                        style = XType.bodyStrong,
                        color = XMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    itemsIndexed(controller.tracks, key = { _, it -> it.id }) { position, item ->
                        val active = position == controller.index
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) XSurfaceRaised else Color.Transparent)
                                .clickable { controller.play(position) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            XTrackArt(item, 38.dp, RoundedCornerShape(13.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    item.title,
                                    style = XType.rowTitle.copy(fontSize = 13.sp),
                                    color = XText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.artist,
                                    style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    color = XMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (item.id in controller.liked) {
                                Icon(Icons.Filled.Favorite, null, tint = XRed, modifier = Modifier.size(15.dp))
                            }
                            if (active) {
                                Icon(Icons.Outlined.GraphicEq, null, tint = XText, modifier = Modifier.size(17.dp))
                            } else {
                                Text(
                                    formatTrackClock(item.durationMs),
                                    style = XType.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    color = XMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Album art when MediaStore has it, otherwise the canvas avatar gradient. */
@Composable
internal fun XTrackArt(track: MusicTrack?, size: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape) {
    Box(Modifier.size(size).clip(shape).background(XAvatarBrush)) {
        if (track != null) {
            RemoteImage(track.artworkUri.toString(), Modifier.fillMaxSize().clip(shape), ContentScale.Crop)
        }
    }
}

@Composable
private fun XMusicPlateButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(XSurfaceRaised).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = XText, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun XMusicGhostButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // В макете активное состояние — просто --txt, без синего акцента.
        Icon(icon, description, tint = if (active) XText else XMuted, modifier = Modifier.size(19.dp))
    }
}
