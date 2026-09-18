package net.orionx.messenger.calls

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.CloudSession
import net.orionx.messenger.cloud.SupabaseApi
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.concurrent.CopyOnWriteArrayList

class OrionCallController(
    context: Context,
    private val api: SupabaseApi,
    private val session: CloudSession,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val turnRepository = TurnCredentialsRepository()
    private var engine: WebRtcCallEngine? = null
    private var signalJob: Job? = null
    private var statusJob: Job? = null
    private var timerJob: Job? = null
    private var callId: String = ""
    private var peerId: String = ""
    private var outgoing = false
    private var lastSignalId = 0L
    private var remoteSdpReady = false
    private val pendingRemoteIce = CopyOnWriteArrayList<IceCandidate>()
    private var terminal = false

    /**
     * Почему не получили TURN. Раньше отказ проглатывался молча, звонок уходил на одном
     * STUN и умирал с невнятным «Ошибка ICE/TURN соединения» — причину приходилось
     * выяснять по логам сервера. Теперь она доезжает до текста ошибки.
     */
    private var turnFailure: String? = null

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    val eglContext get() = engine?.eglBase?.eglBaseContext

    suspend fun startOutgoing(
        conversationId: String,
        peerName: String,
        peerAvatarUrl: String,
        video: Boolean,
    ) {
        outgoing = true
        updateState(
            CallUiState(
                peerName = peerName,
                peerAvatarUrl = peerAvatarUrl,
                type = if (video) CallType.VIDEO else CallType.VOICE,
                state = CallState.PREPARING,
                statusText = "Подготовка ${if (video) "видеозвонка" else "звонка"}…",
                speaker = video,
            )
        )
        try {
            val iceServers = loadIceServers()
            val call = withContext(Dispatchers.IO) {
                api.startDirectCall(
                    token = session.accessToken,
                    conversationId = conversationId,
                    peerName = peerName,
                    peerAvatarUrl = peerAvatarUrl,
                    video = video,
                )
            }
            callId = call.id
            peerId = call.peerId
            scope.launch { runCatching { api.notifyIncomingCall(session.accessToken, call.id) } }
            updateState(
                _uiState.value.copy(
                    callId = call.id,
                    peerId = call.peerId,
                    state = CallState.RINGING,
                    statusText = "Вызов…",
                )
            )
            createEngine(video, iceServers)
            configureAudio(video)
            CallForegroundService.start(appContext, video, peerName)
            startSignalLoop()
            startStatusLoop()
            val offer = withContext(Dispatchers.Default) { requireNotNull(engine).createOffer() }
            sendSdp("offer", offer)
        } catch (t: Throwable) {
            fail(t.message ?: "Не удалось начать звонок")
        }
    }

    suspend fun acceptIncoming(invite: IncomingCall) {
        outgoing = false
        callId = invite.id
        peerId = invite.callerId
        val video = invite.type == CallType.VIDEO
        updateState(
            CallUiState(
                callId = invite.id,
                peerId = invite.callerId,
                peerName = invite.callerName,
                peerAvatarUrl = invite.callerAvatarUrl,
                type = invite.type,
                state = CallState.PREPARING,
                statusText = "Подключение…",
                speaker = video,
            )
        )
        try {
            val iceServers = loadIceServers()
            createEngine(video, iceServers)
            withContext(Dispatchers.IO) { api.acceptDirectCall(session.accessToken, invite.id) }
            configureAudio(video)
            CallForegroundService.start(appContext, video, invite.callerName)
            updateState(_uiState.value.copy(state = CallState.CONNECTING, statusText = "Соединение…"))
            startSignalLoop()
            startStatusLoop()
        } catch (t: Throwable) {
            fail(t.message ?: "Не удалось принять звонок")
        }
    }

    fun toggleMute() {
        val muted = !_uiState.value.muted
        engine?.setMuted(muted)
        updateState(_uiState.value.copy(muted = muted))
    }

    fun toggleSpeaker() {
        val speaker = !_uiState.value.speaker
        runCatching { audioManager.isSpeakerphoneOn = speaker }
        updateState(_uiState.value.copy(speaker = speaker))
    }

    fun toggleCamera() {
        if (_uiState.value.type != CallType.VIDEO) return
        val enabled = !_uiState.value.cameraEnabled
        engine?.setCameraEnabled(enabled)
        updateState(_uiState.value.copy(cameraEnabled = enabled))
    }

    fun switchCamera() {
        if (_uiState.value.type != CallType.VIDEO) return
        engine?.switchCamera(
            onDone = { front -> updateState(_uiState.value.copy(usingFrontCamera = front)) },
            onError = { message -> updateState(_uiState.value.copy(error = message)) },
        )
    }

    fun hangUp() {
        if (terminal) return
        scope.launch {
            runCatching { sendSignal("hangup", JSONObject().put("reason", "hangup")) }
            val status = if (_uiState.value.state == CallState.RINGING && outgoing) "cancelled" else "ended"
            if (callId.isNotBlank()) runCatching { api.finishCall(session.accessToken, callId, status, "hangup") }
            finishLocal(CallState.ENDED, "Звонок завершён")
        }
    }

    fun dispose() {
        terminal = true
        signalJob?.cancel()
        statusJob?.cancel()
        timerJob?.cancel()
        engine?.close()
        engine = null
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        restoreAudio()
        CallForegroundService.stop(appContext)
        scope.cancel()
    }

    private suspend fun loadIceServers(): List<IceServer> {
        val fallback = IceServer(listOf("stun:stun.l.google.com:19302"))
        val fetched = runCatching { withContext(Dispatchers.IO) { turnRepository.fetch(session.accessToken).iceServers } }
        val turn = fetched.getOrDefault(emptyList())
        turnFailure = when {
            fetched.isFailure -> fetched.exceptionOrNull()?.message?.takeIf { it.isNotBlank() } ?: "запрос не прошёл"
            turn.isEmpty() -> "сервер не вернул адресов"
            else -> null
        }
        turnFailure?.let { Log.w("OrionCall", "TURN unavailable: " + it) }
        return (turn + fallback).distinctBy { it.urls.joinToString("|") + it.username }
    }

    /**
     * Текст неудачи соединения. Без TURN на мобильной сети (CGNAT) путь для медиа обычно
     * не строится вовсе, поэтому если релея не было — называем это прямо, а не прячем
     * за общей формулировкой про ICE.
     */
    private fun connectionFailureText(fallbackText: String): String =
        turnFailure?.let { "Нет сервера ретрансляции — звонок не соединился" } ?: fallbackText

    private fun createEngine(video: Boolean, iceServers: List<IceServer>) {
        val created = WebRtcCallEngine(appContext, video, iceServers, object : WebRtcCallEngine.Listener {
            override fun onLocalIceCandidate(candidate: IceCandidate) {
                if (callId.isBlank() || peerId.isBlank() || terminal) return
                scope.launch {
                    runCatching {
                        sendSignal(
                            "ice",
                            JSONObject().apply {
                                put("sdp_mid", candidate.sdpMid)
                                put("sdp_mline_index", candidate.sdpMLineIndex)
                                put("candidate", candidate.sdp)
                            },
                        )
                    }
                }
            }

            override fun onRemoteVideoTrack(track: VideoTrack) {
                _remoteVideoTrack.value = track
            }

            override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> onConnected()
                    PeerConnection.PeerConnectionState.CONNECTING -> updateState(_uiState.value.copy(state = CallState.CONNECTING, statusText = "Соединение…"))
                    PeerConnection.PeerConnectionState.DISCONNECTED -> if (!terminal) updateState(_uiState.value.copy(statusText = "Восстановление связи…"))
                    PeerConnection.PeerConnectionState.FAILED -> failAsync(connectionFailureText("Не удалось установить соединение"))
                    PeerConnection.PeerConnectionState.CLOSED -> if (!terminal) finishLocalAsync(CallState.ENDED, "Звонок завершён")
                    else -> Unit
                }
            }

            override fun onIceState(state: PeerConnection.IceConnectionState) {
                if (state == PeerConnection.IceConnectionState.FAILED) {
                    failAsync(connectionFailureText("Ошибка ICE/TURN соединения"))
                }
            }

            override fun onCameraError(message: String) {
                updateState(_uiState.value.copy(error = message, cameraEnabled = false))
            }
        })
        engine = created
        _localVideoTrack.value = created.localVideoTrack
    }

    private fun startSignalLoop() {
        signalJob?.cancel()
        signalJob = scope.launch {
            while (isActive && !terminal) {
                val signals = runCatching {
                    api.callSignals(session.accessToken, callId, session.userId, lastSignalId)
                }.getOrDefault(emptyList())
                for (signal in signals) {
                    lastSignalId = maxOf(lastSignalId, signal.id)
                    runCatching { processSignal(signal) }.onFailure {
                        updateState(_uiState.value.copy(error = it.message ?: "Ошибка сигнализации звонка"))
                    }
                }
                delay(350)
            }
        }
    }

    private fun startStatusLoop() {
        statusJob?.cancel()
        statusJob = scope.launch {
            val ringStartedAt = System.currentTimeMillis()
            while (isActive && !terminal) {
                val remoteState = runCatching { api.callState(session.accessToken, callId) }.getOrNull()
                when (remoteState) {
                    CallState.DECLINED -> finishLocal(CallState.DECLINED, "Вызов отклонён")
                    CallState.MISSED -> finishLocal(CallState.MISSED, "Нет ответа")
                    CallState.FAILED -> finishLocal(CallState.FAILED, "Звонок завершился с ошибкой")
                    CallState.ENDED -> finishLocal(CallState.ENDED, "Звонок завершён")
                    CallState.ACTIVE -> if (_uiState.value.state == CallState.RINGING) {
                        updateState(_uiState.value.copy(state = CallState.CONNECTING, statusText = "Соединение…"))
                    }
                    else -> Unit
                }
                if (outgoing && _uiState.value.state == CallState.RINGING && System.currentTimeMillis() - ringStartedAt > 50_000) {
                    runCatching { api.finishCall(session.accessToken, callId, "missed", "no_answer") }
                    finishLocal(CallState.MISSED, "Нет ответа")
                }
                delay(900)
            }
        }
    }

    private suspend fun processSignal(signal: CallSignal) {
        if (signal.senderId == session.userId) return
        val payload = JSONObject(signal.payloadJson.ifBlank { "{}" })
        when (signal.kind) {
            "offer" -> {
                if (outgoing) return
                val sdp = payload.optString("sdp")
                if (sdp.isBlank()) return
                requireNotNull(engine).setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, sdp))
                remoteSdpReady = true
                flushPendingIce()
                val answer = requireNotNull(engine).createAnswer()
                sendSdp("answer", answer)
                updateState(_uiState.value.copy(state = CallState.CONNECTING, statusText = "Соединение…"))
            }
            "answer" -> {
                if (!outgoing) return
                val sdp = payload.optString("sdp")
                if (sdp.isBlank()) return
                requireNotNull(engine).setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, sdp))
                remoteSdpReady = true
                flushPendingIce()
                updateState(_uiState.value.copy(state = CallState.CONNECTING, statusText = "Соединение…"))
            }
            "ice" -> {
                val candidate = IceCandidate(
                    payload.optString("sdp_mid").takeIf { it.isNotBlank() },
                    payload.optInt("sdp_mline_index", 0),
                    payload.optString("candidate"),
                )
                if (candidate.sdp.isBlank()) return
                if (remoteSdpReady || engine?.hasRemoteDescription == true) {
                    engine?.addRemoteIceCandidate(candidate)
                } else {
                    pendingRemoteIce += candidate
                }
            }
            "hangup" -> finishLocal(CallState.ENDED, "Звонок завершён")
        }
    }

    private fun flushPendingIce() {
        pendingRemoteIce.forEach { engine?.addRemoteIceCandidate(it) }
        pendingRemoteIce.clear()
    }

    private suspend fun sendSdp(kind: String, description: SessionDescription) {
        sendSignal(
            kind,
            JSONObject().apply {
                put("type", description.type.canonicalForm())
                put("sdp", description.description)
            },
        )
    }

    private suspend fun sendSignal(kind: String, payload: JSONObject) {
        if (callId.isBlank() || peerId.isBlank()) return
        withContext(Dispatchers.IO) {
            api.sendCallSignal(
                token = session.accessToken,
                callId = callId,
                senderId = session.userId,
                recipientId = peerId,
                kind = kind,
                payload = payload,
            )
        }
    }

    private fun configureAudio(video: Boolean) {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = video
        }
        updateState(_uiState.value.copy(speaker = video))
    }

    private fun restoreAudio() {
        runCatching {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun onConnected() {
        if (terminal) return
        updateState(_uiState.value.copy(state = CallState.ACTIVE, statusText = "Соединено", error = null))
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            var seconds = 0
            while (isActive && !terminal && _uiState.value.state == CallState.ACTIVE) {
                updateState(_uiState.value.copy(elapsedSeconds = seconds))
                delay(1_000)
                seconds++
            }
        }
    }

    private fun failAsync(message: String) {
        if (terminal) return
        scope.launch { fail(message) }
    }

    private suspend fun fail(message: String) {
        if (terminal) return
        if (callId.isNotBlank()) runCatching {
            withContext(Dispatchers.IO) { api.finishCall(session.accessToken, callId, "failed", message.take(100)) }
        }
        finishLocal(CallState.FAILED, "Ошибка звонка", message)
    }

    private fun finishLocalAsync(state: CallState, status: String) {
        if (terminal) return
        scope.launch { finishLocal(state, status) }
    }

    private fun finishLocal(state: CallState, status: String, error: String? = null) {
        if (terminal) return
        terminal = true
        signalJob?.cancel()
        statusJob?.cancel()
        timerJob?.cancel()
        engine?.close()
        engine = null
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        restoreAudio()
        CallForegroundService.stop(appContext)
        updateState(_uiState.value.copy(state = state, statusText = status, error = error))
    }

    private fun updateState(next: CallUiState) {
        _uiState.value = next
    }
}
