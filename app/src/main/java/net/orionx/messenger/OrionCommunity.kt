package net.orionx.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RemoveModerator
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.CloudConversation
import net.orionx.messenger.cloud.CloudJoinRequest
import net.orionx.messenger.cloud.CloudMember
import net.orionx.messenger.cloud.CloudMessage
import net.orionx.messenger.cloud.CloudPinnedMessage
import net.orionx.messenger.cloud.CloudProfile
import net.orionx.messenger.cloud.CloudSession
import net.orionx.messenger.cloud.SupabaseApi

/*
 * Groups and channels — the management layer of the "OrionX Redesign" canvas.
 *
 * The canvas screens `groupinfo`, `gsettings`, `gadmins`, `gadd`, `chinfo`,
 * `chsettings` and `chadmins` all read the same three things (the conversation
 * row, the roster and the pins) and differ mainly in wording, so they share one
 * state holder and one set of building blocks here.
 *
 * The group and channel *feeds* are not in this file: `XConversationScreen`
 * already renders both — it branches on `conversation.type` for the header and
 * passes `showSenderName` for non-direct chats. What it lacked was a way in,
 * which its header and overflow button now provide by calling `onOpenCommunity`.
 */

// ---------------------------------------------------------------------------
// Canvas metrics
//
// The community screens use their own radii and text sizes, different from the
// shared `XShape`/`XType` scale. They are transcribed from the canvas rather than
// approximated, so each value below quotes the CSS it came from.
// ---------------------------------------------------------------------------

internal object XCommunityShape {
    /** `border-radius:30px` — the hero card. */
    val hero = RoundedCornerShape(30.dp)
    /** `88px;border-radius:30px` — the hero avatar is a squircle. */
    val heroAvatar = RoundedCornerShape(30.dp)
    /** `border-radius:20px` — the «о чём тут» inset. */
    val about = RoundedCornerShape(20.dp)
    /** `border-radius:26px` — roster and list cards. */
    val card = RoundedCornerShape(26.dp)
    /** `border-radius:16px` — the four action tiles. */
    val tile = RoundedCornerShape(16.dp)
    /** `40px;border-radius:14px` — roster avatars and icon plates. */
    val plate = RoundedCornerShape(14.dp)
    /** `68px;border-radius:24px` — the editable avatar on the settings screen. */
    val settingsAvatar = RoundedCornerShape(24.dp)
    /** `border-radius:14px` — a single-line field. */
    val field = RoundedCornerShape(14.dp)
    /** `border-radius:16px` — the description textarea. */
    val textArea = RoundedCornerShape(16.dp)
    /** `height:38px;border-radius:13px` — a segmented tab. */
    val segment = RoundedCornerShape(13.dp)
}

internal object XCommunityType {
    /** `font:800 16px/1.2` — the screen title next to the back button. */
    val screenTitle = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 19.sp)
    /** `font:800 20px/1.2;letter-spacing:-.02em` */
    val heroName = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-.02).em)
    /** `font:600 12px/1` */
    val heroSub = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    /** `font:800 9.5px;letter-spacing:.16em;uppercase` */
    val aboutLabel = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 9.5.sp, letterSpacing = .16.em)
    /** `font:500 13px/1.55` */
    val aboutBody = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp)
    /** `font:800 9px;letter-spacing:.08em;uppercase` — tile labels. */
    val tileLabel = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = .08.em)
    /** `font:800 10px;letter-spacing:.16em;uppercase` — card section labels. */
    val cardLabel = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = .16.em)
    /** `font:700 11px/1` — counters. */
    val cardCount = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    /** `font:800 13.5px/1.2` — a roster name. */
    val memberName = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, lineHeight = 16.sp)
    /** `font:600 11.5px/1` — a roster subtitle. */
    val memberSub = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
    /** `font:800 9px;letter-spacing:.08em;uppercase` — the role pill. */
    val rolePill = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = .08.em)
    /** `font:800 13px/1.2` — ссылка-приглашение в профиле канала. */
    val linkTitle = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, lineHeight = 16.sp)
    /** `font:600 10.5px/1` — подпись под ссылкой. */
    val linkNote = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp)
    /** `font:700 13px/1` — a list row. */
    val rowTitle = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    /** `font:800 13px/1` — the accent «добавить участника» row. */
    val accentRow = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    /** `font:800 9.5px;letter-spacing:.1em;uppercase` — the save pill. */
    val savePill = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 9.5.sp, letterSpacing = .1.em)
    /** `font:800 9.5px;letter-spacing:.14em;uppercase` — a field label. */
    val fieldLabel = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 9.5.sp, letterSpacing = .14.em)
    /** `font:800 14px/1` — the name field. */
    val inputStrong = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    /** `font:500 13px/1.5` — the description field. */
    val inputBody = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp)
    /** `font:700 10.5px/1` — the hint beside a settings label. */
    val hint = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
    /** `font:600 13.5px/1` — the search field. */
    val searchField = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
    /** `font:800 11.5px;letter-spacing:.12em;uppercase` — the wide bottom button. */
    val bigButton = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, letterSpacing = .12.em)
    /** `font:800 11px/1` — the count pill in the admins header. */
    val countPill = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
    /** `font:600 12.5px/1.4` — the empty-state and info notes. */
    val emptyNote = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 17.5.sp)
    /** `font:800 10px;letter-spacing:.06em;uppercase` — a segment label. */
    val segment = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = .06.em)
}

/** `#3ED27E` — the canvas' online dot. */
private val XCommunityOnline = Color(0xFF3ED27E)

private fun xCommunityLink(conversation: CloudConversation): String =
    if (conversation.username.isNotBlank()) "https://orionx.net/c/${conversation.username}"
    else "https://orionx.net/c/${conversation.id}"

// ---------------------------------------------------------------------------
// Shared state
// ---------------------------------------------------------------------------

/**
 * The conversation row, its roster and its pins — everything the community screens
 * read. One instance is created per screen; each mutation reloads it, so a screen
 * always reflects the change that was just made.
 *
 * Roles come from the server (`conversation_members.member_role`); every mutation
 * re-checks them in its RPC, so the flags here only decide what to *draw*.
 */
