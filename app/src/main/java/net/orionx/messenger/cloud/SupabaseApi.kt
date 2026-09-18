package net.orionx.messenger.cloud

import net.orionx.messenger.calls.CallSession
import net.orionx.messenger.calls.CallSignal
import net.orionx.messenger.calls.CallState
import net.orionx.messenger.calls.CallType
import net.orionx.messenger.calls.IncomingCall
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.UUID

class SupabaseApi {
    private fun request(path: String, method: String = "GET", token: String? = null, body: ByteArray? = null, contentType: String = "application/json", prefer: String? = null): String {
        val connection = (URL(SupabaseConfig.URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", contentType)
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (prefer != null) setRequestProperty("Prefer", prefer)
            doInput = true
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body) }
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching {
                val o = JSONObject(text)
                o.optString("msg").ifBlank { o.optString("message") }.ifBlank { o.optString("error_description") }
            }.getOrNull()
            throw IllegalStateException(message?.ifBlank { null } ?: "Supabase HTTP $code: $text")
        }
        return text
    }

    private fun json(path: String, method: String = "GET", token: String? = null, body: JSONObject? = null, prefer: String? = null) =
        request(path, method, token, body?.toString()?.toByteArray(), "application/json", prefer)

    fun signUp(email: String, password: String, nickname: String, displayName: String): CloudSession? {
        val payload = JSONObject().apply {
            put("email", email.trim().lowercase()); put("password", password)
            put("data", JSONObject().apply { put("nickname", nickname.trim().removePrefix("@").lowercase()); put("display_name", displayName.trim()) })
        }
        val o = JSONObject(json("/auth/v1/signup", "POST", body = payload))
        return if (o.has("access_token")) parseSession(o) else null
    }

    fun signIn(email: String, password: String): CloudSession = parseSession(JSONObject(json("/auth/v1/token?grant_type=password", "POST", body = JSONObject().apply { put("email", email.trim().lowercase()); put("password", password) })))
    fun verifySignupOtp(email: String, code: String): CloudSession = parseSession(
        JSONObject(
            json(
                "/auth/v1/verify",
                "POST",
                body = JSONObject().apply {
                    put("type", "signup")
                    put("email", email.trim().lowercase())
                    put("token", code.trim())
                }
            )
        )
    )

    fun resendSignupOtp(email: String) {
        json(
            "/auth/v1/resend",
            "POST",
            body = JSONObject().apply {
                put("type", "signup")
                put("email", email.trim().lowercase())
            }
        )
    }

    fun requestPasswordReset(email: String) {
        json(
            "/auth/v1/recover",
            "POST",
            body = JSONObject().apply { put("email", email.trim().lowercase()) }
        )
    }

    fun refresh(refreshToken: String): CloudSession = parseSession(JSONObject(json("/auth/v1/token?grant_type=refresh_token", "POST", body = JSONObject().apply { put("refresh_token", refreshToken) })))
    private fun parseSession(o: JSONObject): CloudSession { val user = o.getJSONObject("user"); return CloudSession(o.getString("access_token"), o.getString("refresh_token"), user.getString("id"), user.optString("email")) }

    fun profile(token: String, userId: String): CloudProfile = JSONArray(json("/rest/v1/profiles?id=eq.${enc(userId)}&select=*", token = token)).let { if (it.length()==0) error("Профиль не найден") else profileFrom(it.getJSONObject(0)) }
    fun updateProfile(token: String, nickname: String, displayName: String): CloudProfile = profileFrom(JSONObject(json("/rest/v1/rpc/update_my_profile", "POST", token, JSONObject().apply { put("p_nickname",nickname.trim().removePrefix("@")); put("p_display_name",displayName.trim()) })))

    fun updateExtendedProfile(
        token: String,
        userId: String,
        nickname: String,
        displayName: String,
        bio: String,
        phone: String,
        birthday: String,
        location: String,
        website: String,
        avatarUrl: String,
    ): CloudProfile {
        // userId remains in the signature for compatibility. The RPC always writes auth.uid(),
        // which prevents another account from being modified and avoids RLS PATCH failures.
        val normalizedBirthday = birthday.trim().takeIf { it.isNotBlank() }?.let { value ->
            java.time.LocalDate.parse(value).also { require(!it.isAfter(java.time.LocalDate.now())) { "Дата рождения не может быть в будущем" } }.toString()
        }
        val payload = JSONObject().apply {
            put("p_nickname", nickname.trim().removePrefix("@").lowercase())
            put("p_display_name", displayName.trim())
            put("p_bio", bio.trim())
            put("p_phone", phone.trim())
            if (normalizedBirthday == null) put("p_birthday", JSONObject.NULL) else put("p_birthday", normalizedBirthday)
            put("p_location", location.trim())
            put("p_website", website.trim())
            if (avatarUrl.isBlank()) put("p_avatar_url", JSONObject.NULL) else put("p_avatar_url", avatarUrl)
        }
        return runCatching {
            profileFrom(JSONObject(json("/rest/v1/rpc/update_my_extended_profile", "POST", token, payload)))
        }.getOrElse { rpcError ->
            // Compatibility fallback for installations where the extended-profile RPC has not yet
            // been refreshed after a Supabase pause/migration. RLS still restricts this PATCH to
            // the authenticated user's own row.
            val patch = JSONObject().apply {
                put("nickname", nickname.trim().removePrefix("@").lowercase())
                put("display_name", displayName.trim())
                put("bio", bio.trim())
                put("phone", phone.trim())
                if (normalizedBirthday == null) put("birthday", JSONObject.NULL) else put("birthday", normalizedBirthday)
                put("location", location.trim())
                put("website", website.trim())
                if (avatarUrl.isBlank()) put("avatar_url", JSONObject.NULL) else put("avatar_url", avatarUrl)
                put("updated_at", java.time.Instant.now().toString())
            }
            runCatching {
                val rows = JSONArray(
                    json(
                        "/rest/v1/profiles?id=eq.${enc(userId)}",
                        "PATCH",
                        token,
                        patch,
                        "return=representation",
                    )
                )
                if (rows.length() == 0) error("Профиль не найден после сохранения")
                profileFrom(rows.getJSONObject(0))
            }.getOrElse { throw rpcError }
        }
    }

    fun requestEmailChange(token: String, newEmail: String) {
        json("/auth/v1/user", "PUT", token, JSONObject().apply { put("email", newEmail.trim().lowercase()) })
    }

    fun updatePassword(token: String, newPassword: String) {
        require(newPassword.length >= 8) { "Пароль должен содержать минимум 8 символов" }
        json("/auth/v1/user", "PUT", token, JSONObject().apply { put("password", newPassword) })
    }

    fun userDevices(token: String, userId: String): List<CloudDevice> {
        val rows = JSONArray(
            json(
                "/rest/v1/user_devices?user_id=eq.${enc(userId)}&revoked_at=is.null&select=id,user_id,device_name,platform,app_version,last_active_at,installation_id&order=last_active_at.desc",
                token = token,
            )
        )
        return List(rows.length()) { deviceFrom(rows.getJSONObject(it)) }
    }

    fun removeDevice(token: String, deviceId: String) {
        json("/rest/v1/rpc/revoke_device", "POST", token, JSONObject().apply { put("p_device_id", deviceId) })
    }

    /**
     * Отвязывает это устройство от аккаунта при выходе: без него сервер продолжал
     * слать пуши вышедшему аккаунту и держал устройство «активным» в списке.
     */
    fun revokeDeviceByInstallation(token: String, userId: String, installationId: String) {
        if (installationId.isBlank()) return
        val rows = JSONArray(
            json(
                "/rest/v1/user_devices?user_id=eq.${enc(userId)}&installation_id=eq.${enc(installationId)}&revoked_at=is.null&select=id&limit=1",
                token = token,
            )
        )
        if (rows.length() == 0) return
        val id = rows.getJSONObject(0).optString("id")
        if (id.isNotBlank()) removeDevice(token, id)
    }

    fun uploadProfileAvatar(token: String, userId: String, mimeType: String, bytes: ByteArray): String {
        require(bytes.size <= 8 * 1024 * 1024) { "Изображение должно быть не больше 8 МБ" }
        val extension = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val path = "$userId/avatar_${System.currentTimeMillis()}.$extension"
        request("/storage/v1/object/avatars/${encPath(path)}", "POST", token, bytes, mimeType)
        return "${SupabaseConfig.URL}/storage/v1/object/public/avatars/$path"
    }

    fun ensureOriaThread(token: String, userId: String): OriaThread {
        val existing = JSONArray(json("/rest/v1/oria_threads?user_id=eq.${enc(userId)}&archived=eq.false&select=*&order=updated_at.desc&limit=1", token = token))
        if (existing.length() > 0) return oriaThreadFrom(existing.getJSONObject(0))
        val created = JSONArray(
            json(
                "/rest/v1/oria_threads",
                "POST",
                token,
                JSONObject().apply { put("user_id", userId); put("title", "Oria AI") },
                "return=representation",
            )
        )
        require(created.length() > 0) { "Не удалось создать диалог Oria" }
        return oriaThreadFrom(created.getJSONObject(0))
    }

    fun oriaMessages(token: String, threadId: String): List<OriaMessage> {
        val rows = JSONArray(json("/rest/v1/oria_messages?thread_id=eq.${enc(threadId)}&select=*&order=created_at.asc", token = token))
        return List(rows.length()) { oriaMessageFrom(rows.getJSONObject(it)) }
    }

    fun askOria(token: String, threadId: String, prompt: String): OriaMessage {
        val userRows = JSONArray(
            json(
                "/rest/v1/oria_messages",
                "POST",
                token,
                JSONObject().apply { put("thread_id", threadId); put("role", "user"); put("content", prompt.trim()) },
                "return=representation",
            )
        )
        require(userRows.length() > 0) { "Сообщение не сохранено" }

        val response = JSONObject(
            request(
                "/functions/v1/oria-chat",
                "POST",
                token,
                JSONObject().apply { put("thread_id", threadId); put("message", prompt.trim()) }.toString().toByteArray(),
                "application/json",
            )
        )
        val answer = response.optString("answer").ifBlank { response.optString("content") }
        require(answer.isNotBlank()) { "Oria не вернула ответ" }
        val assistantRows = JSONArray(
            json(
                "/rest/v1/oria_messages",
                "POST",
                token,
                JSONObject().apply { put("thread_id", threadId); put("role", "assistant"); put("content", answer) },
                "return=representation",
            )
        )
        require(assistantRows.length() > 0) { "Ответ Oria не сохранён" }
        return oriaMessageFrom(assistantRows.getJSONObject(0))
    }

    fun searchProfiles(token: String, query: String): List<CloudProfile> {
        // `)` и `,` в запросе ломают постгрестов or=(...) — заменяем их пробелом.
        val q=query.trim().removePrefix("@").lowercase().replace(Regex("[(),]"), " ").trim(); if(q.isBlank()) return emptyList()
        val rows=JSONArray(json("/rest/v1/profiles?or=(${enc("nickname.ilike.*$q*,display_name.ilike.*$q*")})&blocked=eq.false&select=id,email,nickname,display_name,role,blocked,avatar_url,bio,phone,birthday,location,website,status_text&limit=50",token=token))
        return List(rows.length()){ profileFrom(rows.getJSONObject(it)) }
    }
    fun allProfiles(token: String): List<CloudProfile> = JSONArray(json("/rest/v1/profiles?select=id,email,nickname,display_name,role,blocked,avatar_url,bio,phone,birthday,location,website,status_text&order=created_at.desc&limit=500",token=token)).let { r->List(r.length()){profileFrom(r.getJSONObject(it))} }
    fun searchChannels(token: String, query: String): List<CloudConversation> { val q=query.trim().removePrefix("@").lowercase().replace(Regex("[(),]"), " ").trim(); if(q.isBlank()) return emptyList(); val r=JSONArray(json("/rest/v1/conversations?type=in.(group,channel)&is_public=eq.true&or=(${enc("title.ilike.*$q*,description.ilike.*$q*,username.ilike.*$q*")})&select=*&limit=50",token=token)); return List(r.length()){conversationFrom(r.getJSONObject(it))} }
    fun myConversations(token: String, userId: String): List<CloudConversation> {
        // Defense in depth: never trust the admin-capable view alone for the normal messenger UI.
        // First resolve the current account memberships, then fetch only those conversations.
        val memberRows = JSONArray(
            json(
                "/rest/v1/conversation_members?user_id=eq.${enc(userId)}&left_at=is.null&select=conversation_id",
                token = token,
            )
        )
        val ids = buildList {
            for (i in 0 until memberRows.length()) {
                memberRows.getJSONObject(i).optString("conversation_id").takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }.distinct()
        if (ids.isEmpty()) return emptyList()
        val filter = ids.joinToString(",")
        val rows = JSONArray(
            json(
                "/rest/v1/my_conversations?id=in.($filter)&select=*&order=last_message_at.desc.nullslast,created_at.desc",
                token = token,
            )
        )
        val allowed = ids.toHashSet()
        return List(rows.length()) { conversationFrom(rows.getJSONObject(it)) }.filter { it.id in allowed }
    }
    fun createDirect(token:String,targetUserId:String)=conversationFrom(JSONObject(json("/rest/v1/rpc/create_direct_conversation","POST",token,JSONObject().apply{put("target_user_id",targetUserId)})))
    fun createCommunity(
        token: String,
        title: String,
        description: String,
        type: String,
        memberIds: List<String>,
        isPublic: Boolean = false,
        avatarUrl: String? = null,
        username: String? = null,
        // Everything the canvas create form sets besides the identity fields. `v3`
        // applies them inside the same call, so a new community is never briefly live
        // with the wrong permissions.
        flags: CommunityFlags = CommunityFlags(),
    ): CloudConversation = conversationFrom(
        JSONObject(
            json(
                "/rest/v1/rpc/create_community_v3",
                "POST",
                token,
                JSONObject().apply {
                    put("p_title", title.trim())
                    put("p_description", description.trim())
                    put("p_type", type)
                    put("p_member_ids", JSONArray(memberIds))
                    put("p_is_public", isPublic)
                    if (avatarUrl.isNullOrBlank()) put("p_avatar_url", JSONObject.NULL) else put("p_avatar_url", avatarUrl)
                    if (username.isNullOrBlank()) put("p_username", JSONObject.NULL) else put("p_username", username.trim().removePrefix("@").lowercase())
                    put("p_members_can_post", flags.membersCanPost)
                    put("p_slow_mode_seconds", flags.slowModeSeconds)
                    put("p_members_can_invite", flags.membersCanInvite)
                    put("p_history_visible_to_new", flags.historyVisibleToNew)
                    put("p_media_allowed", flags.mediaAllowed)
                    put("p_join_requires_approval", flags.joinRequiresApproval)
                    put("p_allow_forwarding", flags.allowForwarding)
                    put("p_show_author_signature", flags.showAuthorSignature)
                    put("p_show_view_counter", flags.showViewCounter)
                    put("p_discussion_enabled", flags.discussionEnabled)
                    if (flags.reactionSetOnCreate.isNotEmpty()) {
                        put("p_reaction_set", JSONArray(flags.reactionSetOnCreate))
                    }
                },
            )
        )
    )

    /**
     * Joins a public community, or files a request when it screens new members.
     *
     * @return `"joined"` or `"pending"`.
     */
    fun joinChannel(token: String, conversationId: String): String {
        val raw = json(
            "/rest/v1/rpc/join_public_community",
            "POST",
            token,
            JSONObject().apply { put("p_conversation_id", conversationId) },
        )
        return runCatching { JSONObject(raw).optString("status", "joined") }.getOrDefault("joined")
    }

    /** Pending «заявки на вступление», for the community settings screen. Staff only. */
    fun communityJoinRequests(token: String, conversationId: String): List<CloudJoinRequest> {
        val rows = JSONArray(
            json("/rest/v1/rpc/community_join_requests_v1", "POST", token, JSONObject().apply {
                put("p_conversation_id", conversationId)
            })
        )
        return List(rows.length()) { index ->
            val o = rows.getJSONObject(index)
            CloudJoinRequest(
                userId = o.getString("user_id"),
                displayName = o.optString("display_name").takeUnless { it == "null" }.orEmpty(),
                nickname = o.optString("nickname").takeUnless { it == "null" }.orEmpty(),
                avatarUrl = o.optString("avatar_url").takeUnless { it == "null" }.orEmpty(),
                createdAt = o.optString("created_at").takeUnless { it == "null" }.orEmpty(),
            )
        }
    }

    fun resolveJoinRequest(token: String, conversationId: String, userId: String, approve: Boolean) {
        json("/rest/v1/rpc/resolve_join_request_v1", "POST", token, JSONObject().apply {
            put("p_conversation_id", conversationId)
            put("p_user_id", userId)
            put("p_approve", approve)
        })
    }

    // -------------------------------------------------------------------------
    // Groups and channels (migration 017). `conversations` has no UPDATE policy
    // and `conversation_members` no write policy, so every mutation goes through
    // a SECURITY DEFINER RPC that re-checks the caller's role server-side.
    // -------------------------------------------------------------------------

    /**
     * The three counters on the canvas profile screen. Each is a plain projection of
     * ids, so the row count is the answer without pulling any payload.
     */
    fun profileCounters(token: String, userId: String): Triple<Int, Int, Int> {
        fun count(path: String): Int = runCatching { JSONArray(json(path, token = token)).length() }.getOrDefault(0)
        val chats = count("/rest/v1/conversation_members?user_id=eq.${enc(userId)}&left_at=is.null&select=conversation_id")
        // Attachments live on the message row itself — `message_attachments` is part of
        // the schema but this client never writes to it, so counting there returns 0.
        val media = count("/rest/v1/messages?sender_id=eq.${enc(userId)}&attachment_url=not.is.null&select=id")
        val contacts = count("/rest/v1/contacts?owner_id=eq.${enc(userId)}&select=contact_id")
        return Triple(chats, media, contacts)
    }

    /**
     * One conversation row with every column. `myConversations` projects only the
     * list summary, so the settings screens re-read the row through this to get
     * `members_can_post`, `members_can_invite` and `slow_mode_seconds`.
     */
    fun conversation(token: String, conversationId: String): CloudConversation? {
        val rows = JSONArray(
            json("/rest/v1/conversations?id=eq.${enc(conversationId)}&select=*&limit=1", token = token)
        )
        return if (rows.length() == 0) null else conversationFrom(rows.getJSONObject(0))
    }

    /** Roster of a group or channel, ordered owner → admins → moderators → members. */
    fun communityMembers(token: String, conversationId: String): List<CloudMember> {
        val rows = JSONArray(
            json("/rest/v1/rpc/community_members_v1", "POST", token, JSONObject().apply {
                put("p_conversation_id", conversationId)
            })
        )
        return List(rows.length()) { memberFrom(rows.getJSONObject(it)) }
    }

    fun communityPins(token: String, conversationId: String): List<CloudPinnedMessage> {
        val rows = JSONArray(
            json("/rest/v1/rpc/community_pins_v1", "POST", token, JSONObject().apply {
                put("p_conversation_id", conversationId)
            })
        )
        return List(rows.length()) {
            val o = rows.getJSONObject(it)
            CloudPinnedMessage(
                messageId = o.getString("message_id"),
                body = o.optString("body"),
                senderId = o.optString("sender_id"),
                senderName = o.optString("sender_name"),
                pinnedAt = o.optString("pinned_at"),
            )
        }
    }

    fun setMessagePinned(token: String, conversationId: String, messageId: String, pinned: Boolean) {
        json("/rest/v1/rpc/set_message_pinned_v1", "POST", token, JSONObject().apply {
            put("p_conversation_id", conversationId)
            put("p_message_id", messageId)
            put("p_pinned", pinned)
        })
    }

    /**
     * Updates community settings. Every parameter is optional — `null` leaves the
     * stored value untouched, which is how the server distinguishes "not changed"
     * from "cleared".
     */
    fun updateCommunity(
        token: String,
        conversationId: String,
        title: String? = null,
        description: String? = null,
        avatarUrl: String? = null,
        username: String? = null,
        isPublic: Boolean? = null,
        membersCanPost: Boolean? = null,
        slowModeSeconds: Int? = null,
        membersCanInvite: Boolean? = null,
        historyVisibleToNew: Boolean? = null,
        mediaAllowed: Boolean? = null,
        joinRequiresApproval: Boolean? = null,
        allowForwarding: Boolean? = null,
        showAuthorSignature: Boolean? = null,
        showViewCounter: Boolean? = null,
        discussionEnabled: Boolean? = null,
        reactionsEnabled: Boolean? = null,
        pinsForEveryone: Boolean? = null,
        hideMemberList: Boolean? = null,
        autoDeleteSeconds: Int? = null,
        reactionSet: List<String>? = null,
    ): CloudConversation = conversationFrom(
        JSONObject(
            json("/rest/v1/rpc/update_community_v1", "POST", token, JSONObject().apply {
                put("p_conversation_id", conversationId)
                if (title != null) put("p_title", title)
                if (description != null) put("p_description", description)
                if (avatarUrl != null) put("p_avatar_url", avatarUrl)
                if (username != null) put("p_username", username)
                if (isPublic != null) put("p_is_public", isPublic)
                if (membersCanPost != null) put("p_members_can_post", membersCanPost)
                if (slowModeSeconds != null) put("p_slow_mode_seconds", slowModeSeconds)
                if (membersCanInvite != null) put("p_members_can_invite", membersCanInvite)
                if (historyVisibleToNew != null) put("p_history_visible_to_new", historyVisibleToNew)
                if (mediaAllowed != null) put("p_media_allowed", mediaAllowed)
                if (joinRequiresApproval != null) put("p_join_requires_approval", joinRequiresApproval)
                if (allowForwarding != null) put("p_allow_forwarding", allowForwarding)
                if (showAuthorSignature != null) put("p_show_author_signature", showAuthorSignature)
                if (showViewCounter != null) put("p_show_view_counter", showViewCounter)
                if (discussionEnabled != null) put("p_discussion_enabled", discussionEnabled)
                if (reactionsEnabled != null) put("p_reactions_enabled", reactionsEnabled)
                if (pinsForEveryone != null) put("p_pins_for_everyone", pinsForEveryone)
                if (hideMemberList != null) put("p_hide_member_list", hideMemberList)
                if (autoDeleteSeconds != null) put("p_auto_delete_seconds", autoDeleteSeconds)
                if (reactionSet != null) put("p_reaction_set", JSONArray(reactionSet))
            })
        )
    )

    /** Returns how many were actually added; people already in are skipped. */
    fun addCommunityMembers(token: String, conversationId: String, userIds: List<String>): Int =
        json("/rest/v1/rpc/add_community_members_v1", "POST", token, JSONObject().apply {
            put("p_conversation_id", conversationId)
            put("p_user_ids", JSONArray(userIds))
        }).trim().toIntOrNull() ?: 0

    fun removeCommunityMember(token: String, conversationId: String, userId: String) {
        json("/rest/v1/rpc/remove_community_member_v1", "POST", token, JSONObject().apply {
            put("p_conversation_id", conversationId)
            put("p_user_id", userId)
        })
    }

    /** Owner-only. [role] is `admin`, `moderator`, `member` or `subscriber`. */
    fun setMemberRole(token: String, conversationId: String, userId: String, role: String, adminTag: String? = null) {
        json("/rest/v1/rpc/set_member_role_v1", "POST", token, JSONObject().apply {
            put("p_conversation_id", conversationId)
            put("p_user_id", userId)
            put("p_role", role)
            if (adminTag.isNullOrBlank()) put("p_admin_tag", JSONObject.NULL) else put("p_admin_tag", adminTag.trim())
        })
    }

    fun deleteCommunity(token: String, conversationId: String) {
        json("/rest/v1/rpc/delete_community_v1", "POST", token, JSONObject().apply {
            put("p_conversation_id", conversationId)
        })
    }

    private fun memberFrom(o: JSONObject) = CloudMember(
        userId = o.getString("user_id"),
        displayName = o.optString("display_name"),
        nickname = o.optString("nickname"),
        avatarUrl = o.optString("avatar_url").takeUnless { it == "null" }.orEmpty(),
        verified = o.optBoolean("verified"),
        isOnline = o.optBoolean("is_online"),
        lastSeenAt = o.optString("last_seen_at").takeUnless { it == "null" }.orEmpty(),
        memberRole = o.optString("member_role", "member"),
        adminTag = o.optString("admin_tag").takeUnless { it == "null" }.orEmpty(),
        joinedAt = o.optString("joined_at").takeUnless { it == "null" }.orEmpty(),
        notificationsEnabled = o.optBoolean("notifications_enabled", true),
    )

    /** Mutes or unmutes the community for the calling user only. */
    fun setMemberMuted(token: String, conversationId: String, muted: Boolean) {
        json("/rest/v1/rpc/set_member_muted_v1", "POST", token, JSONObject().apply {
            put("p_conversation_id", conversationId)
            put("p_muted", muted)
        })
    }

    fun messages(token:String,conversationId:String):List<CloudMessage> {
        val rows = JSONArray(
            json(
                "/rest/v1/message_feed?conversation_id=eq.${enc(conversationId)}&select=*&order=created_at.asc",
                token=token,
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                if (row.optBoolean("deleted_for_all", false)) continue
                if (row.optBoolean("hidden_for_me", false)) continue
                add(messageFrom(row))
            }
        }
    }
    fun sendMessage(token:String,conversationId:String,senderId:String,text:String,replyToId:String?=null,attachment:UploadedFile?=null,metadata:org.json.JSONObject?=null){
        val body=JSONObject().apply{put("conversation_id",conversationId);put("sender_id",senderId);put("body",text.trim()); if(replyToId!=null)put("reply_to_id",replyToId); if(attachment!=null){put("attachment_url",attachment.publicUrl);put("attachment_name",attachment.name);put("attachment_type",attachment.mimeType)}; if(metadata!=null&&metadata.length()>0)put("metadata",metadata)}
        json("/rest/v1/messages","POST",token,body,"return=minimal")
    }
    fun markConversationRead(token:String,conversationId:String){json("/rest/v1/rpc/mark_conversation_read","POST",token,JSONObject().apply{put("p_conversation_id",conversationId)})}

    fun markConversationDelivered(token: String, conversationId: String) {
        json("/rest/v1/rpc/mark_conversation_delivered", "POST", token, JSONObject().apply {
            put("p_conversation_id", conversationId)
        })
    }

    fun markAllDelivered(token: String) {
        json("/rest/v1/rpc/mark_all_delivered", "POST", token, JSONObject())
    }

    fun messageReceiptStates(token: String, messageIds: List<String>, currentUserId: String): Map<String, MessageReceiptState> {
        if (messageIds.isEmpty()) return emptyMap()
        val ids = messageIds.joinToString(",")
        val rows = JSONArray(
            json(
                "/rest/v1/message_receipts?message_id=in.($ids)&user_id=neq.${enc(currentUserId)}&select=message_id,delivered_at,read_at",
                token = token,
            )
        )
        val states = linkedMapOf<String, MessageReceiptState>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val id = row.optString("message_id")
            val delivered = row.optString("delivered_at").let { it.isNotBlank() && it != "null" }
            val read = row.optString("read_at").let { it.isNotBlank() && it != "null" }
            val old = states[id]
            states[id] = MessageReceiptState(
                delivered = delivered || old?.delivered == true,
                read = read || old?.read == true,
            )
        }
        return states
    }

    fun peerPresence(token: String, conversationId: String, currentUserId: String): CloudPeerPresence? {
        val members = JSONArray(
            json(
                "/rest/v1/conversation_members?conversation_id=eq.${enc(conversationId)}&user_id=neq.${enc(currentUserId)}&left_at=is.null&select=user_id&limit=1",
                token = token,
            )
        )
        if (members.length() == 0) return null
        val peerId = members.getJSONObject(0).optString("user_id")
        if (peerId.isBlank()) return null
        val profiles = JSONArray(
            json(
                "/rest/v1/profiles?id=eq.${enc(peerId)}&select=id,display_name,nickname,is_online,last_seen_at,avatar_url,bio,location,website&limit=1",
                token = token,
            )
        )
        if (profiles.length() == 0) return null
        val row = profiles.getJSONObject(0)
        val lastSeen = row.optString("last_seen_at").takeIf { it != "null" }.orEmpty()
        val fresh = runCatching {
            lastSeen.isNotBlank() && java.time.Instant.parse(lastSeen).isAfter(java.time.Instant.now().minusSeconds(75))
        }.getOrDefault(false)
        return CloudPeerPresence(
            userId = peerId,
            displayName = row.optString("display_name"),
            nickname = row.optString("nickname"),
            isOnline = row.optBoolean("is_online") && fresh,
            lastSeenAt = lastSeen,
            avatarUrl = row.optString("avatar_url").takeUnless { it == "null" }.orEmpty(),
            bio = row.optString("bio").takeUnless { it == "null" }.orEmpty(),
            location = row.optString("location").takeUnless { it == "null" }.orEmpty(),
            website = row.optString("website").takeUnless { it == "null" }.orEmpty(),
        )
    }
    fun setConversationArchivedForUser(token: String, conversationId: String, userId: String, archived: Boolean) {
        // userId is kept in the signature for source compatibility; the RPC always uses auth.uid().
        json(
            "/rest/v1/rpc/set_conversation_archived",
            "POST", token, JSONObject().apply {
                put("p_conversation_id", conversationId)
                put("p_archived", archived)
            }
        )
    }

    fun setConversationsArchivedForUser(token: String, conversationIds: Collection<String>, archived: Boolean) {
        if (conversationIds.isEmpty()) return
        runCatching {
            json(
                "/rest/v1/rpc/set_conversations_archived",
                "POST",
                token,
                JSONObject().apply {
                    put("p_conversation_ids", JSONArray(conversationIds.toList()))
                    put("p_archived", archived)
                },
            )
        }.getOrElse {
            // Compatibility with installations that have the older single-chat RPC only.
            conversationIds.forEach { conversationId ->
                setConversationArchivedForUser(token, conversationId, "", archived)
            }
        }
    }

    fun uploadCommunityAvatar(token: String, userId: String, mimeType: String, bytes: ByteArray): String {
        require(bytes.size <= 8 * 1024 * 1024) { "Изображение должно быть не больше 8 МБ" }
        val extension = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val path = "$userId/community_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension"
        request("/storage/v1/object/avatars/${encPath(path)}", "POST", token, bytes, mimeType)
        return "${SupabaseConfig.URL}/storage/v1/object/public/avatars/$path"
    }

    fun userSettings(token: String, userId: String): CloudUserSettings {
        val rows = JSONArray(json("/rest/v1/user_settings?user_id=eq.${enc(userId)}&select=*&limit=1", token = token))
        if (rows.length() == 0) return CloudUserSettings()
        val o = rows.getJSONObject(0)
        val reactionsArray = o.optJSONArray("reaction_emojis")
        val reactions = if (reactionsArray == null) CloudUserSettings().reactionEmojis else buildList {
            for (i in 0 until reactionsArray.length()) add(reactionsArray.optString(i))
        }.filter { it.isNotBlank() }
        return CloudUserSettings(
            theme = o.optString("theme", "system"),
            accentColor = o.optString("accent_color", "purple"),
            language = o.optString("language", "ru"),
            fontScale = o.optDouble("font_scale", 1.0).toFloat(),
            compactMode = o.optBoolean("compact_mode"),
            reduceMotion = o.optBoolean("reduce_motion"),
            readReceipts = o.optBoolean("read_receipts", true),
            showOnline = o.optBoolean("show_online", true),
            allowCalls = o.optString("allow_calls", "everyone"),
            allowGroupInvites = o.optString("allow_group_invites", "everyone"),
            reactionEmojis = reactions.ifEmpty { CloudUserSettings().reactionEmojis },
        )
    }

    fun updateUserSettings(token: String, userId: String, patch: JSONObject) {
        patch.put("updated_at", java.time.Instant.now().toString())
        json("/rest/v1/user_settings?user_id=eq.${enc(userId)}", "PATCH", token, patch, "return=minimal")
    }

    fun updateReactionEmojis(token: String, userId: String, emojis: List<String>) {
        updateUserSettings(token, userId, JSONObject().apply { put("reaction_emojis", JSONArray(emojis.distinct().take(12))) })
    }

    fun uploadMessageFile(token:String,userId:String,fileName:String,mimeType:String,bytes:ByteArray):UploadedFile{
        val safe=fileName.replace(Regex("[^A-Za-z0-9._-]"),"_").takeLast(90).ifBlank{"file"}
        val path="$userId/${System.currentTimeMillis()}_${UUID.randomUUID()}_$safe"
        request("/storage/v1/object/message-media/${encPath(path)}","POST",token,bytes,mimeType)
        return UploadedFile("${SupabaseConfig.URL}/storage/v1/object/public/message-media/$path",safe,mimeType)
    }
    fun oriaMediaAssets(token: String, kind: String): List<OriaMediaAsset> {
        val rows = JSONArray(json("/rest/v1/oria_media_assets?kind=eq.${enc(kind)}&select=*&order=created_at.desc&limit=80", token = token))
        return List(rows.length()) { oriaMediaFrom(rows.getJSONObject(it)) }
    }

    fun addOriaMediaAsset(
        token: String,
        ownerId: String,
        kind: String,
        title: String,
        note: String,
        file: UploadedFile,
    ): OriaMediaAsset {
        val body = JSONObject().apply {
            put("owner_id", ownerId)
            put("kind", kind)
            put("title", title.take(80))
            put("prompt", note.take(500))
            put("public_url", file.publicUrl)
            put("mime_type", file.mimeType)
            put("is_public", false)
        }
        val result = JSONArray(json("/rest/v1/oria_media_assets", "POST", token, body, "return=representation"))
        require(result.length() > 0) { "Media library insert failed" }
        return oriaMediaFrom(result.getJSONObject(0))
    }

    fun editMessage(token:String,messageId:String,text:String){json("/rest/v1/rpc/edit_message","POST",token,JSONObject().apply{put("p_message_id",messageId);put("p_body",text.trim())})}
    fun deleteMessageForAll(token:String,messageId:String){json("/rest/v1/rpc/delete_message_for_all","POST",token,JSONObject().apply{put("p_message_id",messageId)})}
    fun toggleReaction(token:String,messageId:String,emoji:String){json("/rest/v1/rpc/toggle_reaction","POST",token,JSONObject().apply{put("p_message_id",messageId);put("p_emoji",emoji)})}



    fun registerPushToken(token: String, userId: String, installationId: String, pushToken: String, deviceName: String, appVersion: String): Boolean {
        return touchCurrentDevice(token, userId, installationId, deviceName, appVersion, pushToken)
    }

    fun updatePresence(token: String, online: Boolean) {
        json("/rest/v1/rpc/update_presence", "POST", token, JSONObject().apply { put("p_online", online) })
    }

    fun touchCurrentDevice(
        token: String,
        userId: String,
        installationId: String,
        deviceName: String,
        appVersion: String,
        pushToken: String? = null,
    ): Boolean {
        val raw = json(
            "/rest/v1/rpc/touch_device",
            "POST",
            token,
            JSONObject().apply {
                put("p_installation_id", installationId)
                put("p_device_name", deviceName)
                put("p_app_version", appVersion)
                if (pushToken == null) put("p_push_token", JSONObject.NULL) else put("p_push_token", pushToken)
            },
        )
        return raw.trim().equals("true", ignoreCase = true)
    }

    fun setTyping(token: String, conversationId: String, typing: Boolean) {
        json("/rest/v1/rpc/set_typing", "POST", token, JSONObject().apply {
            put("p_conversation_id", conversationId); put("p_is_typing", typing)
        })
    }

    fun typingUsers(token: String, conversationId: String, currentUserId: String): List<String> {
        val rows = JSONArray(json("/rest/v1/typing_states?conversation_id=eq.${enc(conversationId)}&user_id=neq.${enc(currentUserId)}&is_typing=eq.true&updated_at=gt.${enc(java.time.Instant.now().minusSeconds(8).toString())}&select=user_id", token = token))
        val ids = buildList { for (i in 0 until rows.length()) add(rows.getJSONObject(i).getString("user_id")) }
        if (ids.isEmpty()) return emptyList()
        val inFilter = ids.joinToString(",")
        val profiles = JSONArray(json("/rest/v1/profiles?id=in.(${enc(inFilter)})&select=display_name", token = token))
        return List(profiles.length()) { profiles.getJSONObject(it).optString("display_name") }.filter { it.isNotBlank() }
    }


    /**
     * Display names for a set of senders, in one request.
     *
     * The canvas writes a group row's preview as «артём: релиз в четверг», but
     * `my_conversations` only carries `last_message_sender_id`. Resolving the names here
     * keeps the view untouched: one `id=in.(…)` call covers every group and channel on
     * screen, and names change rarely enough to cache for the life of the process.
     */
    fun displayNamesOf(token: String, userIds: Collection<String>): Map<String, String> {
        val ids = userIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val rows = JSONArray(
            json("/rest/v1/profiles?id=in.(${enc(ids.joinToString(","))})&select=id,display_name,nickname", token = token)
        )
        return buildMap {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val name = row.optString("display_name").ifBlank { row.optString("nickname") }
                if (name.isNotBlank()) put(row.getString("id"), name)
            }
        }
    }

    fun hideMessageForMe(token: String, messageId: String) {
        json("/rest/v1/rpc/hide_message_for_me", "POST", token, JSONObject().apply { put("p_message_id", messageId) })
    }

    fun leaveConversation(token: String, conversationId: String) {
        json("/rest/v1/rpc/leave_conversation", "POST", token, JSONObject().apply { put("p_conversation_id", conversationId) })
    }

    fun saveDraft(token: String, conversationId: String, userId: String, bodyText: String, replyToId: String?) {
        val payload = JSONObject().apply {
            put("conversation_id", conversationId); put("user_id", userId); put("body", bodyText)
            if (replyToId == null) put("reply_to_id", JSONObject.NULL) else put("reply_to_id", replyToId)
            put("updated_at", java.time.Instant.now().toString())
        }
        json("/rest/v1/message_drafts?on_conflict=conversation_id,user_id", "POST", token, payload, "resolution=merge-duplicates,return=minimal")
    }

    fun loadDraft(token: String, conversationId: String, userId: String): Pair<String, String?> {
        val rows = JSONArray(json("/rest/v1/message_drafts?conversation_id=eq.${enc(conversationId)}&user_id=eq.${enc(userId)}&select=body,reply_to_id&limit=1", token = token))
        if (rows.length() == 0) return "" to null
        val row = rows.getJSONObject(0)
        return row.optString("body") to row.optString("reply_to_id").takeIf { it.isNotBlank() && it != "null" }
    }

    fun clearDraft(token: String, conversationId: String, userId: String) {
        json("/rest/v1/message_drafts?conversation_id=eq.${enc(conversationId)}&user_id=eq.${enc(userId)}", "DELETE", token, prefer = "return=minimal")
    }

    fun blockUser(token: String, blockerId: String, blockedId: String) {
        json("/rest/v1/user_blocks", "POST", token, JSONObject().apply { put("blocker_id", blockerId); put("blocked_id", blockedId) }, "resolution=ignore-duplicates,return=minimal")
    }

    fun unblockUser(token: String, blockerId: String, blockedId: String) {
        json("/rest/v1/user_blocks?blocker_id=eq.${enc(blockerId)}&blocked_id=eq.${enc(blockedId)}", "DELETE", token, prefer = "return=minimal")
    }

    fun blockedUserIds(token: String, blockerId: String): Set<String> {
        val rows = JSONArray(json("/rest/v1/user_blocks?blocker_id=eq.${enc(blockerId)}&select=blocked_id", token = token))
        return buildSet { for (i in 0 until rows.length()) add(rows.getJSONObject(i).getString("blocked_id")) }
    }


    // -------------------------------------------------------------------------
    // OrionX 6.8.0: 1-to-1 WebRTC call signalling.
    // -------------------------------------------------------------------------
    fun startDirectCall(
        token: String,
        conversationId: String,
        peerName: String,
        peerAvatarUrl: String,
        video: Boolean,
    ): CallSession {
        val o = JSONObject(
            json(
                "/rest/v1/rpc/start_direct_call",
                "POST",
                token,
                JSONObject().apply {
                    put("p_conversation_id", conversationId)
                    put("p_call_type", if (video) "video" else "audio")
                },
            )
        )
        return CallSession(
            id = o.getString("id"),
            conversationId = o.getString("conversation_id"),
            createdBy = o.getString("initiated_by"),
            peerId = o.getString("peer_id"),
            peerName = peerName,
            peerAvatarUrl = peerAvatarUrl,
            type = if (o.optString("call_type") == "video") CallType.VIDEO else CallType.VOICE,
            state = CallState.RINGING,
            createdAt = o.optString("started_at"),
        )
    }

    fun notifyIncomingCall(token: String, callId: String) {
        json(
            "/functions/v1/call-notify",
            "POST",
            token,
            JSONObject().apply { put("call_id", callId) },
        )
    }

    fun acceptDirectCall(token: String, callId: String) {
        json(
            "/rest/v1/rpc/accept_direct_call",
            "POST",
            token,
            JSONObject().apply { put("p_call_id", callId) },
        )
    }

    fun declineDirectCall(token: String, callId: String) {
        json(
            "/rest/v1/rpc/decline_direct_call",
            "POST",
            token,
            JSONObject().apply { put("p_call_id", callId) },
        )
    }

    fun finishCall(token: String, callId: String, status: String = "ended", reason: String = "hangup") {
        json(
            "/rest/v1/rpc/finish_call",
            "POST",
            token,
            JSONObject().apply {
                put("p_call_id", callId)
                put("p_status", status)
                put("p_reason", reason)
            },
        )
    }

    fun pendingIncomingCall(token: String, userId: String): IncomingCall? {
        val participantRows = JSONArray(
            json(
                "/rest/v1/call_participants?user_id=eq.${enc(userId)}&state=in.(invited,ringing)&select=call_id&limit=8",
                token = token,
            )
        )
        if (participantRows.length() == 0) return null
        val callIds = buildList {
            for (i in 0 until participantRows.length()) {
                participantRows.getJSONObject(i).optString("call_id").takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        if (callIds.isEmpty()) return null
        val rows = JSONArray(
            json(
                "/rest/v1/calls?id=in.(${callIds.joinToString(",")})&status=eq.ringing&select=id,conversation_id,initiated_by,call_type,status,started_at&order=started_at.desc&limit=1",
                token = token,
            )
        )
        if (rows.length() == 0) return null
        val call = rows.getJSONObject(0)
        val startedAt = call.optString("started_at")
        val tooOld = runCatching {
            startedAt.isNotBlank() && java.time.Instant.parse(startedAt).isBefore(java.time.Instant.now().minusSeconds(70))
        }.getOrDefault(false)
        if (tooOld) return null
        val callerId = call.getString("initiated_by")
        val profileRows = JSONArray(
            json(
                "/rest/v1/profiles?id=eq.${enc(callerId)}&select=id,display_name,nickname,avatar_url&limit=1",
                token = token,
            )
        )
        val caller = profileRows.optJSONObject(0)
        val displayName = caller?.optString("display_name").orEmpty().ifBlank {
            caller?.optString("nickname").orEmpty().ifBlank { "OrionX" }
        }
        return IncomingCall(
            id = call.getString("id"),
            conversationId = call.optString("conversation_id"),
            callerId = callerId,
            callerName = displayName,
            callerAvatarUrl = caller?.optString("avatar_url").orEmpty().takeUnless { it == "null" }.orEmpty(),
            type = if (call.optString("call_type") == "video") CallType.VIDEO else CallType.VOICE,
            startedAt = startedAt,
        )
    }

    fun callState(token: String, callId: String): CallState? {
        val rows = JSONArray(
            json(
                "/rest/v1/calls?id=eq.${enc(callId)}&select=status&limit=1",
                token = token,
            )
        )
        if (rows.length() == 0) return null
        return when (rows.getJSONObject(0).optString("status")) {
            "ringing" -> CallState.RINGING
            "active" -> CallState.ACTIVE
            "declined" -> CallState.DECLINED
            "missed" -> CallState.MISSED
            "failed" -> CallState.FAILED
            "ended", "cancelled" -> CallState.ENDED
            else -> null
        }
    }

    fun sendCallSignal(
        token: String,
        callId: String,
        senderId: String,
        recipientId: String,
        kind: String,
        payload: JSONObject,
    ) {
        json(
            "/rest/v1/call_signals",
            "POST",
            token,
            JSONObject().apply {
                put("call_id", callId)
                put("sender_id", senderId)
                put("recipient_id", recipientId)
                put("signal_type", kind)
                put("payload", payload)
            },
            "return=minimal",
        )
    }

    fun callSignals(token: String, callId: String, recipientId: String, afterId: Long): List<CallSignal> {
        val rows = JSONArray(
            json(
                "/rest/v1/call_signals?call_id=eq.${enc(callId)}&recipient_id=eq.${enc(recipientId)}&id=gt.$afterId&select=id,call_id,sender_id,recipient_id,signal_type,payload&order=id.asc&limit=100",
                token = token,
            )
        )
        return List(rows.length()) { index ->
            val o = rows.getJSONObject(index)
            CallSignal(
                id = o.getLong("id"),
                callId = o.getString("call_id"),
                senderId = o.getString("sender_id"),
                recipientId = o.optString("recipient_id").takeIf { it.isNotBlank() && it != "null" },
                kind = o.getString("signal_type"),
                payloadJson = o.optJSONObject("payload")?.toString() ?: o.optString("payload", "{}"),
            )
        }
    }

    fun latestUpdate(token:String?=null):AppUpdate?=JSONArray(json("/rest/v1/app_updates?published=eq.true&select=*&order=version_code.desc&limit=1",token=token)).let{if(it.length()==0)null else updateFrom(it.getJSONObject(0))}
    fun publishUpdate(token:String,versionName:String,versionCode:Int,changelog:String,apkUrl:String?,mandatory:Boolean):AppUpdate{val r=JSONArray(json("/rest/v1/app_updates","POST",token,JSONObject().apply{put("version_name",versionName.trim());put("version_code",versionCode);put("title","OrionX v${versionName.trim()}");put("changelog",changelog.trim());put("apk_url",apkUrl?.trim());put("mandatory",mandatory);put("published",true)},"return=representation"));return updateFrom(r.getJSONObject(0))}
    fun announcements(token:String):List<Announcement> = JSONArray(json("/rest/v1/announcements?published=eq.true&select=*&order=created_at.desc",token=token)).let{r->List(r.length()){announcementFrom(r.getJSONObject(it))}}
    fun publishAnnouncement(token:String,title:String,bodyText:String,createdBy:String):Announcement{val r=JSONArray(json("/rest/v1/announcements","POST",token,JSONObject().apply{put("title",title.trim());put("body",bodyText.trim());put("created_by",createdBy);put("published",true)},"return=representation"));return announcementFrom(r.getJSONObject(0))}
    fun setUserBlocked(token:String,userId:String,blocked:Boolean){json("/rest/v1/rpc/admin_set_user_blocked","POST",token,JSONObject().apply{put("p_user_id",userId);put("p_blocked",blocked)})}
    fun adminDeleteConversation(token:String,conversationId:String){json("/rest/v1/rpc/admin_delete_conversation","POST",token,JSONObject().apply{put("p_conversation_id",conversationId)})}
    fun adminStats(token:String):AdminStats=JSONObject(json("/rest/v1/rpc/admin_stats","POST",token,JSONObject())).let{AdminStats(it.optInt("users"),it.optInt("conversations"),it.optInt("messages"),it.optInt("blocked_users"))}

    private fun profileFrom(o:JSONObject)=CloudProfile(
        id = o.getString("id"),
        email = o.optString("email"),
        nickname = o.optString("nickname"),
        displayName = o.optString("display_name"),
        role = o.optString("role", "user"),
        blocked = o.optBoolean("blocked"),
        avatarUrl = o.optString("avatar_url").takeUnless { it == "null" }.orEmpty(),
        bio = o.optString("bio"),
        phone = o.optString("phone"),
        birthday = o.optString("birthday").takeUnless { it == "null" }.orEmpty(),
        location = o.optString("location"),
        website = o.optString("website"),
        statusText = o.optString("status_text"),
    )
    private fun conversationFrom(o: JSONObject) = CloudConversation(
        id = o.getString("id"),
        title = o.getString("title"),
        type = o.getString("type"),
        ownerId = o.getString("owner_id"),
        description = o.optString("description"),
        avatarUrl = o.optString("avatar_url").takeUnless { it == "null" }.orEmpty(),
        username = o.optString("username").takeUnless { it == "null" }.orEmpty(),
        isPublic = o.optBoolean("is_public"),
        archived = o.optBoolean("archived"),
        // Absent on the list RPCs, which only project the row summary — default to
        // the permissive server defaults so a partial row never reads as locked down.
        membersCanPost = o.optBoolean("members_can_post", true),
        membersCanInvite = o.optBoolean("members_can_invite", true),
        slowModeSeconds = o.optInt("slow_mode_seconds", 0).coerceAtLeast(0),
        historyVisibleToNew = o.optBoolean("history_visible_to_new", true),
        mediaAllowed = o.optBoolean("media_allowed", true),
        joinRequiresApproval = o.optBoolean("join_requires_approval", false),
        allowForwarding = o.optBoolean("allow_forwarding", true),
        showAuthorSignature = o.optBoolean("show_author_signature", true),
        showViewCounter = o.optBoolean("show_view_counter", true),
        discussionEnabled = o.optBoolean("discussion_enabled", true),
        reactionsEnabled = o.optBoolean("reactions_enabled", true),
        pinsForEveryone = o.optBoolean("pins_for_everyone", false),
        hideMemberList = o.optBoolean("hide_member_list", false),
        autoDeleteSeconds = o.optInt("auto_delete_seconds", 0).coerceAtLeast(0),
        reactionSet = o.optJSONArray("reaction_set")?.let { arr ->
            List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
        }.orEmpty(),
        peerDisplayName = o.optString("peer_display_name").takeUnless { it == "null" }.orEmpty(),
        peerNickname = o.optString("peer_nickname").takeUnless { it == "null" }.orEmpty(),
        peerAvatarUrl = o.optString("peer_avatar_url").takeUnless { it == "null" }.orEmpty(),
        unreadCount = o.optInt("unread_count", 0).coerceAtLeast(0),
        lastMessagePreview = o.optString("last_message_preview").takeUnless { it == "null" }.orEmpty(),
        lastMessageAt = o.optString("last_message_at").takeUnless { it == "null" }.orEmpty(),
        lastMessageSenderId = o.optString("last_message_sender_id").takeUnless { it == "null" }.orEmpty(),
        lastMessageMine = o.optBoolean("last_message_mine"),
        lastMessageRead = o.optBoolean("last_message_read"),
        lastMessageDelivered = o.optBoolean("last_message_delivered"),
    )
    private fun messageFrom(o: JSONObject): CloudMessage {
        val ro = o.optJSONObject("reactions")
        val reactions = mutableMapOf<String, Int>()
        if (ro != null) {
            val keys = ro.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                reactions[key] = ro.optInt(key)
            }
        }
        fun nullable(key: String): String? = o.optString(key).takeIf { it.isNotBlank() && it != "null" }
        return CloudMessage(
            id = o.getString("id"),
            conversationId = o.getString("conversation_id"),
            senderId = o.getString("sender_id"),
            senderName = o.optString("sender_name", "Пользователь"),
            messageType = o.optString("message_type", "text").ifBlank { "text" },
            body = o.optString("body"),
            createdAt = o.optString("created_at"),
            editedAt = nullable("edited_at"),
            replyToId = nullable("reply_to_id"),
            replyPreview = nullable("reply_preview"),
            replySenderName = nullable("reply_sender_name"),
            deletedForAll = o.optBoolean("deleted_for_all"),
            reactions = reactions,
            attachmentUrl = nullable("attachment_url"),
            attachmentName = nullable("attachment_name"),
            attachmentType = nullable("attachment_type"),
            authorSigned = o.optJSONObject("metadata")?.optBoolean("sign") == true,
        )
    }
    private fun oriaMediaFrom(o:JSONObject)=OriaMediaAsset(o.getString("id"),o.getString("owner_id"),o.getString("kind"),o.optString("title"),o.optString("prompt"),o.getString("public_url"),o.optString("mime_type", if(o.optString("kind")=="gif") "image/gif" else "image/png"),o.optString("created_at"))
    private fun oriaThreadFrom(o: JSONObject)=OriaThread(o.getString("id"), o.getString("user_id"), o.optString("title", "Oria AI"), o.optString("created_at"))
    private fun oriaMessageFrom(o: JSONObject)=OriaMessage(o.getString("id"), o.getString("thread_id"), o.optString("role"), o.optString("content"), o.optString("created_at"))
    private fun deviceFrom(o: JSONObject)=CloudDevice(
        id = o.getString("id"),
        userId = o.getString("user_id"),
        deviceName = o.optString("device_name", "Android"),
        platform = o.optString("platform", "android"),
        appVersion = o.optString("app_version"),
        lastActiveAt = o.optString("last_active_at"),
        installationId = o.optString("installation_id"),
    )
    private fun announcementFrom(o:JSONObject)=Announcement(o.getString("id"),o.getString("title"),o.getString("body"),o.optString("created_at"))
    private fun updateFrom(o:JSONObject)=AppUpdate(o.getString("id"),o.getString("version_name"),o.getInt("version_code"),o.getString("title"),o.getString("changelog"),o.optString("apk_url").takeIf{it.isNotBlank()&&it!="null"},o.optBoolean("mandatory"),o.optString("published_at"))
    private fun enc(v:String)=URLEncoder.encode(v,"UTF-8")
    private fun encPath(v:String)=v.split('/').joinToString("/"){enc(it)}

    // ---- App update releases (in-app OTA) ----
    fun uploadReleaseFile(token: String, fileName: String, mimeType: String, bytes: ByteArray): String {
        val safe = fileName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }
        val path = "${System.currentTimeMillis()}_$safe"
        request("/storage/v1/object/releases/${encPath(path)}", "POST", token, bytes, mimeType.ifBlank { "application/octet-stream" })
        return "${SupabaseConfig.URL}/storage/v1/object/public/releases/$path"
    }

    fun publishRelease(token: String, version: String, versionCode: Int, description: String, apkUrl: String, imageUrl: String?) {
        val body = JSONObject().apply {
            put("version", version.trim())
            put("version_code", versionCode)
            put("description", description.trim())
            put("apk_url", apkUrl)
            put("image_url", if (imageUrl.isNullOrBlank()) JSONObject.NULL else imageUrl)
        }
        json("/rest/v1/app_releases", "POST", token, body, "return=minimal")
    }

    fun latestRelease(token: String): AppRelease? {
        val rows = JSONArray(json("/rest/v1/app_releases?select=*&order=version_code.desc&limit=1", token = token))
        if (rows.length() == 0) return null
        val o = rows.getJSONObject(0)
        return AppRelease(
            id = o.optString("id"),
            version = o.optString("version"),
            versionCode = o.optInt("version_code"),
            description = o.optString("description").takeUnless { it == "null" }.orEmpty(),
            apkUrl = o.optString("apk_url"),
            imageUrl = o.optString("image_url").takeUnless { it.isBlank() || it == "null" },
            createdAt = o.optString("created_at"),
        )
    }
}
