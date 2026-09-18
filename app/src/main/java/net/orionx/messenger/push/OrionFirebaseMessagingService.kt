package net.orionx.messenger.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.orionx.messenger.cloud.CloudSessionStore
import net.orionx.messenger.cloud.SupabaseApi
import net.orionx.messenger.cloud.accessTokenNeedsRefresh
import net.orionx.messenger.cloud.isExpiredJwt

class OrionFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        PushTokenSync.savePending(this, token)
        scope.launch { runCatching { PushTokenSync.syncCurrent(this@OrionFirebaseMessagingService, SupabaseApi()) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val conversationId = message.data["conversation_id"]
        val callId = message.data["call_id"]
        val preferences = getSharedPreferences("orionx_redesign_settings", MODE_PRIVATE)
        val kind = message.data["kind"] ?: message.data["type"] ?: "message"
        val isCall = kind.lowercase() in setOf("call", "audio_call", "video_call")

        if (!isCall && !conversationId.isNullOrBlank()) {
            scope.launch {
                val store = CloudSessionStore(this@OrionFirebaseMessagingService)
                var session = store.load()
                if (session != null) {
                    val api = SupabaseApi()
                    if (session.accessTokenNeedsRefresh()) {
                        session = runCatching { api.refresh(session.refreshToken) }.onSuccess(store::save).getOrNull()
                    }
                    val active = session
                    if (active != null) {
                        runCatching { api.markConversationDelivered(active.accessToken, conversationId) }
                            .onFailure { failure ->
                                if (failure.isExpiredJwt()) {
                                    runCatching { api.refresh(active.refreshToken) }
                                        .onSuccess { renewed ->
                                            store.save(renewed)
                                            runCatching { api.markConversationDelivered(renewed.accessToken, conversationId) }
                                        }
                                }
                            }
                    }
                }
            }
        }

        val enabled = when (kind.lowercase()) {
            "group" -> preferences.getBoolean("notify_groups", true)
            "channel", "announcement" -> preferences.getBoolean("notify_channels", true)
            "call", "audio_call", "video_call" -> preferences.getBoolean("notify_calls", true)
            else -> preferences.getBoolean("notify_messages", true)
        }
        if (!enabled) return

        val title = message.notification?.title ?: message.data["title"] ?: "OrionX"
        val originalBody = message.notification?.body ?: message.data["body"] ?: getString(net.orionx.messenger.R.string.new_message)
        val preview = preferences.getBoolean("notify_preview", true)

        if (isCall) {
            OrionNotifications.showIncomingCall(
                context = this,
                title = title,
                callerName = if (preview) originalBody else "Входящий звонок",
                conversationId = conversationId,
                callId = callId,
            )
            return
        }

        val body = if (preview) originalBody else getString(net.orionx.messenger.R.string.new_message)
        showNotification(title, body, conversationId)
    }

    private fun showNotification(title: String, body: String, conversationId: String?) {
        if (OrionMutedChats.isMuted(this, conversationId)) return
        OrionNotifications.show(this, title, body, conversationId)
    }
}
