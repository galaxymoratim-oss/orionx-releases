package net.orionx.messenger

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.CloudConversation
import net.orionx.messenger.cloud.CloudProfile
import net.orionx.messenger.cloud.CloudSession
import net.orionx.messenger.cloud.CommunityFlags
import net.orionx.messenger.cloud.SupabaseApi

/*
 * Canvas screen «создание» — the full-screen form the «создать» pill opens.
 *
 * It replaces the old dark-blue dialog, which was the last pre-redesign surface reachable
 * from the chats screen. The layout follows the canvas card for card: kind picker, avatar
 * with name, description with a counter, type with the public link, who may write plus
 * slow mode, the toggles, the member picker, and the closing note.
 *
 * Every control writes to the server in one call: `create_community_v3` (migration 018)
 * takes the identity fields plus the whole switch block, so a new community is never
 * briefly live with the wrong permissions.
 */

/** `linear-gradient(140deg,…)` — the four avatar presets the canvas cycles through. */
private val XCreateAvatars: List<List<Color>> = listOf(
    listOf(Color(0xFF8E7BFF), Color(0xFF2A1750)),
    listOf(Color(0xFFFFB48A), Color(0xFFE39BD8), Color(0xFF8FA8FF)),
    listOf(Color(0xFF7BE0C8), Color(0xFF2F6FA8)),
    listOf(Color(0xFFFF9E76), Color(0xFFC2456F)),
)

/** `CRTG_G` / `CRTG_C` — the canvas toggle rows, per kind. */
private data class XCreateToggle(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val default: Boolean,
)

private val XCreateGroupToggles = listOf(
    XCreateToggle("hist", Icons.Outlined.History, "история для новых", "новые участники увидят прошлые сообщения", true),
    XCreateToggle("inv", Icons.Outlined.PersonAdd, "участники приглашают", "любой может добавить друга в группу", true),
    XCreateToggle("media", Icons.Outlined.Image, "медиа и файлы", "фото, видео и документы разрешены", true),
    XCreateToggle("appr", Icons.Outlined.HowToReg, "заявки на вступление", "админ подтверждает каждого нового", false),
)

private val XCreateChannelToggles = listOf(
    XCreateToggle("sign", Icons.Outlined.Forum, "подписи авторов", "под постом видно, кто из админов опубликовал", true),
    XCreateToggle("views", Icons.Outlined.Visibility, "счётчик просмотров", "число просмотров видно всем в ленте", true),
    XCreateToggle("chat", Icons.Outlined.Forum, "чат обсуждения", "комментарии уйдут в отдельный групповой чат", true),
    XCreateToggle("fwd", Icons.Outlined.Forward, "разрешить пересылку", "посты можно репостить дальше", true),
)

