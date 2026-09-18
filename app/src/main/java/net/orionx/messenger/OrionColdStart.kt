package net.orionx.messenger

import android.content.Context
import net.orionx.messenger.cloud.CloudConversation
import net.orionx.messenger.cloud.CloudProfile
import org.json.JSONArray
import org.json.JSONObject

/*
 * Cold-start cache.
 *
 * `XScreenCache` only survives while the process lives, so a fresh launch still had to
 * wait on the network twice: a full-screen spinner until the profile arrived, then an
 * empty chat list until the conversations did.
 *
 * This writes the last profile and conversation list to disk, so a launch paints the
 * real screen immediately and refreshes behind it. Everything is stamped with the
 * account id — a different user never sees the previous one's rows.
 */
private const val X_COLD_PREFS = "orionx_cold_start"

internal object XColdStart {

    fun saveProfile(context: Context, profile: CloudProfile) {
        val json = JSONObject().apply {
            put("id", profile.id)
            put("email", profile.email)
            put("nickname", profile.nickname)
            put("display_name", profile.displayName)
            put("role", profile.role)
            put("blocked", profile.blocked)
            put("avatar_url", profile.avatarUrl)
            put("bio", profile.bio)
            put("phone", profile.phone)
            put("birthday", profile.birthday)
            put("location", profile.location)
            put("website", profile.website)
            put("status_text", profile.statusText)
        }
        prefs(context).edit().putString("profile_${profile.id}", json.toString()).apply()
    }

    fun loadProfile(context: Context, userId: String): CloudProfile? {
        val raw = prefs(context).getString("profile_$userId", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            CloudProfile(
                id = o.getString("id"),
                email = o.optString("email"),
                nickname = o.optString("nickname"),
                displayName = o.optString("display_name"),
                role = o.optString("role", "user"),
                blocked = o.optBoolean("blocked"),
                avatarUrl = o.optString("avatar_url"),
                bio = o.optString("bio"),
                phone = o.optString("phone"),
                birthday = o.optString("birthday"),
                location = o.optString("location"),
                website = o.optString("website"),
                statusText = o.optString("status_text"),
            )
        }.getOrNull()
    }

    /** Only the columns the chats list draws — the rest is refetched anyway. */
    fun saveConversations(context: Context, userId: String, list: List<CloudConversation>) {
        val array = JSONArray()
        list.take(MAX_ROWS).forEach { c ->
            array.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("title", c.title)
                    put("type", c.type)
                    put("owner_id", c.ownerId)
                    put("description", c.description)
                    put("avatar_url", c.avatarUrl)
                    put("username", c.username)
                    put("is_public", c.isPublic)
                    put("archived", c.archived)
                    put("peer_display_name", c.peerDisplayName)
                    put("peer_nickname", c.peerNickname)
                    put("peer_avatar_url", c.peerAvatarUrl)
                    put("unread_count", c.unreadCount)
                    put("last_message_preview", c.lastMessagePreview)
                    put("last_message_at", c.lastMessageAt)
                    put("last_message_sender_id", c.lastMessageSenderId)
                    put("last_message_mine", c.lastMessageMine)
                    put("last_message_read", c.lastMessageRead)
                    put("last_message_delivered", c.lastMessageDelivered)
                }
            )
        }
        prefs(context).edit().putString("chats_$userId", array.toString()).apply()
    }

    fun loadConversations(context: Context, userId: String): List<CloudConversation> {
        val raw = prefs(context).getString("chats_$userId", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val o = array.getJSONObject(index)
                CloudConversation(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    type = o.optString("type", "direct"),
                    ownerId = o.optString("owner_id"),
                    description = o.optString("description"),
                    avatarUrl = o.optString("avatar_url"),
                    username = o.optString("username"),
                    isPublic = o.optBoolean("is_public"),
                    archived = o.optBoolean("archived"),
                    peerDisplayName = o.optString("peer_display_name"),
                    peerNickname = o.optString("peer_nickname"),
                    peerAvatarUrl = o.optString("peer_avatar_url"),
                    unreadCount = o.optInt("unread_count"),
                    lastMessagePreview = o.optString("last_message_preview"),
                    lastMessageAt = o.optString("last_message_at"),
                    lastMessageSenderId = o.optString("last_message_sender_id"),
                    lastMessageMine = o.optBoolean("last_message_mine"),
                    lastMessageRead = o.optBoolean("last_message_read"),
                    lastMessageDelivered = o.optBoolean("last_message_delivered"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Oria's row is drawn from her last reply; keeping it avoids an empty subtitle on launch. */
    fun saveOria(context: Context, userId: String, preview: String, time: String) {
        prefs(context).edit()
            .putString("oria_preview_$userId", preview)
            .putString("oria_time_$userId", time)
            .apply()
    }

    fun loadOria(context: Context, userId: String): Pair<String, String> {
        val p = prefs(context)
        return p.getString("oria_preview_$userId", "").orEmpty() to p.getString("oria_time_$userId", "").orEmpty()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(X_COLD_PREFS, Context.MODE_PRIVATE)

    /** A screenful and some scroll; the rest arrives with the refresh. */
    private const val MAX_ROWS = 40
}