@Stable
internal class XCommunityState(
    private val api: SupabaseApi,
    private val session: CloudSession,
    initial: CloudConversation,
) {
    var conversation by mutableStateOf(initial)
        private set
    // Seeded from the cache so re-entering a group shows its roster at once.
    var members by mutableStateOf(XScreenCache.rosterOf(initial.id) ?: emptyList())
        private set
    var pins by mutableStateOf<List<CloudPinnedMessage>>(emptyList())
        private set
    /** Messages of this community that carry an attachment — the «медиа и файлы» row. */
    var media by mutableStateOf<List<CloudMessage>>(emptyList())
        private set
    var loading by mutableStateOf(XScreenCache.rosterOf(initial.id) == null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    val isChannel: Boolean get() = conversation.type == "channel"
    val isOwner: Boolean get() = conversation.ownerId == session.userId

    /** Mirrors `is_conversation_staff` — owner, admin or moderator. */
    val isStaff: Boolean get() = isOwner || members.firstOrNull { it.userId == session.userId }?.isStaff == true

    val staff: List<CloudMember> get() = members.filter { it.isStaff }
    val regulars: List<CloudMember> get() = members.filterNot { it.isStaff }

    /** My own row decides the «звук вкл / без звука» tile. */
    val muted: Boolean get() = members.firstOrNull { it.userId == session.userId }?.notificationsEnabled == false

    suspend fun load(force: Boolean = false) {
        if (!force && !loading) return
        loading = true
        runCatching {
            withContext(Dispatchers.IO) {
                val row = api.conversation(session.accessToken, conversation.id)
                val roster = api.communityMembers(session.accessToken, conversation.id)
                val pinned = runCatching { api.communityPins(session.accessToken, conversation.id) }.getOrDefault(emptyList())
                // Attachments come from the message feed the chat screen already reads;
                // a failure here must not blank the whole screen.
                val files = runCatching {
                    api.messages(session.accessToken, conversation.id)
                        .filter { !it.attachmentUrl.isNullOrBlank() && !it.deletedForAll }
                        .asReversed()
                }.getOrDefault(emptyList())
                XCommunitySnapshot(row, roster, pinned, files)
            }
        }.onSuccess { snapshot ->
            snapshot.conversation?.let { conversation = it.copy(unreadCount = conversation.unreadCount) }
            members = snapshot.members
            XScreenCache.putRoster(conversation.id, snapshot.members)
            pins = snapshot.pins
            media = snapshot.media
            error = null
        }.onFailure { error = localizedNetworkError(it) }
        loading = false
    }

    /**
     * Pending «заявки на вступление» (migration 018).
     *
     * Only staff can read the queue, and only a community that screens new members has
     * one, so this is fetched separately from [load] rather than on every open.
     */
    var joinRequests by mutableStateOf<List<CloudJoinRequest>>(emptyList())
        private set

    suspend fun loadJoinRequests() {
        if (!isStaff || !conversation.joinRequiresApproval) {
            joinRequests = emptyList()
            return
        }
        runCatching {
            withContext(Dispatchers.IO) { api.communityJoinRequests(session.accessToken, conversation.id) }
        }.onSuccess { joinRequests = it }
    }

    /** Approve or decline one request; an approval also adds the member. */
    suspend fun resolveJoinRequest(userId: String, approve: Boolean) {
        // Drop the row first — the queue is a list of decisions, and a slow network
        // should not leave the same face sitting there waiting to be tapped twice.
        joinRequests = joinRequests.filterNot { it.userId == userId }
        val ok = mutate { token -> resolveJoinRequest(token, conversation.id, userId, approve) }
        if (!ok) loadJoinRequests()
    }

    /** Optimistic: the tile flips at once, then the reload confirms it. */
    suspend fun toggleMuted() {
        val next = !muted
        val me = session.userId
        members = members.map { if (it.userId == me) it.copy(notificationsEnabled = !next) else it }
        mutate { token -> setMemberMuted(token, conversation.id, next) }
    }

    /**
     * Runs one mutation, then reloads. Returns true when it succeeded, so callers
     * can navigate only on success.
     */
    suspend fun mutate(block: suspend SupabaseApi.(token: String) -> Unit): Boolean {
        if (busy) return false
        busy = true
        val ok = runCatching { withContext(Dispatchers.IO) { api.block(session.accessToken) } }
            .onFailure { error = localizedNetworkError(it) }
            .isSuccess
        busy = false
        if (ok) {
            error = null
            load(force = true)
        }
        return ok
    }

    /** Applies a settings change locally as well, so the field does not flash back. */
    suspend fun updateSettings(
        title: String? = null,
        description: String? = null,
        username: String? = null,
        isPublic: Boolean? = null,
        membersCanPost: Boolean? = null,
        membersCanInvite: Boolean? = null,
        slowModeSeconds: Int? = null,
        // Migration 018 — the rest of the canvas create-form switches.
        historyVisibleToNew: Boolean? = null,
        mediaAllowed: Boolean? = null,
        joinRequiresApproval: Boolean? = null,
        allowForwarding: Boolean? = null,
        showAuthorSignature: Boolean? = null,
        showViewCounter: Boolean? = null,
        discussionEnabled: Boolean? = null,
        // The rest of the «настройки группы» artboard.
        reactionsEnabled: Boolean? = null,
        pinsForEveryone: Boolean? = null,
        hideMemberList: Boolean? = null,
        autoDeleteSeconds: Int? = null,
        reactionSet: List<String>? = null,
    ): Boolean {
        if (busy) return false
        busy = true
        val result = runCatching {
            withContext(Dispatchers.IO) {
                api.updateCommunity(
                    token = session.accessToken,
                    conversationId = conversation.id,
                    title = title,
                    description = description,
                    username = username,
                    isPublic = isPublic,
                    membersCanPost = membersCanPost,
                    membersCanInvite = membersCanInvite,
                    slowModeSeconds = slowModeSeconds,
                    historyVisibleToNew = historyVisibleToNew,
                    mediaAllowed = mediaAllowed,
                    joinRequiresApproval = joinRequiresApproval,
                    allowForwarding = allowForwarding,
                    showAuthorSignature = showAuthorSignature,
                    showViewCounter = showViewCounter,
                    discussionEnabled = discussionEnabled,
                    reactionsEnabled = reactionsEnabled,
                    pinsForEveryone = pinsForEveryone,
                    hideMemberList = hideMemberList,
                    autoDeleteSeconds = autoDeleteSeconds,
                    reactionSet = reactionSet,
                )
            }
        }
        busy = false
        return result.onSuccess {
            conversation = it.copy(unreadCount = conversation.unreadCount)
            error = null
        }.onFailure { error = localizedNetworkError(it) }.isSuccess
    }
}

@Composable
internal fun rememberCommunityState(
    api: SupabaseApi,
    session: CloudSession,
    conversation: CloudConversation,
): XCommunityState {
    val state = remember(conversation.id, session.userId) { XCommunityState(api, session, conversation) }
    LaunchedEffect(conversation.id) { state.load(force = true) }
    return state
}

// ---------------------------------------------------------------------------
// Info — canvas `groupinfo` / `chinfo`
// ---------------------------------------------------------------------------

@Composable
internal fun XCommunityInfoScreen(
    state: XCommunityState,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onAddMembers: () -> Unit,
    onAdmins: () -> Unit,
    onLeft: () -> Unit,
    /** «пост» / «написать пост» — возврат к ленте канала с открытым редактором. */
    onNewPost: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val copyText = rememberCommunityCopy()
    val shareText = rememberCommunityShare()
    var confirmLeave by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<XCommunitySheet?>(null) }
    var linkCopied by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }
    val c = state.conversation
    val channel = state.isChannel
    val link = remember(c.id, c.username) { xCommunityLink(c) }

    // `ссылка` flips to a check for a moment, the way the canvas does it.
    LaunchedEffect(linkCopied) {
        if (linkCopied) {
            kotlinx.coroutines.delay(1_600)
            linkCopied = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // `display:flex;gap:11px;padding:0 16px 10px` — 42px circles, 800/16 title.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            XCircleButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = tr("back"),
                size = 42.dp,
                iconSize = 20.dp,
                onClick = onBack,
            )
            Text(
                when {
                    channel && state.isStaff -> tr("community_your_channel")
                    channel -> tr("community_about_channel")
                    else -> tr("community_about_group")
                },
                style = XCommunityType.screenTitle,
                color = XText,
                modifier = Modifier.weight(1f),
            )
            // В «профиле канала» шестерёнки в шапке нет: настройки открываются
            // строкой в карточке владельца.
            if (state.isStaff && !channel) {
                XCircleButton(
                    icon = Icons.Outlined.Tune,
                    contentDescription = tr("community_settings"),
                    size = 42.dp,
                    iconSize = 20.dp,
                    onClick = onSettings,
                )
            }
        }

        XCommunityStatus(state)

        // `padding:2px 16px 24px;gap:11px`
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // Hero — `border-radius:30px;padding:22px 20px;gap:12px`
            Column(
                Modifier
                    .fillMaxWidth()
                    .xShadow(XCommunityShape.hero)
                    .clip(XCommunityShape.hero)
                    .background(XSurface)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // `88px` — у группы сквиркл 30, у канала круг.
                XLetterAvatar(
                    text = c.title,
                    size = 88.dp,
                    shape = if (channel) CircleShape else XCommunityShape.heroAvatar,
                    letterColor = Color(0xFFFCFBFF),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (channel) 6.dp else 5.dp),
                ) {
                    Text(c.title, style = XCommunityType.heroName, color = XText, textAlign = TextAlign.Center)
                    Text(
                        if (channel) tr(
                            "community_channel_line",
                            "type" to tr(if (c.isPublic) "community_channel_public" else "community_channel_private"),
                            "count" to "${state.members.size}",
                        ) else xCommunitySubtitle(channel, state.members.size),
                        style = XCommunityType.heroSub,
                        color = XMuted,
                    )
                }
                if (c.description.isNotBlank()) {
                    // `background:var(--sf2);border-radius:20px;padding:14px 16px 15px;gap:8px`
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(XCommunityShape.about)
                            .background(XSurfaceRaised)
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 15.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(Icons.Outlined.Info, null, tint = XMuted2, modifier = Modifier.size(14.dp))
                            Text(
                                tr(if (channel) "community_about_label_channel" else "community_about_label").uppercase(),
                                style = XCommunityType.aboutLabel,
                                color = XMuted2,
                            )
                        }
                        Text(c.description, style = XCommunityType.aboutBody, color = XText.copy(alpha = .82f))
                    }
                }

                // «профиль канала»: строка ссылки-приглашения — `--sf2`, r20, `14px 16px`.
                if (channel) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(XCommunityShape.about)
                            .background(XSurfaceRaised)
                            .clickable { copyText(link); linkCopied = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                link.removePrefix("https://"),
                                style = XCommunityType.linkTitle,
                                color = XText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (linkCopied) tr("community_link_copied_note") else tr("community_invite_link"),
                                style = XCommunityType.linkNote,
                                color = if (linkCopied) XCommunityOnline else XMuted,
                                maxLines = 1,
                            )
                        }
                        Icon(Icons.Outlined.ContentCopy, null, tint = XMuted, modifier = Modifier.size(18.dp))
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(XSurface)
                                .clickable { qrOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.QrCode2, null, tint = XText, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // `flex:1;gap:7px;border-radius:16px;padding:11px 4px` — у владельца канала
                // в макете всего две плитки, у подписчика четыре, у группы свои четыре.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (channel && state.isStaff) {
                        XCommunityActionTile(
                            icon = Icons.Outlined.Edit,
                            label = tr("community_post_short"),
                            active = false,
                            modifier = Modifier.weight(1f),
                            onClick = onNewPost,
                        )
                        XCommunityActionTile(
                            icon = Icons.Outlined.QrCode2,
                            label = tr("community_invite"),
                            active = false,
                            modifier = Modifier.weight(1f),
                        ) { qrOpen = true }
                    } else {
                        XCommunityActionTile(
                            icon = if (state.muted) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                            label = if (state.muted) tr("community_sound_off") else tr("community_sound_on"),
                            active = state.muted,
                            modifier = Modifier.weight(1f),
                        ) { scope.launch { state.toggleMuted() } }
                        if (channel) {
                            XCommunityActionTile(
                                icon = Icons.Outlined.IosShare,
                                label = tr("community_share"),
                                active = false,
                                modifier = Modifier.weight(1f),
                            ) { shareText(link) }
                        } else {
                            XCommunityActionTile(
                                icon = Icons.Outlined.Groups,
                                label = tr("community_call"),
                                active = false,
                                modifier = Modifier.weight(1f),
                            ) { state.notice = tr("community_call_soon") }
                        }
                        XCommunityActionTile(
                            icon = Icons.Outlined.Search,
                            label = tr("community_search"),
                            active = false,
                            modifier = Modifier.weight(1f),
                        ) { state.notice = tr("community_search_soon") }
                        if (channel) {
                            XCommunityActionTile(
                                icon = Icons.AutoMirrored.Outlined.Logout,
                                label = tr("community_leave_tile"),
                                active = false,
                                foreground = XRed,
                                modifier = Modifier.weight(1f),
                            ) { confirmLeave = true }
                        } else {
                            XCommunityActionTile(
                                icon = if (linkCopied) Icons.Outlined.Check else Icons.Outlined.Link,
                                label = if (linkCopied) tr("community_copied") else tr("community_link"),
                                active = linkCopied,
                                modifier = Modifier.weight(1f),
                            ) {
                                copyText(link)
                                linkCopied = true
                            }
                        }
                    }
                }
            }

            // Roster — `border-radius:26px;padding:16px 18px`. A channel's subscriber
            // list stays private, so only staff see it.
            if (!channel || state.isStaff) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .xShadow(XCommunityShape.card)
                        .clip(XCommunityShape.card)
                        .background(XSurface)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (channel) tr("community_subscribers") else tr("community_members")).uppercase(),
                            style = XCommunityType.cardLabel,
                            color = XMuted2,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${state.members.size}", style = XCommunityType.cardCount, color = XMuted)
                    }
                    state.members.take(12).forEachIndexed { index, member ->
                        XCommunityMemberRow(
                            member = member,
                            divider = index < state.members.lastIndex.coerceAtMost(11),
                        )
                    }
                    if (state.members.size > 12) {
                        Text(
                            tr("community_and_more", "count" to "${state.members.size - 12}"),
                            style = XCommunityType.cardCount,
                            color = XMuted,
                            modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                        )
                    }
                    if (state.isStaff || c.membersCanInvite) {
                        // `padding:13px 0 4px` — accent plate, accent label.
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onAddMembers)
                                .padding(top = 13.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(XCommunityShape.plate).background(XSurfaceRaised),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.PersonAdd, null, tint = XAccent, modifier = Modifier.size(19.dp))
                            }
                            Text(tr("community_add_member"), style = XCommunityType.accentRow, color = XAccent)
                        }
                    }
                }
            }

            // `border-radius:26px;padding:6px 18px` — the artboard reaches the settings and
            // the admin list straight from the profile, not only from the header button.
            if (state.isStaff) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .xShadow(XCommunityShape.card)
                        .clip(XCommunityShape.card)
                        .background(XSurface)
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                ) {
                    XCommunityListRow(
                        icon = Icons.Outlined.Tune,
                        title = if (channel) tr("community_channel_settings") else tr("community_group_settings"),
                        gap = 13.dp,
                        chevronSize = 18.dp,
                        onClick = onSettings,
                    )
                    XCommunityListRow(
                        icon = if (channel) Icons.Outlined.AdminPanelSettings else Icons.Outlined.Shield,
                        title = tr("community_admins"),
                        value = "${state.members.count { it.isStaff }}",
                        gap = 13.dp,
                        chevronSize = 18.dp,
                        divider = channel,
                        onClick = onAdmins,
                    )
                    // Третья строка карточки владельца канала в макете — «написать пост».
                    if (channel) {
                        XCommunityListRow(
                            icon = Icons.Outlined.Edit,
                            title = tr("community_write_post"),
                            tint = XAccent,
                            gap = 13.dp,
                            divider = false,
                            chevron = false,
                            onClick = onNewPost,
                        )
                    }
                }
            }

            // `border-radius:26px;padding:6px 18px` — media / pins / leave.
            Column(
                Modifier
                    .fillMaxWidth()
                    .xShadow(XCommunityShape.card)
                    .clip(XCommunityShape.card)
                    .background(XSurface)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                XCommunityListRow(
                    icon = Icons.Outlined.Image,
                    title = tr("community_media"),
                    value = "${state.media.size}",
                    onClick = { sheet = XCommunitySheet.MEDIA },
                )
                XCommunityListRow(
                    icon = Icons.Outlined.PushPin,
                    title = tr("community_pinned"),
                    value = "${state.pins.size}",
                    onClick = { sheet = XCommunitySheet.PINS },
                )
                XCommunityListRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = if (channel) tr("community_unsubscribe") else tr("community_leave"),
                    tint = XRed,
                    divider = false,
                    chevron = false,
                    onClick = { confirmLeave = true },
                )
            }
        }
    }

    when (sheet) {
        XCommunitySheet.PINS -> XCommunitySheetPanel(title = tr("community_pinned"), onDismiss = { sheet = null }) {
            if (state.pins.isEmpty()) {
                XCommunityEmptyLine(tr("community_no_pins"))
            } else {
                state.pins.forEach { pin ->
                    XPinRow(
                        pin = pin,
                        canUnpin = state.isStaff,
                        onUnpin = {
                            scope.launch {
                                state.mutate { token -> setMessagePinned(token, state.conversation.id, pin.messageId, false) }
                            }
                        },
                    )
                }
            }
        }
        XCommunitySheet.MEDIA -> XCommunitySheetPanel(title = tr("community_media"), onDismiss = { sheet = null }) {
            if (state.media.isEmpty()) {
                XCommunityEmptyLine(tr("community_no_media"))
            } else {
                state.media.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(XCommunityShape.plate).background(XSurfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Image, null, tint = XMuted, modifier = Modifier.size(19.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                item.attachmentName?.takeIf { it.isNotBlank() } ?: tr("file"),
                                style = XCommunityType.rowTitle,
                                color = XText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(item.senderName, style = XCommunityType.cardCount, color = XMuted, maxLines = 1)
                        }
                    }
                }
            }
        }
        null -> Unit
    }

    if (qrOpen) {
        XQrShareDialog(
            title = tr("community_invite_link"),
            hint = tr(if (channel) "community_qr_hint_channel" else "community_qr_hint_group"),
            link = link.removePrefix("https://"),
            shareUrl = link,
            onDismiss = { qrOpen = false },
        )
    }

    if (confirmLeave) {
        XCommunityConfirmSheet(
            title = if (channel) tr("community_unsubscribe") else tr("community_leave"),
            message = tr(
                if (channel) "community_unsubscribe_hint" else "community_leave_hint",
                "name" to c.title,
            ),
            confirmLabel = if (channel) tr("community_unsubscribe") else tr("community_leave_short"),
            onConfirm = {
                confirmLeave = false
                scope.launch {
                    val ok = state.mutate { token -> leaveConversation(token, state.conversation.id) }
                    if (ok) onLeft()
                }
            },
            onDismiss = { confirmLeave = false },
        )
    }
}

