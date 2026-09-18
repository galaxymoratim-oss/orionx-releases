package net.orionx.messenger.calls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    Dialog(
        onDismissRequest = { if (!terminal) controller.hangUp() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF02040D), Color(0xFF07091B), Color(0xFF11092B))
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
                    Surface(
                        modifier = Modifier.size(138.dp),
                        shape = CircleShape,
                        color = Color(0xFF372071),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF9A5BFF)),
                        shadowElevation = 20.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                initials(ui.peerName),
                                color = Color.White,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(ui.peerName.ifBlank { "OrionX" }, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(callStatus(ui), color = Color(0xFFC1BCD5), fontSize = 15.sp)
                }
            }
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 18.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                CallControl(
                    label = if (ui.muted) "Включить" else "Микрофон",
                    selected = ui.muted,
                    onClick = controller::toggleMute,
                ) {
                    Icon(if (ui.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, null, tint = Color.White)
                }
                CallControl(
                    label = "Динамик",
                    selected = ui.speaker,
                    onClick = controller::toggleSpeaker,
                ) {
                    Icon(if (ui.speaker) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, null, tint = Color.White)
                }
                if (isVideo) {
                    CallControl(
                        label = "Камера",
                        selected = !ui.cameraEnabled,
                        onClick = controller::toggleCamera,
                    ) {
                        Icon(if (ui.cameraEnabled) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff, null, tint = Color.White)
                    }
                    CallControl(label = "Сменить", onClick = controller::switchCamera) {
                        Icon(Icons.Outlined.Cameraswitch, null, tint = Color.White)
                    }
                }
                CallControl(label = "Завершить", danger = true, onClick = controller::hangUp) {
                    Icon(Icons.Outlined.CallEnd, null, tint = Color.White)
                }
            }
        }
    }
    }
}

@Composable
private fun CallControl(
    label: String,
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(if (danger) 62.dp else 54.dp),
            shape = CircleShape,
            color = when {
                danger -> Color(0xFFE84D68)
                selected -> Color(0xFF7040D8)
                else -> Color(0xB92A2B3D)
            },
            border = if (danger) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0x554F4D68)),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFFD4D0DE), fontSize = 10.sp, maxLines = 1)
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
    val ringtone = remember(invite.id) {
        runCatching {
            android.media.RingtoneManager.getRingtone(
                context,
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
            )
        }.getOrNull()
    }
    DisposableEffect(invite.id, ringtone) {
        runCatching { ringtone?.play() }
        onDispose { runCatching { ringtone?.stop() } }
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
