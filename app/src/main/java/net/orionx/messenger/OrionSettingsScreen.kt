package net.orionx.messenger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.CloudDevice
import net.orionx.messenger.cloud.CloudProfile
import net.orionx.messenger.cloud.CloudSession
import net.orionx.messenger.cloud.SupabaseApi

/*
 * The canvas `settings` screen.
 *
 * The app used to show nine category tiles, each opening its own panel. The canvas
 * has no such split: everything lives on one scrolling screen as cards of rows, and
 * the heavier topics — privacy, animation, devices, blocked, about — expand in place
 * instead of navigating away.
 *
 * Values below are transcribed from the canvas markup, each quoted next to its use.
 */

private object XSetShape {
    /** `border-radius:28px` — a settings card. */
    val card = RoundedCornerShape(28.dp)
    /** `border-radius:12px;padding:3px` — the theme segment tray. */
    val tray = RoundedCornerShape(12.dp)
    /** `width:34px;height:28px;border-radius:9px` — one theme segment. */
    val traySegment = RoundedCornerShape(9.dp)
    /** `border-radius:14px;padding:4px` — the halo segment tray. */
    val wideTray = RoundedCornerShape(14.dp)
    /** `height:34px;border-radius:10px` — one halo segment. */
    val wideSegment = RoundedCornerShape(10.dp)
}

private object XSetType {
    /** `font:800 19px/1;letter-spacing:-.01em` — the screen title. */
    val title = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = (-.01).em)
    /** `font:700 14px/1` — a row title. */
    val row = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    /** `font:700 12px/1` — the value on the right of a row. */
    val value = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    /** `font:600 14px/1` — the search field. */
    val search = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    /** `font:800 9.5px;letter-spacing:.06em;uppercase` — a halo segment label. */
    val segment = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 9.5.sp, letterSpacing = .06.em)
    /** `font:700 11px;letter-spacing:.08em;uppercase` — a slider label. */
    val sliderLabel = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = .08.em)
    /** `font:700 11px/1` — a slider readout. */
    val sliderValue = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    /** `font:600 12.5px/1.45` — the nested rows inside an expanded section. */
    val nested = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 18.sp)
}

/** `transition:max-height .42s cubic-bezier(.22,1,.28,1),opacity .3s ease` */
private val XSetExpandEasing = CubicBezierEasing(.22f, 1f, .28f, 1f)

// ---------------------------------------------------------------------------

