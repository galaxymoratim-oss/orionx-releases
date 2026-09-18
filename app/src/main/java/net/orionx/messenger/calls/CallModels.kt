package net.orionx.messenger.calls

enum class CallType { VOICE, VIDEO }
enum class CallState { PREPARING, RINGING, CONNECTING, ACTIVE, ENDED, DECLINED, MISSED, FAILED }

data class CallSession(
    val id: String,
    val conversationId: String,
    val createdBy: String,
    val peerId: String,
    val peerName: String,
    val peerAvatarUrl: String = "",
    val type: CallType,
    val state: CallState,
    val createdAt: String,
)

data class IncomingCall(
    val id: String,
    val conversationId: String,
    val callerId: String,
    val callerName: String,
    val callerAvatarUrl: String,
    val type: CallType,
    val startedAt: String,
)

data class CallSignal(
    val id: Long,
    val callId: String,
    val senderId: String,
    val recipientId: String?,
    val kind: String,
    val payloadJson: String,
)

data class CallUiState(
    val callId: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val peerAvatarUrl: String = "",
    val type: CallType = CallType.VOICE,
    val state: CallState = CallState.PREPARING,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val cameraEnabled: Boolean = true,
    val usingFrontCamera: Boolean = true,
    val elapsedSeconds: Int = 0,
    val statusText: String = "Подготовка звонка…",
    val error: String? = null,
)
