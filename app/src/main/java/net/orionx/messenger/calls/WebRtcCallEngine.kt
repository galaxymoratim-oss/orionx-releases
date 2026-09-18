package net.orionx.messenger.calls

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebRtcCallEngine(
    context: Context,
    private val videoEnabled: Boolean,
    iceServers: List<IceServer>,
    private val listener: Listener,
) {
    interface Listener {
        fun onLocalIceCandidate(candidate: IceCandidate)
        fun onRemoteVideoTrack(track: VideoTrack)
        fun onConnectionState(state: PeerConnection.PeerConnectionState)
        fun onIceState(state: PeerConnection.IceConnectionState)
        fun onCameraError(message: String)
    }

    private val appContext = context.applicationContext
    val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrackInternal: VideoTrack? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var remoteDescriptionReady = false
    private var released = false

    val localVideoTrack: VideoTrack? get() = localVideoTrackInternal
    val hasRemoteDescription: Boolean get() = remoteDescriptionReady

    init {
        initializeWebRtc(appContext)
        val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        try {
            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .createPeerConnectionFactory()
        } finally {
            audioDeviceModule.release()
        }

        val pcIceServers = iceServers.mapNotNull { server ->
            if (server.urls.isEmpty()) null else PeerConnection.IceServer.builder(server.urls)
                .setUsername(server.username)
                .setPassword(server.credential)
                .createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(pcIceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }
        peerConnection = factory?.createPeerConnection(config, createPeerObserver())
            ?: error("Не удалось создать WebRTC PeerConnection")

        val currentFactory = requireNotNull(factory)
        audioSource = currentFactory.createAudioSource(MediaConstraints())
        audioTrack = currentFactory.createAudioTrack("orionx-audio", requireNotNull(audioSource)).also { track ->
            track.setEnabled(true)
            peerConnection?.addTrack(track, listOf("orionx-stream"))
        }

        if (videoEnabled) startCamera()
    }

    private fun startCamera() {
        val enumerator = Camera2Enumerator(appContext)
        val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: run {
                listener.onCameraError("Камера не найдена")
                return
            }
        val capturer = enumerator.createCapturer(cameraName, object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(errorDescription: String) = listener.onCameraError(errorDescription)
            override fun onCameraDisconnected() = listener.onCameraError("Камера отключена")
            override fun onCameraFreezed(errorDescription: String) = listener.onCameraError(errorDescription)
            override fun onCameraOpening(cameraName: String) = Unit
            override fun onFirstFrameAvailable() = Unit
            override fun onCameraClosed() = Unit
        }) as? CameraVideoCapturer ?: run {
            listener.onCameraError("Не удалось открыть камеру")
            return
        }
        cameraCapturer = capturer
        surfaceTextureHelper = SurfaceTextureHelper.create("OrionXCameraCapture", eglBase.eglBaseContext)
        videoSource = factory?.createVideoSource(false)
        val source = videoSource ?: return
        capturer.initialize(surfaceTextureHelper, appContext, source.capturerObserver)
        capturer.startCapture(720, 1280, 30)
        localVideoTrackInternal = factory?.createVideoTrack("orionx-video", source)?.also {
            it.setEnabled(true)
            peerConnection?.addTrack(it, listOf("orionx-stream"))
        }
    }

    suspend fun createOffer(): SessionDescription {
        val pc = requireNotNull(peerConnection)
        val offer = createSdp { observer -> pc.createOffer(observer, MediaConstraints()) }
        setLocalDescription(offer)
        return offer
    }

    suspend fun createAnswer(): SessionDescription {
        val pc = requireNotNull(peerConnection)
        val answer = createSdp { observer -> pc.createAnswer(observer, MediaConstraints()) }
        setLocalDescription(answer)
        return answer
    }

    suspend fun setRemoteDescription(description: SessionDescription) {
        val pc = requireNotNull(peerConnection)
        suspendCancellableCoroutine<Unit> { continuation ->
            pc.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    remoteDescriptionReady = true
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Remote SDP: $error"))
                }
            }, description)
        }
    }

    fun addRemoteIceCandidate(candidate: IceCandidate): Boolean = peerConnection?.addIceCandidate(candidate) == true

    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrackInternal?.setEnabled(enabled)
    }

    fun switchCamera(onDone: (Boolean) -> Unit, onError: (String) -> Unit) {
        val capturer = cameraCapturer ?: return onError("Камера недоступна")
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) = onDone(isFrontCamera)
            override fun onCameraSwitchError(errorDescription: String) = onError(errorDescription)
        })
    }

    fun close() {
        if (released) return
        released = true
        runCatching { cameraCapturer?.stopCapture() }
        runCatching { cameraCapturer?.dispose() }
        cameraCapturer = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null
        runCatching { localVideoTrackInternal?.dispose() }
        localVideoTrackInternal = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { audioTrack?.dispose() }
        audioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        runCatching { factory?.dispose() }
        factory = null
        runCatching { eglBase.release() }
    }

    private suspend fun createSdp(block: (SdpObserver) -> Unit): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            block(object : SdpObserverAdapter() {
                override fun onCreateSuccess(description: SessionDescription) {
                    if (continuation.isActive) continuation.resume(description)
                }

                override fun onCreateFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("SDP: $error"))
                }
            })
        }

    private suspend fun setLocalDescription(description: SessionDescription) {
        val pc = requireNotNull(peerConnection)
        suspendCancellableCoroutine<Unit> { continuation ->
            pc.setLocalDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Local SDP: $error"))
                }
            }, description)
        }
    }

    private fun createPeerObserver(): PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) = listener.onIceState(newState)
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) = listener.onLocalIceCandidate(candidate)
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) {
            stream.videoTracks.firstOrNull()?.let(listener::onRemoteVideoTrack)
        }
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(dataChannel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            (receiver.track() as? VideoTrack)?.let(listener::onRemoteVideoTrack)
        }
        override fun onTrack(transceiver: RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let(listener::onRemoteVideoTrack)
        }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) = listener.onConnectionState(newState)
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }

    companion object {
        @Volatile private var initialized = false

        private fun initializeWebRtc(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions(),
                )
                initialized = true
            }
        }
    }
}
