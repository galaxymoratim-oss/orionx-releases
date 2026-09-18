package net.orionx.messenger.calls

/** Temporary ICE server credentials returned by the trusted backend. */
data class IceServer(
    val urls: List<String>,
    val username: String = "",
    val credential: String = "",
)

data class TurnCredentialBundle(
    val iceServers: List<IceServer>,
    val expiresAtEpochSeconds: Long,
)