private enum class XCommunitySheet { PINS, MEDIA }
// ---------------------------------------------------------------------------
// Settings — canvas `gsettings` / `chsettings`
// ---------------------------------------------------------------------------

/** `slow_mode_seconds` presets — the canvas' four «медленный режим» tabs. */
private val XSlowModeOptions = listOf(0, 10, 60, 300)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun XCommunitySettingsScreen(
    state: XCommunityState,
    onBack: () -> Unit,
    onAdmins: () -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val copyText = rememberCommunityCopy()
    val c = state.conversation
    val channel = state.isChannel

    var title by remember(c.id) { mutableStateOf(c.title) }
    var description by remember(c.id) { mutableStateOf(c.description) }
    var username by remember(c.id) { mutableStateOf(c.username) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickingEmoji by remember { mutableStateOf(false) }

    // Ссылку может менять только владелец — для остальных правка имени
    // пользователя не считается изменением, иначе Save «зелёный», а апдейт пустой.
    val dirty = title.trim() != c.title ||
        description.trim() != c.description ||
        (state.isOwner && username.trim().removePrefix("@") != c.username)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            XCircleButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = tr("back"),
                size = 42.dp,
                iconSize = 20.dp,
                onClick = onBack,
            )
            Text(
                if (channel) tr("community_channel_settings") else tr("community_group_settings"),
                style = XCommunityType.screenTitle,
                color = XText,
                modifier = Modifier.weight(1f),
            )
            // `padding:0 16px;height:42px;border-radius:21px` — fills with --txt once dirty.
            val saveEnabled = dirty && !state.busy && state.isStaff
            val saveBg by animateColorAsState(if (saveEnabled) XText else XSurface, tween(220), label = "save-bg")
            val saveFg by animateColorAsState(if (saveEnabled) XInverse else XMuted3, tween(220), label = "save-fg")
            Box(
                Modifier
                    .height(42.dp)
                    .clip(CircleShape)
                    .background(saveBg)
                    .clickable(enabled = saveEnabled) {
                        scope.launch {
                            state.updateSettings(
                                title = title.trim().takeIf { it != c.title },
                                description = description.trim().takeIf { it != c.description },
                                username = username.trim().removePrefix("@")
                                    .takeIf { it != c.username && state.isOwner },
                            )
                        }
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("save").uppercase(), style = XCommunityType.savePill, color = saveFg)
            }
        }

        XCommunityStatus(state)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // Avatar + name — `padding:16px 18px 18px;gap:14px`, 68px/24 plate.
            XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(68.dp)) {
                        XLetterAvatar(
                            text = title.ifBlank { c.title },
                            size = 68.dp,
                            shape = XCommunityShape.settingsAvatar,
                            letterColor = Color(0xFFFCFBFF),
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(XSurfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, null, tint = XText, modifier = Modifier.size(15.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("community_name").uppercase(), style = XCommunityType.fieldLabel, color = XMuted2)
                        XCommunityInput(
                            value = title,
                            onValueChange = { title = it.take(100) },
                            enabled = state.isStaff,
                            textStyle = XCommunityType.inputStrong,
                        )
                    }
                }
            }

            // Description — textarea `border-radius:16px;padding:13px;font:500 13px/1.5`.
            XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(tr("community_description").uppercase(), style = XCommunityType.fieldLabel, color = XMuted2)
                    XCommunityInput(
                        value = description,
                        onValueChange = { description = it.take(500) },
                        enabled = state.isStaff,
                        textStyle = XCommunityType.inputBody,
                        singleLine = false,
                        minHeight = 78.dp,
                        shape = XCommunityShape.textArea,
                    )
                }
            }

            if (!channel) {
            XCommunitySegmentCard(
                label = tr("community_slow_mode"),
                hint = xSlowModeHint(c.slowModeSeconds),
                options = XSlowModeOptions.map { xSlowModeLabel(it) },
                // Значение не из набора не подсвечиваем вовсе: в подсказке над сегментом
                // всё равно написано настоящее — иначе 30 с выглядели бы как «выкл».
                selected = XSlowModeOptions.indexOf(c.slowModeSeconds),
                enabled = state.isStaff && !state.busy,
            ) { index -> scope.launch { state.updateSettings(slowModeSeconds = XSlowModeOptions[index]) } }

            // «АВТОУДАЛЕНИЕ» — ВЫКЛ / 24 Ч / 7 ДН / 30 ДН. A message stops being readable once
            // its window has passed; the rule lives in the messages policy.
            XCommunitySegmentCard(
                label = tr("community_auto_delete"),
                hint = xAutoDeleteHint(c.autoDeleteSeconds),
                options = XAutoDeleteOptions.map { xAutoDeleteLabel(it) },
                selected = XAutoDeleteOptions.indexOf(c.autoDeleteSeconds),
                enabled = state.isStaff && !state.busy,
            ) { index -> scope.launch { state.updateSettings(autoDeleteSeconds = XAutoDeleteOptions[index]) } }
            }

            // ТИП КАНАЛА — публичный или частный; only the owner may flip it.
            if (channel) {
                XCommunitySegmentCard(
                    label = tr("community_channel_type"),
                    hint = if (c.isPublic) tr("community_channel_public_hint") else tr("community_channel_private_hint"),
                    options = listOf(tr("community_channel_public"), tr("community_channel_private")),
                    selected = if (c.isPublic) 0 else 1,
                    enabled = state.isOwner && !state.busy,
                ) { index -> scope.launch { state.updateSettings(isPublic = index == 0) } }

                // КТО МОЖЕТ КОММЕНТИРОВАТЬ — the artboard offers three, the column behind it holds two.
                XCommunitySegmentCard(
                    label = tr("community_who_can_comment"),
                    hint = if (c.discussionEnabled) tr("community_comments_all_hint") else tr("community_comments_off_hint"),
                    options = listOf(tr("community_comments_all"), tr("community_comments_none")),
                    selected = if (c.discussionEnabled) 0 else 1,
                    enabled = state.isStaff && !state.busy,
                ) { index -> scope.launch { state.updateSettings(discussionEnabled = index == 0) } }
            }

            // КТО МОЖЕТ ПИСАТЬ — the canvas gives a group the same segment a channel has,
            // with «все участники» first.
            XCommunitySegmentCard(
                label = if (channel) tr("community_who_can_post") else tr("community_who_can_write"),
                hint = when {
                    channel && c.membersCanPost -> tr("community_post_all_hint")
                    channel -> tr("community_post_admins_hint")
                    c.membersCanPost -> tr("community_write_all_hint")
                    else -> tr("community_write_admins_hint")
                },
                options = listOf(tr("community_post_all"), tr("community_post_admins")),
                selected = if (c.membersCanPost) 0 else 1,
                enabled = state.isStaff && !state.busy,
            ) { index -> scope.launch { state.updateSettings(membersCanPost = index == 0) } }

            // The switch block from the canvas create form, editable after the fact.
            // Same rows, same order — see `CRTG_G` / `CRTG_C`.
            XCommunityCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                val rows: List<Triple<ImageVector, Pair<String, String>, Pair<Boolean, (Boolean) -> Unit>>> =
                    if (channel) listOf(
                        Triple(
                            Icons.Outlined.Badge, tr("community_signature") to tr("community_signature_sub"),
                            c.showAuthorSignature to { on: Boolean -> scope.launch { state.updateSettings(showAuthorSignature = on) }; Unit },
                        ),
                        Triple(
                            Icons.Outlined.Visibility, tr("community_views") to tr("community_views_sub"),
                            c.showViewCounter to { on: Boolean -> scope.launch { state.updateSettings(showViewCounter = on) }; Unit },
                        ),
                        Triple(
                            Icons.Outlined.Forward, tr("community_forwarding") to tr("community_forwarding_sub"),
                            c.allowForwarding to { on: Boolean -> scope.launch { state.updateSettings(allowForwarding = on) }; Unit },
                        ),
                    ) else listOf(
                        Triple(
                            Icons.Outlined.History, tr("community_history") to tr("community_history_sub"),
                            c.historyVisibleToNew to { on: Boolean -> scope.launch { state.updateSettings(historyVisibleToNew = on) }; Unit },
                        ),
                        Triple(
                            Icons.Outlined.Image, tr("community_media") to tr("community_media_sub"),
                            c.mediaAllowed to { on: Boolean -> scope.launch { state.updateSettings(mediaAllowed = on) }; Unit },
                        ),
                        Triple(
                            Icons.Outlined.Link, tr("community_invite_toggle") to tr("community_invite_toggle_sub"),
                            c.membersCanInvite to { on: Boolean -> scope.launch { state.updateSettings(membersCanInvite = on) }; Unit },
                        ),
                        Triple(
                            Icons.Outlined.HowToReg, tr("community_approval") to tr("community_approval_sub"),
                            c.joinRequiresApproval to { on: Boolean ->
                                scope.launch {
                                    state.updateSettings(joinRequiresApproval = on)
                                    state.loadJoinRequests()
                                }
                                Unit
                            },
                        ),
                        Triple(
                            Icons.Outlined.AddReaction, tr("community_reactions") to tr("community_reactions_sub"),
                            c.reactionsEnabled to { on: Boolean -> scope.launch { state.updateSettings(reactionsEnabled = on) }; Unit },
                        ),
                        Triple(
                            Icons.Outlined.PushPin, tr("community_pins_all") to tr("community_pins_all_sub"),
                            c.pinsForEveryone to { on: Boolean -> scope.launch { state.updateSettings(pinsForEveryone = on) }; Unit },
                        ),
                        Triple(
                            Icons.Outlined.VisibilityOff, tr("community_hide_roster") to tr("community_hide_roster_sub"),
                            c.hideMemberList to { on: Boolean -> scope.launch { state.updateSettings(hideMemberList = on) }; Unit },
                        ),
                        Triple(
                            Icons.Outlined.Forward, tr("community_forwarding") to tr("community_forwarding_sub"),
                            c.allowForwarding to { on: Boolean -> scope.launch { state.updateSettings(allowForwarding = on) }; Unit },
                        ),
                    )
                rows.forEachIndexed { index, (icon, copy, toggle) ->
                    XCommunityToggleRow(
                        icon = icon,
                        title = copy.first,
                        subtitle = copy.second,
                        checked = toggle.first,
                        enabled = state.isStaff && !state.busy,
                        last = index == rows.lastIndex,
                        onCheckedChange = toggle.second,
                    )
                }
            }

            // «НАБОР РЕАКЦИЙ» — `border-radius:26px;padding:16px 18px 18px;gap:11px`.
            // Chips are `#5B3FD6` with a `close` glyph, the picker is a `--sf2` pill.
            run {
                val reactionSet = c.reactionSet.ifEmpty { XDefaultReactionSet }
                XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                tr("community_reaction_set").uppercase(),
                                style = XCommunityType.fieldLabel,
                                color = XMuted2,
                            )
                            Text(
                                tr("community_reaction_set_count", "count" to "${reactionSet.size}"),
                                style = XCommunityType.hint,
                                color = XMuted,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            reactionSet.forEach { emoji ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(XCommunityReactionChip)
                                        .clickable(enabled = state.isStaff && !state.busy && reactionSet.size > 1) {
                                            scope.launch { state.updateSettings(reactionSet = reactionSet - emoji) }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Text(emoji, fontSize = 16.sp, lineHeight = 16.sp)
                                    Icon(
                                        Icons.Outlined.Close,
                                        null,
                                        tint = Color(0xFFFCFBFF),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            if (reactionSet.size < 12) {
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(XSurfaceRaised)
                                        .clickable(enabled = state.isStaff && !state.busy) { pickingEmoji = true }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Outlined.Add, null, tint = XText, modifier = Modifier.size(15.dp))
                                    Text(
                                        tr("community_reaction_add").uppercase(),
                                        style = XCommunityType.tileLabel,
                                        color = XText,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // The queue the «заявки на вступление» switch feeds. Staff only, and only
            // while the switch is on — otherwise there is nothing to decide.
            if (state.isStaff && c.joinRequiresApproval) {
                LaunchedEffect(c.id, c.joinRequiresApproval) { state.loadJoinRequests() }
                XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr("community_requests").uppercase(), style = XCommunityType.fieldLabel, color = XMuted2)
                            Text(
                                state.joinRequests.size.toString(),
                                style = XCommunityType.hint,
                                color = XMuted,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.joinRequests.isEmpty()) {
                            XCommunityEmptyLine(tr("community_requests_empty"))
                        } else {
                            Column {
                                state.joinRequests.forEachIndexed { index, request ->
                                    XCommunityJoinRequestRow(
                                        request = request,
                                        enabled = !state.busy,
                                        last = index == state.joinRequests.lastIndex,
                                        onApprove = { scope.launch { state.resolveJoinRequest(request.userId, true) } },
                                        onDecline = { scope.launch { state.resolveJoinRequest(request.userId, false) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Public link — owner-only on the server.
            if (c.isPublic || state.isOwner) {
                XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(tr("community_public_link").uppercase(), style = XCommunityType.fieldLabel, color = XMuted2)
                        XCommunityInput(
                            value = username,
                            onValueChange = { input ->
                                username = input.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(32)
                            },
                            enabled = state.isOwner,
                            textStyle = XCommunityType.inputStrong,
                            placeholder = tr("community_link_placeholder"),
                        )
                        Text(
                            if (c.username.isBlank()) tr("community_link_empty_hint") else "orionx.net/c/${c.username}",
                            style = XCommunityType.hint,
                            color = XMuted,
                        )
                    }
                }
            }

            // `border-radius:26px;padding:6px 18px` — link / admins / delete.
            Column(
                Modifier
                    .fillMaxWidth()
                    .xShadow(XCommunityShape.card)
                    .clip(XCommunityShape.card)
                    .background(XSurface)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                if (c.username.isNotBlank()) {
                    XCommunityListRow(
                        icon = Icons.Outlined.ContentCopy,
                        title = tr("community_copy_link"),
                        chevron = false,
                        onClick = {
                            copyText(xCommunityLink(c))
                            state.notice = tr("community_link_copied")
                        },
                    )
                }
                XCommunityListRow(
                    icon = Icons.Outlined.Shield,
                    title = tr("community_admins"),
                    value = "${state.staff.size}",
                    divider = state.isOwner,
                    onClick = onAdmins,
                )
                if (state.isOwner) {
                    XCommunityListRow(
                        icon = Icons.Outlined.Delete,
                        title = if (channel) tr("community_delete_channel") else tr("community_delete_group"),
                        tint = XRed,
                        divider = false,
                        chevron = false,
                        onClick = { confirmDelete = true },
                    )
                }
            }
        }
    }

    if (pickingEmoji) {
        val current = c.reactionSet.ifEmpty { XDefaultReactionSet }
        XReactionPickerSheet(
            taken = current,
            onPick = { emoji ->
                pickingEmoji = false
                scope.launch { state.updateSettings(reactionSet = current + emoji) }
            },
            onDismiss = { pickingEmoji = false },
        )
    }

    if (confirmDelete) {
        XCommunityConfirmSheet(
            title = if (channel) tr("community_delete_channel") else tr("community_delete_group"),
            message = tr("community_delete_hint", "name" to c.title),
            confirmLabel = tr("delete"),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    val ok = state.mutate { token -> deleteCommunity(token, state.conversation.id) }
                    if (ok) onDeleted()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** `background:var(--sf);border-radius:26px;box-shadow:0 12px 30px var(--shc)` */
@Composable
internal fun XCommunityCard(
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .xShadow(XCommunityShape.card)
            .clip(XCommunityShape.card)
            .background(XSurface)
            .padding(contentPadding),
        content = content,
    )
}

/** `background:var(--sf2);border-radius:14px;padding:12px 13px` — the canvas field. */
@Composable
internal fun XCommunityInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    textStyle: TextStyle,
    singleLine: Boolean = true,
    placeholder: String = "",
    minHeight: Dp = 0.dp,
    shape: Shape = XCommunityShape.field,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(XSurfaceRaised)
            .heightIn(min = minHeight)
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty() && placeholder.isNotBlank()) {
            Text(placeholder, style = textStyle, color = XMuted3)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 6,
            textStyle = textStyle.copy(color = if (enabled) XText else XMuted),
            cursorBrush = SolidColor(XAccent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


/**
 * `padding:14px 0` with a hairline under every row but the last — the canvas switch row,
 * shared by the create form and these settings.
 */
@Composable
private fun XCommunityToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    last: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Icon(icon, null, tint = XText, modifier = Modifier.size(19.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = XCommunityType.rowTitle, color = XText)
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = XCommunityType.memberSub.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = XMuted,
                )
            }
            // `44x26;border-radius:13px;padding:3px`, knob 20px, translateX(18px) when on.
            val track by animateColorAsState(
                if (checked) Color(0xFF5B3FD6) else XSurfaceRaised,
                tween(240),
                label = "comm-tg-track",
            )
            val knob by animateColorAsState(
                if (checked) Color(0xFFFCFBFF) else XMuted2,
                tween(240),
                label = "comm-tg-knob",
            )
            val knobX by animateDpAsState(
                targetValue = if (checked) 18.dp else 0.dp,
                animationSpec = tween(260, easing = CubicBezierEasing(.33f, 1f, .68f, 1f)),
                label = "comm-tg-x",
            )
            Box(
                Modifier
                    .size(44.dp, 26.dp)
                    .clip(XCommunityShape.segment)
                    .background(track)
                    .xPressable(enabled = enabled) { onCheckedChange(!checked) }
                    .padding(3.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.offset(x = knobX).size(20.dp).clip(CircleShape).background(knob))
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(XHairline))
    }
}

/** One pending «заявка на вступление»: who is asking, and the two decisions. */
@Composable
private fun XCommunityJoinRequestRow(
    request: CloudJoinRequest,
    enabled: Boolean,
    last: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (request.avatarUrl.isNotBlank()) {
                RemoteImage(request.avatarUrl, Modifier.size(38.dp).clip(CircleShape), ContentScale.Crop)
            } else {
                XLetterAvatar(request.displayName.ifBlank { request.nickname }, 38.dp, CircleShape)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    request.displayName.ifBlank { request.nickname },
                    style = XCommunityType.memberName,
                    color = XText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text("@${request.nickname}", style = XCommunityType.memberSub, color = XMuted, maxLines = 1)
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(XSurfaceRaised)
                    .xPressable(pressedScale = .92f, enabled = enabled, onClick = onDecline),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, tr("cancel"), tint = XMuted, modifier = Modifier.size(18.dp)) }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5B3FD6))
                    .xPressable(pressedScale = .92f, enabled = enabled, onClick = onApprove),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Check, null, tint = Color(0xFFFCFBFF), modifier = Modifier.size(18.dp)) }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(XHairline))
    }
}