@Composable
internal fun XSettingsScreen(
    api: SupabaseApi,
    session: CloudSession,
    profile: CloudProfile,
    motion: XMotionSettings,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenHorizon: () -> Unit,
    onOpenStorage: () -> Unit,
    onCategory: (SettingsCategory) -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val runtime = LocalXRuntimeSettings.current
    val updateRuntime = LocalXRuntimeSettingsUpdater.current
    val language = LocalOrionLanguage.current

    val context = LocalContext.current

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var privacyOpen by remember { mutableStateOf(false) }
    var animationOpen by remember { mutableStateOf(false) }
    var devicesOpen by remember { mutableStateOf(false) }
    var blockedOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var blockedQuery by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    var devices by remember { mutableStateOf<List<CloudDevice>>(emptyList()) }
    var blocked by remember { mutableStateOf<List<CloudProfile>>(emptyList()) }
    // Пустой список — валидное состояние после отзыва последнего устройства/разбана,
    // поэтому «загружено или нет» отслеживаем отдельно, чтобы переоткрытие обновляло данные.
    var devicesLoaded by remember { mutableStateOf(false) }
    var blockedLoaded by remember { mutableStateOf(false) }
    var mediaMode by remember(context) { mutableIntStateOf(xLoadMediaMode(context)) }
    var skinMode by remember(context) { mutableIntStateOf(xLoadSkin(context)) }
    var blockScreenshots by remember(context) { mutableStateOf(xLoadScreenshotBlock(context)) }
    var optState by remember { mutableIntStateOf(0) }
    var optPercent by remember { mutableIntStateOf(0) }
    var optFreed by remember { mutableLongStateOf(0L) }

    val isPlatformAdmin = profile.role.equals("admin", true) &&
        profile.email.equals(net.orionx.messenger.cloud.SupabaseConfig.ADMIN_EMAIL, true)

    fun save(patch: org.json.JSONObject) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.updateUserSettings(session.accessToken, session.userId, patch) } }
                .onFailure { status = localizedNetworkError(it) }
        }
    }

    // Both lists load lazily — the canvas only reveals them when the row is opened.
    fun loadDevices() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.userDevices(session.accessToken, session.userId) } }
                .onSuccess { devices = it; devicesLoaded = true; status = null }
                .onFailure { status = localizedNetworkError(it) }
        }
    }

    fun loadBlocked() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val ids = api.blockedUserIds(session.accessToken, session.userId)
                    if (ids.isEmpty()) emptyList()
                    else api.allProfiles(session.accessToken).filter { it.id in ids }
                }
            }
                .onSuccess { blocked = it; blockedLoaded = true; status = null }
                .onFailure { status = localizedNetworkError(it) }
        }
    }

    val needle = query.trim().lowercase()
    fun shows(vararg keys: String): Boolean =
        needle.isBlank() || keys.any { xSettingsMatch(it, needle) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        XSettingsHeader(
            searching = searching,
            query = query,
            onQuery = { query = it },
            onBack = onBack,
            onToggleSearch = {
                searching = !searching
                if (!searching) query = ""
            },
        )

        status?.let {
            Text(it, style = XSetType.value, color = XRed, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // ---- account ----
            if (shows("профиль", "profile", "имя пользователя", "username", "номер телефона", "phone")) {
                item {
                    XSetCard {
                        if (shows("профиль", "profile")) {
                            XSetRow(
                                icon = Icons.Outlined.Person,
                                title = xSettingText("profile_row"),
                                chevron = true,
                                divider = shows("имя пользователя", "username", "номер телефона", "phone"),
                                onClick = onOpenProfile,
                            )
                        }
                        if (shows("имя пользователя", "username")) {
                            XSetRow(
                                icon = Icons.Outlined.AlternateEmail,
                                title = xSettingText("username_row"),
                                value = "@${profile.nickname}",
                                divider = shows("номер телефона", "phone"),
                            )
                        }
                        if (shows("номер телефона", "phone")) {
                            XSetRow(
                                icon = Icons.Outlined.Call,
                                title = xSettingText("phone_row"),
                                value = profile.phone.ifBlank { "—" },
                                divider = false,
                            )
                        }
                    }
                }
            }

            // ---- logout ----
            if (shows("выйти", "logout")) {
                item {
                    XSetCard {
                        XSetRow(
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            title = xSettingText("logout_row"),
                            tint = XRed,
                            divider = false,
                            onClick = onLogout,
                        )
                    }
                }
            }

            // ---- appearance and system ----
            item {
                XSetCard {
                    if (shows("тема", "theme")) {
                        // `display:flex;gap:3px;background:var(--sf2);border-radius:12px;padding:3px`
                        XSetRow(
                            icon = if (runtime.theme == "light") Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            title = xSettingText("theme_row"),
                            divider = true,
                        ) {
                            Row(
                                Modifier.clip(XSetShape.tray).background(XSurfaceRaised).padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                listOf(
                                    "system" to Icons.Outlined.Smartphone,
                                    "light" to Icons.Outlined.LightMode,
                                    "dark" to Icons.Outlined.DarkMode,
                                ).forEach { (key, icon) ->
                                    val active = runtime.theme == key
                                    val bg by animateColorAsState(if (active) XText else Color.Transparent, tween(200), label = "theme-bg")
                                    val fg by animateColorAsState(if (active) XInverse else XMuted, tween(200), label = "theme-fg")
                                    Box(
                                        Modifier
                                            .size(34.dp, 28.dp)
                                            .clip(XSetShape.traySegment)
                                            .background(bg)
                                            .xPressable {
                                                val next = runtime.copy(theme = key)
                                                updateRuntime(next)
                                                save(org.json.JSONObject().apply { put("theme", key) })
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (shows("horizon", "горизонт")) {
                        XSetRow(
                            icon = Icons.Outlined.Apps,
                            title = "horizon",
                            chevron = true,
                            divider = true,
                            onClick = onOpenHorizon,
                        )
                    }

                    if (shows("уведомления", "notifications")) {
                        // Мастер-переключатель личных уведомлений; раздельные тумблеры —
                        // в категории «уведомления». Раньше он ошибочно двигал show_online.
                        val notificationsEnabled = rememberXPreference(context, "notify_messages", true)
                        XSetRow(
                            icon = Icons.Outlined.Notifications,
                            title = xSettingText("notifications_row"),
                            divider = true,
                        ) {
                            XSwitch(checked = notificationsEnabled.value) { enabled ->
                                notificationsEnabled.value = enabled
                            }
                        }
                    }

                    if (shows("приватность", "privacy", "отчёты о прочтении", "последний вход")) {
                        XSetRow(
                            icon = Icons.Outlined.Lock,
                            title = xSettingText("privacy_row"),
                            chevron = true,
                            chevronRotated = privacyOpen,
                            divider = true,
                            onClick = { privacyOpen = !privacyOpen },
                        )
                        XSetExpand(visible = privacyOpen) {
                            XSetNestedToggle(
                                title = xSettingText("privacy_last_seen"),
                                checked = runtime.showOnline,
                            ) { enabled ->
                                val next = runtime.copy(showOnline = enabled)
                                updateRuntime(next)
                                save(org.json.JSONObject().apply { put("show_online", enabled) })
                            }
                            XSetNestedToggle(
                                title = xSettingText("privacy_read_receipts"),
                                checked = runtime.readReceipts,
                            ) { enabled ->
                                val next = runtime.copy(readReceipts = enabled)
                                updateRuntime(next)
                                save(org.json.JSONObject().apply { put("read_receipts", enabled) })
                            }
                            // The canvas' third switch. There is no server field for it,
                            // so it is a device setting — and a real one: it drives
                            // FLAG_SECURE, which blocks screenshots and screen capture.
                            XSetNestedToggle(
                                title = xSettingText("privacy_screenshots"),
                                checked = blockScreenshots,
                            ) { enabled ->
                                blockScreenshots = enabled
                                xSaveScreenshotBlock(context, enabled)
                                xApplyScreenshotBlock(context, enabled)
                            }
                        }
                    }

                    if (shows("язык", "language")) {
                        XSetRow(
                            icon = Icons.Outlined.Language,
                            title = xSettingText("language_row"),
                            value = language.nativeName.lowercase(),
                            chevron = true,
                            divider = true,
                            onClick = { onCategory(SettingsCategory.APPEARANCE) },
                        )
                    }

                    if (shows("звук", "sound", "вибрация")) {
                        // Раньше этот переключатель ошибочно управлял анимациями; теперь он
                        // глушит звук уведомлений (OrionNotifications читает notify_sound).
                        val soundEnabled = rememberXPreference(context, "notify_sound", true)
                        XSetRow(
                            icon = Icons.Outlined.VolumeUp,
                            title = xSettingText("sound_row"),
                            divider = true,
                        ) {
                            XSwitch(checked = soundEnabled.value) { enabled ->
                                soundEnabled.value = enabled
                            }
                        }
                    }

                    if (shows("оформление чата", "chat", "скин", "skin")) {
                        // `cycleSkin` — the canvas cycles the label only; the palette
                        // itself is not repainted there either.
                        XSetRow(
                            icon = Icons.Outlined.Palette,
                            title = xSettingText("skin_row"),
                            value = xSettingText(XChatSkins[skinMode]),
                            divider = true,
                            onClick = {
                                skinMode = (skinMode + 1) % XChatSkins.size
                                xSaveSkin(context, skinMode)
                            },
                        )
                    }
                    if (shows("анимация", "animation", "переход")) {
                        XSetRow(
                            icon = Icons.Outlined.Animation,
                            title = xSettingText("anim_row"),
                            value = xSettingText(motion.mode.labelKey),
                            chevron = true,
                            chevronRotated = animationOpen,
                            divider = false,
                            onClick = { animationOpen = !animationOpen },
                        )
                        XSetExpand(visible = animationOpen) {
                            XSetMotionBlock(motion)
                        }
                    }
                }
            }
            // ---- devices, data, blocked, about ----
            item {
                XSetCard {
                    if (shows("устройства", "devices")) {
                        XSetRow(
                            icon = Icons.Outlined.Devices,
                            title = xSettingText("devices_row"),
                            value = if (devices.isEmpty()) null else "${devices.size}",
                            chevron = true,
                            chevronRotated = devicesOpen,
                            divider = true,
                            onClick = {
                                devicesOpen = !devicesOpen
                                if (devicesOpen && !devicesLoaded) loadDevices()
                            },
                        )
                        XSetExpand(visible = devicesOpen) {
                            if (devices.isEmpty()) {
                                XSetNestedNote(xSettingText("devices_empty"))
                            }
                            devices.forEach { device ->
                                XSetNestedRow(
                                    title = device.deviceName.ifBlank { device.platform },
                                    subtitle = listOf(device.platform, device.appVersion)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                ) {
                                    // `padding:8px 13px;border-radius:999px;background:var(--sf2)`
                                    XSetNestedPill(
                                        label = xSettingText("device_revoke"),
                                        tint = XRed,
                                    ) {
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) { api.removeDevice(session.accessToken, device.id) }
                                            }
                                                .onSuccess { loadDevices() }
                                                .onFailure { status = localizedNetworkError(it) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (shows("данные", "память", "data", "storage")) {
                        XSetRow(
                            icon = Icons.Outlined.Storage,
                            title = xSettingText("data_row"),
                            chevron = true,
                            divider = true,
                            onClick = onOpenStorage,
                        )
                    }

                    if (shows("автозагрузка", "медиа", "media")) {
                        // The canvas cycles «по wi-fi / всегда / никогда» on tap.
                        XSetRow(
                            icon = Icons.Outlined.Download,
                            title = xSettingText("media_row"),
                            value = xSettingText(XMediaAutoDownload.entries[mediaMode].labelKey),
                            divider = true,
                            onClick = {
                                mediaMode = (mediaMode + 1) % XMediaAutoDownload.entries.size
                                xSaveMediaMode(context, mediaMode)
                            },
                        )
                    }

                    if (shows("заблокированные", "blocked")) {
                        XSetRow(
                            icon = Icons.Outlined.Block,
                            title = xSettingText("blocked_row"),
                            value = if (blocked.isEmpty()) null else "${blocked.size}",
                            chevron = true,
                            chevronRotated = blockedOpen,
                            divider = true,
                            onClick = {
                                blockedOpen = !blockedOpen
                                if (blockedOpen && !blockedLoaded) loadBlocked()
                            },
                        )
                        XSetExpand(visible = blockedOpen) {
                            val filtered = blocked.filter {
                                blockedQuery.isBlank() ||
                                    it.displayName.contains(blockedQuery, true) ||
                                    it.nickname.contains(blockedQuery, true)
                            }
                            if (blocked.isNotEmpty()) {
                                XSetNestedSearch(
                                    value = blockedQuery,
                                    onValueChange = { blockedQuery = it },
                                    placeholder = xSettingText("blocked_search"),
                                )
                            }
                            if (filtered.isEmpty()) {
                                XSetNestedNote(xSettingText("blocked_empty"))
                            }
                            filtered.forEach { person ->
                                XSetNestedRow(
                                    title = person.displayName,
                                    subtitle = "@${person.nickname}",
                                    leading = {
                                        // `32px;border-radius:50%;font:800 12px;color:#1a1a1f`
                                        XLetterAvatar(
                                            text = person.displayName,
                                            size = 32.dp,
                                            letterColor = Color(0xFF1A1A1F),
                                        )
                                    },
                                ) {
                                    XSetNestedPill(label = xSettingText("blocked_unblock"), tint = XText) {
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    api.unblockUser(session.accessToken, session.userId, person.id)
                                                }
                                            }
                                                .onSuccess { loadBlocked() }
                                                .onFailure { status = localizedNetworkError(it) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (shows("помощь", "help")) {
                        XSetRow(
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            title = xSettingText("help_row"),
                            chevron = true,
                            divider = true,
                            onClick = { onCategory(SettingsCategory.HELP) },
                        )
                    }

                    if (shows("о приложении", "about", "версия")) {
                        XSetRow(
                            icon = Icons.Outlined.Info,
                            title = xSettingText("about_row"),
                            value = BuildConfig.VERSION_NAME,
                            chevron = true,
                            chevronRotated = aboutOpen,
                            divider = false,
                            onClick = { aboutOpen = !aboutOpen },
                        )
                        XSetExpand(visible = aboutOpen) {
                            XSetNestedNote("orionx ${BuildConfig.VERSION_NAME} · сборка ${BuildConfig.VERSION_CODE}")
                            XSetNestedNote(xSettingText("about_e2ee"))
                        }
                    }
                }
            }

            // ---- updates ----
            if (shows("обновление", "update", "админ", "admin")) {
                item {
                    XSetCard {
                        if (shows("оптимизация", "optimi", "кеш", "cache")) {
                            // The canvas fakes a progress here. This clears the real
                            // cache directory and reports what it actually freed.
                            XSetRow(
                                icon = Icons.Outlined.Speed,
                                title = xSettingText("optimize_row"),
                                value = when (optState) {
                                    1 -> "$optPercent%"
                                    2 -> xSettingText("optimize_done").replace("{size}", xFormatBytes(optFreed))
                                    else -> xSettingText("optimize_run")
                                },
                                divider = true,
                                onClick = {
                                    if (optState == 1) return@XSetRow
                                    optState = 1
                                    optPercent = 0
                                    scope.launch {
                                        val freed = xClearCache(context) { done, total ->
                                            optPercent = if (total == 0) 100 else (done * 100 / total)
                                        }
                                        optFreed = freed
                                        optPercent = 100
                                        optState = 2
                                    }
                                },
                            )
                            XSetExpand(visible = optState == 1) {
                                XSetProgressBar(optPercent / 100f)
                            }
                        }
                        if (shows("обновление", "update")) {
                            XSetRow(
                                icon = Icons.Outlined.SystemUpdateAlt,
                                title = xSettingText("update_row"),
                                chevron = true,
                                divider = isPlatformAdmin,
                                onClick = { onCategory(SettingsCategory.UPDATES) },
                            )
                        }
                        if (isPlatformAdmin && shows("админ", "admin", "добавить обновление")) {
                            XSetRow(
                                icon = Icons.Outlined.CloudUpload,
                                title = xSettingText("admin_upload_row"),
                                value = xSettingText("admin_badge"),
                                chevron = true,
                                divider = false,
                                onClick = { onCategory(SettingsCategory.ADMIN) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header — the title slides out and a search pill slides in.
// ---------------------------------------------------------------------------

@Composable
private fun XSettingsHeader(
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    // `transition:opacity .26s ease,transform 0.51s cubic-bezier(.22,1,.28,1)`
    val slide = tween<Float>(510, easing = XSetExpandEasing)
    val titleAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (searching) 0f else 1f, tween(260), label = "title-alpha",
    )
    val titleShift by androidx.compose.animation.core.animateFloatAsState(
        if (searching) -24f else 0f, slide, label = "title-x",
    )
    val fieldAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (searching) 1f else 0f, tween(260), label = "field-alpha",
    )
    val fieldShift by androidx.compose.animation.core.animateFloatAsState(
        if (searching) 0f else 24f, slide, label = "field-x",
    )
    // The right button rotates one icon out and the other in.
    val searchRotation by androidx.compose.animation.core.animateFloatAsState(
        if (searching) -90f else 0f, slide, label = "icon-search-rot",
    )
    val closeRotation by androidx.compose.animation.core.animateFloatAsState(
        if (searching) 0f else 90f, slide, label = "icon-close-rot",
    )

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
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
        Box(Modifier.weight(1f).height(42.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = titleAlpha; translationX = titleShift.dp.toPx() }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(xSettingText("settings_title"), style = XSetType.title, color = XText)
            }
            // `height:42px;border-radius:21px;background:var(--sf);gap:10px;padding:0 14px`
            Row(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fieldAlpha; translationX = fieldShift.dp.toPx() }
                    .xShadow(CircleShape)
                    .clip(CircleShape)
                    .background(XSurface)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Search, null, tint = XMuted, modifier = Modifier.size(19.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(xSettingText("settings_search_hint"), style = XSetType.search, color = XMuted)
                    }
                    if (searching) {
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = XSetType.search.copy(color = XText),
                            cursorBrush = SolidColor(XAccent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .size(42.dp)
                .xShadow(CircleShape)
                .clip(CircleShape)
                .background(XSurface)
                .xPressable(onClick = onToggleSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Search, null, tint = XText,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    alpha = 1f - fieldAlpha
                    rotationZ = searchRotation
                },
            )
            Icon(
                Icons.Outlined.Close, null, tint = XText,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    alpha = fieldAlpha
                    rotationZ = closeRotation
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

/** `background:var(--sf);border-radius:28px;padding:6px 18px;box-shadow:var(--sh)` */
@Composable
private fun XSetCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .xShadow(XSetShape.card)
            .clip(XSetShape.card)
            .background(XSurface)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        content = content,
    )
}

/** `display:flex;align-items:center;gap:14px;padding:16px 0;border-bottom:1px solid var(--bd)` */
@Composable
private fun XSetRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    tint: Color = XText,
    chevron: Boolean = false,
    chevronRotated: Boolean = false,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        if (chevronRotated) 90f else 0f,
        tween(420, easing = XSetExpandEasing),
        label = "row-chevron",
    )
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.xPressable(onClick = onClick) else it }
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            Text(
                title,
                style = XSetType.row,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(value, style = XSetType.value, color = XMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
            if (chevron) {
                Icon(
                    Icons.Outlined.ChevronRight, null, tint = XMuted,
                    modifier = Modifier.size(19.dp).graphicsLayer { rotationZ = rotation },
                )
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(XBorder))
    }
}

/** `max-height .42s cubic-bezier(.22,1,.28,1)` + `opacity .3s ease` */
@Composable
private fun XSetExpand(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(420, easing = XSetExpandEasing)) + fadeIn(tween(300)),
        exit = shrinkVertically(tween(420, easing = XSetExpandEasing)) + fadeOut(tween(300)),
    ) {
        Column(Modifier.padding(bottom = 14.dp), content = content)
    }
}

/** A row inside an expanded section: optional avatar, title, subtitle, trailing pill. */
@Composable
private fun XSetNestedRow(
    title: String,
    subtitle: String,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (leading == null) 35.dp else 0.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = XSetType.nested, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = XSetType.value.copy(fontSize = 11.sp), color = XMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** `padding:8px 13px;border-radius:999px;background:var(--sf2);font:800 9.5px;.1em;uppercase` */
@Composable
private fun XSetNestedPill(label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(XSurfaceRaised)
            .xPressable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(
            label.uppercase(),
            style = XSetType.segment.copy(letterSpacing = .1.em),
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun XSetNestedNote(text: String) {
    Text(
        text,
        style = XSetType.nested,
        color = XMuted,
        modifier = Modifier.fillMaxWidth().padding(start = 35.dp, top = 6.dp, bottom = 6.dp),
    )
}

@Composable
private fun XSetNestedSearch(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 35.dp, top = 4.dp, bottom = 8.dp)
            .clip(CircleShape)
            .background(XSurfaceRaised)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, null, tint = XMuted, modifier = Modifier.size(17.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = XSetType.nested, color = XMuted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = XSetType.nested.copy(color = XText),
                cursorBrush = SolidColor(XAccent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** `SKINS = ['лиловый','графит','рассвет']`. */
private val XChatSkins = listOf("skin_lilac", "skin_graphite", "skin_dawn")

/**
 * Canvas «скриншоты в чатах». Backed by `FLAG_SECURE` on the activity window, which
 * is what actually stops screenshots and screen recording.
 */
internal fun xLoadScreenshotBlock(context: android.content.Context): Boolean =
    context.getSharedPreferences("orionx_redesign", android.content.Context.MODE_PRIVATE)
        .getBoolean("block_screenshots", false)

private fun xSaveScreenshotBlock(context: android.content.Context, blocked: Boolean) {
    context.getSharedPreferences("orionx_redesign", android.content.Context.MODE_PRIVATE)
        .edit().putBoolean("block_screenshots", blocked).apply()
}

internal fun xApplyScreenshotBlock(context: android.content.Context, blocked: Boolean) {
    val window = (context as? android.app.Activity)?.window ?: return
    if (blocked) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
}

private fun xSaveSkin(context: android.content.Context, mode: Int) {
    context.getSharedPreferences("orionx_redesign", android.content.Context.MODE_PRIVATE)
        .edit().putInt("chat_skin", mode).apply()
}

private fun xLoadSkin(context: android.content.Context): Int =
    context.getSharedPreferences("orionx_redesign", android.content.Context.MODE_PRIVATE)
        .getInt("chat_skin", 0).coerceIn(0, XChatSkins.lastIndex)

/** `height:6px;border-radius:3px;background:var(--sf2)` with a `--txt` fill. */
@Composable
private fun XSetProgressBar(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 35.dp, top = 4.dp, bottom = 8.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(XSurfaceRaised),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(XText),
        )
    }
}

/**
 * Deletes the app's cache directory, reporting progress per top-level entry, and
 * returns how many bytes went away.
 */
private suspend fun xClearCache(
    context: android.content.Context,
    onProgress: (done: Int, total: Int) -> Unit,
): Long = withContext(Dispatchers.IO) {
    fun sizeOf(file: java.io.File): Long =
        if (file.isDirectory) file.listFiles()?.sumOf { sizeOf(it) } ?: 0L else file.length()

    val roots = listOfNotNull(context.cacheDir, context.externalCacheDir)
    val entries = roots.flatMap { it.listFiles()?.toList() ?: emptyList() }
    var freed = 0L
    entries.forEachIndexed { index, entry ->
        freed += runCatching { sizeOf(entry) }.getOrDefault(0L)
        runCatching { entry.deleteRecursively() }
        withContext(Dispatchers.Main) { onProgress(index + 1, entries.size) }
        // The canvas shows the bar travelling; without a beat it would jump to 100.
        kotlinx.coroutines.delay(60)
    }
    freed
}

private fun xFormatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f ГБ".format(bytes / (1024f * 1024 * 1024))
    bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} МБ"
    bytes >= 1024L -> "${bytes / 1024} КБ"
    else -> "$bytes Б"
}

/** Canvas `MEDIA = ['по wi-fi','всегда','никогда']`, kept as a local preference. */
private enum class XMediaAutoDownload(val labelKey: String) {
    WIFI("media_wifi"),
    ALWAYS("media_always"),
    NEVER("media_never"),
}

private fun xLoadMediaMode(context: android.content.Context): Int =
    context.getSharedPreferences("orionx_redesign", android.content.Context.MODE_PRIVATE)
        .getInt("media_autodownload", 0)
        .coerceIn(0, XMediaAutoDownload.entries.lastIndex)

private fun xSaveMediaMode(context: android.content.Context, mode: Int) {
    context.getSharedPreferences("orionx_redesign", android.content.Context.MODE_PRIVATE)
        .edit().putInt("media_autodownload", mode).apply()
}

@Composable
private fun XSetNestedToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 35.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = XSetType.nested, color = XText, modifier = Modifier.weight(1f))
        XSwitch(checked = checked, onCheckedChange = onChange)
    }
}
/**
 * The «анимация» body the canvas reveals under the row: speed with worded edges, then
 * «тип перехода» as content-sized chips.
 */
@Composable
private fun XSetMotionBlock(motion: XMotionSettings) {
    var draft by remember(motion.speed) { mutableFloatStateOf(motion.speed) }
    val rounded = kotlin.math.round(draft * 10f) / 10f
    Column(Modifier.padding(start = 35.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                xSettingText("anim_speed").uppercase(),
                style = XSetType.sliderLabel.copy(fontSize = 9.5.sp, letterSpacing = .14.em),
                color = XMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${xSettingText(motion.speedWordKey)} · ${if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}" else "$rounded"}×",
                style = XSetType.sliderValue,
                color = XText,
            )
        }
        Slider(
            value = draft,
            // `min="0.5" max="2" step="0.1"`
            onValueChange = { raw -> draft = (kotlin.math.round(raw * 10f) / 10f).coerceIn(.5f, 2f) },
            onValueChangeFinished = { motion.chooseSpeed(draft) },
            valueRange = .5f..2f,
            steps = 14,
            colors = SliderDefaults.colors(
                thumbColor = XText,
                activeTrackColor = XText,
                inactiveTrackColor = XSurfaceRaised,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val edge = XSetType.sliderValue.copy(fontSize = 9.5.sp)
            Text(xSettingText("anim_speed_slow"), style = edge, color = XMuted2)
            Text(xSettingText("anim_speed_instant"), style = edge, color = XMuted2)
        }
        Text(
            xSettingText("anim_kind").uppercase(),
            style = XSetType.sliderLabel.copy(fontSize = 9.5.sp, letterSpacing = .14.em),
            color = XMuted,
        )
        // `min-height:34px;padding:0 14px;border-radius:999px;gap:7px`
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            XScreenTransition.entries.forEach { option ->
                val active = option == motion.mode
                val bg by animateColorAsState(if (active) XText else XSurfaceRaised, tween(300), label = "mode-bg")
                val fg by animateColorAsState(if (active) XInverse else XText, tween(300), label = "mode-fg")
                Box(
                    Modifier
                        .heightIn(min = 34.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .xPressable { motion.chooseMode(option) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        xSettingText(option.labelKey),
                        style = XSetType.value.copy(fontSize = 10.5.sp, letterSpacing = .02.em),
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** `width:64px` label, then the range, then the readout. */
@Composable
private fun XSetSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label.uppercase(), style = XSetType.sliderLabel, color = XMuted, modifier = Modifier.width(64.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = XText,
                activeTrackColor = XText,
                inactiveTrackColor = XSurfaceRaised,
            ),
        )
        Text(readout, style = XSetType.sliderValue, color = XText, modifier = Modifier.width(32.dp))
    }
}

/** Matches a row against the settings search, ignoring case. */
private fun xSettingsMatch(haystack: String, needle: String): Boolean =
    haystack.lowercase().contains(needle)