/** `['выкл','30 сек','1 мин','5 мин']` and the seconds each one means. */
private val XCreateSlowSeconds = listOf(0, 10, 60, 300)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun XCreateCommunityScreen(
    api: SupabaseApi,
    session: CloudSession,
    /** «группа» or «канал» — the canvas opens the channel form from the channels tab. */
    initialKind: String,
    onBack: () -> Unit,
    onCreated: (CloudConversation) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var channel by remember { mutableStateOf(initialKind == "channel") }
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var avatarIndex by remember { mutableIntStateOf(0) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var privateType by remember { mutableStateOf(false) }
    var handle by remember { mutableStateOf("") }
    var access by remember { mutableIntStateOf(0) }
    var slow by remember { mutableIntStateOf(0) }
    // «НАБОР РЕАКЦИЙ» — в макете он есть уже в форме создания, а не только в настройках.
    var reactionSet by remember { mutableStateOf(XDefaultReactionSet) }
    var pickingEmoji by remember { mutableStateOf(false) }
    // Keyed by kind + toggle key, exactly like the canvas' `crTg`, so switching between
    // группа and канал brings each set back the way it was left.
    var toggles by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var people by remember { mutableStateOf<List<CloudProfile>>(emptyList()) }
    var peopleQuery by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) avatarUri = uri
    }

    LaunchedEffect(session.accessToken) {
        runCatching { withContext(Dispatchers.IO) { api.allProfiles(session.accessToken) } }
            .onSuccess { loaded -> people = loaded.filterNot { it.id == session.userId || it.blocked } }
    }

    // The canvas card lists everyone; a real account has hundreds, so the field filters
    // and the card draws the first screenful.
    val visiblePeople = remember(people, peopleQuery) {
        val q = peopleQuery.trim().removePrefix("@").lowercase()
        people
            .filter { q.isBlank() || it.nickname.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
            .take(40)
    }

    val toggleSpecs = if (channel) XCreateChannelToggles else XCreateGroupToggles
    fun toggleOn(spec: XCreateToggle): Boolean =
        toggles["${if (channel) 1 else 0}${spec.key}"] ?: spec.default

    // `crReady = crName.trim().length >= 3 && (crType === 1 || crHOk)`
    val handleOk = handle.length >= 5
    val ready = name.trim().length >= 3 && (privateType || handleOk) && !busy

    fun submit() {
        if (!ready) return
        busy = true
        error = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val avatarUrl = avatarUri?.let { uri ->
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Не удалось прочитать аватар")
                        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                        api.uploadCommunityAvatar(session.accessToken, session.userId, mime, bytes)
                    }
                    // Every switch on the form goes out with the create call itself
                    // (`create_community_v3`), so the community is never briefly live
                    // with the wrong permissions. `members_can_post` is «все» only —
                    // the other two options keep writing to staff, which is the rule
                    // the server enforces.
                    api.createCommunity(
                        token = session.accessToken,
                        title = name.trim(),
                        description = about.trim(),
                        type = if (channel) "channel" else "group",
                        memberIds = selected.toList(),
                        isPublic = !privateType,
                        avatarUrl = avatarUrl,
                        username = handle.takeIf { !privateType && handleOk },
                        flags = CommunityFlags(
                            membersCanPost = access == 0,
                            slowModeSeconds = XCreateSlowSeconds[slow],
                            reactionSetOnCreate = reactionSet,
                            membersCanInvite = if (channel) false else toggleOn(XCreateGroupToggles[1]),
                            historyVisibleToNew = if (channel) true else toggleOn(XCreateGroupToggles[0]),
                            mediaAllowed = if (channel) true else toggleOn(XCreateGroupToggles[2]),
                            joinRequiresApproval = if (channel) false else toggleOn(XCreateGroupToggles[3]),
                            allowForwarding = if (channel) toggleOn(XCreateChannelToggles[3]) else true,
                            showAuthorSignature = if (channel) toggleOn(XCreateChannelToggles[0]) else true,
                            showViewCounter = if (channel) toggleOn(XCreateChannelToggles[1]) else true,
                            discussionEnabled = if (channel) toggleOn(XCreateChannelToggles[2]) else true,
                        ),
                    )
                }
            }
            busy = false
            result.onSuccess(onCreated).onFailure { error = localizedNetworkError(it) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        XScreenHeader(
            title = if (channel) "новый канал" else "новая группа",
            onBack = onBack,
        ) {
            XCreateSubmitPill(enabled = ready, busy = busy, onClick = ::submit)
        }

        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                // `crKinds` — two cards, the active one filled with `--txt`.
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    XCreateKindCard(
                        icon = Icons.Outlined.Groups,
                        label = "группа",
                        subtitle = "общий чат, все пишут",
                        active = !channel,
                        modifier = Modifier.weight(1f),
                    ) { channel = false; access = 0 }
                    XCreateKindCard(
                        icon = Icons.Outlined.Campaign,
                        label = "канал",
                        subtitle = "вы публикуете, все читают",
                        active = channel,
                        modifier = Modifier.weight(1f),
                    ) { channel = true; access = 0 }
                }
            }

            item {
                XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        XCreateAvatar(
                            letter = (name.trim().firstOrNull() ?: if (channel) 'К' else 'Г').uppercaseChar().toString(),
                            gradient = XCreateAvatars[avatarIndex % XCreateAvatars.size],
                            uri = avatarUri,
                            // A tap cycles the canvas presets; the badge opens the gallery.
                            onCycle = { avatarIndex = (avatarIndex + 1) % XCreateAvatars.size; avatarUri = null },
                            onPick = { avatarPicker.launch("image/*") },
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                (if (channel) "название канала" else "название группы").uppercase(),
                                style = XCommunityType.fieldLabel,
                                color = XMuted2,
                            )
                            XCommunityInput(
                                value = name,
                                onValueChange = { name = it.take(80) },
                                enabled = !busy,
                                textStyle = XCommunityType.inputStrong,
                                placeholder = "без названия",
                            )
                        }
                    }
                }
            }

            item {
                XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        XCreateCardHeader("описание", "${about.length}/255")
                        XCommunityInput(
                            value = about,
                            onValueChange = { about = it.take(255) },
                            enabled = !busy,
                            textStyle = XCommunityType.inputBody,
                            singleLine = false,
                            placeholder = "о чём это место",
                            minHeight = 86.dp,
                            shape = XCommunityShape.textArea,
                        )
                    }
                }
            }

            item {
                XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                (if (channel) "тип канала" else "тип группы").uppercase(),
                                style = XCommunityType.fieldLabel,
                                color = XMuted2,
                                maxLines = 1,
                            )
                            Text(
                                if (!privateType) {
                                    if (channel) "виден всем и ищется по ссылке" else "вступают по ссылке"
                                } else "только по приглашению",
                                style = XCommunityType.hint,
                                color = XMuted,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        XCreateTabs(
                            options = listOf("публичный", "частный"),
                            selected = if (privateType) 1 else 0,
                            height = 40.dp,
                            shape = XCommunityShape.field,
                            enabled = !busy,
                        ) { privateType = it == 1 }
                        AnimatedVisibility(
                            visible = !privateType,
                            enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(160)) + shrinkVertically(tween(180)),
                        ) {
                            XCreateHandleField(
                                handle = handle,
                                ok = handleOk,
                                enabled = !busy,
                                onChange = { handle = it.filter { c -> c.isLetterOrDigit() || c == '_' }.lowercase().take(32) },
                            )
                        }
                    }
                }
            }

            item {
                XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            (if (channel) "кто может комментировать" else "кто может писать").uppercase(),
                            style = XCommunityType.fieldLabel,
                            color = XMuted2,
                        )
                        XCreateTabs(
                            options = if (channel) listOf("все", "подписчики", "никто") else listOf("все", "по одобрению", "админы"),
                            selected = access,
                            height = 38.dp,
                            shape = XCommunityShape.segment,
                            enabled = !busy,
                        ) { access = it }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "медленный режим".uppercase(),
                                style = XCommunityType.fieldLabel.copy(fontSize = 9.sp),
                                color = XMuted2,
                            )
                            XCreateTabs(
                                options = listOf("выкл", "10 с", "1 мин", "5 мин"),
                                selected = slow,
                                height = 36.dp,
                                shape = RoundedCornerShape(12.dp),
                                labelSize = 9.5.sp,
                                enabled = !busy,
                            ) { slow = it }
                        }
                    }
                }
            }

            item {
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
                                        .clickable(enabled = !busy && reactionSet.size > 1) {
                                            reactionSet = reactionSet - emoji
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
                                        .clickable(enabled = !busy) { pickingEmoji = true }
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

            item {
                XCommunityCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                    toggleSpecs.forEachIndexed { index, spec ->
                        XCreateToggleRow(
                            spec = spec,
                            checked = toggleOn(spec),
                            last = index == toggleSpecs.lastIndex,
                        ) { next ->
                            toggles = toggles + ("${if (channel) 1 else 0}${spec.key}" to next)
                        }
                    }
                }
            }

            item {
                // Canvas: one card, the people stacked inside it with a hairline between
                // rows. The search field is the one addition — the canvas mock has six
                // people, a real account has hundreds.
                XCommunityCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        XCreateCardHeader(
                            if (channel) "пригласить первых" else "участники",
                            if (selected.isEmpty()) "никого не выбрано" else "выбрано ${selected.size}",
                        )
                        XCommunityInput(
                            value = peopleQuery,
                            onValueChange = { peopleQuery = it },
                            enabled = !busy,
                            textStyle = XCommunityType.searchField,
                            placeholder = "имя или @ник",
                        )
                        when {
                            people.isEmpty() -> XCreateNote("загружаем людей…")
                            visiblePeople.isEmpty() -> XCreateNote("никого не нашлось")
                            else -> Column {
                                visiblePeople.forEachIndexed { index, person ->
                                    XCreatePersonRow(
                                        person = person,
                                        checked = person.id in selected,
                                        enabled = !busy,
                                        last = index == visiblePeople.lastIndex,
                                    ) {
                                        selected = if (person.id in selected) selected - person.id else selected + person.id
                                    }
                                }
                            }
                        }
                        if (people.size > visiblePeople.size && peopleQuery.isBlank()) {
                            XCreateNote("показаны первые ${visiblePeople.size} — найдите остальных поиском")
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Info, null, tint = XMuted2, modifier = Modifier.size(17.dp))
                    Text(
                        if (channel) "канал сразу станет вашим: публиковать смогут только вы и приглашённые админы"
                        else "вы станете владельцем группы и сможете назначить админов после создания",
                        style = XCommunityType.emptyNote.copy(fontSize = 11.5.sp),
                        color = XMuted2,
                    )
                }
            }

            error?.let { message ->
                item {
                    Text(
                        message,
                        style = XCommunityType.emptyNote,
                        color = XRed,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }

    if (pickingEmoji) {
        XReactionPickerSheet(
            taken = reactionSet,
            onPick = { emoji ->
                pickingEmoji = false
                reactionSet = reactionSet + emoji
            },
            onDismiss = { pickingEmoji = false },
        )
    }
}

/**
 * `background:{{ crBtnBg }};color:{{ crBtnFg }}` — filled once the form is valid, flat
 * `--sf` while it is not.
 */
@Composable
private fun XCreateSubmitPill(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (enabled) XText else XSurface, tween(220), label = "cr-btn-bg")
    val foreground by animateColorAsState(if (enabled) XInverse else XMuted, tween(220), label = "cr-btn-fg")
    Box(
        Modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(background)
            .xPressable(pressedScale = .97f, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = foreground, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Text("создать".uppercase(), style = XCommunityType.savePill, color = foreground)
        }
    }
}