/**
 * Label + right-aligned hint + a row of segments —
 * `height:38px;border-radius:13px;gap:6px`, the active one filled with `--txt`.
 */
@Composable
internal fun XCommunitySegmentCard(
    label: String,
    hint: String,
    options: List<String>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // `justify-content:space-between` — the label keeps its natural width and
            // the hint takes the rest, so a long label never collapses into a column.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label.uppercase(), style = XCommunityType.fieldLabel, color = XMuted2)
                Text(
                    hint,
                    style = XCommunityType.hint,
                    color = XMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEachIndexed { index, option ->
                    val active = index == selected
                    val background by animateColorAsState(if (active) XText else XSurfaceRaised, tween(220), label = "seg-bg")
                    val foreground by animateColorAsState(if (active) XInverse else XMuted, tween(220), label = "seg-fg")
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(XCommunityShape.segment)
                            .background(background)
                            .clickable(enabled = enabled && !active) { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(option.uppercase(), style = XCommunityType.segment, color = foreground, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Admins — canvas `gadmins` / `chadmins`
// ---------------------------------------------------------------------------

@Composable
internal fun XCommunityAdminsScreen(
    state: XCommunityState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var demoteTarget by remember { mutableStateOf<CloudMember?>(null) }
    var promoteTarget by remember { mutableStateOf<CloudMember?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            XCircleButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = tr("back"),
                size = 42.dp,
                iconSize = 20.dp,
                onClick = onBack,
            )
            Text(
                if (state.isChannel) tr("community_channel_admins") else tr("community_admins"),
                style = XCommunityType.screenTitle,
                color = XText,
                modifier = Modifier.weight(1f),
            )
            // `padding:0 14px;height:42px;border-radius:21px;background:var(--sf)`
            Box(
                Modifier.height(42.dp).xShadow(CircleShape).clip(CircleShape).background(XSurface).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${state.staff.size}", style = XCommunityType.countPill, color = XMuted)
            }
        }

        XCommunityStatus(state)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // «назначены»
            XCommunityCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    (if (state.isChannel) tr("community_run_channel") else tr("community_appointed")).uppercase(),
                    style = XCommunityType.fieldLabel,
                    color = XMuted2,
                )
                Column(Modifier.padding(top = 4.dp)) {
                    state.staff.forEachIndexed { index, member ->
                        XCommunityAdminRow(
                            member = member,
                            subtitle = xRoleLabel(member),
                            subtitleColor = if (member.isOwner) XAccentSoft else XMuted,
                            divider = index < state.staff.lastIndex,
                        ) {
                            // Only the owner may change roles, and never their own.
                            if (state.isOwner && !member.isOwner) {
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(XSurfaceRaised)
                                        .clickable { demoteTarget = member },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.MoreVert, null, tint = XText, modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    }
                }
            }

            // «участники группы» — only the owner can promote, so hide it otherwise.
            if (state.isOwner) {
                XCommunityCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        (if (state.isChannel) tr("community_subscribers") else tr("community_members")).uppercase(),
                        style = XCommunityType.fieldLabel,
                        color = XMuted2,
                    )
                    Column(Modifier.padding(top = 4.dp)) {
                        state.regulars.forEachIndexed { index, member ->
                            XCommunityAdminRow(
                                member = member,
                                subtitle = "@${member.nickname}",
                                subtitleColor = XMuted,
                                divider = index < state.regulars.lastIndex,
                            ) {
                                // `padding:8px 12px;border-radius:999px;background:var(--txt)`
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(XText)
                                        .clickable { promoteTarget = member }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Outlined.Shield, null, tint = XInverse, modifier = Modifier.size(14.dp))
                                    Text(
                                        tr("community_promote").uppercase(),
                                        style = XCommunityType.tileLabel,
                                        color = XInverse,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (state.regulars.isEmpty()) {
                            Text(
                                tr("community_all_are_admins"),
                                style = XCommunityType.emptyNote,
                                color = XMuted2,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
            }

            // `display:flex;align-items:flex-start;gap:10px;padding:0 6px`
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Info, null, tint = XMuted2, modifier = Modifier.size(17.dp))
                Text(
                    if (state.isChannel) tr("community_channel_rights_note") else tr("community_admin_rights_note"),
                    style = XCommunityType.emptyNote,
                    color = XMuted2,
                )
            }
        }
    }

    demoteTarget?.let { member ->
        XCommunityConfirmSheet(
            title = tr("community_demote"),
            message = tr("community_demote_hint", "name" to member.displayName),
            confirmLabel = tr("community_demote"),
            onConfirm = {
                demoteTarget = null
                scope.launch {
                    state.mutate { token ->
                        setMemberRole(
                            token = token,
                            conversationId = state.conversation.id,
                            userId = member.userId,
                            role = if (state.isChannel) "subscriber" else "member",
                        )
                    }
                }
            },
            onDismiss = { demoteTarget = null },
        )
    }

    promoteTarget?.let { member ->
        XAdminTagSheet(
            member = member,
            onDismiss = { promoteTarget = null },
            onConfirm = { tag ->
                promoteTarget = null
                scope.launch {
                    state.mutate { token ->
                        setMemberRole(
                            token = token,
                            conversationId = state.conversation.id,
                            userId = member.userId,
                            role = "admin",
                            adminTag = tag,
                        )
                    }
                }
            },
        )
    }
}

/** A row of either admins card — `gap:12px;padding:12px 0`, 40px squircle, trailing control. */
@Composable
private fun XCommunityAdminRow(
    member: CloudMember,
    subtitle: String,
    subtitleColor: Color,
    divider: Boolean,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XLetterAvatar(
                text = member.displayName,
                size = 40.dp,
                shape = XCommunityShape.plate,
                letterColor = Color(0xFF101014),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(member.displayName, style = XCommunityType.memberName, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    member.adminTag.ifBlank { subtitle },
                    style = XCommunityType.memberSub,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(XHairline))
    }
}

// ---------------------------------------------------------------------------
// Add members — canvas `gadd`
// ---------------------------------------------------------------------------

@Composable
internal fun XAddMembersScreen(
    api: SupabaseApi,
    session: CloudSession,
    state: XCommunityState,
    onBack: () -> Unit,
    onAdded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<List<CloudProfile>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.userId) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { api.allProfiles(session.accessToken) } }
            .onSuccess { people = it.filterNot { p -> p.blocked }; error = null }
            .onFailure { error = localizedNetworkError(it) }
        loading = false
    }

    val existing = remember(state.members) { state.members.map { it.userId }.toSet() }
    val candidates = remember(people, query, existing) {
        val needle = query.trim().removePrefix("@").lowercase()
        people
            .filterNot { it.id in existing }
            .filter {
                needle.isBlank() ||
                    it.displayName.lowercase().contains(needle) ||
                    it.nickname.lowercase().contains(needle)
            }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Header — title with the selection count underneath, as in the canvas.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            XCircleButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = tr("back"),
                size = 42.dp,
                iconSize = 20.dp,
                onClick = onBack,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("community_add_members"), style = XCommunityType.screenTitle, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        selected.isNotEmpty() -> tr("community_picked", "count" to "${selected.size}")
                        state.isChannel -> tr("community_in_channel", "count" to "${state.members.size}")
                        else -> tr("community_in_group", "count" to "${state.members.size}")
                    },
                    style = XCommunityType.memberSub,
                    color = XMuted,
                )
            }
        }

        // `height:46px;border-radius:23px;background:var(--sf);gap:10px;padding:0 16px`
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp)
                .height(46.dp)
                .xShadow(CircleShape)
                .clip(CircleShape)
                .background(XSurface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Search, null, tint = XMuted, modifier = Modifier.size(19.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(tr("community_search_people"), style = XCommunityType.searchField, color = XMuted)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = XCommunityType.searchField.copy(color = XText),
                    cursorBrush = SolidColor(XAccent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        error?.let { XCommunityBanner(it, XRed) }
        state.error?.let { XCommunityBanner(it, XRed) }

        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = XAccent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
            ) {
                XCommunityCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(tr("contacts").uppercase(), style = XCommunityType.fieldLabel, color = XMuted2)
                    Column(Modifier.padding(top = 4.dp)) {
                        candidates.forEachIndexed { index, person ->
                            val checked = person.id in selected
                            XCommunityPickRow(
                                person = person,
                                checked = checked,
                                divider = index < candidates.lastIndex,
                            ) {
                                selected = if (checked) selected - person.id else selected + person.id
                            }
                        }
                        if (candidates.isEmpty()) {
                            Text(
                                tr("community_nobody_found"),
                                style = XCommunityType.emptyNote,
                                color = XMuted2,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
            }

            // `height:52px;border-radius:26px` — always present, greyed until something is picked.
            val enabled = selected.isNotEmpty() && !state.busy
            val background by animateColorAsState(if (enabled) XText else XSurface, tween(220), label = "add-bg")
            val foreground by animateColorAsState(if (enabled) XInverse else XMuted3, tween(220), label = "add-fg")
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 22.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(background)
                    .clickable(enabled = enabled) {
                        scope.launch {
                            val ok = state.mutate { token ->
                                addCommunityMembers(token, state.conversation.id, selected.toList())
                            }
                            if (ok) onAdded()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (selected.isEmpty()) tr("community_pick_people").uppercase()
                    else tr("community_add_n", "count" to "${selected.size}").uppercase(),
                    style = XCommunityType.bigButton,
                    color = foreground,
                )
            }
        }
    }
}

/** A pickable contact — `gap:12px;padding:12px 0`, 24px checkbox with a 2px border. */
@Composable
private fun XCommunityPickRow(
    person: CloudProfile,
    checked: Boolean,
    divider: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (person.avatarUrl.isNotBlank()) {
                RemoteImage(person.avatarUrl, Modifier.size(40.dp).clip(XCommunityShape.plate), ContentScale.Crop)
            } else {
                XLetterAvatar(
                    text = person.displayName,
                    size = 40.dp,
                    shape = XCommunityShape.plate,
                    letterColor = Color(0xFF101014),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(person.displayName, style = XCommunityType.memberName, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${person.nickname}", style = XCommunityType.memberSub, color = XMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val boxBg by animateColorAsState(if (checked) XText else Color.Transparent, tween(200), label = "box-bg")
            val boxBorder by animateColorAsState(if (checked) XText else XMuted3, tween(200), label = "box-bd")
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(boxBg)
                    .border(2.dp, boxBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Icon(Icons.Outlined.Check, null, tint = XInverse, modifier = Modifier.size(15.dp))
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(XHairline))
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

/** What one [XCommunityState.load] reads, so the IO block returns a single value. */
private data class XCommunitySnapshot(
    val conversation: CloudConversation?,
    val members: List<CloudMember>,
    val pins: List<CloudPinnedMessage>,
    val media: List<CloudMessage>,
)

/**
 * One of the four tiles under the hero card.
 * `flex:1;padding:11px 4px;border-radius:16px;background:var(--sf2)` — the canvas
 * fills the tile with `--txt` while it is active (mute on, link just copied).
 */
@Composable
private fun XCommunityActionTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    foreground: Color? = null,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(if (active) XText else XSurfaceRaised, tween(220), label = "tile-bg")
    val ink by animateColorAsState(foreground ?: if (active) XInverse else XText, tween(220), label = "tile-fg")
    Column(
        modifier
            .clip(XCommunityShape.tile)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(19.dp))
        Text(label.uppercase(), style = XCommunityType.tileLabel, color = ink, maxLines = 1)
    }
}

/**
 * A roster row — `gap:12px;padding:11px 0;border-bottom:1px solid var(--hair)`,
 * a 40px squircle avatar and an 11px online dot ringed in the card colour.
 */
@Composable
private fun XCommunityMemberRow(
    member: CloudMember,
    divider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp)) {
                XLetterAvatar(
                    text = member.displayName,
                    size = 40.dp,
                    shape = XCommunityShape.plate,
                    letterColor = Color(0xFF101014),
                )
                if (member.isOnline) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(XSurface)
                            .padding(2.5.dp)
                            .clip(CircleShape)
                            .background(XCommunityOnline),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(member.displayName, style = XCommunityType.memberName, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (member.isOnline) tr("online") else "@${member.nickname}",
                    style = XCommunityType.memberSub,
                    color = XMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) trailing() else XRoleBadge(member)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(XBorder))
    }
}

/** A row of the media / pins / leave card — `gap:12px;padding:14px 0`. */
@Composable
private fun XCommunityListRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    tint: Color = XText,
    divider: Boolean = true,
    chevron: Boolean = true,
    gap: Dp = 12.dp,
    chevronSize: Dp = 17.dp,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            Text(title, style = XCommunityType.rowTitle, color = tint, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = XCommunityType.cardCount, color = XMuted)
            if (chevron) Icon(Icons.Outlined.ChevronRight, null, tint = XMuted, modifier = Modifier.size(chevronSize))
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(XBorder))
    }
}

