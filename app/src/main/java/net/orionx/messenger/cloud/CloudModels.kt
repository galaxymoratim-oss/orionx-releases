package net.orionx.messenger.cloud

data class AppRelease(
    val id: String,
    val version: String,
    val versionCode: Int,
    val description: String,
    val apkUrl: String,
    val imageUrl: String?,
    val createdAt: String,
)

data class CloudSession(val accessToken: String, val refreshToken: String, val userId: String, val email: String)

data class CloudProfile(
    val id: String,
    val email: String,
    val nickname: String,
    val displayName: String,
    val role: String,
    val blocked: Boolean,
    val avatarUrl: String = "",
    val bio: String = "",
    val phone: String = "",
    val birthday: String = "",
    val location: String = "",
    val website: String = "",
    val statusText: String = "",
)

data class CloudConversation(
    val id: String,
    val title: String,
    val type: String,
    val ownerId: String,
    val description: String,
    val avatarUrl: String = "",
    val username: String = "",
    val isPublic: Boolean = false,
    val archived: Boolean = false,
    /** Who may write. Migration 018 extended this from channels to groups as well. */
    val membersCanPost: Boolean = true,
    /** Groups: whether an ordinary member may add people (canvas: «участники приглашают»). */
    val membersCanInvite: Boolean = true,
    /** Minimum seconds between two messages from the same non-staff member; 0 = off. */
    val slowModeSeconds: Int = 0,
    // The rest of the canvas create-form switches, backed by migration 018.
    /** Off hides messages written before the member joined («история для новых»). */
    val historyVisibleToNew: Boolean = true,
    /** Off rejects attachments from non-staff members («медиа и файлы»). */
    val mediaAllowed: Boolean = true,
    /** On files a join request instead of admitting at once («заявки на вступление»). */
    val joinRequiresApproval: Boolean = false,
    /** Off rejects messages forwarded out of here («разрешить пересылку»). */
    val allowForwarding: Boolean = true,
    /** Channel feed display rules — stored server-side, read by the feed. */
    val showAuthorSignature: Boolean = true,
    val showViewCounter: Boolean = true,
    val discussionEnabled: Boolean = true,
    /** Off takes the reaction row off every message here («реакции»). */
    val reactionsEnabled: Boolean = true,
    /** On lets any member pin, not only staff («закреплять сообщения»). */
    val pinsForEveryone: Boolean = false,
    /** On shows the roster to staff only («скрыть список участников»). */
    val hideMemberList: Boolean = false,
    /** Seconds a message stays readable; 0 keeps it forever («автоудаление»). */
    val autoDeleteSeconds: Int = 0,
    /** The emoji this community offers on a long press («набор реакций»). */
    val reactionSet: List<String> = emptyList(),
    val peerDisplayName: String = "",
    val peerNickname: String = "",
    val peerAvatarUrl: String = "",
    val unreadCount: Int = 0,
    val lastMessagePreview: String = "",
    val lastMessageAt: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageMine: Boolean = false,
    val lastMessageRead: Boolean = false,
    val lastMessageDelivered: Boolean = false,
)

/**
 * The community settings the canvas create form collects, in one bundle.
 *
 * Defaults match the server defaults and the canvas' own `def` values, so a caller
 * that does not care about a switch simply leaves it alone.
 */
data class CommunityFlags(
    val membersCanPost: Boolean = true,
    val slowModeSeconds: Int = 0,
    val membersCanInvite: Boolean = true,
    val historyVisibleToNew: Boolean = true,
    val mediaAllowed: Boolean = true,
    val joinRequiresApproval: Boolean = false,
    val allowForwarding: Boolean = true,
    val showAuthorSignature: Boolean = true,
    val showViewCounter: Boolean = true,
    val discussionEnabled: Boolean = true,
    /** Набор реакций, выбранный прямо в форме создания. */
    val reactionSetOnCreate: List<String> = emptyList(),
    val reactionsEnabled: Boolean = true,
    val pinsForEveryone: Boolean = false,
    val hideMemberList: Boolean = false,
    val autoDeleteSeconds: Int = 0,
    val reactionSet: List<String> = emptyList(),
)