/** One of the two kind cards — `border-radius:24px;padding:15px 15px 16px;gap:9px`. */
@Composable
private fun XCreateKindCard(
    icon: ImageVector,
    label: String,
    subtitle: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(if (active) XText else XSurface, tween(240), label = "cr-kind-bg")
    val foreground by animateColorAsState(if (active) XInverse else XText, tween(240), label = "cr-kind-fg")
    val subForeground by animateColorAsState(if (active) XOnMuted else XMuted, tween(240), label = "cr-kind-sub")
    Column(
        modifier
            .xShadow(XShape.card)
            .clip(XShape.card)
            .background(background)
            .xPressable(pressedScale = .97f, onClick = onClick)
            .padding(start = 15.dp, end = 15.dp, top = 15.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, null, tint = foreground, modifier = Modifier.size(22.dp))
        Text(label, style = XCommunityType.rowTitle.copy(fontWeight = FontWeight.ExtraBold), color = foreground)
        Text(
            subtitle,
            style = XCommunityType.memberSub.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
            color = subForeground,
        )
    }
}

/** The 68 px avatar with the `photo_camera` badge. */
@Composable
private fun XCreateAvatar(
    letter: String,
    gradient: List<Color>,
    uri: Uri?,
    onCycle: () -> Unit,
    onPick: () -> Unit,
) {
    Box(Modifier.size(76.dp), contentAlignment = Alignment.TopStart) {
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient))
                .xPressable(pressedScale = .96f, onClick = onCycle),
            contentAlignment = Alignment.Center,
        ) {
            if (uri != null) {
                XLocalImage(uri, Modifier.fillMaxSize())
            } else {
                Text(
                    letter,
                    style = XCommunityType.heroName.copy(fontSize = 26.sp),
                    color = Color(0xFFFCFBFF),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .xShadow(CircleShape, elevation = 8.dp)
                .clip(CircleShape)
                .background(XSurfaceRaised)
                .xPressable(pressedScale = .92f, onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PhotoCamera, "Выбрать фото", tint = XText, modifier = Modifier.size(15.dp))
        }
    }
}