@Composable
private fun XCommunityEmptyLine(text: String) {
    Text(
        text,
        style = XCommunityType.aboutBody,
        color = XMuted,
        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
        textAlign = TextAlign.Center,
    )
}

/** «поделиться» — системный шэр-лист со ссылкой-приглашением. */
@Composable
private fun rememberCommunityShare(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url: String ->
            runCatching {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                context.startActivity(Intent.createChooser(share, null))
            }
            Unit
        }
    }
}

/** Copies through the platform clipboard, the way the rest of the app does. */
@Composable
private fun rememberCommunityCopy(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text: String ->
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.setPrimaryClip(ClipData.newPlainText("OrionX", text))
            Unit
        }
    }
}

/** The inline error / notice strip every community screen shares. */
@Composable
private fun XCommunityStatus(state: XCommunityState) {
    state.error?.let { XCommunityBanner(it, XRed) }
    state.notice?.let { message ->
        XCommunityBanner(message, XGreen)
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2_000)
            state.notice = null
        }
    }
}

@Composable
private fun XCommunityBanner(text: String, tint: Color) {
    Text(
        text,
        style = XType.meta,
        color = tint,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun XMemberRow(
    member: CloudMember,
    subtitle: String? = null,
    divider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                XLetterAvatar(member.displayName, 42.dp)
                if (member.isOnline) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(XSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(XGreen))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(member.displayName, style = XType.rowTitle, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle ?: if (member.isOnline) tr("online") else "@${member.nickname}",
                    style = XType.meta,
                    color = XMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
        if (divider) Box(Modifier.fillMaxWidth().padding(start = 68.dp).height(1.dp).background(XHairline))
    }
}

/** The uppercase role pill — the canvas shows the custom tag when one is set. */
@Composable
private fun XRoleBadge(member: CloudMember) {
    if (!member.isStaff) return
    val label = member.adminTag.ifBlank { xRoleLabel(member) }
    // `padding:6px 10px;border-radius:999px;background:var(--sf2)`
    Text(
        label.uppercase(),
        style = XCommunityType.rolePill,
        color = XAccentSoft,
        modifier = Modifier.clip(CircleShape).background(XSurfaceRaised).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun XPinRow(pin: CloudPinnedMessage, canUnpin: Boolean, onUnpin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.PushPin, null, tint = XAccent, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(pin.senderName, style = XType.meta, color = XMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pin.body, style = XType.bodyStrong, color = XText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (canUnpin) {
            XCircleButton(
                icon = Icons.Outlined.Close,
                contentDescription = tr("community_unpin"),
                size = 32.dp,
                iconSize = 16.dp,
                background = XSurfaceRaised,
                tint = XMuted,
                onClick = onUnpin,
            )
        }
    }
}

@Composable
private fun XCommunityQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(XShape.card)
            .background(XSurfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, null, tint = XMuted, modifier = Modifier.size(17.dp))
        Text(label, style = XType.bodyStrong, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Label + boxed input, the shape the canvas uses for «название» and «описание». */
@Composable
private fun XLabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean = true,
    placeholder: String = "",
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (label.isNotBlank()) XCardLabel(label)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(XShape.chip)
                .background(XSurfaceRaised)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotBlank()) {
                Text(placeholder, style = XType.bodyStrong, color = XMuted3)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 5,
                textStyle = XType.bodyStrong.copy(color = if (enabled) XText else XMuted),
                cursorBrush = SolidColor(XAccent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The canvas' bottom sheet: a scrim, a rounded panel and a grabber. */
@Composable
private fun XCommunitySheetPanel(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    .clip(XShape.panel)
                    .background(XSurfaceSoft)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(42.dp, 4.dp)
                        .clip(CircleShape)
                        .background(XSurfaceRaised),
                )
                Spacer(Modifier.height(6.dp))
                XCardLabel(title)
                content()
                Spacer(Modifier.height(4.dp))
                XSurfacePill(label = tr("close"), modifier = Modifier.fillMaxWidth(), height = 48.dp, background = XSurfaceRaised, onClick = onDismiss)
            }
        }
    }
}

/** Destructive confirmation — canvas «покинуть группу» / «удалить». */
@Composable
internal fun XCommunityConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(XShape.panel).background(XSurfaceSoft).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(title, style = XType.rowTitle, color = XText)
                    Text(message, style = XType.body, color = XMuted)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(XSurfaceSoft)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(confirmLabel, style = XType.rowTitle, color = XRed)
                }
                XSurfacePill(label = tr("cancel"), modifier = Modifier.fillMaxWidth(), height = 52.dp, onClick = onDismiss)
            }
        }
    }
}

