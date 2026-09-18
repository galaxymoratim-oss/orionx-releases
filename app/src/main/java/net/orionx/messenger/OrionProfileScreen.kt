package net.orionx.messenger

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.CloudProfile
import net.orionx.messenger.cloud.CloudSession
import net.orionx.messenger.cloud.SupabaseApi

/*
 * The canvas `profile` screen.
 *
 * Its shape differs from the old form: a centred hero with the avatar, three counter
 * tiles, then a list where each row *expands in place* into its own editor instead of
 * every field being a permanently open text box.
 */

private object XProShape {
    /** `border-radius:20px` — a settings card. */
    val card = RoundedCornerShape(20.dp)
    /** `border-radius:24px` — a counter tile. */
    val tile = RoundedCornerShape(24.dp)
    /** `border-radius:14px` — the editor field and its buttons. */
    val field = RoundedCornerShape(14.dp)
}

private object XProType {
    /** `font:800 11px;letter-spacing:.14em;uppercase` — the «настройки» pill. */
    val headerPill = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = .14.em)
    /** `font:800 24px/1.1;letter-spacing:-.02em` */
    val name = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 27.sp, letterSpacing = (-.02).em)
    /** `font:600 13px/1` */
    val handle = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    /** `font:800 22px/1` — a counter. */
    val metric = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    /** `font:700 10.5px;letter-spacing:.12em;uppercase` — a counter caption. */
    val metricLabel = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = .12.em)
    /** `font:800 13px;letter-spacing:.16em;uppercase` — the section label. */
    val section = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = .16.em)
    /** `font:800 13.5px/1` — a row title. */
    val rowTitle = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
    /** `font:600 12px/1` — a row value. */
    val rowValue = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    /** `font:700 14px/1.2` — the editor input. */
    val input = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp)
    /** `font:800 10.5px;letter-spacing:.1em;uppercase` — the editor buttons. */
    val button = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = .1.em)
    /** `font:700 10.5px/1` — the character counter. */
    val counter = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
}

/** `transition:max-height .34s cubic-bezier(.22,1,.36,1)` */
private val XProEasing = CubicBezierEasing(.22f, 1f, .36f, 1f)

/** `background:#5B3FD6` on the save button — the canvas' one saturated accent. */
private val XProSaveColor = Color(0xFF5B3FD6)

/** Which row currently has its editor open. */
private enum class XProField { NONE, NAME, NICKNAME, BIO, PHONE }