/** Preview for a freshly picked avatar — the file is local, so no cache is involved. */
@Composable
private fun XLocalImage(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    bitmap?.let {
        androidx.compose.foundation.Image(it, null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}

/** `label` on the left, a small right-aligned counter or hint on the right. */
@Composable
private fun XCreateCardHeader(label: String, hint: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = XCommunityType.fieldLabel, color = XMuted2)
        Text(
            hint,
            style = XCommunityType.hint,
            color = XMuted2,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A row of equal-width segments, the active one filled with `--txt`. */
@Composable
private fun XCreateTabs(
    options: List<String>,
    selected: Int,
    height: Dp,
    shape: Shape,
    labelSize: TextUnit = 10.sp,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, option ->
            val active = index == selected
            val background by animateColorAsState(if (active) XText else XSurfaceRaised, tween(220), label = "cr-tab-bg")
            val foreground by animateColorAsState(if (active) XInverse else XMuted, tween(220), label = "cr-tab-fg")
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(shape)
                    .background(background)
                    .xPressable(pressedScale = .97f, enabled = enabled && !active) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.uppercase(),
                    style = XCommunityType.segment.copy(fontSize = labelSize),
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** `orionx.me/@…` with the validity glyph from the canvas. */
@Composable
private fun XCreateHandleField(handle: String, ok: Boolean, enabled: Boolean, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(XCommunityShape.textArea)
            .background(XSurfaceRaised)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("orionx.me/@", style = XCommunityType.rowTitle, color = XMuted)
        Box(Modifier.weight(1f)) {
            if (handle.isEmpty()) {
                Text("ссылка", style = XCommunityType.rowTitle.copy(fontWeight = FontWeight.ExtraBold), color = XMuted3)
            }
            BasicTextField(
                value = handle,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = XCommunityType.rowTitle.copy(fontWeight = FontWeight.ExtraBold, color = XText),
                cursorBrush = SolidColor(XAccent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
            null,
            tint = if (ok) Color(0xFF3ED27E) else Color(0xFFFF9B6A),
            modifier = Modifier.size(17.dp),
        )
    }
}

/** `padding:14px 0` with a hairline under every row but the last. */
@Composable
private fun XCreateToggleRow(
    spec: XCreateToggle,
    checked: Boolean,
    last: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Icon(spec.icon, null, tint = XText, modifier = Modifier.size(19.dp))
            Column(Modifier.weight(1f)) {
                Text(spec.title, style = XCommunityType.rowTitle, color = XText)
                Spacer(Modifier.height(4.dp))
                Text(
                    spec.subtitle,
                    style = XCommunityType.memberSub.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = XMuted,
                )
            }
            XCreateSwitch(checked, onCheckedChange)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(XHairline))
    }
}

/** `44×26;border-radius:13px;padding:3px`, knob 20 px, `translateX(18px)` when on. */
@Composable
private fun XCreateSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (checked) Color(0xFF5B3FD6) else XSurfaceRaised, tween(240), label = "cr-tg-track")
    val knob by animateColorAsState(if (checked) Color(0xFFFCFBFF) else XMuted2, tween(240), label = "cr-tg-knob")
    val offset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = tween(260, easing = CubicBezierEasing(.33f, 1f, .68f, 1f)),
        label = "cr-tg-x",
    )
    Box(
        Modifier
            .size(44.dp, 26.dp)
            .clip(XCommunityShape.segment)
            .background(track)
            .xPressable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = offset).size(20.dp).clip(CircleShape).background(knob))
    }
}