/** Promote sheet with the optional custom tag the canvas calls «тег». */
@Composable
private fun XAdminTagSheet(
    member: CloudMember,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var tag by remember(member.userId) { mutableStateOf(member.adminTag) }
    XCommunitySheetPanel(title = tr("community_promote"), onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            XLetterAvatar(member.displayName, 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(member.displayName, style = XType.rowTitle, color = XText)
                Text("@${member.nickname}", style = XType.meta, color = XMuted)
            }
        }
        Spacer(Modifier.height(6.dp))
        XLabelledField(
            label = tr("community_admin_tag"),
            value = tag,
            onValueChange = { tag = it.take(16) },
            enabled = true,
            placeholder = tr("community_admin_tag_placeholder"),
        )
        Text(tr("community_admin_tag_hint"), style = XType.meta, color = XMuted)
        Spacer(Modifier.height(6.dp))
        XInversePill(label = tr("community_promote"), modifier = Modifier.fillMaxWidth()) { onConfirm(tag) }
    }
}

// ---------------------------------------------------------------------------
// Wording helpers
// ---------------------------------------------------------------------------

private fun xCommunitySubtitle(channel: Boolean, count: Int): String =
    if (channel) tr("community_subscriber_count", "count" to "$count")
    else tr("community_member_count", "count" to "$count")