@Composable
internal fun XProfileCanvasScreen(
    api: SupabaseApi,
    session: CloudSession,
    profile: CloudProfile,
    onProfileChanged: (CloudProfile) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQr: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var editing by remember { mutableStateOf(XProField.NONE) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    // Start from what was counted last time; the screen is destroyed on navigation, so
    // without this the tiles show 0 0 0 on every visit until the request lands.
    var counters by remember(session.userId) {
        mutableStateOf(XScreenCache.countersOf(session.userId) ?: Triple(0, 0, 0))
    }

    LaunchedEffect(session.userId) {
        runCatching { withContext(Dispatchers.IO) { api.profileCounters(session.accessToken, session.userId) } }
            .onSuccess { counters = it; XScreenCache.putCounters(session.userId, it) }
    }

    fun commit(field: XProField, value: String) {
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.updateExtendedProfile(
                        token = session.accessToken,
                        userId = session.userId,
                        nickname = if (field == XProField.NICKNAME) value else profile.nickname,
                        displayName = if (field == XProField.NAME) value else profile.displayName,
                        bio = if (field == XProField.BIO) value else profile.bio,
                        phone = if (field == XProField.PHONE) value else profile.phone,
                        birthday = profile.birthday,
                        location = profile.location,
                        website = profile.website,
                        avatarUrl = profile.avatarUrl,
                    )
                }
            }
                .onSuccess {
                    onProfileChanged(it)
                    editing = XProField.NONE
                    messageIsError = false
                    message = tr("profile_saved")
                }
                .onFailure {
                    messageIsError = true
                    message = localizedNetworkError(it)
                }
            busy = false
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Не удалось прочитать файл")
                    val url = api.uploadProfileAvatar(session.accessToken, session.userId, mime, bytes)
                    api.updateExtendedProfile(
                        token = session.accessToken,
                        userId = session.userId,
                        nickname = profile.nickname,
                        displayName = profile.displayName,
                        bio = profile.bio,
                        phone = profile.phone,
                        birthday = profile.birthday,
                        location = profile.location,
                        website = profile.website,
                        avatarUrl = url,
                    )
                }
            }
                .onSuccess { onProfileChanged(it); messageIsError = false; message = tr("profile_saved") }
                .onFailure { messageIsError = true; message = localizedNetworkError(it) }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // `gap:11px;padding:2px 16px 6px` — back, a spacer, then the «настройки» pill.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
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
            Spacer(Modifier.weight(1f))
            // `background:var(--txt);border-radius:999px;padding:12px 18px`
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(XText)
                    .xPressable(onClick = onOpenSettings)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(tr("settings").uppercase(), style = XProType.headerPill, color = XInverse)
            }
        }

        message?.let {
            Text(
                it,
                style = XProType.rowValue,
                color = if (messageIsError) XRed else XGreen,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                // 110dp снизу, как в макете: строка «мой QR» не должна уходить
                // под плавающий док меню при прокрутке в конец.
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // `108px` avatar with a `38px` camera badge hanging off the corner.
            Box(Modifier.size(108.dp)) {
                if (profile.avatarUrl.isNotBlank()) {
                    RemoteImage(profile.avatarUrl, Modifier.fillMaxSize().clip(CircleShape), ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(XProfileOrbBrush))
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(XSurfaceSoft)
                        .xPressable { avatarPicker.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PhotoCamera, null, tint = XText, modifier = Modifier.size(19.dp))
                }
            }

            Text(
                profile.displayName,
                style = XProType.name,
                color = XText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            Text(
                "@${profile.nickname} · orionx ${BuildConfig.VERSION_NAME}",
                style = XProType.handle,
                color = XMuted,
            )

            // `gap:9px;padding:20px 0 12px` — three `border-radius:24px` tiles.
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                XProCounter(counters.first, tr("profile_chats"), Modifier.weight(1f))
                XProCounter(counters.second, tr("profile_media"), Modifier.weight(1f))
                XProCounter(counters.third, tr("profile_contacts"), Modifier.weight(1f))
            }

            Text(
                tr("profile_section").uppercase(),
                style = XProType.section,
                color = XMuted,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 12.dp),
            )

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                XProEditableRow(
                    icon = Icons.Outlined.Badge,
                    title = tr("profile_name"),
                    value = profile.displayName,
                    open = editing == XProField.NAME,
                    busy = busy,
                    draft = draft,
                    onDraft = { draft = it },
                    onOpen = { editing = XProField.NAME; draft = profile.displayName; message = null },
                    onClose = { editing = XProField.NONE },
                    onSave = { commit(XProField.NAME, draft) },
                )
                XProEditableRow(
                    icon = Icons.Outlined.AlternateEmail,
                    title = tr("profile_username"),
                    value = "@${profile.nickname}",
                    open = editing == XProField.NICKNAME,
                    busy = busy,
                    draft = draft,
                    onDraft = { value -> draft = value.lowercase().filter { it.isLetterOrDigit() || it == '_' } },
                    onOpen = { editing = XProField.NICKNAME; draft = profile.nickname; message = null },
                    onClose = { editing = XProField.NONE },
                    onSave = { commit(XProField.NICKNAME, draft) },
                )
                XProEditableRow(
                    icon = Icons.Outlined.Info,
                    title = tr("profile_bio"),
                    value = profile.bio.ifBlank { tr("profile_bio_empty") },
                    open = editing == XProField.BIO,
                    busy = busy,
                    draft = draft,
                    onDraft = { draft = it.take(280) },
                    maxLength = 280,
                    singleLine = false,
                    onOpen = { editing = XProField.BIO; draft = profile.bio; message = null },
                    onClose = { editing = XProField.NONE },
                    onSave = { commit(XProField.BIO, draft) },
                )
                XProEditableRow(
                    icon = Icons.Outlined.Call,
                    title = tr("profile_phone"),
                    value = profile.phone.ifBlank { tr("profile_phone_empty") },
                    open = editing == XProField.PHONE,
                    busy = busy,
                    draft = draft,
                    onDraft = { draft = it },
                    onOpen = { editing = XProField.PHONE; draft = profile.phone; message = null },
                    onClose = { editing = XProField.NONE },
                    onSave = { commit(XProField.PHONE, draft) },
                )

                // `мой QR-код` — a plain navigation row, no editor.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .xShadow(XProShape.card)
                        .clip(XProShape.card)
                        .background(XSurface),
                ) {
                    Row(
                        Modifier.fillMaxWidth().xPressable(onClick = onOpenQr).padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        Icon(Icons.Outlined.QrCode2, null, tint = XText, modifier = Modifier.size(21.dp))
                        Text(tr("profile_qr"), style = XProType.rowTitle, color = XText, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight, null, tint = XMuted3, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** `flex:1;border-radius:24px;padding:16px 14px` */
@Composable
private fun XProCounter(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .xShadow(XProShape.tile)
            .clip(XProShape.tile)
            .background(XSurface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Text("$value", style = XProType.metric, color = XText)
        Text(
            label.uppercase(),
            style = XProType.metricLabel,
            color = XMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * A row that opens its own editor underneath —
 * `max-height .34s cubic-bezier(.22,1,.36,1)` with the chevron turning 90°.
 */
@Composable
private fun XProEditableRow(
    icon: ImageVector,
    title: String,
    value: String,
    open: Boolean,
    busy: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    maxLength: Int = 0,
    singleLine: Boolean = true,
) {
    val chevron by animateFloatAsState(if (open) 90f else 0f, tween(300, easing = XProEasing), label = "pro-chevron")
    Column(
        Modifier
            .fillMaxWidth()
            .xShadow(XProShape.card)
            .clip(XProShape.card)
            .background(XSurface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .xPressable { if (open) onClose() else onOpen() }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Icon(icon, null, tint = XText, modifier = Modifier.size(21.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = XProType.rowTitle, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    value,
                    style = XProType.rowValue,
                    color = XMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                tint = XMuted3,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevron },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(340, easing = XProEasing)) + fadeIn(tween(260)),
            exit = shrinkVertically(tween(340, easing = XProEasing)) + fadeOut(tween(200)),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                // `background:var(--sf3);border:1px solid var(--hairB);border-radius:14px`
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(XProShape.field)
                        .background(XSurfaceSoft)
                        .border(1.dp, XBorder, XProShape.field)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraft,
                        singleLine = singleLine,
                        maxLines = if (singleLine) 1 else 5,
                        textStyle = XProType.input.copy(color = XText),
                        cursorBrush = SolidColor(XAccent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (maxLength > 0) {
                    Text(
                        "${draft.length} / $maxLength",
                        style = XProType.counter,
                        color = XMuted3,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .height(42.dp)
                            .clip(XProShape.field)
                            .background(XSurfaceRaised)
                            .xPressable(onClick = onClose)
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(tr("cancel").uppercase(), style = XProType.button, color = XMuted)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(XProShape.field)
                            .background(XProSaveColor)
                            .xPressable(enabled = !busy, onClick = onSave),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(tr("save").uppercase(), style = XProType.button, color = Color.White)
                    }
                }
            }
        }
    }
}