/** One pending «заявка на вступление». */
data class CloudJoinRequest(
    val userId: String,
    val displayName: String,
    val nickname: String,
    val avatarUrl: String,
    val createdAt: String,
)

data class CloudMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val createdAt: String,
    val editedAt: String? = null,
    val replyToId: String? = null,
    val replyPreview: String? = null,
    val replySenderName: String? = null,
    val deletedForAll: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(),
    val attachmentUrl: String? = null,
    val attachmentName: String? = null,
    val attachmentType: String? = null,
    val deliveredToOthers: Boolean = false,
    val readByOthers: Boolean = false,
    /** `text` для обычного сообщения, `system` — для служебной плашки ленты. */
    val messageType: String = "text",
    /** Пост канала опубликован с подписью автора («подписать автором»). */
    val authorSigned: Boolean = false,
)

data class CloudUserSettings(
    val theme: String = "system",
    val accentColor: String = "purple",
    val language: String = "ru",
    val fontScale: Float = 1f,
    val compactMode: Boolean = false,
    val reduceMotion: Boolean = false,
    val readReceipts: Boolean = true,
    val showOnline: Boolean = true,
    val allowCalls: String = "everyone",
    val allowGroupInvites: String = "everyone",
    val reactionEmojis: List<String> = listOf("👍", "❤️", "😂", "😮", "😢", "🔥"),
)

data class AppUpdate(val id: String, val versionName: String, val versionCode: Int, val title: String, val changelog: String, val apkUrl: String?, val mandatory: Boolean, val publishedAt: String)
data class Announcement(val id: String, val title: String, val body: String, val createdAt: String)
data class AdminStats(val users: Int, val conversations: Int, val messages: Int, val blockedUsers: Int)
data class UploadedFile(val publicUrl: String, val name: String, val mimeType: String)
data class MessageReceiptState(val delivered: Boolean = false, val read: Boolean = false)

data class CloudPeerPresence(
    val userId: String,
    val displayName: String,
    val nickname: String,
    val isOnline: Boolean,
    val lastSeenAt: String,
    val avatarUrl: String = "",
    val bio: String = "",
    val location: String = "",
    val website: String = "",
)

data class CloudDevice(
    val id: String,
    val userId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val lastActiveAt: String,
    val installationId: String = "",
)

data class OriaMediaAsset(
    val id: String,
    val ownerId: String,
    val kind: String,
    val title: String,
    val prompt: String,
    val publicUrl: String,
    val mimeType: String,
    val createdAt: String,
)

data class OriaThread(val id: String, val userId: String, val title: String, val createdAt: String)
data class OriaMessage(val id: String, val threadId: String, val role: String, val content: String, val createdAt: String)

/**
 * A member of a group or channel, as returned by `community_members_v1`.
 *
 * `memberRole` is one of `owner`, `admin`, `moderator`, `member`, `subscriber` —
 * the values `conversation_members_role_check` allows. [adminTag] is the short
 * custom label the canvas shows next to a name in the group feed («тег»).
 */
data class CloudMember(
    val userId: String,
    val displayName: String,
    val nickname: String,
    val avatarUrl: String = "",
    val verified: Boolean = false,
    val isOnline: Boolean = false,
    val lastSeenAt: String = "",
    val memberRole: String = "member",
    val adminTag: String = "",
    val joinedAt: String = "",
    /** False when this member muted the community for themselves. */
    val notificationsEnabled: Boolean = true,
) {
    val isOwner: Boolean get() = memberRole == "owner"

    /** Owner, admin and moderator all pass `is_conversation_staff` on the server. */
    val isStaff: Boolean get() = memberRole == "owner" || memberRole == "admin" || memberRole == "moderator"
}

/** A pinned message of a community, as returned by `community_pins_v1`. */
data class CloudPinnedMessage(
    val messageId: String,
    val body: String,
    val senderId: String,
    val senderName: String,
    val pinnedAt: String,
)