private fun xRoleLabel(member: CloudMember): String = when (member.memberRole) {
    "owner" -> tr("community_role_owner")
    "admin" -> tr("community_role_admin")
    "moderator" -> tr("community_role_moderator")
    "subscriber" -> tr("community_role_subscriber")
    else -> tr("community_role_member")
}

/** `#5B3FD6` — the deep violet the canvas fills a reaction chip with. */
internal val XCommunityReactionChip = Color(0xFF5B3FD6)

/** The five the canvas ships with, used until a community picks its own. */
internal val XDefaultReactionSet = listOf("❤️", "🔥", "👍", "😂", "😮")

/** «АВТОУДАЛЕНИЕ»: ВЫКЛ / 24 Ч / 7 ДН / 30 ДН. */
private val XAutoDeleteOptions = listOf(0, 86_400, 604_800, 2_592_000)

private fun xAutoDeleteLabel(seconds: Int): String = when (seconds) {
    0 -> tr("community_slow_off")
    86_400 -> tr("community_auto_delete_day")
    604_800 -> tr("community_auto_delete_week")
    else -> tr("community_auto_delete_month")
}

private fun xAutoDeleteHint(seconds: Int): String =
    if (seconds <= 0) tr("community_auto_delete_never")
    else tr("community_auto_delete_on", "window" to xAutoDeleteLabel(seconds).lowercase())

