package net.orionx.messenger

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The mini-apps the canvas lists for the horizon panel. */
internal enum class XHorizonApp(val key: String, val icon: ImageVector, val titleKey: String) {
    CALC("calc", Icons.Outlined.Calculate, "app_calc"),
    NOTE("note", Icons.Outlined.EditNote, "app_note"),
    WEATHER("weather", Icons.Outlined.WbSunny, "app_weather"),
    TIMER("timer", Icons.Outlined.Timer, "app_timer"),
    MUSIC("music", Icons.Outlined.MusicNote, "app_music"),
    FILES("files", Icons.Outlined.Folder, "app_files"),
    TRANSLATOR("translator", Icons.Outlined.Translate, "app_translator"),
    ;

    companion object {
        fun byKey(key: String): XHorizonApp? = entries.firstOrNull { it.key == key }
    }
}

/** How a mini-app opens: ask each time, always full screen, or always in a floating window. */
internal enum class XHorizonOpenMode(val key: String) { ASK("ask"), FULL("full"), WINDOW("window") }

private const val HORIZON_PREFS = "orionx_horizon"
private const val MAX_PANEL_APPS = 5

/** Panel state, persisted so the strip stays where the user put it. */
@Stable
internal class XHorizonState(private val context: Context) {
    private val prefs = context.getSharedPreferences(HORIZON_PREFS, Context.MODE_PRIVATE)

    var enabled by mutableStateOf(prefs.getBoolean("enabled", true))
        private set
    var onLeft by mutableStateOf(prefs.getString("side", "right") == "left")
        private set
    var openMode by mutableStateOf(
        XHorizonOpenMode.entries.firstOrNull { it.key == prefs.getString("open_mode", "ask") } ?: XHorizonOpenMode.ASK
    )
        private set
    var apps by mutableStateOf(
        prefs.getString("apps", "calc,note,timer")
            .orEmpty()
            .split(',')
            .mapNotNull { XHorizonApp.byKey(it.trim()) }
    )
        private set

    var menuOpen by mutableStateOf(false)

    val full: Boolean get() = apps.size >= MAX_PANEL_APPS
    val available: List<XHorizonApp> get() = XHorizonApp.entries.filterNot { it in apps }

    fun enable(value: Boolean) {
        enabled = value
        if (!value) menuOpen = false
        prefs.edit().putBoolean("enabled", value).apply()
    }

    fun setSide(left: Boolean) {
        onLeft = left
        prefs.edit().putString("side", if (left) "left" else "right").apply()
    }

    fun chooseOpenMode(mode: XHorizonOpenMode) {
        openMode = mode
        prefs.edit().putString("open_mode", mode.key).apply()
    }

    fun add(app: XHorizonApp) {
        if (full || app in apps) return
        apps = apps + app
        persistApps()
    }

    fun remove(app: XHorizonApp) {
        apps = apps - app
        persistApps()
    }

    private fun persistApps() {
        prefs.edit().putString("apps", apps.joinToString(",") { it.key }).apply()
    }
}

/**
 * The horizon strip and its fly-out: a 5x104 dp handle pinned to the chosen edge which opens a
 * 64 dp rail of mini-app tiles plus the panel settings.
 */
@Composable
internal fun BoxScope.XHorizon(
    state: XHorizonState,
    onOpenApp: (XHorizonApp) -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (!state.enabled) return
    val alignment = if (state.onLeft) Alignment.CenterStart else Alignment.CenterEnd

    if (state.menuOpen) {
        // Tapping anywhere else closes the rail.
        Box(Modifier.matchParentSize().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
        ) { state.menuOpen = false })
    }

    val reveal by animateFloatAsState(
        targetValue = if (state.menuOpen) 1f else 0f,
        animationSpec = tween(610, easing = CubicBezierEasing(.22f, 1f, .28f, 1f)),
        label = "horizon-reveal",
    )

    if (!state.menuOpen) {
        Box(
            Modifier
                .align(alignment)
                .padding(horizontal = 2.dp)
                .size(5.dp, 104.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(XText)
                .alpha(.55f)
                // Панель выезжает смахиванием от края (и по тапу — тоже).
                .pointerInput(state.onLeft) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val outward = if (state.onLeft) dragAmount else -dragAmount
                        if (outward > 40f) state.menuOpen = true
                    }
                }
                .clickable { state.menuOpen = true },
        )
    }

    if (reveal > 0f) {
        Column(
            Modifier
                .align(alignment)
                .padding(horizontal = 8.dp)
                .graphicsLayer {
                    alpha = reveal
                    translationX = (if (state.onLeft) -1f else 1f) * (1f - reveal) * 60.dp.toPx()
                }
                .width(64.dp)
                // 22dp тень расползалась по всему краю экрана — компактнее.
                .xShadow(RoundedCornerShape(32.dp), 12.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(XSurface)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.apps.forEach { app ->
                XHorizonTile(app.icon, xSettingText(app.titleKey)) {
                    state.menuOpen = false
                    onOpenApp(app)
                }
            }
            Box(Modifier.size(26.dp, 1.dp).background(XBorder))
            XHorizonTile(Icons.Outlined.Settings, xSettingText("horizon_settings")) {
                state.menuOpen = false
                onOpenSettings()
            }
        }
    }
}

@Composable
private fun XHorizonTile(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(XSurfaceRaised).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = XText, modifier = Modifier.size(24.dp))
    }
}