/** `padding:11px 0` — avatar, name over nickname, and a 24 px check box. */
@Composable
private fun XCreatePersonRow(
    person: CloudProfile,
    checked: Boolean,
    enabled: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
) {
    val boxBackground by animateColorAsState(
        if (checked) Color(0xFF5B3FD6) else XSurfaceRaised,
        tween(200),
        label = "cr-people-box",
    )
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .xPressable(enabled = enabled, onClick = onToggle)
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (person.avatarUrl.isNotBlank()) {
                RemoteImage(person.avatarUrl, Modifier.size(38.dp).clip(CircleShape), ContentScale.Crop)
            } else {
                XLetterAvatar(person.displayName.ifBlank { person.nickname }, 38.dp, CircleShape)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    person.displayName.ifBlank { person.nickname },
                    style = XCommunityType.rowTitle.copy(fontWeight = FontWeight.ExtraBold),
                    color = XText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text("@${person.nickname}", style = XCommunityType.memberSub.copy(fontSize = 11.sp), color = XMuted, maxLines = 1)
            }
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(boxBackground),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Icon(Icons.Outlined.Check, null, tint = Color(0xFFFCFBFF), modifier = Modifier.size(16.dp))
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(XHairline))
    }
}

/** `font:600 12.5px/1.4;color:var(--mut)` — the small note inside a card. */
@Composable
private fun XCreateNote(text: String) {
    Text(
        text,
        style = XCommunityType.emptyNote,
        color = XMuted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
