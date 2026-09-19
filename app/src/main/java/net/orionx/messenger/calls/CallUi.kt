package net.orionx.messenger.calls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun OrionCallScreen(
    controller: OrionCallController,
    onFinished: () -> Unit,
) {
    val ui by controller.uiState.collectAsState()
    val localVideo by controller.localVideoTrack.collectAsState()
    val remoteVideo by controller.remoteVideoTrack.collectAsState()
    val isVideo = ui.type == CallType.VIDEO
    val terminal = ui.state in setOf(CallState.ENDED, CallState.DECLINED, CallState.MISSED, CallState.FAILED)

    BackHandler(enabled = !terminal) { controller.hangUp() }

    LaunchedEffect(ui.state) {
        if (terminal) {
            delay(1_350)
            onFinished()
        }
    }

    // Кольцевой сигнал ожидания («гудки») во время исходящего вызова — как у обычного телефона.
    if (ui.state == CallState.RINGING) {
        val context = androidx.compose.ui.platform.LocalContext.current
        DisposableEffect(ui.state) {
            var tone: android.media.ToneGenerator? = null
            var cancelled = false
            val thread = Thread {
                while (!cancelled) {
                    runCatching {
                        tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_VOICE_CALL, 70)
                        tone?.startTone(android.media.ToneGenerator.TONE_SUP_RINGTONE, 2000)
                    }
                    runCatching { Thread.sleep(3000) }
                }
            }.apply { runCatching { start() } }
            onDispose {
                cancelled = true
                runCatching { tone?.stopTone() }
                runCatching { tone?.release() }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!terminal) controller.hangUp() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF4A1C11), Color(0xFF3A1A1D), Color(0xFF2C1628), Color(0xFF1E1533), Color(0xFF141630), Color(0xFF0B0C12))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (isVideo && remoteVideo != null && controller.eglContext != null) {
            WebRtcVideoRenderer(
                track = requireNotNull(remoteVideo),
                eglContext = requireNotNull(controller.eglContext),
                mirror = false,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.22f), Color.Transparent, Color.Black.copy(.48f)))))
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Макет: 150dp диск с тёплым радиальным градиентом и мягкой тенью.
                    Box(
                        Modifier
                            .size(150.dp)
                            .shadow(24.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0f to Color(0xFFFFD9B4),
                                        .44f to Color(0xFFF79AC6),
                                        .74f to Color(0xFF9B8CFF),
                                        1f to Color(0xFF3F2585),
                                    ),
                                    center = Offset(.34f, .28f),
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initials(ui.peerName),
                            color = Color.White,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        ui.peerName.ifBlank { "OrionX" },
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-.03).em,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(callStatus(ui), color = Color.White.copy(alpha = .55f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    // Макет: пилюля «сквозное шифрование» с замком под таймером.
                    Row(
                        Modifier
                            .background(Color(0x14FFFFFF), RoundedCornerShape(999.dp))
                            .padding(horizontal = 15.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(Icons.Outlined.Lock, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text("сквозное шифрование", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 18.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!isVideo) {
                // Макет: чёрная пилюля «защищённый звонок» над шапкой.
                Text(
                    "защищённый звонок",
                    color = Color(0xFF101014),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = .18.em,
                    modifier = Modifier
                        .background(Color(0xFFF2F1F5), RoundedCornerShape(999.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            if (isVideo) {
                Text(ui.peerName.ifBlank { "OrionX" }, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(callStatus(ui), color = Color(0xFFD2CDE0), fontSize = 13.sp)
            }
            ui.error?.let {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color(0xAA3A1020), shape = RoundedCornerShape(14.dp)) {
                    Text(it, color = Color(0xFFFFA7B7), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), textAlign = TextAlign.Center)
                }
            }
        }

        if (isVideo && localVideo != null && controller.eglContext != null && ui.cameraEnabled) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 84.dp, end = 16.dp)
                    .width(112.dp)
                    .height(158.dp),
                color = Color.Black,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x669A5BFF)),
                shadowElevation = 12.dp,
            ) {
                WebRtcVideoRenderer(
                    track = requireNotNull(localVideo),
                    eglContext = requireNotNull(controller.eglContext),
                    mirror = ui.usingFrontCamera,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (!terminal) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Макет: четыре круга 62dp на сплошном --sf, без подписей под кнопками.
                CallControl(selected = ui.muted, onClick = controller::toggleMute) {
                    Icon(if (ui.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                CallControl(selected = ui.speaker, onClick = controller::toggleSpeaker) {
                    Icon(if (ui.speaker) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                if (isVideo) {
                    CallControl(onClick = controller::toggleCamera) {
                        Icon(if (ui.cameraEnabled) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    CallControl(onClick = controller::switchCamera) {
                        Icon(Icons.Outlined.Cameraswitch, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                CallControl(danger = true, onClick = controller::hangUp) {
                    Icon(Icons.Outlined.CallEnd, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
    }
}

@Composable
private fun CallControl(
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(62.dp),
            shape = CircleShape,
            // Макет: сплошной --sf, опасная кнопка #FF5C76; активная — мягкая подсветка.
            color = when {
                danger -> Color(0xFFFF5C76)
                selected -> Color(0xFF7040D8)
                else -> Color(0xFF191920)
            },
            shadowElevation = 12.dp,
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
    }
}

@Composable
private fun WebRtcVideoRenderer(
    track: VideoTrack,
    eglContext: EglBase.Context,
    mirror: Boolean,
    modifier: Modifier = Modifier,
) {
    var renderer by remember(track) { mutableStateOf<SurfaceViewRenderer?>(null) }
    DisposableEffect(track, renderer) {
        val current = renderer
        onDispose {
            if (current != null) {
                runCatching { track.removeSink(current) }
                runCatching { current.release() }
            }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).also { view ->
                view.init(eglContext, null)
                view.setMirror(mirror)
                view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                view.setEnableHardwareScaler(true)
                track.addSink(view)
                renderer = view
            }
        },
        update = { view -> view.setMirror(mirror) },
    )
}

private fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "OX"
    }
}

private fun callStatus(ui: CallUiState): String = when (ui.state) {
    CallState.ACTIVE -> formatDuration(ui.elapsedSeconds)
    else -> ui.statusText
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}

@Composable
fun OrionIncomingCallDialog(
    invite: IncomingCall,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current
    // Рингтон зациклен: стандартный Ringtone.play() играет один раз и умолкает.
    DisposableEffect(invite.id) {
        val player = android.media.MediaPlayer()
        var prepared = false
        runCatching {
            player.setDataSource(context, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE))
            player.isLooping = true
            player.setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            player.setOnPreparedListener { it.start(); prepared = true }
            player.prepareAsync()
        }
        onDispose {
            runCatching { if (prepared) player.stop() }
            runCatching { player.release() }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDecline) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = Color(0xF20B0E22),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x665C42A8)),
            shadowElevation = 24.dp,
        ) {
            Column(
                Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (invite.type == CallType.VIDEO) "Входящий видеозвонок" else "Входящий звонок",
                    color = Color(0xFFC5BCD9),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.size(104.dp),
                    shape = CircleShape,
                    color = Color(0xFF3C247D),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF9A5BFF)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(initials(invite.callerName), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(invite.callerName, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("OrionX", color = Color(0xFF9A5BFF), fontSize = 13.sp)
                Spacer(Modifier.height(26.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(onClick = onDecline, modifier = Modifier.size(62.dp), shape = CircleShape, color = Color(0xFFE84D68)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                        }
                        Spacer(Modifier.height(7.dp))
                        Text("Отклонить", color = Color(0xFFD4D0DE), fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(onClick = onAccept, modifier = Modifier.size(62.dp), shape = CircleShape, color = Color(0xFF39C98C)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (invite.type == CallType.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Mic, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Text("Ответить", color = Color(0xFFD4D0DE), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