/** «horizon» settings screen: the on/off row, the side picker, how apps open, and the app lists. */
@Composable
internal fun XHorizonSettingsScreen(state: XHorizonState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        XScreenHeader(title = "horizon", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                XCard {
                    XCardRow(
                        icon = Icons.Outlined.PowerSettingsNew,
                        title = xSettingText("horizon_enabled"),
                        trailing = { XSwitch(checked = state.enabled, onCheckedChange = state::enable) },
                        onClick = { state.enable(!state.enabled) },
                    )
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Outlined.SwapHoriz, null, tint = XText, modifier = Modifier.size(21.dp))
                            Text(xSettingText("horizon_side"), style = XType.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = XText)
                        }
                        XSegmentedTabs(
                            labels = listOf(xSettingText("side_left"), xSettingText("side_right")),
                            selected = if (state.onLeft) 0 else 1,
                            onSelect = { state.setSide(it == 0) },
                            modifier = Modifier.height(36.dp),
                        )
                    }
                    Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(XBorder))
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Outlined.OpenInNew, null, tint = XText, modifier = Modifier.size(21.dp))
                            Text(xSettingText("horizon_open_how"), style = XType.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = XText)
                        }
                        XSegmentedTabs(
                            labels = listOf(xSettingText("mode_ask"), xSettingText("mode_full"), xSettingText("mode_window")),
                            selected = XHorizonOpenMode.entries.indexOf(state.openMode),
                            onSelect = { state.chooseOpenMode(XHorizonOpenMode.entries[it]) },
                            modifier = Modifier.height(36.dp),
                        )
                        Text(
                            when (state.openMode) {
                                XHorizonOpenMode.ASK -> xSettingText("mode_ask_hint")
                                XHorizonOpenMode.FULL -> xSettingText("mode_full_hint")
                                XHorizonOpenMode.WINDOW -> xSettingText("mode_window_hint")
                            },
                            style = XType.bodyStrong,
                            color = XMuted,
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    XCardLabel(xSettingText("horizon_in_panel"))
                    Text(
                        "${state.apps.size}/$MAX_PANEL_APPS",
                        style = XType.sectionLabel.copy(letterSpacing = .06.em),
                        color = if (state.full) XAccent else XMuted,
                    )
                }
            }

            item {
                XCard {
                    state.apps.forEachIndexed { index, app ->
                        XHorizonAppRow(
                            app = app,
                            divider = index != state.apps.lastIndex,
                            actionLabel = xSettingText("remove_action"),
                            actionColor = XRed,
                            onAction = { state.remove(app) },
                        )
                    }
                    if (state.apps.isEmpty()) {
                        Text(
                            xSettingText("horizon_panel_empty"),
                            style = XType.bodyStrong,
                            color = XMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                        )
                    }
                }
            }

            item { XCardLabel(xSettingText("horizon_system_apps"), Modifier.padding(start = 4.dp, top = 14.dp)) }

            item {
                XCard {
                    val available = state.available
                    available.forEachIndexed { index, app ->
                        XHorizonAppRow(
                            app = app,
                            divider = index != available.lastIndex,
                            actionLabel = xSettingText("add_action"),
                            actionColor = if (state.full) XMuted else XText,
                            onAction = { state.add(app) },
                        )
                    }
                    if (state.full) {
                        Text(
                            xSettingText("horizon_panel_full"),
                            style = XType.bodyStrong,
                            color = XMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                        )
                    }
                    if (available.isEmpty()) {
                        Text(
                            xSettingText("horizon_all_added"),
                            style = XType.bodyStrong,
                            color = XMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XHorizonAppRow(
    app: XHorizonApp,
    divider: Boolean,
    actionLabel: String,
    actionColor: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(XSurfaceRaised),
                contentAlignment = Alignment.Center,
            ) { Icon(app.icon, null, tint = XText, modifier = Modifier.size(20.dp)) }
            Text(
                xSettingText(app.titleKey),
                style = XType.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = XText,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.clip(CircleShape).background(XSurfaceRaised).clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(actionLabel.uppercase(), style = XType.pillSmall.copy(fontSize = 9.5.sp), color = actionColor)
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(XBorder))
    }
}

/** Where each mini-app lives in the shell's destination graph. */
internal fun xHorizonDestination(app: XHorizonApp): XDestination = when (app) {
    XHorizonApp.CALC -> XDestination.Calculator
    XHorizonApp.NOTE -> XDestination.Notes
    XHorizonApp.WEATHER -> XDestination.Weather
    XHorizonApp.TIMER -> XDestination.Timer
    XHorizonApp.MUSIC -> XDestination.Music
    XHorizonApp.FILES -> XDestination.Files
    XHorizonApp.TRANSLATOR -> XDestination.Translator
}

/**
 * «экран или окно» — the sheet horizon shows when the open mode is «спросить».
 */
@Composable
internal fun XHorizonOpenDialog(
    app: XHorizonApp,
    onFull: () -> Unit,
    onWindow: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .xShadow(RoundedCornerShape(26.dp), 26.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(XSurface)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(XSurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) { Icon(app.icon, null, tint = XText, modifier = Modifier.size(24.dp)) }
                Text(
                    xSettingText(app.titleKey),
                    style = XType.rowTitle.copy(fontSize = 16.sp),
                    color = XText,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(xSettingText("mode_ask_hint"), style = XType.bodyStrong, color = XMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(CircleShape).background(XText).clickable(onClick = onFull),
                    contentAlignment = Alignment.Center,
                ) { Text(xSettingText("open_full").uppercase(), style = XType.pillSmall, color = XInverse) }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(CircleShape).background(XSurfaceRaised).clickable(onClick = onWindow),
                    contentAlignment = Alignment.Center,
                ) { Text(xSettingText("open_window").uppercase(), style = XType.pillSmall, color = XText) }
            }
        }
    }
}