/** The palette «+ ЭМОДЗИ» offers; the set itself is what the community saves. */
internal val XReactionPalette = listOf(
    "❤️", "🔥", "👍", "👎", "😂", "😮", "😢", "🙏",
    "🎉", "👏", "🤔", "😍", "🤯", "💯", "✅", "❌",
    "⚡", "🌟", "🚀", "🍿", "☕", "🧠", "🫶", "😴",
)

/** The sheet «+ ЭМОДЗИ» opens — a plain grid, six to a row. */
@Composable
internal fun XReactionPickerSheet(
    taken: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .xShadow(XCommunityShape.card, elevation = 20.dp, shadowColor = XShadowStrong)
                .clip(XCommunityShape.card)
                .background(XSurfaceSoft)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("community_reaction_add").uppercase(),
                style = XCommunityType.fieldLabel,
                color = XMuted2,
            )
            XReactionPalette.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { emoji ->
                        val used = emoji in taken
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (used) XCommunityReactionChip else XSurfaceRaised)
                                .clickable(enabled = !used) { onPick(emoji) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emoji, fontSize = 20.sp, lineHeight = 20.sp)
                        }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun xSlowModeLabel(seconds: Int): String = when (seconds) {
    0 -> tr("community_slow_off")
    else -> if (seconds % 60 == 0) tr("community_slow_minutes", "n" to "${seconds / 60}")
    else tr("community_slow_seconds", "n" to "$seconds")
}

private fun xSlowModeHint(seconds: Int): String =
    if (seconds <= 0) tr("community_slow_off_hint")
    else tr("community_slow_on_hint", "value" to xSlowModeLabel(seconds))
