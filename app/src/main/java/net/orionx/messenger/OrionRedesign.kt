@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package net.orionx.messenger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.*
import net.orionx.messenger.calls.*
import net.orionx.messenger.videonote.PendingVideoNote
import net.orionx.messenger.videonote.PendingVideoNoteBubble
import net.orionx.messenger.videonote.VideoNote
import net.orionx.messenger.videonote.VideoNoteBubble
import net.orionx.messenger.videonote.VideoNoteEncoder
import net.orionx.messenger.videonote.VideoNoteMiniPlayer
import net.orionx.messenger.videonote.VideoNoteOutbox
import net.orionx.messenger.videonote.VideoNotePlaybackController
import net.orionx.messenger.videonote.VideoNoteRecorderOverlay
import net.orionx.messenger.videonote.VideoNoteRecorderState
import net.orionx.messenger.videonote.VideoNoteThumbnails
import net.orionx.messenger.videonote.VideoNoteViewerDialog
import net.orionx.messenger.videonote.VideoNoteWatchState
import net.orionx.messenger.videonote.rememberVideoNotePlaybackController
import net.orionx.messenger.videonote.rememberVideoNoteRecorderState
import net.orionx.messenger.videonote.rememberVideoNoteWatchState
import net.orionx.messenger.push.OrionNotifications
import net.orionx.messenger.push.OrionMutedChats
import net.orionx.messenger.push.PushTokenSync


/** Repairs the most common UTF-8 -> Windows-1252 mojibake seen in stored emoji values. */
private fun normalizeEmojiText(value: String): String {
    if (value.isBlank()) return value
    val suspicious = value.any { it == 'ð' || it == 'Ã' || it == 'â' || it == 'Ÿ' || it == '�' }
    if (!suspicious) return value
    return runCatching {
        val repaired = String(value.toByteArray(Charset.forName("windows-1252")), Charsets.UTF_8)
        if ('�' in repaired) value else repaired
    }.getOrDefault(value)
}

private fun normalizeReactionList(values: List<String>): List<String> =
    values.map(::normalizeEmojiText).filter { it.isNotBlank() }.distinct().ifEmpty {
        listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
    }

private val LocalXCompact = staticCompositionLocalOf { false }
internal val LocalXRuntimeSettings = staticCompositionLocalOf { CloudUserSettings(theme = "dark") }
internal val LocalXRuntimeSettingsUpdater = staticCompositionLocalOf<(CloudUserSettings) -> Unit> { {} }

private const val X_PREFS_NAME = "orionx_redesign_settings"

private fun loadLocalUiSettings(context: Context): CloudUserSettings {
    val prefs = context.getSharedPreferences(X_PREFS_NAME, Context.MODE_PRIVATE)
    return CloudUserSettings(
        theme = prefs.getString("appearance_theme", "dark") ?: "dark",
        language = context.getSharedPreferences("orionx_language", Context.MODE_PRIVATE).getString("language", "ru") ?: "ru",
        fontScale = prefs.getFloat("font_scale", 1.0f).coerceIn(.9f, 1.3f),
        compactMode = prefs.getBoolean("compact_mode", false),
        reduceMotion = prefs.getBoolean("reduced_motion", false),
    )
}

private fun persistLocalUiSettings(context: Context, settings: CloudUserSettings) {
    context.getSharedPreferences(X_PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString("appearance_theme", settings.theme)
        .putFloat("font_scale", settings.fontScale.coerceIn(.9f, 1.3f))
        .putBoolean("compact_mode", settings.compactMode)
        .putBoolean("reduced_motion", settings.reduceMotion)
        .apply()
}

private enum class XMediaComposeMode { VOICE, VIDEO }

// Contacts folded into the search screen (the canvas shows «все контакты» there), so the shell
// has three destinations: chats, search and the settings full screen.
// The dock draws its own glyphs, so the old per-tab drawables are gone from here.
internal enum class XTab { CHATS, SEARCH, SETTINGS }

/**
 * The search tab keeps its own state outside the composable, the way the chats tab
 * does. Without this, switching tabs threw away the typed query and the contact list,
 * so coming back showed an empty screen while `myConversations` was fetched again.
 */
@Stable
private class XSearchTabState {
    val query = mutableStateOf("")
    val scope = mutableIntStateOf(0)
    val users = mutableStateOf<List<CloudProfile>>(emptyList())
    val communities = mutableStateOf<List<CloudConversation>>(emptyList())
    val contacts = mutableStateOf<List<CloudConversation>>(emptyList())
    val loadedOnce = mutableStateOf(false)
}

/**
 * What the header wordmark says instead of «OrionX».
 *
 * The list refreshes itself on a timer, so most of the time there is nothing to report —
 * a poll that comes back in a few hundred milliseconds must not make the title flicker.
 * [SYNCING] therefore appears only once a refresh has been running long enough to be a
 * wait, and [OFFLINE] only after the network has been failing for a while.
 */
internal enum class XSyncStatus { IDLE, SYNCING, OFFLINE }

/** A refresh has to outlast this before the header mentions it. */
private const val X_SYNC_ANNOUNCE_AFTER = 700L

/** And once it is up it stays at least this long, so the word never flashes. */
private const val X_SYNC_MIN_SHOWN = 900L

/** How long the network has to stay down before the header says so. */
private const val X_OFFLINE_AFTER = 15_000L

/** And how long «нет сети» stays up before the wordmark returns. */
private const val X_OFFLINE_SHOWN = 3_500L

@Stable
private class XChatsTabState {
    val conversations = mutableStateOf<List<CloudConversation>>(emptyList())
    val loading = mutableStateOf(true)
    val showArchived = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val locallyReadIds = mutableStateOf<Set<String>>(emptySet())
    val selectedIds = mutableStateOf<Set<String>>(emptySet())
    val loadedOnce = mutableStateOf(false)

    /** Which of the three chats tabs is open: 0 direct, 1 groups, 2 channels. */
    val chatKind = mutableIntStateOf(0)

    /** The «написать» dropdown: open flag, query, and what the search came back with. */
    val composeOpen = mutableStateOf(false)
    val composeQuery = mutableStateOf("")
    val composeFound = mutableStateOf<List<CloudProfile>>(emptyList())
    val composeSearching = mutableStateOf(false)

    /** Header status, read by [XHeader] one level up. */
    val syncStatus = mutableStateOf(XSyncStatus.IDLE)

    /** When the load in flight began; 0 when nothing is running. */
    val loadStartedAt = mutableLongStateOf(0L)

    /**
     * The load the header is allowed to talk about — the first one after the screen opens,
     * or one the user asked for. The background poll never sets this.
     */
    val announcedLoadAt = mutableLongStateOf(0L)

    /**
     * When the list last came back from the server. With no network a request does not
     * fail — it hangs — so an outage is measured by the absence of a success, not by an
     * error arriving.
     */
    val lastSuccessAt = mutableLongStateOf(0L)

    /** True once the current outage has had its say; reset by the next success. */
    val outageAnnounced = mutableStateOf(false)

    /** Sender display names for group previews, keyed by the last message sender id. */
    val senderNames = mutableStateOf<Map<String, String>>(emptyMap())

    /** Last Oria reply and its time, so her row reads like a normal chat row. */
    val oriaPreview = mutableStateOf("")
    val oriaTime = mutableStateOf("")

    fun upsert(conversation: CloudConversation) {
        conversations.value = listOf(conversation.copy(unreadCount = 0)) +
            conversations.value.filterNot { it.id == conversation.id }
    }
}

internal sealed interface XDestination {
    data object Root : XDestination
    data class Conversation(val conversation: CloudConversation) : XDestination
    data object Oria : XDestination
    data object Profile : XDestination
    data object Scanner : XDestination
    data object Music : XDestination
    data object HorizonSettings : XDestination
    data object Calculator : XDestination
    data object Notes : XDestination
    data object Timer : XDestination
    data object Weather : XDestination
    data object Files : XDestination
    data object Translator : XDestination
    data class SettingsPanel(val category: SettingsCategory) : XDestination
    data object Storage : XDestination

    /** Canvas screen «создание»; `kind` is «группа» or «канал». */
    data class CreateCommunity(val kind: String) : XDestination

    // Groups and channels. Each one carries the conversation it belongs to so the
    // screen can be rebuilt from the destination alone after a config change.
    data class CommunityInfo(val conversation: CloudConversation) : XDestination
    data class CommunitySettings(val conversation: CloudConversation) : XDestination
    data class CommunityAdmins(val conversation: CloudConversation) : XDestination
    data class CommunityAddMembers(val conversation: CloudConversation) : XDestination
}

private sealed interface XPendingCallAction {
    val video: Boolean

    data class Outgoing(
        val conversationId: String,
        val peerName: String,
        val peerAvatarUrl: String,
        override val video: Boolean,
    ) : XPendingCallAction

    data class Incoming(
        val invite: net.orionx.messenger.calls.IncomingCall,
    ) : XPendingCallAction {
        override val video: Boolean get() = invite.type == CallType.VIDEO
    }
}

internal enum class SettingsCategory(val titleKey: String, val subtitleKey: String, val icon: ImageVector) {
    ACCOUNT("account", "account_sub", Icons.Outlined.Person),
    APPEARANCE("appearance", "appearance_sub", Icons.Outlined.Palette),
    NOTIFICATIONS("notifications", "notifications_sub", Icons.Outlined.Notifications),
    PRIVACY("privacy", "privacy_sub", Icons.Outlined.Security),
    DATA("data_storage", "data_storage_sub", Icons.Outlined.Storage),
    DEVICES("devices", "devices_sub", Icons.Outlined.Devices),
    UPDATES("updates_settings", "updates_settings_sub", Icons.Outlined.SystemUpdateAlt),
    HELP("help_about", "help_about_sub", Icons.Outlined.HelpOutline),
    ADMIN("administration", "administration_sub", Icons.Outlined.AdminPanelSettings),
}

private val X_TEXT_EN = mapOf(
        "account" to "Account", "account_sub" to "Profile, email, phone and username",
        "appearance" to "Appearance", "appearance_sub" to "Theme, colors, text size and animations",
        "notifications" to "Notifications", "notifications_sub" to "Messages, calls, channels and sound",
        "privacy" to "Privacy", "privacy_sub" to "Visibility, calls, blocks and 2FA",
        "data_storage" to "Data and storage", "data_storage_sub" to "Auto-download, cache and network usage",
        "devices" to "Devices", "devices_sub" to "Active sessions and account sign-ins",
        "updates_settings" to "update", "updates_settings_sub" to "New OrionX versions and change history",
        "help_about" to "Help and about", "help_about_sub" to "Support, rules and OrionX version",
        "administration" to "Administration", "administration_sub" to "Users, announcements and updates",
        "dark_theme" to "Dark theme", "dark_theme_sub" to "Switch between dark and light appearance",
        "compact_mode" to "Compact mode", "compact_mode_sub" to "Reduces cards, bars and spacing",
        "reduce_motion" to "Reduce animations", "reduce_motion_sub" to "Disables transitions and unnecessary motion",
        "text_size" to "Text size", "text_size_sub" to "Five interface scale levels",
        "interface_language" to "Interface language", "close" to "Close",
        "personal_messages" to "Direct messages", "personal_messages_sub" to "Notifications for new messages",
        "groups" to "Groups", "groups_sub" to "Messages and mentions",
        "channels_setting" to "Channels", "channels_setting_sub" to "New posts",
        "calls" to "Calls", "calls_sub" to "Incoming audio and video calls",
        "notification_preview" to "Notification preview", "notification_preview_sub" to "Show message text in notifications",
        "notification_permission" to "System permission", "notification_allowed" to "Notifications are allowed", "notification_blocked" to "Notifications are blocked by Android",
        "allow_notifications" to "Allow notifications", "test_notification" to "Test notification", "test_notification_sub" to "Show a local test notification",
        "push_token" to "Push connection", "push_token_sub" to "Sync this device with Firebase",
        "archive" to "Archive", "hidden_chats" to "Hidden chats", "messages_and_oria" to "Your messages and Oria assistant",
        "find_users" to "Find users", "nothing_found" to "Nothing found", "change_query" to "Try another query",
        "oria_card_sub" to "Assistant for search, text and ideas", "archive_action" to "Archive", "unarchive_action" to "Restore", "archive_empty" to "Archive is empty", "found" to "found",
        "scanner_title" to "Scanner", "torch" to "Torch", "scanner_hint" to "Point at a profile or channel QR code", "scanner_no_camera" to "Camera access is needed to scan", "profile_found" to "profile found", "open_action" to "Open", "qr_not_orionx" to "Not an OrionX code", "qr_unreadable" to "Could not read the code", "scan_again" to "Again", "my_qr" to "My QR code", "my_qr_hint" to "Show the code — one tap to be added", "from_gallery" to "From gallery", "share_action" to "Share", "close_action" to "Close",
        "app_music" to "Music", "music_nothing" to "Nothing playing", "music_pick_hint" to "Pick a track below", "music_empty" to "No music on this device", "music_no_permission" to "Allow access to audio to see your library", "like_action" to "Like", "shuffle_action" to "Shuffle", "prev_action" to "Previous", "next_action" to "Next", "play_action" to "Play", "repeat_action" to "Repeat",
        "app_calc" to "Calculator", "app_note" to "Notes", "app_weather" to "Weather", "app_timer" to "Timer", "app_files" to "Files", "app_translator" to "Translator", "horizon_settings" to "Horizon settings", "horizon_enabled" to "Panel on", "horizon_side" to "Side", "side_left" to "Left", "side_right" to "Right", "horizon_open_how" to "How to open", "mode_ask" to "Ask", "mode_full" to "Screen", "mode_window" to "Window", "mode_ask_hint" to "Horizon will ask: full screen or window", "mode_full_hint" to "Apps open full screen right away", "mode_window_hint" to "Apps open in a floating window right away", "horizon_in_panel" to "In panel", "horizon_system_apps" to "System apps", "remove_action" to "Remove", "add_action" to "Add", "horizon_panel_empty" to "The panel is empty — add an app below", "horizon_panel_full" to "The panel holds five apps — remove one to add another", "horizon_all_added" to "Every system app is already in the panel", "window_wip_title" to "Window is in progress", "window_wip_body" to "Only app mode is available for now", "ok_action" to "OK", "open_full" to "Screen", "open_window" to "Window",
        "calc_backspace" to "Backspace", "stopwatch" to "Stopwatch", "lap" to "Lap", "reset_action" to "Reset", "start_action" to "Start", "pause_action" to "Pause", "resume_action" to "Resume", "apply_action" to "Set", "custom_time" to "Custom time", "timer_running" to "Counting down", "timer_pick" to "Pick a duration", "min_1" to "1 min", "min_3" to "3 min", "min_5" to "5 min", "min_10" to "10 min", "min_25" to "25 min", "lap_saved" to "Lap saved to notes", "note_title_prompt" to "Note title", "note_title_example" to "for example: set 3", "cancel_action" to "Cancel", "save_action" to "Save", "notes_empty" to "No notes yet", "notes_empty_hint" to "Tap plus to write your first thought", "note_untitled" to "untitled", "note_empty" to "empty note", "note_new" to "New note", "all_notes" to "All notes", "note_title_hint" to "Title", "note_body_hint" to "Write something…", "pin_action" to "Pin", "delete_action" to "Delete",
        "weather_failed" to "Could not load the forecast", "wind" to "Wind", "humidity" to "Humidity", "pressure" to "Pressure", "swap_langs" to "Swap languages", "translate_hint" to "enter text…", "translate_result_hint" to "the translation appears here", "translating" to "translating…", "translate_failed" to "could not translate", "clear_action" to "clear", "copy_action" to "Copy", "copied" to "Copied", "saved_to_notes" to "Saved to notes", "phrase_hello" to "hello", "phrase_thanks" to "thank you", "phrase_where" to "where is it", "files_no_folder" to "No folder chosen", "files_pick" to "Choose a folder", "files_pick_hint" to "Pick a folder and OrionX will show what is inside", "files_search" to "search in folder", "sort_name" to "By name", "sort_date" to "By date", "sort_size" to "By size", "files_empty" to "Nothing here", "folder" to "folder", "of_total" to "of", "send_action" to "Send", "file_deleted" to "File deleted", "file_delete_failed" to "Could not delete the file",
        "groups_channels" to "Groups and channels", "messenger_main" to "Main OrionX news", "news_empty_sub" to "We will tell you about important news and updates",
        "settings_subtitle" to "Account and OrionX features",
        // Screen transitions — the canvas «анимация» block.
        "anim_title" to "Screen transitions",
        "anim_slide" to "slide", "anim_dissolve" to "dissolve", "anim_depth" to "depth",
        "anim_speed" to "Speed", "anim_kind" to "Transition type",
        "settings_title" to "Settings", "settings_search_hint" to "Find a setting",
        "notifications_row" to "notifications", "privacy_row" to "privacy", "language_row" to "language",
        "profile_row" to "profile", "skin_row" to "chat appearance", "privacy_screenshots" to "screenshots in chats",
        "stg_used" to "used", "stg_media" to "photos and video", "stg_voice" to "voice",
        "stg_stickers" to "stickers and emoji", "stg_other" to "other",
        "stg_cache" to "cache", "stg_clear" to "clear", "stg_emptied" to "emptied",
        "stg_auto" to "delete media automatically", "stg_auto_never" to "never",
        "stg_auto_week" to "after a week", "stg_auto_month" to "after a month",
        "stg_cleared_note" to "cache cleared",
        "skin_lilac" to "lilac", "skin_graphite" to "graphite", "skin_dawn" to "dawn",
        "optimize_row" to "device optimization", "optimize_run" to "run",
        "optimize_done" to "freed {size}",
        "devices_row" to "devices", "devices_empty" to "Only this device is signed in",
        "device_revoke" to "revoke", "data_row" to "data and storage", "media_row" to "media auto-download",
        "media_wifi" to "on wi-fi", "media_always" to "always", "media_never" to "never",
        "blocked_row" to "blocked", "blocked_search" to "find someone blocked",
        "blocked_empty" to "Nobody is blocked", "blocked_unblock" to "unblock",
        "help_row" to "help", "about_row" to "about", "about_e2ee" to "end-to-end encryption is on for every chat",
        "update_row" to "update", "admin_upload_row" to "publish an update", "admin_badge" to "admin",
        "username_row" to "Username", "phone_row" to "Phone number", "logout_row" to "Sign out",
        "theme_row" to "Theme", "anim_row" to "Animation", "sound_row" to "Sound and vibration",
        "privacy_last_seen" to "Last seen", "privacy_read_receipts" to "Read receipts",
        "halo_height" to "Height", "halo_width" to "Width", "halo_offset" to "Position",
        "halo_island" to "island", "halo_notch" to "notch / drop", "halo_hole" to "hole", "halo_off" to "off",
        "anim_speed_slow" to "slow", "anim_speed_normal" to "normal",
        "anim_speed_fast" to "fast", "anim_speed_instant" to "instant",
    )
private val X_TEXT_RU = mapOf(
        "account" to "Аккаунт", "account_sub" to "Профиль, почта, телефон и имя пользователя",
        "appearance" to "Оформление", "appearance_sub" to "Тема, цвет, размер текста и анимации",
        "notifications" to "Уведомления", "notifications_sub" to "Сообщения, звонки, каналы и звук",
        "privacy" to "Конфиденциальность", "privacy_sub" to "Видимость, звонки, блокировки и 2FA",
        "data_storage" to "Данные и память", "data_storage_sub" to "Автозагрузка, кэш и использование сети",
        "devices" to "Устройства", "devices_sub" to "Активные сеансы и входы в аккаунт",
        "updates_settings" to "обновление", "updates_settings_sub" to "Новые версии OrionX и история изменений",
        "help_about" to "Помощь и о приложении", "help_about_sub" to "Поддержка, правила и версия OrionX",
        "administration" to "Администрирование", "administration_sub" to "Пользователи, объявления и обновления",
        "dark_theme" to "Тёмная тема", "dark_theme_sub" to "Переключение между тёмным и светлым оформлением",
        "compact_mode" to "Компактный режим", "compact_mode_sub" to "Уменьшает карточки, панели и интервалы",
        "reduce_motion" to "Уменьшение анимаций", "reduce_motion_sub" to "Отключает переходы и лишнее движение",
        "text_size" to "Размер текста", "text_size_sub" to "Пять уровней масштаба интерфейса",
        "interface_language" to "Язык интерфейса", "close" to "Закрыть",
        "personal_messages" to "Личные сообщения", "personal_messages_sub" to "Уведомления о новых сообщениях",
        "groups" to "Группы", "groups_sub" to "Сообщения и упоминания",
        "channels_setting" to "Каналы", "channels_setting_sub" to "Новые публикации",
        "calls" to "Звонки", "calls_sub" to "Входящие аудио- и видеозвонки",
        "notification_preview" to "Текст в уведомлении", "notification_preview_sub" to "Показывать предварительный просмотр сообщения",
        "notification_permission" to "Системное разрешение", "notification_allowed" to "Уведомления разрешены", "notification_blocked" to "Уведомления заблокированы Android",
        "allow_notifications" to "Разрешить уведомления", "test_notification" to "Тестовое уведомление", "test_notification_sub" to "Показать локальное тестовое уведомление",
        "push_token" to "Подключение Push", "push_token_sub" to "Синхронизировать это устройство с Firebase",
        "archive" to "Архив", "hidden_chats" to "Скрытые диалоги", "messages_and_oria" to "Ваши сообщения и помощник Oria",
        "find_users" to "Найдите пользователей", "nothing_found" to "Ничего не найдено", "change_query" to "Попробуйте изменить запрос",
        "oria_card_sub" to "Помощник для поиска, текста и идей", "archive_action" to "В архив", "unarchive_action" to "Вернуть", "archive_empty" to "Архив пуст", "found" to "найдено",
        "scanner_title" to "Сканер", "torch" to "Фонарик", "scanner_hint" to "Наведи на qr-код профиля или канала", "scanner_no_camera" to "Для сканирования нужен доступ к камере", "profile_found" to "профиль найден", "open_action" to "Открыть", "qr_not_orionx" to "Это не код OrionX", "qr_unreadable" to "Не удалось прочитать код", "scan_again" to "Ещё раз", "my_qr" to "Мой qr-код", "my_qr_hint" to "Покажи код — тебя добавят в один тап", "from_gallery" to "Из галереи", "share_action" to "Поделиться", "close_action" to "Закрыть",
        "app_music" to "Музыка", "music_nothing" to "Ничего не играет", "music_pick_hint" to "Выберите трек ниже", "music_empty" to "На устройстве нет музыки", "music_no_permission" to "Разрешите доступ к аудио, чтобы увидеть фонотеку", "like_action" to "Нравится", "shuffle_action" to "Перемешать", "prev_action" to "Предыдущий", "next_action" to "Следующий", "play_action" to "Воспроизвести", "repeat_action" to "Повтор",
        "app_calc" to "Калькулятор", "app_note" to "Блокнот", "app_weather" to "Погода", "app_timer" to "Таймер", "app_files" to "Файлы", "app_translator" to "Переводчик", "horizon_settings" to "Настройки horizon", "horizon_enabled" to "Панель включена", "horizon_side" to "Сторона", "side_left" to "Слева", "side_right" to "Справа", "horizon_open_how" to "Как открывать", "mode_ask" to "Спросить", "mode_full" to "Экран", "mode_window" to "Окно", "mode_ask_hint" to "При запуске приложения horizon спросит: полный экран или окно", "mode_full_hint" to "Приложения сразу открываются на полный экран, без вопроса", "mode_window_hint" to "Приложения сразу открываются в плавающем окне, без вопроса", "horizon_in_panel" to "В панели", "horizon_system_apps" to "Системные приложения", "remove_action" to "Убрать", "add_action" to "Добавить", "horizon_panel_empty" to "В панели пусто — добавьте приложение ниже", "horizon_panel_full" to "В панели максимум 5 приложений — уберите одно, чтобы добавить новое", "horizon_all_added" to "Все системные приложения уже в панели", "window_wip_title" to "Окно в разработке", "window_wip_body" to "Пока доступен только режим приложения", "ok_action" to "Хорошо", "open_full" to "Экран", "open_window" to "Окно",
        "calc_backspace" to "Стереть", "stopwatch" to "Секундомер", "lap" to "Круг", "reset_action" to "Сброс", "start_action" to "Старт", "pause_action" to "Пауза", "resume_action" to "Продолжить", "apply_action" to "Задать", "custom_time" to "Своё время", "timer_running" to "Идёт отсчёт", "timer_pick" to "Выберите длительность", "min_1" to "1 мин", "min_3" to "3 мин", "min_5" to "5 мин", "min_10" to "10 мин", "min_25" to "25 мин", "lap_saved" to "Круг сохранён в блокнот", "note_title_prompt" to "Название заметки", "note_title_example" to "например: подход 3", "cancel_action" to "Отмена", "save_action" to "Сохранить", "notes_empty" to "Заметок пока нет", "notes_empty_hint" to "Нажмите плюс, чтобы записать первую мысль", "note_untitled" to "без названия", "note_empty" to "пустая заметка", "note_new" to "Новая заметка", "all_notes" to "Все заметки", "note_title_hint" to "Название", "note_body_hint" to "Напишите что-нибудь…", "pin_action" to "Закрепить", "delete_action" to "Удалить",
        "weather_failed" to "Не удалось загрузить прогноз", "wind" to "Ветер", "humidity" to "Влажность", "pressure" to "Давление", "swap_langs" to "Поменять языки", "translate_hint" to "введите текст…", "translate_result_hint" to "здесь появится перевод", "translating" to "переводим…", "translate_failed" to "не удалось перевести", "clear_action" to "очистить", "copy_action" to "Копировать", "copied" to "Скопировано", "saved_to_notes" to "Сохранено в блокнот", "phrase_hello" to "здравствуйте", "phrase_thanks" to "спасибо", "phrase_where" to "где это находится", "files_no_folder" to "Папка не выбрана", "files_pick" to "Выбрать папку", "files_pick_hint" to "Выберите папку, и OrionX покажет, что внутри", "files_search" to "поиск в папке", "sort_name" to "По имени", "sort_date" to "По дате", "sort_size" to "По размеру", "files_empty" to "Здесь пусто", "folder" to "папка", "of_total" to "из", "send_action" to "Отправить", "file_deleted" to "Файл удалён", "file_delete_failed" to "Не удалось удалить файл",
        "groups_channels" to "Группы и каналы", "messenger_main" to "Главное о мессенджере", "news_empty_sub" to "Мы сообщим вам о важных новостях и обновлениях",
        "settings_subtitle" to "Аккаунт и возможности OrionX",
        // Переходы между экранами — блок «анимация» из макета.
        "anim_title" to "Переходы между экранами",
        "anim_slide" to "слайд", "anim_dissolve" to "растворение", "anim_depth" to "глубина",
        "anim_speed" to "Скорость", "anim_kind" to "Тип перехода",
        "settings_title" to "настройки", "settings_search_hint" to "искать настройку",
        "notifications_row" to "уведомления", "privacy_row" to "приватность", "language_row" to "язык",
        "profile_row" to "профиль", "skin_row" to "оформление чата", "privacy_screenshots" to "скриншоты в чатах",
        "stg_used" to "занято", "stg_media" to "фото и видео", "stg_voice" to "голосовые",
        "stg_stickers" to "стикеры и эмодзи", "stg_other" to "другое",
        "stg_cache" to "кэш", "stg_clear" to "очистить", "stg_emptied" to "очищено",
        "stg_auto" to "удалять медиа автоматически", "stg_auto_never" to "никогда",
        "stg_auto_week" to "через неделю", "stg_auto_month" to "через месяц",
        "stg_cleared_note" to "кэш очищен",
        "skin_lilac" to "лиловый", "skin_graphite" to "графит", "skin_dawn" to "рассвет",
        "optimize_row" to "оптимизация устройства", "optimize_run" to "запустить",
        "optimize_done" to "ускорено · {size}",
        "devices_row" to "устройства", "devices_empty" to "вошли только с этого устройства",
        "device_revoke" to "завершить", "data_row" to "данные и память", "media_row" to "автозагрузка медиа",
        "media_wifi" to "по wi-fi", "media_always" to "всегда", "media_never" to "никогда",
        "blocked_row" to "заблокированные", "blocked_search" to "найти заблокированного",
        "blocked_empty" to "заблокированных нет", "blocked_unblock" to "разблокировать",
        "help_row" to "помощь", "about_row" to "о приложении", "about_e2ee" to "сквозное шифрование включено для всех чатов",
        "update_row" to "обновление", "admin_upload_row" to "добавить обновление", "admin_badge" to "админ",
        "username_row" to "имя пользователя", "phone_row" to "номер телефона", "logout_row" to "выйти из аккаунта",
        "theme_row" to "тема", "anim_row" to "анимация", "sound_row" to "звук и вибрация",
        "privacy_last_seen" to "последний вход", "privacy_read_receipts" to "отчёты о прочтении",
        "halo_height" to "высота", "halo_width" to "ширина", "halo_offset" to "позиция",
        "halo_island" to "островок", "halo_notch" to "капля / чёлка", "halo_hole" to "отверстие", "halo_off" to "выкл",
        "anim_speed_slow" to "медленно", "anim_speed_normal" to "обычно",
        "anim_speed_fast" to "быстро", "anim_speed_instant" to "мгновенно",
    )
private val X_TEXT_UK = mapOf(
        "account" to "Акаунт", "account_sub" to "Профіль, пошта, телефон та ім’я користувача",
        "appearance" to "Оформлення", "appearance_sub" to "Тема, колір, розмір тексту та анімації",
        "notifications" to "Сповіщення", "notifications_sub" to "Повідомлення, дзвінки, канали та звук",
        "privacy" to "Конфіденційність", "privacy_sub" to "Видимість, дзвінки, блокування та 2FA",
        "data_storage" to "Дані та пам’ять", "data_storage_sub" to "Автозавантаження, кеш і використання мережі",
        "devices" to "Пристрої", "devices_sub" to "Активні сеанси та входи в акаунт",
        "updates_settings" to "Оновлення", "updates_settings_sub" to "Нові версії OrionX та історія змін",
        "help_about" to "Допомога та про застосунок", "help_about_sub" to "Підтримка, правила та версія OrionX",
        "administration" to "Адміністрування", "administration_sub" to "Користувачі, оголошення та оновлення",
        "dark_theme" to "Темна тема", "dark_theme_sub" to "Перемикання між темним і світлим оформленням",
        "compact_mode" to "Компактний режим", "compact_mode_sub" to "Зменшує картки, панелі та відступи",
        "reduce_motion" to "Зменшити анімації", "reduce_motion_sub" to "Вимикає переходи та зайвий рух",
        "text_size" to "Розмір тексту", "text_size_sub" to "П’ять рівнів масштабу інтерфейсу",
        "interface_language" to "Мова інтерфейсу", "close" to "Закрити",
        "personal_messages" to "Особисті повідомлення", "personal_messages_sub" to "Сповіщення про нові повідомлення",
        "groups" to "Групи", "groups_sub" to "Повідомлення та згадки",
        "channels_setting" to "Канали", "channels_setting_sub" to "Нові публікації",
        "calls" to "Дзвінки", "calls_sub" to "Вхідні аудіо- та відеодзвінки",
        "notification_preview" to "Текст у сповіщенні", "notification_preview_sub" to "Показувати попередній перегляд повідомлення",
        "notification_permission" to "Системний дозвіл", "notification_allowed" to "Сповіщення дозволено", "notification_blocked" to "Сповіщення заблоковано Android",
        "allow_notifications" to "Дозволити сповіщення", "test_notification" to "Тестове сповіщення", "test_notification_sub" to "Показати локальне тестове сповіщення",
        "push_token" to "Push-підключення", "push_token_sub" to "Синхронізувати цей пристрій з Firebase",
        "archive" to "Архів", "hidden_chats" to "Приховані діалоги", "messages_and_oria" to "Ваші повідомлення та помічник Oria",
        "find_users" to "Знайдіть користувачів", "nothing_found" to "Нічого не знайдено", "change_query" to "Спробуйте змінити запит",
        "oria_card_sub" to "Помічник для пошуку, тексту та ідей", "archive_action" to "В архів", "unarchive_action" to "Повернути", "archive_empty" to "Архів порожній", "found" to "знайдено",
        "scanner_title" to "Сканер", "torch" to "Ліхтарик", "scanner_hint" to "Наведи на qr-код профілю або каналу", "scanner_no_camera" to "Для сканування потрібен доступ до камери", "profile_found" to "профіль знайдено", "open_action" to "Відкрити", "qr_not_orionx" to "Це не код OrionX", "qr_unreadable" to "Не вдалося прочитати код", "scan_again" to "Ще раз", "my_qr" to "Мій qr-код", "my_qr_hint" to "Покажи код — тебе додадуть в один дотик", "from_gallery" to "З галереї", "share_action" to "Поділитися", "close_action" to "Закрити",
        "app_music" to "Музика", "music_nothing" to "Нічого не грає", "music_pick_hint" to "Оберіть трек нижче", "music_empty" to "На пристрої немає музики", "music_no_permission" to "Дозвольте доступ до аудіо, щоб побачити фонотеку", "like_action" to "Подобається", "shuffle_action" to "Перемішати", "prev_action" to "Попередній", "next_action" to "Наступний", "play_action" to "Відтворити", "repeat_action" to "Повтор",
        "app_calc" to "Калькулятор", "app_note" to "Блокнот", "app_weather" to "Погода", "app_timer" to "Таймер", "app_files" to "Файли", "app_translator" to "Перекладач", "horizon_settings" to "Налаштування horizon", "horizon_enabled" to "Панель увімкнена", "horizon_side" to "Сторона", "side_left" to "Ліворуч", "side_right" to "Праворуч", "horizon_open_how" to "Як відкривати", "mode_ask" to "Запитати", "mode_full" to "Екран", "mode_window" to "Вікно", "mode_ask_hint" to "Під час запуску horizon запитає: повний екран чи вікно", "mode_full_hint" to "Застосунки одразу відкриваються на повний екран", "mode_window_hint" to "Застосунки одразу відкриваються у плаваючому вікні", "horizon_in_panel" to "У панелі", "horizon_system_apps" to "Системні застосунки", "remove_action" to "Прибрати", "add_action" to "Додати", "horizon_panel_empty" to "Панель порожня — додайте застосунок нижче", "horizon_panel_full" to "У панелі максимум 5 застосунків", "horizon_all_added" to "Усі системні застосунки вже в панелі", "window_wip_title" to "Вікно в розробці", "window_wip_body" to "Поки доступний лише режим застосунку", "ok_action" to "Добре", "open_full" to "Екран", "open_window" to "Вікно",
        "calc_backspace" to "Стерти", "stopwatch" to "Секундомір", "lap" to "Коло", "reset_action" to "Скинути", "start_action" to "Старт", "pause_action" to "Пауза", "resume_action" to "Продовжити", "apply_action" to "Задати", "custom_time" to "Свій час", "timer_running" to "Триває відлік", "timer_pick" to "Оберіть тривалість", "min_1" to "1 хв", "min_3" to "3 хв", "min_5" to "5 хв", "min_10" to "10 хв", "min_25" to "25 хв", "lap_saved" to "Коло збережено в блокнот", "note_title_prompt" to "Назва нотатки", "note_title_example" to "наприклад: підхід 3", "cancel_action" to "Скасувати", "save_action" to "Зберегти", "notes_empty" to "Нотаток поки немає", "notes_empty_hint" to "Натисніть плюс, щоб записати першу думку", "note_untitled" to "без назви", "note_empty" to "порожня нотатка", "note_new" to "Нова нотатка", "all_notes" to "Усі нотатки", "note_title_hint" to "Назва", "note_body_hint" to "Напишіть щось…", "pin_action" to "Закріпити", "delete_action" to "Видалити",
        "weather_failed" to "Не вдалося завантажити прогноз", "wind" to "Вітер", "humidity" to "Вологість", "pressure" to "Тиск", "swap_langs" to "Поміняти мови", "translate_hint" to "введіть текст…", "translate_result_hint" to "тут з’явиться переклад", "translating" to "перекладаємо…", "translate_failed" to "не вдалося перекласти", "clear_action" to "очистити", "copy_action" to "Копіювати", "copied" to "Скопійовано", "saved_to_notes" to "Збережено в блокнот", "phrase_hello" to "вітаю", "phrase_thanks" to "дякую", "phrase_where" to "де це знаходиться", "files_no_folder" to "Папку не обрано", "files_pick" to "Обрати папку", "files_pick_hint" to "Оберіть папку, і OrionX покаже, що всередині", "files_search" to "пошук у папці", "sort_name" to "За назвою", "sort_date" to "За датою", "sort_size" to "За розміром", "files_empty" to "Тут порожньо", "folder" to "папка", "of_total" to "з", "send_action" to "Надіслати", "file_deleted" to "Файл видалено", "file_delete_failed" to "Не вдалося видалити файл",
        "groups_channels" to "Групи та канали", "messenger_main" to "Головне про месенджер", "news_empty_sub" to "Ми повідомимо про важливі новини та оновлення",
        "settings_subtitle" to "Акаунт і можливості OrionX",
    )
private val X_TEXT_BE = X_TEXT_RU + mapOf(
        "account" to "Акаўнт", "account_sub" to "Профіль, пошта, тэлефон і імя карыстальніка",
        "appearance" to "Афармленне", "appearance_sub" to "Тэма, колер, памер тэксту і анімацыі",
        "notifications" to "Апавяшчэнні", "notifications_sub" to "Паведамленні, званкі, каналы і гук",
        "privacy" to "Канфідэнцыяльнасць", "privacy_sub" to "Бачнасць, званкі, блакіроўкі і 2FA",
        "data_storage" to "Даныя і памяць", "data_storage_sub" to "Аўтазагрузка, кэш і выкарыстанне сеткі",
        "devices" to "Прылады", "devices_sub" to "Актыўныя сеансы і ўваходы ў акаўнт",
        "updates_settings" to "Абнаўленні", "updates_settings_sub" to "Новыя версіі OrionX і гісторыя змен",
        "help_about" to "Дапамога і пра дадатак", "help_about_sub" to "Падтрымка, правілы і версія OrionX",
        "administration" to "Адміністраванне", "administration_sub" to "Карыстальнікі, аб’явы і абнаўленні",
        "dark_theme" to "Цёмная тэма", "dark_theme_sub" to "Пераключэнне паміж цёмным і светлым афармленнем",
        "compact_mode" to "Кампактны рэжым", "compact_mode_sub" to "Памяншае карткі, панэлі і водступы",
        "reduce_motion" to "Паменшыць анімацыі", "reduce_motion_sub" to "Адключае пераходы і лішні рух",
        "text_size" to "Памер тэксту", "text_size_sub" to "Пяць узроўняў маштабу інтэрфейсу",
        "interface_language" to "Мова інтэрфейсу", "close" to "Закрыць",
        "personal_messages" to "Асабістыя паведамленні", "personal_messages_sub" to "Апавяшчэнні пра новыя паведамленні",
        "groups" to "Групы", "groups_sub" to "Паведамленні і згадкі",
        "channels_setting" to "Каналы", "channels_setting_sub" to "Новыя публікацыі",
        "calls" to "Званкі", "calls_sub" to "Уваходныя аўдыя- і відэазванкі",
        "notification_preview" to "Тэкст у апавяшчэнні", "notification_preview_sub" to "Паказваць папярэдні прагляд паведамлення",
        "notification_permission" to "Сістэмны дазвол", "notification_allowed" to "Апавяшчэнні дазволены", "notification_blocked" to "Апавяшчэнні заблакіраваны Android",
        "allow_notifications" to "Дазволіць апавяшчэнні", "test_notification" to "Тэставае апавяшчэнне", "test_notification_sub" to "Паказаць лакальнае тэставае апавяшчэнне",
        "push_token" to "Push-падключэнне", "push_token_sub" to "Сінхранізаваць гэту прыладу з Firebase",
        "archive" to "Архіў", "hidden_chats" to "Схаваныя дыялогі", "messages_and_oria" to "Вашы паведамленні і памочнік Oria",
        "find_users" to "Знайдзіце карыстальнікаў", "nothing_found" to "Нічога не знойдзена", "change_query" to "Паспрабуйце змяніць запыт",
        "oria_card_sub" to "Памочнік для пошуку, тэксту і ідэй", "archive_action" to "У архіў", "unarchive_action" to "Вярнуць", "archive_empty" to "Архіў пусты", "found" to "знойдзена",
        "scanner_title" to "Сканер", "torch" to "Ліхтарык", "scanner_hint" to "Навядзі на qr-код профілю або канала", "scanner_no_camera" to "Для сканавання патрэбны доступ да камеры", "profile_found" to "профіль знойдзены", "open_action" to "Адкрыць", "qr_not_orionx" to "Гэта не код OrionX", "qr_unreadable" to "Не ўдалося прачытаць код", "scan_again" to "Яшчэ раз", "my_qr" to "Мой qr-код", "my_qr_hint" to "Пакажы код — цябе дададуць у адзін дотык", "from_gallery" to "З галерэі", "share_action" to "Падзяліцца", "close_action" to "Закрыць",
        "app_music" to "Музыка", "music_nothing" to "Нічога не гучыць", "music_pick_hint" to "Абярыце трэк ніжэй", "music_empty" to "На прыладзе няма музыкі", "music_no_permission" to "Дазвольце доступ да аўдыя, каб убачыць фанатэку", "like_action" to "Падабаецца", "shuffle_action" to "Перамяшаць", "prev_action" to "Папярэдні", "next_action" to "Наступны", "play_action" to "Прайграць", "repeat_action" to "Паўтор",
        "app_calc" to "Калькулятар", "app_note" to "Нататнік", "app_weather" to "Надвор’е", "app_timer" to "Таймер", "app_files" to "Файлы", "app_translator" to "Перакладчык", "horizon_settings" to "Налады horizon", "horizon_enabled" to "Панэль уключана", "horizon_side" to "Бок", "side_left" to "Злева", "side_right" to "Справа", "horizon_open_how" to "Як адкрываць", "mode_ask" to "Спытаць", "mode_full" to "Экран", "mode_window" to "Акно", "mode_ask_hint" to "Пры запуску horizon спытае: поўны экран ці акно", "mode_full_hint" to "Праграмы адразу адкрываюцца на ўвесь экран", "mode_window_hint" to "Праграмы адразу адкрываюцца ў плаваючым акне", "horizon_in_panel" to "У панэлі", "horizon_system_apps" to "Сістэмныя праграмы", "remove_action" to "Прыбраць", "add_action" to "Дадаць", "horizon_panel_empty" to "У панэлі пуста — дадайце праграму ніжэй", "horizon_panel_full" to "У панэлі максімум 5 праграм", "horizon_all_added" to "Усе сістэмныя праграмы ўжо ў панэлі", "window_wip_title" to "Акно ў распрацоўцы", "window_wip_body" to "Пакуль даступны толькі рэжым праграмы", "ok_action" to "Добра", "open_full" to "Экран", "open_window" to "Акно",
        "calc_backspace" to "Сцерці", "stopwatch" to "Секундамер", "lap" to "Кола", "reset_action" to "Скід", "start_action" to "Старт", "pause_action" to "Паўза", "resume_action" to "Працягнуць", "apply_action" to "Задаць", "custom_time" to "Свой час", "timer_running" to "Ідзе адлік", "timer_pick" to "Абярыце працягласць", "min_1" to "1 хв", "min_3" to "3 хв", "min_5" to "5 хв", "min_10" to "10 хв", "min_25" to "25 хв", "lap_saved" to "Кола захавана ў нататнік", "note_title_prompt" to "Назва нататкі", "note_title_example" to "напрыклад: падыход 3", "cancel_action" to "Скасаваць", "save_action" to "Захаваць", "notes_empty" to "Нататак пакуль няма", "notes_empty_hint" to "Націсніце плюс, каб запісаць першую думку", "note_untitled" to "без назвы", "note_empty" to "пустая нататка", "note_new" to "Новая нататка", "all_notes" to "Усе нататкі", "note_title_hint" to "Назва", "note_body_hint" to "Напішыце што-небудзь…", "pin_action" to "Замацаваць", "delete_action" to "Выдаліць",
        "weather_failed" to "Не ўдалося загрузіць прагноз", "wind" to "Вецер", "humidity" to "Вільготнасць", "pressure" to "Ціск", "swap_langs" to "Памяняць мовы", "translate_hint" to "увядзіце тэкст…", "translate_result_hint" to "тут з’явіцца пераклад", "translating" to "перакладаем…", "translate_failed" to "не ўдалося перакласці", "clear_action" to "ачысціць", "copy_action" to "Капіраваць", "copied" to "Скапіявана", "saved_to_notes" to "Захавана ў нататнік", "phrase_hello" to "вітаю", "phrase_thanks" to "дзякуй", "phrase_where" to "дзе гэта знаходзіцца", "files_no_folder" to "Папка не выбрана", "files_pick" to "Выбраць папку", "files_pick_hint" to "Выберыце папку, і OrionX пакажа, што ўнутры", "files_search" to "пошук у папцы", "sort_name" to "Па назве", "sort_date" to "Па даце", "sort_size" to "Па памеры", "files_empty" to "Тут пуста", "folder" to "папка", "of_total" to "з", "send_action" to "Адправіць", "file_deleted" to "Файл выдалены", "file_delete_failed" to "Не ўдалося выдаліць файл",
        "groups_channels" to "Групы і каналы", "messenger_main" to "Галоўнае пра месенджар", "news_empty_sub" to "Мы паведамім пра важныя навіны і абнаўленні",
        "settings_subtitle" to "Акаўнт і магчымасці OrionX",
    )
private val X_TEXT_KK = X_TEXT_RU + mapOf(
        "account" to "Аккаунт", "account_sub" to "Профиль, пошта, телефон және пайдаланушы аты",
        "appearance" to "Безендіру", "appearance_sub" to "Тақырып, түс, мәтін өлшемі және анимациялар",
        "notifications" to "Хабарландырулар", "notifications_sub" to "Хабарламалар, қоңыраулар, арналар және дыбыс",
        "privacy" to "Құпиялық", "privacy_sub" to "Көріну, қоңыраулар, бұғаттау және 2FA",
        "data_storage" to "Деректер мен жад", "data_storage_sub" to "Автожүктеу, кэш және желіні пайдалану",
        "devices" to "Құрылғылар", "devices_sub" to "Белсенді сеанстар мен аккаунтқа кірулер",
        "updates_settings" to "Жаңартулар", "updates_settings_sub" to "OrionX жаңа нұсқалары және өзгерістер тарихы",
        "help_about" to "Көмек және қолданба туралы", "help_about_sub" to "Қолдау, ережелер және OrionX нұсқасы",
        "administration" to "Әкімшілік", "administration_sub" to "Пайдаланушылар, хабарландырулар және жаңартулар",
        "dark_theme" to "Қараңғы тақырып", "dark_theme_sub" to "Қараңғы және ашық безендіру арасында ауысу",
        "compact_mode" to "Ықшам режим", "compact_mode_sub" to "Карталарды, панельдер мен шеттерді кішірейтеді",
        "reduce_motion" to "Анимацияны азайту", "reduce_motion_sub" to "Ауысулар мен артық қозғалысты өшіреді",
        "text_size" to "Мәтін өлшемі", "text_size_sub" to "Интерфейс масштабының бес деңгейі",
        "interface_language" to "Интерфейс тілі", "close" to "Жабу",
        "personal_messages" to "Жеке хабарламалар", "personal_messages_sub" to "Жаңа хабарламалар туралы хабарландыру",
        "groups" to "Топтар", "groups_sub" to "Хабарламалар мен ескертулер",
        "channels_setting" to "Арналар", "channels_setting_sub" to "Жаңа жарияланымдар",
        "calls" to "Қоңыраулар", "calls_sub" to "Кіріс аудио және бейне қоңыраулар",
        "notification_preview" to "Хабарландырудағы мәтін", "notification_preview_sub" to "Хабарламаның алдын ала қарауын көрсету",
        "notification_permission" to "Жүйелік рұқсат", "notification_allowed" to "Хабарландырулар рұқсат етілген", "notification_blocked" to "Хабарландыруларды Android бұғаттаған",
        "allow_notifications" to "Хабарландыруларға рұқсат беру", "test_notification" to "Сынақ хабарландыруы", "test_notification_sub" to "Жергілікті сынақ хабарландыруын көрсету",
        "push_token" to "Push қосылымы", "push_token_sub" to "Осы құрылғыны Firebase-пен синхрондау",
        "archive" to "Мұрағат", "hidden_chats" to "Жасырын чаттар", "messages_and_oria" to "Хабарламаларыңыз және Oria көмекшісі",
        "find_users" to "Пайдаланушыларды табыңыз", "nothing_found" to "Ештеңе табылмады", "change_query" to "Сұранысты өзгертіп көріңіз",
        "oria_card_sub" to "Іздеу, мәтін және идеялар көмекшісі", "archive_action" to "Мұрағатқа", "unarchive_action" to "Қайтару", "archive_empty" to "Мұрағат бос", "found" to "табылды",
        "scanner_title" to "Сканер", "torch" to "Шам", "scanner_hint" to "Профиль немесе арна qr-кодына бағыттаңыз", "scanner_no_camera" to "Сканерлеу үшін камераға рұқсат керек", "profile_found" to "профиль табылды", "open_action" to "Ашу", "qr_not_orionx" to "Бұл OrionX коды емес", "qr_unreadable" to "Кодты оқу мүмкін болмады", "scan_again" to "Тағы", "my_qr" to "Менің qr-кодым", "my_qr_hint" to "Кодты көрсетіңіз — бір түртумен қосады", "from_gallery" to "Галереядан", "share_action" to "Бөлісу", "close_action" to "Жабу",
        "app_music" to "Музыка", "music_nothing" to "Ешнәрсе ойналмайды", "music_pick_hint" to "Төменнен трек таңдаңыз", "music_empty" to "Құрылғыда музыка жоқ", "music_no_permission" to "Фонотеканы көру үшін аудиоға рұқсат беріңіз", "like_action" to "Ұнайды", "shuffle_action" to "Араластыру", "prev_action" to "Алдыңғы", "next_action" to "Келесі", "play_action" to "Ойнату", "repeat_action" to "Қайталау",
        "app_calc" to "Калькулятор", "app_note" to "Блокнот", "app_weather" to "Ауа райы", "app_timer" to "Таймер", "app_files" to "Файлдар", "app_translator" to "Аудармашы", "horizon_settings" to "Horizon баптаулары", "horizon_enabled" to "Панель қосулы", "horizon_side" to "Жағы", "side_left" to "Сол жақта", "side_right" to "Оң жақта", "horizon_open_how" to "Қалай ашу", "mode_ask" to "Сұрау", "mode_full" to "Экран", "mode_window" to "Терезе", "mode_ask_hint" to "Іске қосқанда horizon сұрайды: толық экран ба, терезе ме", "mode_full_hint" to "Қолданбалар бірден толық экранда ашылады", "mode_window_hint" to "Қолданбалар бірден қалқымалы терезеде ашылады", "horizon_in_panel" to "Панельде", "horizon_system_apps" to "Жүйелік қолданбалар", "remove_action" to "Алып тастау", "add_action" to "Қосу", "horizon_panel_empty" to "Панель бос — төменнен қолданба қосыңыз", "horizon_panel_full" to "Панельде ең көбі 5 қолданба", "horizon_all_added" to "Барлық жүйелік қолданбалар панельде", "window_wip_title" to "Терезе әзірленуде", "window_wip_body" to "Әзірге тек қолданба режимі қолжетімді", "ok_action" to "Жақсы", "open_full" to "Экран", "open_window" to "Терезе",
        "calc_backspace" to "Өшіру", "stopwatch" to "Секундомер", "lap" to "Айналым", "reset_action" to "Ысыру", "start_action" to "Бастау", "pause_action" to "Кідірту", "resume_action" to "Жалғастыру", "apply_action" to "Орнату", "custom_time" to "Өз уақытым", "timer_running" to "Кері санақ жүруде", "timer_pick" to "Ұзақтығын таңдаңыз", "min_1" to "1 мин", "min_3" to "3 мин", "min_5" to "5 мин", "min_10" to "10 мин", "min_25" to "25 мин", "lap_saved" to "Айналым блокнотқа сақталды", "note_title_prompt" to "Жазба атауы", "note_title_example" to "мысалы: 3-жаттығу", "cancel_action" to "Болдырмау", "save_action" to "Сақтау", "notes_empty" to "Әзірге жазба жоқ", "notes_empty_hint" to "Алғашқы ойды жазу үшін плюсті басыңыз", "note_untitled" to "атауы жоқ", "note_empty" to "бос жазба", "note_new" to "Жаңа жазба", "all_notes" to "Барлық жазбалар", "note_title_hint" to "Атауы", "note_body_hint" to "Бірдеңе жазыңыз…", "pin_action" to "Бекіту", "delete_action" to "Жою",
        "weather_failed" to "Болжамды жүктеу мүмкін болмады", "wind" to "Жел", "humidity" to "Ылғалдылық", "pressure" to "Қысым", "swap_langs" to "Тілдерді ауыстыру", "translate_hint" to "мәтін енгізіңіз…", "translate_result_hint" to "аударма осында шығады", "translating" to "аударылуда…", "translate_failed" to "аудару мүмкін болмады", "clear_action" to "тазалау", "copy_action" to "Көшіру", "copied" to "Көшірілді", "saved_to_notes" to "Блокнотқа сақталды", "phrase_hello" to "сәлеметсіз бе", "phrase_thanks" to "рақмет", "phrase_where" to "бұл қайда орналасқан", "files_no_folder" to "Қалта таңдалмаған", "files_pick" to "Қалта таңдау", "files_pick_hint" to "Қалта таңдаңыз, OrionX ішіндегісін көрсетеді", "files_search" to "қалтадан іздеу", "sort_name" to "Атауы бойынша", "sort_date" to "Күні бойынша", "sort_size" to "Өлшемі бойынша", "files_empty" to "Мұнда бос", "folder" to "қалта", "of_total" to "барлығы", "send_action" to "Жіберу", "file_deleted" to "Файл жойылды", "file_delete_failed" to "Файлды жою мүмкін болмады",
        "groups_channels" to "Топтар мен арналар", "messenger_main" to "Мессенджер туралы басты ақпарат", "news_empty_sub" to "Маңызды жаңалықтар мен жаңартулар туралы хабарлаймыз",
        "settings_subtitle" to "Аккаунт және OrionX мүмкіндіктері",
    )

@Composable
internal fun xSettingText(key: String): String {
    val language = LocalOrionLanguage.current
    val table = when (language) {
        OrionLanguage.ENGLISH -> X_TEXT_EN
        OrionLanguage.UKRAINIAN -> X_TEXT_UK
        OrionLanguage.BELARUSIAN -> X_TEXT_BE
        OrionLanguage.KAZAKH -> X_TEXT_KK
        OrionLanguage.RUSSIAN -> X_TEXT_RU
    }
    return table[key] ?: X_TEXT_RU[key] ?: key
}

@Composable
internal fun OrionRedesignedApp(
    api: SupabaseApi,
    session: CloudSession,
    initialProfile: CloudProfile,
    onLogout: () -> Unit,
) {
    var profile by remember(session.userId) { mutableStateOf(initialProfile) }
    var tab by remember(session.userId) { mutableStateOf(XTab.CHATS) }
    var destination by remember(session.userId) { mutableStateOf<XDestination>(XDestination.Root) }
    // Community screens nest — feed → info → settings → admins — so navigation keeps
    // a stack. Everything else still pushes one level and pops straight back to root.
    var backStack by remember(session.userId) { mutableStateOf<List<XDestination>>(emptyList()) }
    val navigateTo: (XDestination) -> Unit = { next ->
        backStack = backStack + destination
        destination = next
    }
    val navigateBack: () -> Unit = {
        destination = backStack.lastOrNull() ?: XDestination.Root
        backStack = backStack.dropLast(1)
    }
    /** Drops the whole stack — used after leaving or deleting a community. */
    val navigateRoot: () -> Unit = {
        backStack = emptyList()
        destination = XDestination.Root
    }
    // «пост» из профиля канала: возвращаемся к ленте и сразу открываем редактор.
    var pendingPostComposer by remember { mutableStateOf(false) }
    val chatsTabState = remember(session.userId) { XChatsTabState() }
    val searchTabState = remember(session.userId) { XSearchTabState() }
    val chatsListState = rememberLazyListState()
    val context = LocalContext.current
    var runtimeSettings by remember(context) { mutableStateOf(loadLocalUiSettings(context)) }
    val updateRuntimeSettings: (CloudUserSettings) -> Unit = { next ->
        runtimeSettings = next
        persistLocalUiSettings(context, next)
    }
    val activity = context as? MainActivity
    val foreground = activity?.isAppForeground ?: true
    // Разрешение на уведомления спрашиваем сразу при первом запуске: на Android 13+
    // без него система молча глушит все пуши — сообщения и входящие звонки не приходят.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current

    val callScope = rememberCoroutineScope()
    var activeCallController by remember(session.userId) { mutableStateOf<OrionCallController?>(null) }
    var incomingCall by remember(session.userId) { mutableStateOf<net.orionx.messenger.calls.IncomingCall?>(null) }
    var pendingCallAction by remember(session.userId) { mutableStateOf<XPendingCallAction?>(null) }
    var callError by remember(session.userId) { mutableStateOf<String?>(null) }
    var notifiedIncomingId by remember(session.userId) { mutableStateOf<String?>(null) }

    fun callPermissionsGranted(video: Boolean): Boolean {
        val mic = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camera = !video || context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        return mic && camera
    }

    fun launchCall(action: XPendingCallAction) {
        if (activeCallController != null) return
        val controller = OrionCallController(context, api, session)
        activeCallController = controller
        incomingCall = null
        callError = null
        callScope.launch {
            when (action) {
                is XPendingCallAction.Outgoing -> controller.startOutgoing(
                    conversationId = action.conversationId,
                    peerName = action.peerName,
                    peerAvatarUrl = action.peerAvatarUrl,
                    video = action.video,
                )
                is XPendingCallAction.Incoming -> controller.acceptIncoming(action.invite)
            }
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val action = pendingCallAction
        pendingCallAction = null
        if (action == null) return@rememberLauncherForActivityResult
        val mic = grants[Manifest.permission.RECORD_AUDIO] == true || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camera = !action.video || grants[Manifest.permission.CAMERA] == true || context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (mic && camera) launchCall(action)
        else callError = if (action.video) "Для видеозвонка нужен доступ к камере и микрофону" else "Для звонка нужен доступ к микрофону"
    }

    fun requestCall(action: XPendingCallAction) {
        if (activeCallController != null) return
        if (callPermissionsGranted(action.video)) {
            launchCall(action)
        } else {
            pendingCallAction = action
            callPermissionLauncher.launch(
                if (action.video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                else arrayOf(Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    LaunchedEffect(Unit) { OrionNotifications.ensureChannels(context) }

    // Тап по push-уведомлению «входящий звонок»: показываем диалог сразу,
    // не дожидаясь следующего опроса (1.2–2.5 с).
    var callIdFromIntent by remember { mutableStateOf(activity?.intent?.getStringExtra("call_id")) }
    DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                callIdFromIntent = activity?.intent?.getStringExtra("call_id")
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(callIdFromIntent) {
        val callId = callIdFromIntent ?: return@LaunchedEffect
        if (incomingCall == null && activeCallController == null) {
            val invite = runCatching { withContext(Dispatchers.IO) { api.pendingIncomingCall(session.accessToken, session.userId) } }.getOrNull()
            if (invite != null && invite.id == callId) incomingCall = invite
        }
        callIdFromIntent = null
    }

    LaunchedEffect(session.userId, session.accessToken, activeCallController, foreground) {
        while (activeCallController == null) {
            val current = incomingCall
            if (current != null) {
                val state = runCatching { withContext(Dispatchers.IO) { api.callState(session.accessToken, current.id) } }.getOrNull()
                if (state != null && state != CallState.RINGING) incomingCall = null
            } else {
                val invite = runCatching { withContext(Dispatchers.IO) { api.pendingIncomingCall(session.accessToken, session.userId) } }.getOrNull()
                if (invite != null) {
                    incomingCall = invite
                    if (!foreground && notifiedIncomingId != invite.id) {
                        notifiedIncomingId = invite.id
                        OrionNotifications.show(
                            context = context,
                            title = if (invite.type == CallType.VIDEO) "Входящий видеозвонок OrionX" else "Входящий звонок OrionX",
                            body = invite.callerName,
                            conversationId = invite.conversationId,
                            notificationId = invite.id.hashCode(),
                        )
                    }
                }
            }
            delay(if (foreground) 1_200 else 2_500)
        }
    }

    // Local settings are applied instantly. Cloud settings are used for synchronization between devices,
    // but never block or delay the visible UI switch.
    LaunchedEffect(session.userId, session.accessToken) {
        while (true) {
            runCatching { withContext(Dispatchers.IO) { api.userSettings(session.accessToken, session.userId) } }
                .onSuccess { remote ->
                    val merged = remote.copy(
                        theme = if (remote.theme == "system") runtimeSettings.theme else remote.theme,
                        fontScale = remote.fontScale.coerceIn(.9f, 1.3f),
                    )
                    runtimeSettings = merged
                    persistLocalUiSettings(context, merged)
                }
            delay(60_000)
        }
    }

    LaunchedEffect(session.userId, session.accessToken) {
        runCatching {
            withContext(Dispatchers.IO) {
                api.ensureOriaThread(session.accessToken, session.userId)
            }
        }
    }

    LaunchedEffect(session.userId, session.accessToken, foreground) {
        if (!foreground) {
            runCatching { withContext(Dispatchers.IO) { api.updatePresence(session.accessToken, false) } }
            return@LaunchedEffect
        }
        var heartbeat = 0
        while (true) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val runtimeSettings = runCatching { api.userSettings(session.accessToken, session.userId) }.getOrNull()
                    api.updatePresence(session.accessToken, runtimeSettings?.showOnline != false)
                    api.markAllDelivered(session.accessToken)
                    if (heartbeat % 2 == 0) {
                        var deviceAllowed = api.touchCurrentDevice(
                            token = session.accessToken,
                            userId = session.userId,
                            installationId = DeviceIdentity.id(context),
                            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                            appVersion = BuildConfig.VERSION_NAME,
                        )
                        if (!deviceAllowed) {
                            // Сервер считает установку отозванной (выход из аккаунта отзывает её).
                            // Раз у нас есть живая сессия — перевыпускаем идентификатор и
                            // привязываем устройство заново; выходим, только если сервер снова отказал.
                            val reinstalled = DeviceIdentity.reset(context)
                            deviceAllowed = api.touchCurrentDevice(
                                token = session.accessToken,
                                userId = session.userId,
                                installationId = reinstalled,
                                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                                appVersion = BuildConfig.VERSION_NAME,
                            )
                        }
                        if (!deviceAllowed) {
                            withContext(Dispatchers.Main) { onLogout() }
                            return@withContext
                        }
                    }
                }
            }
            heartbeat++
            delay(30_000)
        }
    }

    DisposableEffect(session.userId, session.accessToken) {
        onDispose {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching { api.updatePresence(session.accessToken, false) }
            }
        }
    }

    BackHandlerCompat(enabled = destination !is XDestination.Root) { navigateBack() }

    val musicController = remember(activity) { net.orionx.messenger.music.MusicController(context.applicationContext) }
    val horizonState = remember(context) { XHorizonState(context) }
    val notesStore = remember(context, session.userId) { XNotesStore(context, session.userId) }
    val motionSettings = remember(context) { XMotionSettings(context) }
    // The canvas asks «экран или окно» before launching a mini-app unless a mode is pinned.
    var horizonPick by remember { mutableStateOf<XHorizonApp?>(null) }
    var horizonWindowToast by remember { mutableStateOf(false) }
    DisposableEffect(musicController) {
        // Новая голосовая глушит музыку — как в Telegram.
        XVoicePlayback.onVoiceStarted = { musicController.pause() }
        onDispose { musicController.release() }
    }

    // Three modes, like the canvas: светлая / тёмная / система. «система» was being read
    // as dark, so picking it did nothing at all.
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (runtimeSettings.theme) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    // The canvas transitions every palette variable over `.62s cubic-bezier(.4,0,.2,1)`
    // instead of swapping them, so the theme is driven by an animated float.
    val darkness by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = if (runtimeSettings.reduceMotion) tween(0)
        else tween(620, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)),
        label = "theme-darkness",
    )
    // The read has to happen here, in composition, or nothing subscribes to the animation
    // and it advances with no one watching — the setting flips and the screen stays put.
    // The write happens after the frame, because snapshot state cannot be written during
    // composition; one frame of lag inside a 620 ms fade is not visible.
    val paletteDarkness = darkness
    SideEffect { applyXPalette(paletteDarkness) }
    // `themeSweep` — a diagonal band of light crossing the screen once per switch.
    var sweepFrom by remember { mutableStateOf(darkTheme) }
    var sweepKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(darkTheme) {
        if (sweepFrom != darkTheme) {
            sweepFrom = darkTheme
            sweepKey++
        }
    }
    SideEffect {
        activity?.window?.let { window ->
            // Transparent bars: the screen gradient runs underneath them.
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            // Without this the platform paints its own grey scrim behind the transparent
            // navigation bar — a band across the bottom of every light-theme screen.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            // Follow the fade rather than the target: flipping the icons on the first
            // frame leaves them inverted against the old background for half a second.
            val barsOnLight = paletteDarkness < .5f
            controller.isAppearanceLightStatusBars = barsOnLight
            controller.isAppearanceLightNavigationBars = barsOnLight
        }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density * if (runtimeSettings.compactMode) .92f else 1f, runtimeSettings.fontScale.coerceIn(.90f, 1.30f)),
        LocalXCompact provides runtimeSettings.compactMode,
        LocalXRuntimeSettings provides runtimeSettings,
        LocalXRuntimeSettingsUpdater provides updateRuntimeSettings,
    ) {
    XCosmicBackground {
        XUpdateChecker(api, session)
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                // Direction and keyframes come from the canvas — see OrionMotion.kt.
                xScreenTransition(
                    mode = motionSettings.mode,
                    speed = motionSettings.speed,
                    direction = xNavDirection(initialState, targetState),
                    reduceMotion = runtimeSettings.reduceMotion,
                    density = baseDensity,
                )
            },
            label = "orion-destination",
        ) { target ->
            when (target) {
                XDestination.Root -> XRootShell(
                    api = api,
                    session = session,
                    profile = profile,
                    tab = tab,
                    onTab = { tab = it },
                    chatsState = chatsTabState,
                    searchState = searchTabState,
                    chatsListState = chatsListState,
                    onOpenConversation = { navigateTo(XDestination.Conversation(it)) },
                    onOpenOria = { navigateTo(XDestination.Oria) },
                    onOpenProfile = { navigateTo(XDestination.Profile) },
                    onOpenScanner = { destination = XDestination.Scanner },
                    onCreate = { kind -> navigateTo(XDestination.CreateCommunity(kind)) },
                    onCategory = { navigateTo(XDestination.SettingsPanel(it)) },
                    music = musicController,
                    motion = motionSettings,
                    onOpenHorizon = { navigateTo(XDestination.HorizonSettings) },
                    onOpenStorage = { navigateTo(XDestination.Storage) },
                    onLogout = onLogout,
                )
                is XDestination.Conversation -> XConversationScreen(
                    api = api,
                    session = session,
                    profile = profile,
                    conversation = target.conversation,
                    onBack = navigateBack,
                    onStartCall = { conversationId, peerName, peerAvatarUrl, video ->
                        requestCall(XPendingCallAction.Outgoing(conversationId, peerName, peerAvatarUrl, video))
                    },
                    onOpenCommunity = { navigateTo(XDestination.CommunityInfo(target.conversation)) },
                    onOpenCommunitySettings = { navigateTo(XDestination.CommunitySettings(target.conversation)) },
                    onOpenCommunityAddMembers = { navigateTo(XDestination.CommunityAddMembers(target.conversation)) },
                    openPostComposer = pendingPostComposer,
                    onPostComposerShown = { pendingPostComposer = false },
                )

                // Groups and channels. Each destination owns its state: AnimatedContent
                // gives every target its own remember scope, so the screen being
                // animated out keeps rendering its own data. The cost is that moving
                // between these screens re-reads the roster, which also keeps roles and
                // pins current after they were just edited.
                is XDestination.CommunityInfo -> {
                    val state = rememberCommunityState(api, session, target.conversation)
                    XCommunityInfoScreen(
                        state = state,
                        onBack = navigateBack,
                        onSettings = { navigateTo(XDestination.CommunitySettings(target.conversation)) },
                        onAddMembers = { navigateTo(XDestination.CommunityAddMembers(target.conversation)) },
                        onAdmins = { navigateTo(XDestination.CommunityAdmins(target.conversation)) },
                        onLeft = navigateRoot,
                        onNewPost = { pendingPostComposer = true; navigateBack() },
                    )
                }
                is XDestination.CommunitySettings -> {
                    val state = rememberCommunityState(api, session, target.conversation)
                    XCommunitySettingsScreen(
                        state = state,
                        onBack = navigateBack,
                        onAdmins = { navigateTo(XDestination.CommunityAdmins(target.conversation)) },
                        onDeleted = navigateRoot,
                    )
                }
                is XDestination.CommunityAdmins -> {
                    val state = rememberCommunityState(api, session, target.conversation)
                    XCommunityAdminsScreen(state = state, onBack = navigateBack)
                }
                is XDestination.CommunityAddMembers -> {
                    val state = rememberCommunityState(api, session, target.conversation)
                    XAddMembersScreen(
                        api = api,
                        session = session,
                        state = state,
                        onBack = navigateBack,
                        onAdded = navigateBack,
                    )
                }
                XDestination.Scanner -> XQrScannerScreen(
                    api = api,
                    session = session,
                    profile = profile,
                    onBack = { destination = XDestination.Root },
                    onOpenPeer = { peer ->
                        callScope.launch {
                            runCatching { withContext(Dispatchers.IO) { api.createDirect(session.accessToken, peer.id) } }
                                .onSuccess { destination = XDestination.Conversation(it) }
                                .onFailure { callError = localizedNetworkError(it) }
                        }
                    },
                )
                XDestination.Storage -> XStorageScreen(api = api, session = session, onBack = navigateBack)
                is XDestination.CreateCommunity -> XCreateCommunityScreen(
                    api = api,
                    session = session,
                    initialKind = target.kind,
                    onBack = navigateBack,
                    onCreated = { created ->
                        // Replace the form on the stack so «назад» from the new chat lands
                        // back on the list, not on a filled-in create screen.
                        navigateBack()
                        navigateTo(XDestination.Conversation(created))
                    },
                )
                XDestination.HorizonSettings -> XHorizonSettingsScreen(
                    state = horizonState,
                    onBack = { destination = XDestination.Root },
                )
                XDestination.Calculator -> XCalculatorScreen(onBack = { destination = XDestination.Root })
                XDestination.Weather -> XWeatherScreen(onBack = { destination = XDestination.Root })
                XDestination.Files -> XFilesScreen(onBack = { destination = XDestination.Root })
                XDestination.Translator -> XTranslatorScreen(notes = notesStore, onBack = { destination = XDestination.Root })
                XDestination.Notes -> XNotesScreen(store = notesStore, onBack = { destination = XDestination.Root })
                XDestination.Timer -> XTimerScreen(notes = notesStore, onBack = { destination = XDestination.Root })
                XDestination.Music -> XMusicScreen(
                    controller = musicController,
                    onBack = { destination = XDestination.Root },
                )
                XDestination.Oria -> XOriaScreen(
                    api = api,
                    session = session,
                    profile = profile,
                    onBack = navigateBack,
                )
                // `showNav` in the canvas lists the screens *without* the dock, and
                // `profile` is not among them — so the dock stays here, with its own
                // slot lit. Oria is on that list, which is why it has no dock.
                XDestination.Profile -> XProfileCanvasScreen(
                    api = api,
                    session = session,
                    profile = profile,
                    onProfileChanged = { profile = it },
                    onBack = navigateBack,
                    onOpenSettings = { tab = XTab.SETTINGS; navigateRoot() },
                    onOpenQr = { navigateTo(XDestination.Scanner) },
                )
                is XDestination.SettingsPanel -> XSettingsCategoryScreen(
                    api = api,
                    session = session,
                    profile = profile,
                    category = target.category,
                    onProfileChanged = { profile = it },
                    onBack = navigateBack,
                    onOpenProfile = { navigateTo(XDestination.Profile) },
                    onLogout = onLogout,
                    motion = motionSettings,
                )
                else -> Unit
            }
        }
        // The canvas draws the dock as a sibling of the screens, outside `--scrAnim`:
        // it never travels with a transition, only its pill slides. Keeping a single
        // instance here also means the pill animates between chats, search and profile
        // instead of being rebuilt — which is what made it jump.
        val dockItem = when (destination) {
            XDestination.Root -> when (tab) {
                XTab.CHATS -> XDockItem.CHATS
                XTab.SEARCH -> XDockItem.SEARCH
                XTab.SETTINGS -> null
            }
            XDestination.Profile -> XDockItem.PROFILE
            else -> null
        }
        if (dockItem != null) {
            XBottomDock(
                selected = dockItem,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
                onSelect = { item ->
                    when (item) {
                        XDockItem.CHATS -> { tab = XTab.CHATS; navigateRoot() }
                        XDockItem.SEARCH -> { tab = XTab.SEARCH; navigateRoot() }
                        XDockItem.PROFILE -> if (destination != XDestination.Profile) navigateTo(XDestination.Profile)
                    }
                },
            )
        }
        // `thsweepA / thsweepB .72s cubic-bezier(.4,0,.2,1)` over the whole screen.
        if (sweepKey > 0 && !runtimeSettings.reduceMotion) {
            XThemeSweep(key = sweepKey, toDark = darkTheme)
        }
        XHalo(
            music = musicController,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        XHorizon(
            state = horizonState,
            onOpenApp = { app ->
                when (horizonState.openMode) {
                    XHorizonOpenMode.FULL -> destination = xHorizonDestination(app)
                    XHorizonOpenMode.WINDOW -> horizonWindowToast = true
                    XHorizonOpenMode.ASK -> horizonPick = app
                }
            },
            onOpenSettings = { destination = XDestination.HorizonSettings },
        )
    }

    horizonPick?.let { app ->
        XHorizonOpenDialog(
            app = app,
            onFull = { horizonPick = null; destination = xHorizonDestination(app) },
            onWindow = { horizonPick = null; horizonWindowToast = true },
            onDismiss = { horizonPick = null },
        )
    }

    if (horizonWindowToast) {
        XInfoDialog(
            xSettingText("window_wip_title"),
            xSettingText("window_wip_body"),
        ) { horizonWindowToast = false }
    }

    if (activeCallController == null) {
        incomingCall?.let { invite ->
            OrionIncomingCallDialog(
                invite = invite,
                onAccept = { requestCall(XPendingCallAction.Incoming(invite)) },
                onDecline = {
                    callScope.launch {
                        runCatching { withContext(Dispatchers.IO) { api.declineDirectCall(session.accessToken, invite.id) } }
                            .onFailure { callError = localizedNetworkError(it) }
                        incomingCall = null
                    }
                },
            )
        }
    }

    activeCallController?.let { controller ->
        OrionCallScreen(controller = controller) {
            controller.dispose()
            activeCallController = null
        }
    }

    callError?.let { message ->
        XInfoDialog("Звонки OrionX", message) { callError = null }
    }
    }
}

@Composable
private fun XRootShell(
    api: SupabaseApi,
    session: CloudSession,
    profile: CloudProfile,
    tab: XTab,
    onTab: (XTab) -> Unit,
    chatsState: XChatsTabState,
    searchState: XSearchTabState,
    chatsListState: LazyListState,
    onOpenConversation: (CloudConversation) -> Unit,
    onOpenOria: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenScanner: () -> Unit,
    onCreate: (String) -> Unit,
    onCategory: (SettingsCategory) -> Unit,
    music: net.orionx.messenger.music.MusicController,
    motion: XMotionSettings,
    onOpenHorizon: () -> Unit,
    onOpenStorage: () -> Unit,
    onLogout: () -> Unit,
) {
    val reduceMotion = LocalXRuntimeSettings.current.reduceMotion
    val cacheAndOpen: (CloudConversation) -> Unit = { conversation ->
        chatsState.upsert(conversation)
        onOpenConversation(conversation)
    }
    // The dock is not part of the shell: in the canvas it is a sibling of the screens,
    // so it must sit outside every animated container — see OrionRedesignedApp.
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        // Tabs are screens too — same keyframes and direction rule.
                        xScreenTransition(
                            mode = motion.mode,
                            speed = motion.speed,
                            direction = xTabDirection(initialState, targetState),
                            reduceMotion = reduceMotion,
                            density = density,
                        )
                    },
                    label = "orion-tabs",
                ) { page ->
                    when (page) {
                        // The header belongs to the screen, not to the shell — in the
                        // canvas it sits inside the animated container and travels with it.
                        XTab.CHATS -> Column(Modifier.fillMaxSize()) {
                            XHeader(
                                profile = profile,
                                status = chatsState.syncStatus.value,
                                onProfile = onOpenProfile,
                                onOria = onOpenOria,
                            )
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                XChatsTab(
                                    api = api,
                                    session = session,
                                    state = chatsState,
                                    listState = chatsListState,
                                    onOpen = onOpenConversation,
                                    onOria = onOpenOria,
                                    onCreate = onCreate,
                                    onSearch = { onTab(XTab.SEARCH) },
                                    onSettings = { onTab(XTab.SETTINGS) },
                                    onScanner = onOpenScanner,
                                    music = music,
                                )
                            }
                        }
                        XTab.SEARCH -> XSearchTab(api, session, searchState, cacheAndOpen, onBack = { onTab(XTab.CHATS) })
                        // The canvas has no category list — settings is one flat screen.
                        XTab.SETTINGS -> XSettingsScreen(
                            api = api,
                            session = session,
                            profile = profile,
                            motion = motion,
                            onBack = { onTab(XTab.CHATS) },
                            onOpenProfile = onOpenProfile,
                            onOpenHorizon = onOpenHorizon,
                            onOpenStorage = onOpenStorage,
                            onCategory = onCategory,
                            onLogout = onLogout,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chats header from the canvas: the profile orb on the left, the wordmark centred, and Oria
 * on the right. Nothing else — the actions moved down into the screen itself.
 */
@Composable
private fun XHeader(
    profile: CloudProfile,
    status: XSyncStatus,
    onProfile: () -> Unit,
    onOria: () -> Unit,
) {
    val compact = LocalXCompact.current
    val orb = if (compact) 42.dp else 46.dp
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(orb).clip(CircleShape).background(XProfileOrbBrush).clickable(onClick = onProfile),
            contentAlignment = Alignment.Center,
        ) {
            if (profile.avatarUrl.startsWith("orionx-gradient:")) {
                XGradientAvatar(
                    profile.avatarUrl.removePrefix("orionx-gradient:").toIntOrNull() ?: 0,
                    profile.displayName.ifBlank { profile.nickname },
                    orb,
                    CircleShape,
                )
            } else if (profile.avatarUrl.isNotBlank()) {
                RemoteImage(profile.avatarUrl, Modifier.fillMaxSize().clip(CircleShape), ContentScale.Crop)
            }
        }
        // The wordmark doubles as the status line: it says what is happening instead of
        // handing the screen a spinner the canvas never had.
        Crossfade(
            targetState = status,
            animationSpec = tween(260),
            label = "wordmark-status",
            modifier = Modifier.weight(1f),
        ) { state ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (state) {
                        XSyncStatus.SYNCING -> tr("header_syncing")
                        XSyncStatus.OFFLINE -> tr("header_offline")
                        XSyncStatus.IDLE -> "OrionX"
                    },
                    style = XType.wordmark,
                    color = if (state == XSyncStatus.OFFLINE) XMuted else XText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                if (state == XSyncStatus.SYNCING) {
                    Spacer(Modifier.width(7.dp))
                    XTypingDots()
                }
            }
        }
        XCircleButton(icon = Icons.Outlined.AutoAwesome, contentDescription = tr("oria"), size = orb, onClick = onOria)
    }
}

/**
 * The canvas' three bouncing dots —
 * `typd 1.05s ease-in-out {0, .16s, .32s} infinite` over
 * `0%,80%,100% { translateY(0); opacity:.35 }  40% { translateY(-3px); opacity:1 }`.
 */
@Composable
private fun XTypingDots(color: Color = XText, dot: Dp = 5.dp) {
    val cycle = rememberInfiniteTransition(label = "typd")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(0, 160, 320).forEach { delayMillis ->
            val phase by cycle.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1050
                        0f at 0
                        1f at 420 using androidx.compose.animation.core.FastOutSlowInEasing
                        0f at 840 using androidx.compose.animation.core.FastOutSlowInEasing
                        0f at 1050
                    },
                    initialStartOffset = androidx.compose.animation.core.StartOffset(delayMillis),
                ),
                label = "typd-$delayMillis",
            )
            Box(
                Modifier
                    .size(dot)
                    .offset(y = (-3).dp * phase)
                    .clip(CircleShape)
                    .background(color.copy(alpha = .35f + .65f * phase)),
            )
        }
    }
}

/** `width:46px;height:46px;border-radius:50%;background:var(--sf);box-shadow:0 12px 30px var(--shc)` */
@Composable
internal fun XCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    size: Dp = 46.dp,
    iconSize: Dp = 22.dp,
    background: Color = XSurface,
    tint: Color = XText,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(background)
            .xPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** [XCircleButton] for the drawable-backed icons (call, video call, attach). */
@Composable
private fun XCirclePainterButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?,
    size: Dp = 46.dp,
    iconSize: Dp = 21.dp,
    background: Color = XSurface,
    tint: Color = XText,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(background)
            .xPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * A back arrow plus a title — the header every non-root screen in the canvas uses:
 * `width:42px;height:42px;border-radius:50%` then `800 19px`.
 */
@Composable
internal fun XScreenHeader(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
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
        Text(title, style = XType.screenTitle, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** `padding:9px 13px;border-radius:999px` — the uppercase filter chips. */
@Composable
internal fun XChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(if (selected) XText else XSurface, tween(220), label = "chip-bg")
    val foreground by animateColorAsState(if (selected) XInverse else XMuted, tween(220), label = "chip-fg")
    Box(
        modifier
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(background)
            .xPressable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(label.uppercase(), style = XType.sectionLabel.copy(letterSpacing = .13.em), color = foreground, maxLines = 1)
    }
}

/** The dark "create"-style pill: `background:var(--txt);color:var(--inv)`. */
@Composable
internal fun XInversePill(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(XText)
            .xPressable(pressedScale = .96f, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), style = XType.pillSmall, color = XInverse, maxLines = 1)
    }
}

/** The light action pill: `background:var(--sf)`, used for «написать» and «люди». */
@Composable
internal fun XSurfacePill(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    background: Color = XSurface,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(height)
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(background)
            .xPressable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label.uppercase(), style = XType.pill, color = XText, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * `width:46px;height:28px;border-radius:999px` with a 22px knob — the canvas switch. Material's
 * own Switch has a different thumb size and a border, so this draws it directly.
 */
@Composable
internal fun XSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (checked) XText else XSurfaceRaised, tween(300), label = "switch-track")
    val knob by animateColorAsState(if (checked) XInverse else XMuted, tween(300), label = "switch-knob")
    val offset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = tween(340, easing = CubicBezierEasing(.34f, 1.5f, .64f, 1f)),
        label = "switch-knob-x",
    )
    Box(
        Modifier
            .size(46.dp, 28.dp)
            .clip(CircleShape)
            .background(track)
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = offset).size(22.dp).clip(CircleShape).background(knob))
    }
}

/** `background:var(--sf);border-radius:28px;box-shadow:0 12px 30px var(--shc)` — the standard card. */
@Composable
internal fun XCard(
    modifier: Modifier = Modifier,
    shape: Shape = XShape.panel,
    background: Color = XSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().xShadow(shape).clip(shape).background(background),
        content = content,
    )
}

/** `font:800 11px;letter-spacing:.16em;text-transform:uppercase;color:var(--mut)` */
@Composable
internal fun XCardLabel(text: String, modifier: Modifier = Modifier, color: Color = XMuted) {
    Text(text.uppercase(), style = XType.sectionLabel, color = color, modifier = modifier)
}

/** The chats glyph from the canvas — a speech bubble with a flat bottom-left corner. */
private val XChatsGlyph: ImageVector by lazy {
    val d = "M21.0039 12C21.0039 16.9706 16.9745 21 12.0039 21C9.9675 21 3.00463 21 3.00463 21" +
        "C3.00463 21 4.56382 17.2561 3.93982 16.0008C3.34076 14.7956 3.00391 13.4372 3.00391 12" +
        "C3.00391 7.02944 7.03334 3 12.0039 3C16.9745 3 21.0039 7.02944 21.0039 12Z"
    ImageVector.Builder(name = "XChats", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

private enum class XDockItem { CHATS, SEARCH, PROFILE }

/**
 * The floating dock: `background:var(--txt);border-radius:999px;padding:7px` with a sliding
 * `var(--inv)` pill at 12% opacity behind the active slot. Three slots — chats, search, profile —
 * and it is not drawn on full screens (settings, a conversation, the auth flow).
 */
@Composable
private fun XBottomDock(
    selected: XDockItem?,
    modifier: Modifier = Modifier,
    onSelect: (XDockItem) -> Unit,
) {
    val slotWidth = 54.dp
    val slotHeight = 46.dp
    val items = listOf(
        Triple(XDockItem.CHATS, XChatsGlyph, tr("chats")),
        Triple(XDockItem.SEARCH, Icons.Outlined.Search, tr("search")),
        Triple(XDockItem.PROFILE, Icons.Outlined.Person, tr("profile")),
    )
    val activeIndex = items.indexOfFirst { it.first == selected }
    val pillOffset by animateDpAsState(
        targetValue = slotWidth * activeIndex.coerceAtLeast(0) + 2.dp * activeIndex.coerceAtLeast(0),
        animationSpec = tween(440, easing = CubicBezierEasing(.22f, 1f, .28f, 1f)),
        label = "dock-pill",
    )
    val pillAlpha by animateFloatAsState(if (activeIndex >= 0) .12f else 0f, tween(260), label = "dock-pill-alpha")
    Row(
        modifier
            .shadow(18.dp, CircleShape, ambientColor = Color(0x6B140A2C), spotColor = Color(0x6B140A2C))
            .clip(CircleShape)
            .background(XText)
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                Modifier
                    .offset(x = pillOffset)
                    .size(slotWidth, slotHeight)
                    .clip(CircleShape)
                    .background(XInverse.copy(alpha = pillAlpha)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { (item, icon, label) ->
                    val tint by animateColorAsState(
                        if (item == selected) XInverse else XInverse.copy(alpha = .42f),
                        tween(300),
                        label = "dock-tint",
                    )
                    Box(
                        Modifier
                            .size(slotWidth, slotHeight)
                            .clip(CircleShape)
                            // The canvas dock slot is a bare pointer target — no ripple.
                            .xPressable { onSelect(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, label, tint = tint, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }
    }
}

/**
 * Локальный «закрепить чат» из меню ⋮ (макет: `pinIconD` в меню переписки).
 * Закреплённые чаты поднимаются в начало своей вкладки; порядок внутри групп
 * сортировка не меняет. Ревизия нужна, чтобы список пересортировался сразу,
 * не дожидаясь фонового опроса.
 */
internal val xChatPinRevision = mutableIntStateOf(0)

internal fun xChatPinnedIds(context: android.content.Context, userId: String): Set<String> =
    context.getSharedPreferences("orionx_chat_pins_$userId", android.content.Context.MODE_PRIVATE)
        .all.keys.toSet()

internal fun xChatPin(context: android.content.Context, userId: String, conversationId: String, pinned: Boolean) {
    val prefs = context.getSharedPreferences("orionx_chat_pins_$userId", android.content.Context.MODE_PRIVATE)
    if (pinned) prefs.edit().putBoolean(conversationId, true).apply()
    else prefs.edit().remove(conversationId).apply()
    xChatPinRevision.intValue++
}

@Composable
private fun XChatsTab(
    api: SupabaseApi,
    session: CloudSession,
    state: XChatsTabState,
    listState: LazyListState,
    onOpen: (CloudConversation) -> Unit,
    onOria: () -> Unit,
    onCreate: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onScanner: () -> Unit,
    music: net.orionx.messenger.music.MusicController,
) {
    var chatKind by state.chatKind
    var oriaPreview by state.oriaPreview
    var oriaTime by state.oriaTime
    var conversations by state.conversations
    var loading by state.loading
    var showArchived by state.showArchived
    var error by state.error
    var locallyReadIds by state.locallyReadIds
    var selectedIds by state.selectedIds
    var loadedOnce by state.loadedOnce
    var senderNames by state.senderNames
    var syncStatus by state.syncStatus
    var loadStartedAt by state.loadStartedAt
    var announcedLoadAt by state.announcedLoadAt
    var lastSuccessAt by state.lastSuccessAt
    var outageAnnounced by state.outageAnnounced
    var pendingArchive by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val chatsForeground = (context as? MainActivity)?.isAppForeground ?: true
    val readWatermarks = remember(session.userId) {
        context.getSharedPreferences("orionx_read_watermarks_${session.userId}", Context.MODE_PRIVATE)
    }

    // Cold start: paint the last known list straight from disk so a relaunch shows real
    // rows instead of an empty screen while the first request is still in flight.
    remember(session.userId) {
        if (!state.loadedOnce.value && state.conversations.value.isEmpty()) {
            val cached = XColdStart.loadConversations(context, session.userId)
            if (cached.isNotEmpty()) {
                state.conversations.value = cached
                state.loading.value = false
            }
            val (cachedPreview, cachedTime) = XColdStart.loadOria(context, session.userId)
            if (cachedPreview.isNotBlank()) {
                state.oriaPreview.value = cachedPreview
                state.oriaTime.value = cachedTime
            }
        }
        true
    }

    fun serverMillis(value: String): Long = runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    fun applyReadOverlay(rows: List<CloudConversation>): List<CloudConversation> = rows.map { row ->
        val watermark = readWatermarks.getLong(row.id, 0L)
        val latest = serverMillis(row.lastMessageAt)
        if (row.id in locallyReadIds || (watermark > 0L && latest > 0L && latest <= watermark)) row.copy(unreadCount = 0) else row
    }

    fun applyArchiveOverlay(rows: List<CloudConversation>): List<CloudConversation> = rows.map { row ->
        val desired = pendingArchive[row.id]
        if (desired != null) row.copy(archived = desired) else row
    }

    fun prunePending(remote: List<CloudConversation>) {
        if (pendingArchive.isEmpty()) return
        pendingArchive = pendingArchive.filterKeys { id ->
            val server = remote.firstOrNull { it.id == id } ?: return@filterKeys true
            server.archived != pendingArchive[id]
        }
    }

    fun mergeRemote(remote: List<CloudConversation>): List<CloudConversation> {
        prunePending(remote)
        return applyReadOverlay(applyArchiveOverlay(remote))
    }

    /**
     * Fills in the «артём: …» prefix the canvas shows on group rows. `my_conversations`
     * carries only the sender id, so the names are fetched once per sender and kept for
     * the life of the process — misses included, so an unresolvable id is not re-requested
     * on every poll.
     */
    suspend fun resolveSenders(rows: List<CloudConversation>) {
        val ids = rows
            .filter { it.type == "group" && !it.lastMessageMine }
            .map { it.lastMessageSenderId }
            .filter { it.isNotBlank() && it != session.userId }
        if (ids.isEmpty()) return
        val missing = XScreenCache.unknownSenders(ids)
        if (missing.isNotEmpty()) {
            runCatching { withContext(Dispatchers.IO) { api.displayNamesOf(session.accessToken, missing) } }
                .onSuccess { found -> XScreenCache.putSenderNames(missing.associateWith { found[it].orEmpty() }) }
        }
        senderNames = XScreenCache.senderNamesOf(ids)
    }

    /**
     * Runs one list fetch while recording what the header needs: when it started, and
     * whether the network is answering at all.
     */
    suspend fun <T> tracked(announce: Boolean = false, block: suspend () -> T): Result<T> {
        val stamp = System.currentTimeMillis()
        loadStartedAt = stamp
        if (announce) announcedLoadAt = stamp
        val result = runCatching { block() }
        if (state.loadStartedAt.value == stamp) loadStartedAt = 0L
        result.onSuccess {
            lastSuccessAt = System.currentTimeMillis()
            outageAnnounced = false
        }
        if (announce && state.announcedLoadAt.value == stamp) announcedLoadAt = 0L
        return result
    }

    suspend fun reload(announce: Boolean = false) {
        try {
            tracked(announce = announce) { withContext(Dispatchers.IO) { api.myConversations(session.accessToken, session.userId) } }
                .onSuccess { remote ->
                    locallyReadIds = locallyReadIds.filter { id -> remote.firstOrNull { it.id == id }?.unreadCount != 0 }.toSet()
                    conversations = mergeRemote(remote)
                    error = null
                    scope.launch(Dispatchers.IO) { XColdStart.saveConversations(context, session.userId, remote) }
                    resolveSenders(remote)
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    // Only surface errors for explicit/first loads. Background polls stay silent
                    // so a brief network hiccup never flashes the banner over a working list.
                    if (conversations.isEmpty()) error = localizedNetworkError(it)
                }
        } finally {
            loading = false
        }
    }

    fun markReadAndOpen(conversation: CloudConversation) {
        val watermark = serverMillis(conversation.lastMessageAt).takeIf { it > 0L } ?: System.currentTimeMillis()
        readWatermarks.edit().putLong(conversation.id, watermark).apply()
        locallyReadIds = locallyReadIds + conversation.id
        conversations = conversations.map { if (it.id == conversation.id) it.copy(unreadCount = 0) else it }
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { api.markConversationRead(session.accessToken, conversation.id) } }
            locallyReadIds = locallyReadIds - conversation.id
            result.onSuccess { reload() }
                .onFailure {
                    // Keep the local read watermark even if Supabase is temporarily unavailable.
                    // A newer message has a newer server timestamp and will still become unread.
                    reload()
                }
        }
        onOpen(conversation.copy(unreadCount = 0))
    }

    fun archive(ids: Set<String>, archived: Boolean) {
        if (ids.isEmpty()) return
        val before = conversations
        pendingArchive = pendingArchive + ids.associateWith { archived }
        conversations = conversations.map { conversation ->
            if (conversation.id in ids) conversation.copy(archived = archived) else conversation
        }
        selectedIds = emptySet()
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.setConversationsArchivedForUser(session.accessToken, ids, archived)
                }
            }
            result.onSuccess {
                reload()
            }.onFailure {
                conversations = before
                pendingArchive = pendingArchive - ids
                error = localizedNetworkError(it)
            }
        }
    }


    // Header status.
    //
    // Two rules, and both are about staying quiet:
    //
    //   • Only a load the user is actually waiting on says «Обновление». The six-second
    //     poll never does — on mobile data it regularly takes a second, and announcing it
    //     turned the title into a blinking light. The first load of the screen announces,
    //     because that is the one where the list is not there yet.
    //   • Once shown it stays put for a beat, so a load that finishes just after the
    //     threshold does not flash the word for 50 ms.
    //
    // «Нет сети» is measured from the last success rather than from an error, because with
    // no network the request does not fail — it hangs. It is said once per outage and then
    // the header goes quiet; retrying in the background is not news.
    var syncShownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(announcedLoadAt) {
        if (announcedLoadAt == 0L) {
            // The load finished. Take the word down, but not before it has been up long
            // enough to read — and never touch «нет сети», which outranks it.
            if (syncStatus == XSyncStatus.SYNCING) {
                val shown = System.currentTimeMillis() - syncShownAt
                if (shown < X_SYNC_MIN_SHOWN) delay(X_SYNC_MIN_SHOWN - shown)
                if (syncStatus == XSyncStatus.SYNCING) syncStatus = XSyncStatus.IDLE
            }
            return@LaunchedEffect
        }
        delay(X_SYNC_ANNOUNCE_AFTER)
        if (state.announcedLoadAt.value != announcedLoadAt) return@LaunchedEffect
        if (outageAnnounced || syncStatus == XSyncStatus.OFFLINE) return@LaunchedEffect
        syncShownAt = System.currentTimeMillis()
        syncStatus = XSyncStatus.SYNCING
    }

    LaunchedEffect(session.userId) {
        // Measured from the moment the screen opens. The poll only runs while this screen
        // is composed, so time spent on another screen must not count as an outage.
        lastSuccessAt = System.currentTimeMillis()
        outageAnnounced = false
        while (true) {
            delay(1_000)
            if (outageAnnounced) continue
            if (System.currentTimeMillis() - lastSuccessAt < X_OFFLINE_AFTER) continue
            syncStatus = XSyncStatus.OFFLINE
            delay(X_OFFLINE_SHOWN)
            outageAnnounced = true
            if (syncStatus == XSyncStatus.OFFLINE) syncStatus = XSyncStatus.IDLE
        }
    }

    LaunchedEffect(session.accessToken, session.userId) {
        runCatching {
            withContext(Dispatchers.IO) {
                val thread = api.ensureOriaThread(session.accessToken, session.userId)
                api.oriaMessages(session.accessToken, thread.id).lastOrNull()
            }
        }.onSuccess { last ->
            if (last != null) {
                oriaPreview = last.content.lineSequence().firstOrNull().orEmpty().take(90)
                oriaTime = formatMessageTime(last.createdAt)
                launch(Dispatchers.IO) { XColdStart.saveOria(context, session.userId, oriaPreview, oriaTime) }
            }
        }
    }

    LaunchedEffect(session.accessToken, chatsForeground) {
        if (!loadedOnce) {
            // Rows restored from disk are already on screen — do not hide them behind a skeleton.
            loading = conversations.isEmpty()
            reload(announce = true)
            loadedOnce = true
        }
        // В фоне список не опрашиваем: это десять лишних запросов в минуту.
        if (!chatsForeground) return@LaunchedEffect
        while (true) {
            delay(6_000)
            tracked { withContext(Dispatchers.IO) { api.myConversations(session.accessToken, session.userId) } }
                .onSuccess { remote ->
                    locallyReadIds = locallyReadIds.filter { id -> remote.firstOrNull { it.id == id }?.unreadCount != 0 }.toSet()
                    conversations = mergeRemote(remote)
                    error = null
                    launch(Dispatchers.IO) { XColdStart.saveConversations(context, session.userId, remote) }
                    resolveSenders(remote)
                }
        }
    }

    // Canvas tab swap: the pill moves at once, the list plays a 170 ms exit, then the
    // new rows fly in staggered by 45 ms. `shownKind` is what the list is currently
    // drawing, which lags `chatKind` by the exit.
    var shownKind by remember { mutableIntStateOf(chatKind) }
    var swapDir by remember { mutableIntStateOf(1) }
    var listGeneration by remember { mutableIntStateOf(0) }
    val exitProgress = remember { Animatable(0f) }
    val kindMotion = LocalXRuntimeSettings.current.reduceMotion

    LaunchedEffect(chatKind) {
        if (chatKind == shownKind) return@LaunchedEffect
        swapDir = if (chatKind < shownKind) -1 else 1
        if (!kindMotion) {
            // `ctoutL/ctoutR .17s ease-in both`
            exitProgress.animateTo(1f, tween(170, easing = androidx.compose.animation.core.EaseIn))
        }
        shownKind = chatKind
        listGeneration++
        exitProgress.snapTo(0f)
    }

    val visible = remember(conversations, showArchived, shownKind, xChatPinRevision.intValue) {
        val rows = conversations.filter { it.archived == showArchived && xChatKindOf(it) == shownKind }
        // «закрепить чат» поднимает строку в начало вкладки; внутри групп — прежний порядок.
        val pinned = xChatPinnedIds(context, session.userId)
        if (pinned.isEmpty()) rows else rows.sortedByDescending { it.id in pinned }
    }
    val archivedCount = remember(conversations) { conversations.count { it.archived } }
    val unreadTotal = remember(visible) { visible.sumOf { it.unreadCount } }
    val unreadRooms = remember(visible) { visible.count { it.unreadCount > 0 } }
    val selecting = selectedIds.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        if (showArchived || selecting) {
            // Archive and multi-select take over the slot the action row normally occupies.
            if (selecting) {
                Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                    XConversationSelectionToolbar(
                        selectedCount = selectedIds.size,
                        archived = showArchived,
                        onApply = { archive(selectedIds, !showArchived) },
                        onClose = { selectedIds = emptySet() },
                    )
                }
            } else {
                XScreenHeader(
                    title = xSettingText("archive"),
                    onBack = {
                        selectedIds = emptySet()
                        showArchived = false
                    },
                )
            }
        } else {
            var composeOpen by state.composeOpen
            // `z-index:30` — the dropdown draws over the cards below it.
            Box(Modifier.fillMaxWidth().zIndex(30f)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XCircleButton(
                            icon = Icons.Outlined.QrCode2,
                            contentDescription = xSettingText("scanner_title"),
                            size = 56.dp,
                            iconSize = 24.dp,
                            onClick = onScanner,
                        )
                        XSurfacePill(
                            label = tr("write_action"),
                            modifier = Modifier.weight(1f),
                            background = if (composeOpen) XSurfaceRaised else XSurface,
                            trailing = {
                                // `transform:rotate(180deg)` over `.4s cubic-bezier(.4,0,.2,1)`
                                val caret by animateFloatAsState(
                                    targetValue = if (composeOpen) 180f else 0f,
                                    animationSpec = tween(400, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)),
                                    label = "compose-caret",
                                )
                                // Скруглённая «молния» как в макете (Material Symbols Rounded bolt).
                                Icon(
                                    painterResource(R.drawable.x_bolt_rounded),
                                    null,
                                    tint = XAccent,
                                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = caret },
                                )
                            },
                            onClick = { composeOpen = !composeOpen },
                        )
                        XSurfacePill(label = tr("people"), onClick = onSearch)
                    }
                    // Zero height so the panel overlays the list instead of pushing it down.
                    Box(Modifier.fillMaxWidth().height(0.dp)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = composeOpen,
                            enter = fadeIn(tween(300)) + scaleIn(
                                animationSpec = tween(340, easing = CubicBezierEasing(.22f, 1f, .36f, 1f)),
                                initialScale = .82f,
                                transformOrigin = TransformOrigin(.5f, 0f),
                            ),
                            exit = fadeOut(tween(200)) + scaleOut(
                                animationSpec = tween(220),
                                targetScale = .82f,
                                transformOrigin = TransformOrigin(.5f, 0f),
                            ),
                            modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true),
                        ) {
                            // Canvas: the panel top meets the button bottom exactly, so the
                            // row padding below the pills has to be cancelled out.
                            Box(Modifier.padding(start = 18.dp, end = 18.dp).offset(y = (-14).dp)) {
                                XComposeMenu(
                                    api = api,
                                    session = session,
                                    state = state,
                                    conversations = conversations,
                                    onOpenConversation = onOpen,
                                    onSearchEverywhere = onSearch,
                                )
                            }
                        }
                    }
                }
            }

            // The unread card. In the canvas this is a row: the counter on the left and,
            // when something is loaded in the player, a 122px tile on the right
            // (`sc-if musicSqD`) — `gap:16px;border-radius:32px;padding:20px`.
            XCard(
                modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp),
                shape = XShape.hero,
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        XCardLabel(tr("unread"))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$unreadTotal", style = XType.metric, color = XText)
                            Text(
                                tr(xPluralKey(unreadTotal, "messages")),
                                style = XType.rowTitle.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                color = XText,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                        Row(
                            Modifier.clip(XShape.chip).background(XSurfaceRaised).padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(XAccent))
                            Text(
                                tr(xPluralKey(unreadRooms, xChatKindScope(shownKind)), "n" to "$unreadRooms"),
                                style = XType.meta.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                color = XText,
                            )
                        }
                    }
                    XUnreadMusicTile(music)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XCircleButton(
                    icon = Icons.Outlined.Tune,
                    contentDescription = tr("settings"),
                    size = 48.dp,
                    onClick = onSettings,
                )
                Spacer(Modifier.weight(1f))
                // `crKind: ctab === 2 ? 1 : 0` — the channels tab opens the channel form.
                XInversePill(label = tr("create"), onClick = { onCreate(if (chatKind == 2) "channel" else "group") })
            }

            XSegmentedTabs(
                labels = listOf(tr("chats"), tr("groups"), tr("channels")),
                selected = chatKind,
                onSelect = { chatKind = it },
                modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 10.dp),
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Only while the exit is playing — see XStaggeredEntry for why a layer
                // that outlives its animation shows up on the card corners. Слой остаётся
                // прицепленным, но бездействует, чтобы не пересобирать цепочку модификаторов
                // на каждом кадре выхода.
                .graphicsLayer {
                    if (exitProgress.value == 0f) return@graphicsLayer
                    // Exit phase: fade out while sliding 20px against the travel direction.
                    alpha = 1f - exitProgress.value
                    translationX = exitProgress.value * (-20).dp.toPx() * swapDir
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) {
            // No pull-to-refresh: the canvas has no such gesture, and the Material
            // spinner it draws is the one foreign element left on this screen. The list
            // refreshes itself every few seconds and says so in the header instead.
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (!showArchived && !selecting && archivedCount > 0) {
                    item {
                        XArchiveFolderRow(
                            count = archivedCount,
                            onClick = {
                                selectedIds = emptySet()
                                showArchived = true
                            },
                        )
                    }
                }

                if (loading) {
                    item { XLoadingCard() }
                } else if (visible.isEmpty()) {
                    item {
                        if (showArchived) XInlineMessage(xSettingText("archive_empty"), error = false)
                        else XEmptyChatsCard(shownKind)
                    }
                } else {
                    itemsIndexed(visible, key = { _, it -> it.id }) { index, conversation ->
                        XStaggeredEntry(index = index, generation = listGeneration, direction = swapDir) {
                            XSwipeConversationRow(
                                conversation = conversation,
                                selected = conversation.id in selectedIds,
                                selectionMode = selecting,
                                archived = showArchived,
                                onClick = {
                                    if (selecting) {
                                        selectedIds = if (conversation.id in selectedIds) selectedIds - conversation.id else selectedIds + conversation.id
                                    } else {
                                        markReadAndOpen(conversation)
                                    }
                                },
                                onLongClick = {
                                    selectedIds = if (conversation.id in selectedIds) selectedIds - conversation.id else selectedIds + conversation.id
                                },
                                onSwipeArchive = { archive(setOf(conversation.id), !showArchived) },
                                currentUserId = session.userId,
                                senderName = senderNames[conversation.lastMessageSenderId].orEmpty(),
                            )
                        }
                    }
                }
                error?.let { item { XInlineMessage(it, error = true) } }
            }
        }
    }
}


/**
 * The «написать» dropdown — canvas `compose`.
 *
 * The pill used to open the group-create form, which is not what «написать» means. In the
 * canvas it drops a panel under the button: a search field, then who to write to. With no
 * query it lists the people already in the chat list («недавние»); typing searches every
 * profile.
 *
 * `position:absolute;top:calc(100% - 6px);left:18px;right:18px;z-index:30` — the panel
 * hangs over the content below instead of pushing it down, so nothing jumps.
 */
@Composable
private fun XComposeMenu(
    api: SupabaseApi,
    session: CloudSession,
    state: XChatsTabState,
    conversations: List<CloudConversation>,
    onOpenConversation: (CloudConversation) -> Unit,
    onSearchEverywhere: () -> Unit,
) {
    var query by state.composeQuery
    var found by state.composeFound
    var searching by state.composeSearching
    val scope = rememberCoroutineScope()

    // «недавние» — the direct chats already on screen. No request, and tapping one opens
    // the conversation that exists rather than asking the server to make another.
    val recent = remember(conversations) {
        conversations.filter { it.type == "direct" && !it.archived }.take(12)
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            found = emptyList()
            searching = false
            return@LaunchedEffect
        }
        // Let the typing settle before asking the server.
        delay(280)
        searching = true
        val result = runCatching { withContext(Dispatchers.IO) { api.searchProfiles(session.accessToken, q) } }
            .getOrDefault(emptyList())
            .filterNot { it.id == session.userId }
        // Старый медленный ответ не перезаписывает свежий запрос.
        if (query.trim() != q) return@LaunchedEffect
        found = result
        searching = false
    }

    val hasQuery = query.trim().length >= 2
    val empty = hasQuery && !searching && found.isEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            // `box-shadow:0 24px 50px var(--shc2)` — heavier than the card shadow.
            .xShadow(XShape.card, elevation = 24.dp, shadowColor = XShadowStrong)
            .clip(XShape.card)
            .background(XSurface)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
    ) {
        // `background:var(--sf2);border-radius:16px;padding:12px 14px;gap:10px`
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(XSurfaceRaised).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Search, null, tint = XMuted, modifier = Modifier.size(19.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(tr("compose_hint"), style = XType.bodyStrong.copy(fontSize = 13.sp), color = XMuted)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = XType.bodyStrong.copy(fontSize = 13.sp, color = XText),
                    cursorBrush = SolidColor(XAccent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Cancel,
                    tr("cancel"),
                    tint = XMuted,
                    modifier = Modifier.size(17.dp).xPressable { query = "" },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tr(if (hasQuery) "compose_results" else "compose_recent").uppercase(),
                style = XType.sectionLabel.copy(fontSize = 10.sp),
                color = XMuted2,
            )
            // Canvas shows how many of how many when there is no query, the plain count
            // when there is.
            val shown = if (hasQuery) found.size else minOf(recent.size, X_COMPOSE_ROWS)
            val count = when {
                hasQuery -> found.size.takeIf { it > 0 }?.toString().orEmpty()
                recent.isEmpty() -> ""
                else -> tr("compose_count", "shown" to shown.toString(), "total" to recent.size.toString())
            }
            Text(
                count,
                style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = XMuted2,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }

        // The canvas caps this at 214px, which lands three and two thirds rows in — so the
        // fourth was sliced through the middle of a face. Snapped to whole rows instead,
        // and while scrolling the bottom edge fades into the panel rather than cutting.
        val listScroll = rememberScrollState()
        Box {
        Column(
            Modifier
                .heightIn(max = X_COMPOSE_ROW * X_COMPOSE_ROWS)
                .verticalScroll(listScroll),
        ) {
            if (hasQuery) {
                found.forEach { person ->
                    XComposeRow(
                        title = person.displayName.ifBlank { person.nickname },
                        subtitle = "@" + person.nickname,
                        avatarUrl = person.avatarUrl,
                    ) {
                        state.composeOpen.value = false
                        query = ""
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { api.createDirect(session.accessToken, person.id) } }
                                .onSuccess(onOpenConversation)
                        }
                    }
                }
            } else {
                recent.forEach { conversation ->
                    XComposeRow(
                        title = conversation.peerDisplayName.ifBlank { conversation.title },
                        subtitle = conversation.peerNickname.takeIf { it.isNotBlank() }?.let { "@" + it }.orEmpty(),
                        avatarUrl = conversation.peerAvatarUrl,
                    ) {
                        state.composeOpen.value = false
                        query = ""
                        onOpenConversation(conversation)
                    }
                }
            }
            if (searching) {
                Text(
                    tr("compose_searching"),
                    style = XType.body,
                    color = XMuted2,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (empty) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        tr("compose_empty", "query" to query.trim()),
                        style = XType.body.copy(fontSize = 12.5.sp),
                        color = XMuted2,
                        textAlign = TextAlign.Center,
                    )
                    Row(
                        Modifier
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(XSurfaceRaised)
                            .xPressable(pressedScale = .96f) {
                                state.composeOpen.value = false
                                query = ""
                                onSearchEverywhere()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(Icons.Outlined.PersonSearch, null, tint = XText, modifier = Modifier.size(17.dp))
                        Text(
                            tr("compose_search_all").uppercase(),
                            style = XType.sectionLabel.copy(fontSize = 9.5.sp, letterSpacing = .1.em),
                            color = XText,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (!hasQuery && recent.isEmpty()) {
                Text(
                    tr("compose_no_recent"),
                    style = XType.body.copy(fontSize = 12.5.sp),
                    color = XMuted2,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (listScroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, XSurface))),
            )
        }
        }
    }
}

/** One row: a 40 px plate with 9 px above and below. */
private val X_COMPOSE_ROW = 58.dp

/** How many of them the panel shows before it scrolls — whole rows, never a sliced one. */
private const val X_COMPOSE_ROWS = 3

/** `padding:9px 4px;gap:12px`, 40 px plate, and the accent `edit` glyph on the right. */
@Composable
private fun XComposeRow(
    title: String,
    subtitle: String,
    avatarUrl: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(XShape.chip)
            .xPressable(pressedScale = .98f, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (avatarUrl.startsWith("orionx-gradient:")) {
            XGradientAvatar(avatarUrl.removePrefix("orionx-gradient:").toIntOrNull() ?: 0, title, 40.dp, RoundedCornerShape(14.dp))
        } else if (avatarUrl.isNotBlank()) {
            RemoteImage(avatarUrl, Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)), ContentScale.Crop)
        } else {
            XLetterAvatar(title, 40.dp, RoundedCornerShape(14.dp), letterColor = XInverse)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = XType.rowTitle.copy(fontSize = 13.5.sp),
                color = XText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = XType.meta.copy(fontSize = 11.5.sp), color = XMuted, maxLines = 1)
            }
        }
        Icon(Icons.Outlined.Edit, null, tint = XAccent, modifier = Modifier.size(20.dp))
    }
}


/**
 * `сегодня` — the day chip between message groups.
 *
 * Canvas: `background:var(--hairA);border-radius:999px;padding:6px 14px;font:700 11px`,
 * uppercase, in `--txt`.
 */
@Composable
private fun XDayDivider(iso: String, modifier: Modifier = Modifier) {
    val label = xDayLabel(iso)
    if (label.isEmpty()) return
    // `align-self:center;border-radius:999px;padding:6px 14px;font:700 11px/1;letter-spacing:.1em`
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.clip(CircleShape).background(XHairline).padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                label.uppercase(),
                style = XType.meta.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = .1.em),
                color = XText,
                maxLines = 1,
            )
        }
    }
}

/** Canvas `CANCEL_AT = -74`, and the lift that pins a take open. */
private val X_REC_CANCEL_DP = 74f
private val X_REC_LOCK_DP = 72f

/** A video note runs at most a minute; the ring around the button measures it. */
private const val X_VIDEO_NOTE_MAX_SECONDS = 60f

/** A day chip in the message feed, carried as its own list entry. */
private data class XDayMark(val key: String, val iso: String)

/** The calendar day of a message, for grouping. Empty when the stamp cannot be read. */
private fun xDayKey(iso: String): String = runCatching {
    java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
}.getOrDefault("")

@Composable
private fun xDayLabel(iso: String): String {
    val date = runCatching {
        java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }.getOrNull() ?: return ""
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> tr("day_today")
        today.minusDays(1) -> tr("day_yesterday")
        else -> {
            val pattern = if (date.year == today.year) "d MMMM" else "d MMMM yyyy"
            java.time.format.DateTimeFormatter
                .ofPattern(pattern, java.util.Locale(OrionI18n.current.code))
                .format(date)
        }
    }
}

/** The three chats tabs map onto the conversation types the backend already stores. */
private fun xChatKindOf(conversation: CloudConversation): Int = when (conversation.type) {
    "group" -> 1
    "channel" -> 2
    else -> 0
}

private fun xChatKindScope(kind: Int): String = when (kind) {
    1 -> "in_groups"
    2 -> "in_channels"
    else -> "in_chats"
}

/**
 * Picks the `<base>_one` / `_few` / `_many` key for a count. Russian, Ukrainian and Belarusian
 * share the same three-form rule; English and Kazakh get the same string in every form.
 */
private fun xPluralKey(count: Int, base: String): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "${base}_one"
        mod10 in 2..4 && mod100 !in 12..14 -> "${base}_few"
        else -> "${base}_many"
    }
}

/**
 * One row of a swapped list — `ctinR/ctinL .42s cubic-bezier(.22,1,.28,1) both` with
 * `animation-delay: index * 0.045s`. [generation] restarts it, the way the canvas
 * restarts the animation by switching the keyframe name.
 */
@Composable
private fun XStaggeredEntry(
    index: Int,
    generation: Int,
    direction: Int,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalXRuntimeSettings.current.reduceMotion
    // Ключ включает reduceMotion: иначе включение настройки посреди каскада
    // замораживает строки на промежуточной прозрачности.
    val progress = remember(generation, reduceMotion) { Animatable(if (generation == 0 || reduceMotion) 1f else 0f) }
    LaunchedEffect(generation, reduceMotion) {
        if (generation == 0 || reduceMotion) {
            if (progress.value < 1f) progress.snapTo(1f)
            return@LaunchedEffect
        }
        // Beyond the first screenful the stagger stops growing, otherwise a row far
        // down would sit invisible for a second before it appeared.
        delay(index.coerceAtMost(8) * 45L)
        progress.animateTo(1f, tween(420, easing = CubicBezierEasing(.22f, 1f, .28f, 1f)))
    }
    // While the row is moving it needs its own layer; once it has settled the layer is
    // dropped again. A `graphicsLayer` that stays behind keeps the row composited
    // separately, and the rounded corner then picks up a bright rim where the layer edge
    // is blended a second time — visible as a glowing outline on every card corner.
    val settled = progress.value >= 1f
    Box(
        if (settled) Modifier else Modifier.graphicsLayer {
            alpha = progress.value
            translationX = (1f - progress.value) * 24.dp.toPx() * direction
            // Without this the layer goes offscreen-buffered and clips to the row bounds —
            // and the drop shadow lives outside them, so it stayed invisible for the whole
            // animation and popped in only when the layer was dropped. Modulating alpha per
            // draw keeps the shadow fading in with the row it belongs to.
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    ) { content() }
}

/**
 * The light band that crosses the screen when the theme changes.
 *
 * Canvas: a full-bleed layer with
 * `background:linear-gradient(100deg,transparent 34%,var(--sweepC) 50%,transparent 66%)`
 * travelling from `translate3d(-120%,-40%)` to `translate3d(120%,40%)` — reversed when
 * going the other way — over `.72s cubic-bezier(.4,0,.2,1)`, with opacity up by 18%.
 * `--sweepC` is a violet glow in dark, white in light.
 */
@Composable
private fun XThemeSweep(key: Int, toDark: Boolean) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(720, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)))
    }
    if (progress.value >= 1f) return

    val sweepColor = if (toDark) Color(0x8C785AFF) else Color(0xD9FFFFFF)
    val direction = if (toDark) 1f else -1f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val travel = with(LocalDensity.current) { maxWidth.toPx() } * 1.2f
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // `0%{opacity:0} 18%{opacity:1} 100%{opacity:0}`
                    alpha = when {
                        progress.value < .18f -> progress.value / .18f
                        else -> 1f - (progress.value - .18f) / .82f
                    }
                    translationX = (progress.value * 2f - 1f) * travel * direction
                    translationY = (progress.value * 2f - 1f) * travel * .33f * direction
                    rotationZ = 18f
                }
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        .34f to Color.Transparent,
                        .5f to sweepColor,
                        .66f to Color.Transparent,
                        1f to Color.Transparent,
                    )
                ),
        )
    }
}

/** `border-radius:13px` segmented control with the pill sliding between the segments. */
@Composable
internal fun XSegmentedTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = labels.size.coerceAtLeast(1)
    BoxWithConstraints(modifier.fillMaxWidth().height(38.dp)) {
        val gap = 6.dp
        val segmentWidth = (maxWidth - gap * (count - 1)) / count
        val offset by animateDpAsState(
            targetValue = (segmentWidth + gap) * selected,
            animationSpec = tween(400, easing = CubicBezierEasing(.22f, 1f, .36f, 1f)),
            label = "segment-pill",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(XShape.segment)
                .background(XText),
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            labels.forEachIndexed { index, label ->
                val fg by animateColorAsState(
                    if (index == selected) XInverse else XMuted,
                    tween(300),
                    label = "segment-fg",
                )
                Box(
                    Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(XShape.segment)
                        // `style-active="transform:scale(.97)"`
                        .xPressable(pressedScale = .97f) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label.uppercase(),
                        style = XType.sectionLabel.copy(letterSpacing = .1.em),
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun XArchiveFolderRow(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .xShadow(XShape.card)
            .clip(XShape.card)
            .background(XSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(XShape.plate).background(XSurfaceRaised),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Archive, null, tint = XText, modifier = Modifier.size(21.dp)) }
        Column(Modifier.weight(1f)) {
            Text(xSettingText("archive"), style = XType.rowTitle, color = XText)
            Spacer(Modifier.height(4.dp))
            Text(
                tr(xPluralKey(count, "in_chats"), "n" to "$count"),
                style = XType.body,
                color = XMuted,
                maxLines = 1,
            )
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = XMuted, modifier = Modifier.size(19.dp))
    }
}

/**
 * In the canvas Oria is not a special card — it is an ordinary chat row
 * (`{i:'О', name:'ория', last:'собрала сводку за неделю', time:'вчера'}`), so it uses the same
 * geometry as [XConversationRow]: 44 dp plate, 800/14 title, 500/12.5 preview, time on the right.
 */
@Composable
private fun XOriaCard(preview: String, time: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .xShadow(XShape.card)
            .clip(XShape.card)
            .background(XSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OriaOrb(44.dp)
        Column(Modifier.weight(1f)) {
            Text(tr("oria"), style = XType.rowTitle, color = XText)
            Spacer(Modifier.height(4.dp))
            Text(
                preview.ifBlank { xSettingText("oria_card_sub") },
                style = XType.body,
                color = XMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (time.isNotBlank()) {
            Text(time, style = XType.meta, color = XMuted, maxLines = 1)
        }
    }
}

@Composable
private fun XEmptyChatsCard(kind: Int) {
    // Без огромной иллюстрации-планеты — только текст, компактно по центру.
    Box(Modifier.fillMaxWidth().heightIn(min = 220.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr(when (kind) { 1 -> "no_groups"; 2 -> "no_channels"; else -> "no_chats" }), style = XType.screenTitle, color = XText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(7.dp))
            Text(tr(when (kind) { 1 -> "create_first_group"; 2 -> "create_first_channel"; else -> "create_first_chat" }), style = XType.bodyStrong, color = XMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun XConversationSelectionToolbar(
    selectedCount: Int,
    archived: Boolean,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .xShadow(XShape.card)
            .clip(XShape.card)
            .background(XSurface)
            .padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Close, tr("cancel"), tint = XMuted, modifier = Modifier.size(20.dp)) }
        Text("$selectedCount", style = XType.rowTitle.copy(fontSize = 16.sp), color = XText, modifier = Modifier.weight(1f))
        XInversePill(
            label = if (archived) xSettingText("unarchive_action") else xSettingText("archive_action"),
            height = 38.dp,
            onClick = onApply,
        )
    }
}

@Composable
private fun XSwipeConversationRow(
    conversation: CloudConversation,
    selected: Boolean,
    selectionMode: Boolean,
    archived: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeArchive: () -> Unit,
    currentUserId: String = "",
    senderName: String = "",
) {
    if (selectionMode) {
        XConversationRow(conversation, selected, onClick, onLongClick, currentUserId, senderName)
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * .28f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onSwipeArchive()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            // The archive strip sits under the row, so anything of it the row does not
            // cover shows through — and a rounded corner never covers its own bounding
            // box. At rest that left a bright violet rim tracing every card corner.
            // Draw it only while a drag is actually running, and at the row own radius.
            val dragOffset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
            if (dragOffset > .5f) {
                Box(
                    Modifier.fillMaxSize().clip(XShape.card).background(XPurple.copy(alpha = .23f)).padding(horizontal = 22.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        if (archived) "Вернуть из архива" else "В архив",
                        tint = XViolet,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        },
    ) {
        XConversationRow(conversation, selected, onClick, onLongClick, currentUserId, senderName)
    }
}

/**
 * The player tile beside the unread counter —
 * `122px;border-radius:26px;background:var(--sf2);padding:13px`.
 *
 * The canvas guards it with `sc-if musicSqD`, so with nothing loaded the card
 * collapses back to the counter alone.
 */
@Composable
private fun XUnreadMusicTile(
    music: net.orionx.messenger.music.MusicController,
) {
    val track = music.current ?: return
    val duration = track.durationMs.coerceAtLeast(1L)
    val progress = (music.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    Column(
        Modifier
            .size(122.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(XSurfaceRaised)
            .clickable { music.toggle() }
            .padding(13.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // `30px;border-radius:10px` cover plate with the canvas' gradient.
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(XAvatarBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.MusicNote, null, tint = Color(0xFF101014), modifier = Modifier.size(17.dp))
            }
            // `@keyframes eq` — four bars, 0.82s, staggered by .16s. Only while playing.
            if (music.playing) {
                val transition = rememberInfiniteTransition(label = "eq")
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    listOf(0, 160, 320, 480).forEach { delayMs ->
                        val scale by transition.animateFloat(
                            initialValue = .3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(410, easing = LinearEasing),
                                initialStartOffset = StartOffset(delayMs),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "eq-bar",
                        )
                        Box(
                            Modifier
                                .width(2.5.dp)
                                .height(13.dp * scale)
                                .clip(RoundedCornerShape(2.dp))
                                .background(XText),
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                track.title,
                style = XType.rowTitle.copy(fontSize = 12.sp, lineHeight = 14.sp),
                color = XText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = XType.meta.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = XMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(XBorder)) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(XText),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val stamp = XType.meta.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = .08.em)
                Text(formatMediaDuration(music.positionMs.toInt()), style = stamp, color = XMuted)
                Text(formatMediaDuration(track.durationMs.toInt()), style = stamp, color = XMuted)
            }
        }
    }
}

@Composable
private fun XConversationRow(
    conversation: CloudConversation,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    currentUserId: String = "",
    /** Who wrote the last message, for the canvas «артём: …» prefix on community rows. */
    senderName: String = "",
) {
    val direct = conversation.type == "direct"
    val displayTitle = if (direct) conversation.peerDisplayName.ifBlank { conversation.title } else conversation.title
    val displayAvatar = if (direct) conversation.peerAvatarUrl else conversation.avatarUrl
    val mineLast = conversation.lastMessageMine ||
        (conversation.lastMessageSenderId.isNotBlank() && conversation.lastMessageSenderId == currentUserId)
    val timeText = conversation.lastMessageAt.takeIf { it.isNotBlank() }?.let { formatListTime(it) }.orEmpty()
    val fallbackSubtitle = if (direct && conversation.peerNickname.isNotBlank()) "@${conversation.peerNickname}"
    else conversation.description.ifBlank {
        when (conversation.type) {
            "channel" -> tr("orionx_channel")
            "group" -> tr("orionx_group")
            else -> tr("direct_chat")
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .xShadow(XShape.card)
            .clip(XShape.card)
            .background(if (selected) XSurfaceRaised else XSurface)
            // The canvas row is a plain pointer target; long-press still opens the menu.
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // People keep the rounded square from the canvas; only the selected state differs.
        if (displayAvatar.startsWith("orionx-gradient:")) {
            XGradientAvatar(displayAvatar.removePrefix("orionx-gradient:").toIntOrNull() ?: 0, displayTitle, 44.dp, XShape.plate)
        } else if (displayAvatar.isNotBlank()) {
            RemoteImage(displayAvatar, Modifier.size(44.dp).clip(XShape.plate), ContentScale.Crop)
        } else {
            XLetterAvatar(displayTitle, 44.dp, XShape.plate)
        }
        Column(Modifier.weight(1f)) {
            Text(displayTitle, style = XType.rowTitle, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mineLast && conversation.lastMessagePreview.isNotBlank() && conversation.type != "channel") {
                    Icon(
                        if (conversation.lastMessageRead || conversation.lastMessageDelivered) Icons.Outlined.DoneAll else Icons.Outlined.Done,
                        null,
                        tint = if (conversation.lastMessageRead) XAccent else XMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                val preview = conversation.lastMessagePreview
                Text(
                    when {
                        preview.isBlank() -> fallbackSubtitle
                        // «артём: релиз в четверг» — groups only; a channel post has no
                        // visible author in the canvas row.
                        conversation.type == "group" && senderName.isNotBlank() && !mineLast ->
                            "$senderName: $preview"
                        else -> preview
                    },
                    style = XType.body,
                    color = XMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (timeText.isNotBlank()) {
                Text(timeText, style = XType.meta, color = XMuted, maxLines = 1)
            }
            when {
                selected -> Box(
                    Modifier.size(21.dp).clip(CircleShape).background(XAccent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                conversation.unreadCount > 0 -> Box(
                    Modifier
                        .defaultMinSize(minWidth = 21.dp, minHeight = 21.dp)
                        .clip(CircleShape)
                        .background(XText)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                        style = XType.meta.copy(fontWeight = FontWeight.ExtraBold),
                        color = XInverse,
                    )
                }
            }
        }
    }
}

@Composable
private fun XSearchTab(
    api: SupabaseApi,
    session: CloudSession,
    state: XSearchTabState,
    onOpen: (CloudConversation) -> Unit,
    onBack: () -> Unit,
) {
    var query by state.query
    var scope by state.scope
    var users by state.users
    var communities by state.communities
    var contacts by state.contacts
    var loadedOnce by state.loadedOnce
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // With an empty query the canvas shows «все контакты» — the people already in a direct chat.
    // Группы и каналы тоже наполняем сразу из подписок, чтобы вкладки не пустовали до поиска.
    LaunchedEffect(session.accessToken, session.userId) {
        // Already have them from a previous visit — refresh quietly, do not blank out.
        runCatching { withContext(Dispatchers.IO) { api.myConversations(session.accessToken, session.userId) } }
            .onSuccess { rows ->
                contacts = rows
                    .filter { it.type == "direct" && !it.archived }
                    .distinctBy { it.id }
                    .sortedBy { it.peerDisplayName.ifBlank { it.title }.lowercase() }
                if (communities.isEmpty()) {
                    communities = rows.filter { it.type == "group" || it.type == "channel" }.distinctBy { it.id }
                }
                loadedOnce = true
            }
    }

    LaunchedEffect(query, session.accessToken) {
        if (query.trim().isEmpty()) {
            users = emptyList(); communities = emptyList(); loading = false; error = null
            return@LaunchedEffect
        }
        val requested = query
        delay(300)
        loading = true
        // Профили и сообщества ищем параллельно, а не друг за другом.
        val profilesDeferred = coroutineScope.async(Dispatchers.IO) { runCatching { api.searchProfiles(session.accessToken, query) } }
        val channelsDeferred = coroutineScope.async(Dispatchers.IO) { runCatching { api.searchChannels(session.accessToken, query) } }
        val profiles = profilesDeferred.await()
        val channels = channelsDeferred.await()
        // Медленный ответ по старому запросу не должен затирать результат нового.
        if (query != requested) return@LaunchedEffect
        profiles.onSuccess { found ->
            users = found.filterNot { user -> user.id == session.userId }
        }
        channels.onSuccess { found ->
            communities = found
        }
        error = when {
            profiles.isFailure -> localizedNetworkError(profiles.exceptionOrNull()!!)
            channels.isFailure -> localizedNetworkError(channels.exceptionOrNull()!!)
            else -> null
        }
        loading = false
    }

    val searching = query.isNotBlank()
    val channels = remember(communities) { communities.filter { it.type == "channel" } }
    val groups = remember(communities) { communities.filter { it.type == "group" } }
    val filteredContacts = remember(contacts, query) {
        val needle = query.trim().removePrefix("@").lowercase()
        if (needle.isBlank()) contacts
        else contacts.filter {
            it.peerDisplayName.lowercase().contains(needle) ||
                it.peerNickname.lowercase().contains(needle) ||
                it.title.lowercase().contains(needle)
        }
    }
    val resultCount = when (scope) {
        1 -> channels.size
        2 -> groups.size
        else -> if (searching) users.size else filteredContacts.size
    }

    fun openDirect(user: CloudProfile) {
        coroutineScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.createDirect(session.accessToken, user.id) } }
                .onSuccess(onOpen)
                .onFailure { error = localizedNetworkError(it) }
        }
    }

    fun joinCommunity(community: CloudConversation) {
        coroutineScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.joinChannel(session.accessToken, community.id); community } }
                .onSuccess(onOpen)
                .onFailure { error = localizedNetworkError(it) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        XScreenHeader(title = tr("who_search"), onBack = onBack)
        XSearchPill(
            value = query,
            onValueChange = { query = it },
            placeholder = tr("search_hint"),
            modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(tr("people"), tr("channels"), tr("groups")).forEachIndexed { index, label ->
                XChip(label = label, selected = index == scope, onClick = { scope = index })
            }
        }
        // `font:800 27px;color:var(--ghost)` — the oversized, barely-there result counter.
        Text(
            if (searching) "${tr("found")} $resultCount" else xSearchScopeEmptyLabel(scope),
            style = XType.screenTitle.copy(fontSize = 27.sp, letterSpacing = (-.025).em),
            color = XGhost,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 6.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = XAccent)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    when (scope) {
                        1 -> items(channels, key = { it.id }) { XCommunitySearchRow(it) { joinCommunity(it) } }
                        2 -> items(groups, key = { it.id }) { XCommunitySearchRow(it) { joinCommunity(it) } }
                        else -> if (searching) {
                            items(users, key = { it.id }) { XUserSearchRow(it) { openDirect(it) } }
                        } else {
                            items(filteredContacts, key = { it.id }) { conversation ->
                                XContactSearchRow(conversation) { onOpen(conversation) }
                            }
                        }
                    }
                    if (resultCount == 0 && error == null) {
                        item {
                            XInlineMessage(
                                if (searching) tr("nothing_found") else tr("search_hint"),
                                error = false,
                            )
                        }
                    }
                    error?.let { item { XInlineMessage(it, true) } }
                }
            }
        }
    }
}

private fun xSearchScopeEmptyLabel(scope: Int): String = when (scope) {
    1 -> "все каналы"
    2 -> "все группы"
    else -> "все контакты"
}

/** `height:56px;border-radius:999px;background:var(--sf)` with a leading search glyph. */
@Composable
internal fun XSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(XSurface)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(Icons.Outlined.Search, null, tint = XMuted, modifier = Modifier.size(21.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = XType.bodyStrong.copy(fontSize = 14.sp), color = XMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = XType.bodyStrong.copy(fontSize = 14.sp, color = XText),
                cursorBrush = SolidColor(XAccent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Outlined.Cancel,
                tr("cancel"),
                tint = XMuted,
                modifier = Modifier.size(19.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

/** The search result row: `border-radius:24px;padding:12px 14px` with a CTA chip on the right. */
@Composable
private fun XResultRow(
    title: String,
    subtitle: String,
    cta: String,
    avatar: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .xShadow(XShape.card)
            .clip(XShape.card)
            .background(XSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        avatar()
        Column(Modifier.weight(1f)) {
            Text(title, style = XType.rowTitle, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, style = XType.meta.copy(fontSize = 12.sp), color = XMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.clip(CircleShape).background(XSurfaceRaised).padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(cta.uppercase(), style = XType.pillSmall, color = XText, maxLines = 1)
        }
    }
}

@Composable
private fun XUserSearchRow(user: CloudProfile, onClick: () -> Unit) {
    XResultRow(
        title = user.displayName.ifBlank { user.nickname },
        subtitle = "@${user.nickname}",
        cta = tr("chat"),
        avatar = { XAvatar(user, 44.dp) },
        onClick = onClick,
    )
}

@Composable
private fun XContactSearchRow(conversation: CloudConversation, onClick: () -> Unit) {
    val displayName = conversation.peerDisplayName.ifBlank { conversation.title.ifBlank { tr("direct_chat") } }
    XResultRow(
        title = displayName,
        subtitle = conversation.peerNickname.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: conversation.lastMessagePreview.ifBlank { tr("direct_chat") },
        cta = tr("chat"),
        avatar = {
            if (conversation.peerAvatarUrl.startsWith("orionx-gradient:")) {
                XGradientAvatar(conversation.peerAvatarUrl.removePrefix("orionx-gradient:").toIntOrNull() ?: 0, displayName, 44.dp)
            } else if (conversation.peerAvatarUrl.isNotBlank()) {
                RemoteImage(conversation.peerAvatarUrl, Modifier.size(44.dp).clip(CircleShape), ContentScale.Crop)
            } else {
                XLetterAvatar(displayName, 44.dp)
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun XCommunitySearchRow(community: CloudConversation, onClick: () -> Unit) {
    XResultRow(
        title = community.title,
        subtitle = community.username.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: community.description.ifBlank {
                if (community.type == "channel") tr("orionx_channel") else tr("orionx_group")
            },
        cta = tr("join"),
        avatar = {
            if (community.avatarUrl.isNotBlank()) {
                RemoteImage(community.avatarUrl, Modifier.size(44.dp).clip(XShape.plate), ContentScale.Crop)
            } else {
                XLetterAvatar(community.title, 44.dp, shape = XShape.plate)
            }
        },
        onClick = onClick,
    )
}

/** Шесть состояний геро-карточки на артборде «обновление». */
private enum class XUpdateStage { IDLE, CHECKING, FOUND, DOWNLOADING, READY, LATEST }

/** Иконка строки «что нового» подбирается по смыслу — как bolt/lock/bug_report в макете. */
private fun xUpdateNoteIcon(line: String): ImageVector {
    val l = line.lowercase()
    return when {
        Regex("быстр|скорост|производит|плавн|faster|speed|performance").containsMatchIn(l) -> Icons.Outlined.Bolt
        Regex("шифр|безопас|пароль|приват|защит|secur|encrypt|privac|password").containsMatchIn(l) -> Icons.Outlined.Lock
        Regex("исправ|почин|фикс|зависа|ошиб|баг|вылет|fix|bug|crash").containsMatchIn(l) -> Icons.Outlined.BugReport
        else -> Icons.Outlined.AutoAwesome
    }
}

@Composable
private fun XUpdatesTab(api: SupabaseApi, session: CloudSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The published release lives in the app_releases table (same source the launch
    // update dialog uses). The old app_updates table is legacy and always empty now,
    // which is why this screen used to show "no updates" while the popup showed one.
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var loading by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf(false) }

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var apkBytes by remember { mutableStateOf(0L) }
    var apkFile by remember { mutableStateOf<java.io.File?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Экран читал только Supabase, а всплывающее окно при запуске — оба канала. Из-за
    // этого релиз, выложенный на GitHub, всплывашка предлагала, а экран продолжал
    // говорить «установлена последняя версия». Берём тот же набор источников, но
    // ошибку Supabase не проглатываем — иначе офлайн выглядел бы как «всё актуально».
    suspend fun refresh(force: Boolean = false): Boolean = runCatching {
        val fromGithub = withContext(Dispatchers.IO) { fetchLatestGithubRelease(force) }?.let {
            AppRelease(
                id = "github-${it.version}",
                version = it.version,
                versionCode = 0,
                description = it.description,
                apkUrl = it.apkUrl,
                imageUrl = null,
                createdAt = "",
            )
        }
        val fromSupabase = withContext(Dispatchers.IO) { api.latestRelease(session.accessToken) }
        when {
            fromGithub == null -> fromSupabase
            fromSupabase == null -> fromGithub
            isNewerVersion(fromSupabase.version, fromGithub.version) -> fromSupabase
            else -> fromGithub
        }
    }.map { release = it; true }.getOrDefault(false)

    LaunchedEffect(session.accessToken) {
        checkError = !refresh()
        loading = false
    }

    val current = BuildConfig.VERSION_CODE
    val r = release
    // У релиза из GitHub нет versionCode, поэтому сравниваем строки версий — так же,
    // как это делает всплывающее окно обновления.
    val hasUpdate = r != null && isNewerVersion(r.version, BuildConfig.VERSION_NAME)

    // Canvas «обновление»: hero card, release notes, then a card of three rows.
    // `padding:4px 18px 130px;gap:12px`.
    val autoWifiKey = "updates_auto_wifi"
    val prefs = remember { context.getSharedPreferences("orionx_updates", Context.MODE_PRIVATE) }
    var autoWifi by remember { mutableStateOf(prefs.getBoolean(autoWifiKey, false)) }
    var lastCheck by remember { mutableStateOf(prefs.getLong("updates_last_check", 0L)) }

    fun markChecked() {
        val now = System.currentTimeMillis()
        lastCheck = now
        prefs.edit().putLong("updates_last_check", now).apply()
    }

    LaunchedEffect(loading, checkError) {
        if (!loading && !checkError) markChecked()
    }

    val allowInstallText = tr("updates_allow_install")
    val downloadFailedText = tr("updates_download_failed")

    fun startDownload() {
        val rel = release ?: return
        downloading = true
        errorText = null
        progress = 0f
        scope.launch {
            val res = runCatching {
                val file = downloadApk(context, rel.apkUrl, rel.version, onTotal = { apkBytes = it }) { progress = it }
                apkFile = file
                installApk(context, file)
            }
            downloading = false
            res.onFailure { e ->
                // Файл уже на диске — выбрасывать его нельзя, если система всего лишь просит
                // разрешение на установку: иначе после выдачи права APK качается заново.
                if (e !is InstallPermissionRequired) apkFile = null
                errorText = if (e is InstallPermissionRequired) allowInstallText else e.message ?: downloadFailedText
            }
        }
    }

    fun runCheck() {
        checking = true
        checkError = false
        scope.launch {
            // Ручная проверка обходит получасовой кэш GitHub API.
            checkError = !refresh(force = true)
            checking = false
            if (!checkError) markChecked()
        }
    }

    // «автообновление по wi-fi»: on an unmetered network the download starts by itself as
    // soon as a newer build is found. The install still goes through the system prompt.
    LaunchedEffect(hasUpdate, autoWifi) {
        if (!hasUpdate || !autoWifi || downloading || apkFile != null) return@LaunchedEffect
        if (!xOnUnmeteredNetwork(context)) return@LaunchedEffect
        startDownload()
    }

    val stage = when {
        downloading -> XUpdateStage.DOWNLOADING
        checking || loading -> XUpdateStage.CHECKING
        apkFile != null -> XUpdateStage.READY
        hasUpdate -> XUpdateStage.FOUND
        lastCheck > 0L && !checkError -> XUpdateStage.LATEST
        else -> XUpdateStage.IDLE
    }
    val newVersion = r?.version.orEmpty()
    val busy = stage == XUpdateStage.CHECKING || stage == XUpdateStage.DOWNLOADING

    val heroIcon = when (stage) {
        XUpdateStage.IDLE -> Icons.Outlined.SystemUpdateAlt
        XUpdateStage.CHECKING -> Icons.Outlined.WifiTethering
        XUpdateStage.FOUND -> Icons.Outlined.CloudDownload
        XUpdateStage.DOWNLOADING -> Icons.Outlined.Downloading
        XUpdateStage.READY -> Icons.Outlined.CheckCircle
        XUpdateStage.LATEST -> Icons.Outlined.Verified
    }
    val heroTitle = when (stage) {
        XUpdateStage.IDLE -> "orionx ${BuildConfig.VERSION_NAME}"
        XUpdateStage.CHECKING -> tr("updates_title_checking")
        XUpdateStage.FOUND -> tr("updates_title_found", "version" to newVersion)
        XUpdateStage.DOWNLOADING -> tr("updates_title_downloading", "version" to newVersion)
        XUpdateStage.READY -> tr("updates_title_ready")
        XUpdateStage.LATEST -> tr("updates_title_latest")
    }
    val heroSub = when (stage) {
        XUpdateStage.IDLE -> tr("updates_build_line", "build" to "$current", "channel" to "stable")
        XUpdateStage.CHECKING -> tr("updates_sub_checking")
        XUpdateStage.FOUND -> tr("updates_sub_found")
        XUpdateStage.DOWNLOADING -> tr("updates_sub_downloading")
        XUpdateStage.READY -> tr("updates_sub_ready")
        XUpdateStage.LATEST -> tr("updates_sub_latest", "version" to BuildConfig.VERSION_NAME, "build" to "$current")
    }
    val heroButton = when (stage) {
        XUpdateStage.IDLE -> tr("updates_check")
        XUpdateStage.CHECKING -> tr("updates_checking")
        XUpdateStage.FOUND -> tr("updates_download_install")
        XUpdateStage.DOWNLOADING -> tr("updates_btn_downloading")
        XUpdateStage.READY -> tr("updates_btn_install")
        XUpdateStage.LATEST -> tr("updates_btn_check_again")
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                // `border-radius:28px;padding:28px 20px 22px`
                Column(
                    Modifier
                        .fillMaxWidth()
                        .xShadow(RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(XSurface)
                        .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // `width:78px;height:78px` on --sf2, with a `2.5px` ring spinning while busy.
                    val angle = if (busy) {
                        val spin = rememberInfiniteTransition(label = "upd-spin")
                        val a by spin.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                            label = "upd-angle",
                        )
                        a
                    } else {
                        0f
                    }
                    val ringTrack = XText.copy(alpha = .12f)
                    val ringHead = XText
                    Box(
                        Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(XSurfaceRaised)
                            .drawBehind {
                                if (!busy) return@drawBehind
                                val stroke = 2.5.dp.toPx()
                                val inset = stroke / 2f
                                drawCircle(
                                    ringTrack,
                                    radius = (size.minDimension - stroke) / 2f,
                                    style = Stroke(stroke),
                                )
                                drawArc(
                                    color = ringHead,
                                    startAngle = angle - 90f,
                                    sweepAngle = 96f,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = Size(size.width - stroke, size.height - stroke),
                                    style = Stroke(stroke, cap = StrokeCap.Round),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(heroIcon, null, tint = XText, modifier = Modifier.size(33.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        heroTitle,
                        style = XType.rowTitle.copy(fontSize = 21.sp, lineHeight = 25.2.sp, letterSpacing = (-.02).em),
                        color = XText,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        heroSub,
                        style = XType.meta.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.8.sp),
                        color = XMuted2,
                        textAlign = TextAlign.Center,
                    )
                    if (stage == XUpdateStage.DOWNLOADING) {
                        // `height:6px;border-radius:3px` on --hairB with a --txt fill, then
                        // `загружено N%` / размер в строке под ней.
                        Spacer(Modifier.height(18.dp))
                        val shown by animateFloatAsState(progress, tween(280, easing = LinearEasing), label = "upd-bar")
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(XText.copy(alpha = .12f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(shown.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(XText),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                tr("updates_progress", "percent" to "${(progress * 100).toInt()}"),
                                style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                color = XMuted2,
                            )
                            Text(
                                if (apkBytes > 0) xUpdateSizeLabel(apkBytes) else newVersion,
                                style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                color = XMuted2,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    // `height:52px;border-radius:999px` — one action, whichever the state calls for.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(CircleShape)
                            .background(if (busy) XSurfaceRaised else XText)
                            .xPressable(pressedScale = .97f, enabled = !busy) {
                                when (stage) {
                                    XUpdateStage.FOUND -> startDownload()
                                    XUpdateStage.READY -> apkFile?.let { file ->
                                        runCatching { installApk(context, file) }.onFailure { e ->
                                            errorText = if (e is InstallPermissionRequired) allowInstallText
                                            else e.message ?: downloadFailedText
                                        }
                                    }
                                    else -> runCheck()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            heroButton.uppercase(),
                            style = XType.sectionLabel.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = .14.em,
                            ),
                            color = if (busy) XMuted2 else XInverse,
                            maxLines = 1,
                        )
                    }
                }
            }

            // В макете «что нового» стоит сразу под геро-карточкой, а не в самом низу.
            if (hasUpdate && r != null) {
                item(key = "notes") {
                    // `border-radius:28px;padding:20px`
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .xShadow(RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .background(XSurface)
                            .padding(20.dp),
                    ) {
                        Text(
                            tr("updates_whats_new_v", "version" to r.version).uppercase(),
                            style = XType.sectionLabel.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = .16.em,
                            ),
                            color = XMuted2,
                        )
                        Spacer(Modifier.height(14.dp))
                        val notes = remember(r.description, r.version) {
                            r.description.split('\n')
                                .map { it.trim().trimStart('•', '-', '–', '*', ' ').trim() }
                                .filter { it.isNotBlank() }
                                .ifEmpty { listOf(r.version) }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            notes.forEach { line ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                    Icon(
                                        xUpdateNoteIcon(line),
                                        null,
                                        tint = XText,
                                        modifier = Modifier.padding(top = 1.dp).size(17.dp),
                                    )
                                    Text(
                                        line,
                                        style = XType.body.copy(
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = 18.1.sp,
                                        ),
                                        color = XText,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "facts") {
                // `border-radius:28px;padding:6px 18px` — rows at `gap:14;padding:15px 0`.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .xShadow(RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(XSurface)
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                ) {
                    XUpdateFactRow(Icons.Outlined.Smartphone, tr("updates_installed")) {
                        Text(
                            BuildConfig.VERSION_NAME,
                            style = XType.meta.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = XMuted2,
                        )
                    }
                    XUpdateFactRow(Icons.Outlined.Schedule, tr("updates_last_check")) {
                        Text(
                            if (lastCheck <= 0L) tr("updates_never_checked") else xUpdateCheckedAt(lastCheck),
                            style = XType.meta.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = XMuted2,
                        )
                    }
                    XUpdateFactRow(Icons.Outlined.Autorenew, tr("updates_auto_wifi"), divider = false) {
                        XCanvasSwitch(checked = autoWifi) {
                            autoWifi = !autoWifi
                            prefs.edit().putBoolean(autoWifiKey, autoWifi).apply()
                        }
                    }
                }
            }

            if (checkError) {
                item(key = "check-error") { XInlineMessage(tr("updates_check_failed"), error = true) }
            }
            errorText?.let { message ->
                item(key = "download-error") { XInlineMessage(message, error = true) }
            }
        }
    }
}

/** «48,6 мб» — размер загрузки в строке под прогрессом. */
private fun xUpdateSizeLabel(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    val value = String.format(java.util.Locale.US, if (mb >= 100) "%.0f" else "%.1f", mb)
    return tr("updates_size_mb", "size" to value.replace('.', ','))
}

/** `display:flex;gap:14px;padding:15px 0` with a hairline under every row but the last. */
@Composable
private fun XUpdateFactRow(
    icon: ImageVector,
    title: String,
    divider: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, tint = XText, modifier = Modifier.size(20.dp))
            Text(
                title,
                style = XType.rowTitle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                color = XText,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing()
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(XBorder))
    }
}

/** `width:40px;height:22px;border-radius:999px;padding:2px` — the canvas switch. */
@Composable
private fun XCanvasSwitch(checked: Boolean, onToggle: () -> Unit) {
    val track by animateColorAsState(if (checked) XText else XSurfaceRaised, tween(200), label = "sw-track")
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, tween(220, easing = FastOutSlowInEasing), label = "sw-knob")
    Box(
        Modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(CircleShape)
            .background(track)
            .xPressable(pressedScale = .94f, onClick = onToggle)
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) XInverse else XMuted),
        )
    }
}

/** True on Wi-Fi or another unmetered link — what «по wi-fi» means in practice. */
private fun xOnUnmeteredNetwork(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}.getOrDefault(false)

private fun xUpdateCheckedAt(millis: Long): String = runCatching {
    java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
}.getOrDefault("")

@Composable
private fun XCurrentVersionCard() {
    XCard(shape = XShape.card) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            XIconPlate(Icons.Outlined.SystemUpdateAlt)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                XCardLabel(tr("updates_current_version"))
                Spacer(Modifier.height(6.dp))
                Text("OrionX ${BuildConfig.VERSION_NAME}", style = XType.rowTitle.copy(fontSize = 16.sp), color = XText)
            }
        }
    }
}

@Composable
private fun XUpdateAvailableCard(
    release: AppRelease,
    downloading: Boolean,
    progress: Float,
    errorText: String?,
    onDownload: () -> Unit,
) {
    XCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XIconPlate(Icons.Outlined.SystemUpdateAlt)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    XCardLabel(tr("updates_available"))
                    Spacer(Modifier.height(6.dp))
                    Text(release.version, style = XType.screenTitle.copy(fontSize = 20.sp), color = XText)
                }
            }
            if (release.description.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(tr("updates_whats_new"), color = XViolet, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(release.description, color = XMuted, fontSize = 13.sp, lineHeight = 19.sp)
            }

            if (errorText != null) {
                Spacer(Modifier.height(14.dp))
                XInlineMessage(errorText, error = true)
            }

            Spacer(Modifier.height(16.dp))
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = XViolet,
                    trackColor = XBorder,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("updates_downloading", "percent" to (progress * 100).toInt().toString()),
                    color = XMuted,
                    fontSize = 12.sp,
                )
            } else {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = XPurple),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Outlined.Download, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(tr("updates_download_install"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun XSettingsTab(
    api: SupabaseApi,
    session: CloudSession,
    profile: CloudProfile,
    onCategory: (SettingsCategory) -> Unit,
    onBack: () -> Unit,
) {
    val categories = SettingsCategory.entries.filter { category ->
        category != SettingsCategory.ACCOUNT &&
            (category != SettingsCategory.ADMIN ||
                (profile.role.equals("admin", true) && profile.email.equals(SupabaseConfig.ADMIN_EMAIL, true)))
    }
    Column(Modifier.fillMaxSize()) {
        XScreenHeader(title = tr("settings"), onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                XCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onCategory(SettingsCategory.ACCOUNT) }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        XAvatar(profile, 52.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                profile.displayName.ifBlank { profile.nickname },
                                style = XType.rowTitle.copy(fontSize = 16.sp),
                                color = XText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("@${profile.nickname}", style = XType.meta.copy(fontSize = 12.sp), color = XMuted, maxLines = 1)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = XMuted, modifier = Modifier.size(19.dp))
                    }
                }
            }
            item {
                XCard {
                    categories.forEachIndexed { index, category ->
                        XCardRow(
                            icon = category.icon,
                            title = xSettingText(category.titleKey),
                            divider = index != categories.lastIndex,
                            onClick = { onCategory(category) },
                        )
                    }
                }
            }
            if (isOrionXAdmin(profile.displayName)) {
                item { XAdminReleaseEntry(api, session, profile.displayName) }
            }
        }
    }
}

/**
 * One row inside a card: `display:flex;gap:14px;padding:16px 0` with a hairline under every row
 * but the last. `value` renders on the right instead of the chevron when a row shows state.
 */
@Composable
internal fun XCardRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    tint: Color = XText,
    divider: Boolean = true,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier.padding(horizontal = 18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            Text(
                title,
                style = XType.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = tint,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                trailing != null -> trailing()
                value != null -> Text(value, style = XType.meta.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = XMuted, maxLines = 1)
                onClick != null -> Icon(Icons.Outlined.ChevronRight, null, tint = XMuted, modifier = Modifier.size(19.dp))
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(XBorder))
    }
}

@Composable
private fun XSettingsCategoryScreen(
    api: SupabaseApi,
    session: CloudSession,
    profile: CloudProfile,
    category: SettingsCategory,
    onProfileChanged: (CloudProfile) -> Unit,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit,
    motion: XMotionSettings,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        XBackHeader(xSettingText(category.titleKey), onBack)
        when (category) {
            SettingsCategory.ACCOUNT -> XAccountSettings(api, session, profile, onOpenProfile, onLogout)
            SettingsCategory.APPEARANCE -> XAppearanceSettings(api, session, motion)
            SettingsCategory.NOTIFICATIONS -> XNotificationSettings(api, session)
            SettingsCategory.PRIVACY -> XPrivacySettings(api, session)
            SettingsCategory.DATA -> XDataSettings(api, session)
            SettingsCategory.DEVICES -> XDevicesSettings(api, session)
            SettingsCategory.UPDATES -> XUpdatesTab(api, session)
            SettingsCategory.HELP -> XHelpSettings()
            SettingsCategory.ADMIN -> XAdminSettings(api, session)
        }
    }
}

@Composable
private fun XAccountSettings(
    api: SupabaseApi,
    session: CloudSession,
    profile: CloudProfile,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    var passwordDialog by remember { mutableStateOf(false) }
    var mfaInfo by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { XSettingsRow(Icons.Outlined.Person, "Профиль", "Аватар, имя, ник и информация") { onOpenProfile() } }
        item { XSettingsRow(Icons.Outlined.Email, "Электронная почта", profile.email) { onOpenProfile() } }
        item { XSettingsRow(Icons.Outlined.Phone, "Номер телефона", profile.phone.ifBlank { "Не добавлен" }) { onOpenProfile() } }
        item { XSettingsRow(Icons.Outlined.Password, "Пароль", "Изменить пароль аккаунта") { passwordDialog = true } }
        item { XSettingsRow(Icons.Outlined.VerifiedUser, "Двухэтапная проверка", "Защитите вход дополнительным кодом") { mfaInfo = true } }
        item { XDangerButton("Выйти из аккаунта", onLogout) }
    }

    if (passwordDialog) {
        XPasswordDialog(api = api, session = session, onDismiss = { passwordDialog = false })
    }
    if (mfaInfo) {
        XInfoDialog(
            title = "Двухэтапная проверка",
            body = "Интерфейс подготовлен, но для включения TOTP необходимо развернуть и проверить MFA-поток Supabase. Функция не включается автоматически, чтобы не заблокировать доступ к аккаунту.",
            onDismiss = { mfaInfo = false },
        )
    }
}

@Composable
private fun XAppearanceSettings(api: SupabaseApi, session: CloudSession, motion: XMotionSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runtime = LocalXRuntimeSettings.current
    val updateRuntime = LocalXRuntimeSettingsUpdater.current
    var status by remember { mutableStateOf<String?>(null) }
    var languageDialog by remember { mutableStateOf(false) }
    val currentLanguage = LocalOrionLanguage.current
    val setLanguage = LocalOrionLanguageSetter.current

    fun save(patch: org.json.JSONObject) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.updateUserSettings(session.accessToken, session.userId, patch) } }
                .onFailure { status = localizedNetworkError(it) }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            XToggleRow(
                XToggleSpec(Icons.Outlined.DarkMode, xSettingText("dark_theme"), xSettingText("dark_theme_sub"), runtime.theme != "light") { enabled ->
                    val next = runtime.copy(theme = if (enabled) "dark" else "light")
                    updateRuntime(next)
                    save(org.json.JSONObject().apply { put("theme", next.theme) })
                },
            )
        }
        item {
            XToggleRow(
                XToggleSpec(Icons.Outlined.ViewCompact, xSettingText("compact_mode"), xSettingText("compact_mode_sub"), runtime.compactMode) { enabled ->
                    val next = runtime.copy(compactMode = enabled)
                    updateRuntime(next)
                    save(org.json.JSONObject().apply { put("compact_mode", enabled) })
                },
            )
        }
        item {
            XToggleRow(
                XToggleSpec(Icons.Outlined.Animation, xSettingText("reduce_motion"), xSettingText("reduce_motion_sub"), runtime.reduceMotion) { enabled ->
                    val next = runtime.copy(reduceMotion = enabled)
                    updateRuntime(next)
                    save(org.json.JSONObject().apply { put("reduce_motion", enabled) })
                },
            )
        }
        // The canvas' «анимация» block: three modes plus a 0.5–2× speed. Pointless
        // while motion is reduced, so it hides with it.
        if (!runtime.reduceMotion) {
            item { XScreenTransitionRow(motion) }
        }
        item {
            XFontScaleRow(
                value = runtime.fontScale,
                onValueChange = { value -> updateRuntime(runtime.copy(fontScale = value)) },
                onValueCommitted = { value ->
                    updateRuntime(runtime.copy(fontScale = value))
                    save(org.json.JSONObject().apply { put("font_scale", value.toDouble()) })
                },
            )
        }
        item {
            XSettingsRow(Icons.Outlined.Language, xSettingText("interface_language"), currentLanguage.nativeName) { languageDialog = true }
        }
        status?.let { item { XInlineMessage(it, error = true) } }
    }

    if (languageDialog) {
        XLanguageDialog(
            current = currentLanguage,
            onDismiss = { languageDialog = false },
            onSelect = { selected ->
                setLanguage(selected)
                languageDialog = false
                updateRuntime(runtime.copy(language = selected.code))
                save(org.json.JSONObject().apply { put("language", selected.code) })
            },
        )
    }
}

@Composable
private fun XLanguageDialog(
    current: OrionLanguage,
    onDismiss: () -> Unit,
    onSelect: (OrionLanguage) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XSurface,
        title = { Text(xSettingText("interface_language"), color = XText, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OrionLanguage.entries.forEach { language ->
                    Surface(
                        color = if (language == current) XPurple.copy(alpha = .24f) else XSurfaceRaised,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (language == current) XViolet else XBorder),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(language) },
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(language.nativeName, color = XText, modifier = Modifier.weight(1f))
                            if (language == current) Icon(Icons.Outlined.Check, null, tint = XCyan)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(xSettingText("close"), color = XMuted) } },
    )
}

@Composable
private fun XNotificationSettings(api: SupabaseApi, session: CloudSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by rememberXPreference(context, "notify_messages", true)
    var groups by rememberXPreference(context, "notify_groups", true)
    var channels by rememberXPreference(context, "notify_channels", true)
    var calls by rememberXPreference(context, "notify_calls", true)
    var preview by rememberXPreference(context, "notify_preview", true)
    var permissionGranted by remember { mutableStateOf(OrionNotifications.permissionGranted(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    val notificationAllowedText = xSettingText("notification_allowed")
    val notificationBlockedText = xSettingText("notification_blocked")

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted || OrionNotifications.permissionGranted(context)
        if (permissionGranted) {
            OrionNotifications.ensureChannels(context)
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { PushTokenSync.syncSession(context, api, session) } }
                    .onSuccess { status = notificationAllowedText }
                    .onFailure { status = localizedNetworkError(it) }
            }
        } else {
            status = notificationBlockedText
        }
    }

    LaunchedEffect(session.userId) {
        OrionNotifications.ensureChannels(context)
        permissionGranted = OrionNotifications.permissionGranted(context)
        if (permissionGranted) {
            runCatching { withContext(Dispatchers.IO) { PushTokenSync.syncSession(context, api, session) } }
                .onFailure { status = localizedNetworkError(it) }
        }
    }

    XToggleList(
        listOf(
            XToggleSpec(Icons.Outlined.ChatBubble, xSettingText("personal_messages"), xSettingText("personal_messages_sub"), messages) { messages = it },
            XToggleSpec(Icons.Outlined.Groups, xSettingText("groups"), xSettingText("groups_sub"), groups) { groups = it },
            XToggleSpec(Icons.Outlined.Campaign, xSettingText("channels_setting"), xSettingText("channels_setting_sub"), channels) { channels = it },
            XToggleSpec(Icons.Outlined.Call, xSettingText("calls"), xSettingText("calls_sub"), calls) { calls = it },
            XToggleSpec(Icons.Outlined.Visibility, xSettingText("notification_preview"), xSettingText("notification_preview_sub"), preview) { preview = it },
        ),
        footer = {
            XSettingsRow(
                Icons.Outlined.NotificationsActive,
                xSettingText("notification_permission"),
                if (permissionGranted) notificationAllowedText else notificationBlockedText,
            ) {
                if (Build.VERSION.SDK_INT >= 33 && !permissionGranted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    runCatching { context.startActivity(intent) }
                }
            }
            XSettingsRow(Icons.Outlined.CloudSync, xSettingText("push_token"), xSettingText("push_token_sub")) {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { PushTokenSync.syncSession(context, api, session) } }
                        .onSuccess { status = notificationAllowedText }
                        .onFailure { status = localizedNetworkError(it) }
                }
            }
            XSettingsRow(Icons.Outlined.NotificationAdd, xSettingText("test_notification"), xSettingText("test_notification_sub")) {
                if (!permissionGranted && Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    status = if (OrionNotifications.showTest(context)) notificationAllowedText else notificationBlockedText
                }
            }
            status?.let { XInlineMessage(it, error = !permissionGranted || it.contains("ошиб", true) || it.contains("error", true)) }
        },
    )
}

@Composable
private fun XPrivacySettings(api: SupabaseApi, session: CloudSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var blockedDialog by remember { mutableStateOf(false) }
    var passcodeInfo by remember { mutableStateOf(false) }
    var reactionDialog by remember { mutableStateOf(false) }
    var online by rememberXPreference(context, "privacy_online", true)
    var receipts by rememberXPreference(context, "privacy_receipts", true)
    var calls by rememberXPreference(context, "privacy_calls", true)
    var groups by rememberXPreference(context, "privacy_groups", true)
    var reactions by remember { mutableStateOf(CloudUserSettings().reactionEmojis) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.userId, session.accessToken) {
        runCatching { withContext(Dispatchers.IO) { api.userSettings(session.accessToken, session.userId) } }
            .onSuccess { remote ->
                online = remote.showOnline
                receipts = remote.readReceipts
                calls = remote.allowCalls != "nobody"
                groups = remote.allowGroupInvites != "nobody"
                reactions = normalizeReactionList(remote.reactionEmojis)
            }
            .onFailure { status = localizedNetworkError(it) }
    }

    fun save(patch: org.json.JSONObject) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.updateUserSettings(session.accessToken, session.userId, patch) } }
                .onFailure { status = localizedNetworkError(it) }
        }
    }

    XToggleList(
        listOf(
            XToggleSpec(Icons.Outlined.OnlinePrediction, "Статус в сети", "Разрешить видеть вашу активность", online) {
                online = it; save(org.json.JSONObject().apply { put("show_online", it) })
            },
            XToggleSpec(Icons.Outlined.DoneAll, "Отчёты о прочтении", "Одна/две серые и две синие галочки", receipts) {
                receipts = it; save(org.json.JSONObject().apply { put("read_receipts", it) })
            },
            XToggleSpec(Icons.Outlined.Call, "Кто может звонить", "Разрешить звонки пользователям", calls) {
                calls = it; save(org.json.JSONObject().apply { put("allow_calls", if (it) "everyone" else "nobody") })
            },
            XToggleSpec(Icons.Outlined.GroupAdd, "Добавление в группы", "Разрешить приглашения", groups) {
                groups = it; save(org.json.JSONObject().apply { put("allow_group_invites", if (it) "everyone" else "nobody") })
            },
        ),
        footer = {
            XSettingsRow(Icons.Outlined.AddReaction, "Быстрые реакции", reactions.joinToString("  ")) { reactionDialog = true }
            XSettingsRow(Icons.Outlined.Block, "Заблокированные пользователи", "Управление чёрным списком") { blockedDialog = true }
            XSettingsRow(Icons.Outlined.Key, "Код-пароль приложения", "Локальная защита OrionX") { passcodeInfo = true }
            status?.let { XInlineMessage(it, error = true) }
        },
    )
    if (reactionDialog) {
        XReactionEditorDialog(
            initial = reactions,
            onDismiss = { reactionDialog = false },
            onSave = { selected ->
                reactions = selected
                reactionDialog = false
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { api.updateReactionEmojis(session.accessToken, session.userId, selected) } }
                        .onFailure { status = localizedNetworkError(it) }
                }
            },
        )
    }
    if (blockedDialog) XBlockedUsersDialog(api = api, session = session, onDismiss = { blockedDialog = false })
    if (passcodeInfo) {
        XInfoDialog(
            title = "Код-пароль приложения",
            body = "Локальная блокировка будет добавлена после проверки биометрии и безопасного хранения ключа. Сейчас включать её небезопасно: незавершённый экран блокировки может закрыть доступ к приложению.",
            onDismiss = { passcodeInfo = false },
        )
    }
}

@Composable
private fun XReactionEditorDialog(
    initial: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val choices = remember { listOf("👍","❤️","😂","🔥","😍","🥰","😮","😢","😡","👏","🎉","🤔","✅","💜","🙏","🤝","👀","⚡","🚀","💯") }
    var selected by remember(initial) { mutableStateOf(initial.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XSurface,
        title = { Text("Быстрые реакции", color = XText, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Выберите до 12 реакций. Они будут использоваться в меню сообщений.", color = XMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(250.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    gridItems(choices) { emoji ->
                        val active = emoji in selected
                        Surface(
                            color = if (active) XPurple.copy(alpha = .30f) else XSurfaceRaised,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (active) XViolet else XBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.aspectRatio(1f).clickable {
                                selected = if (active) selected - emoji else if (selected.size < 12) selected + emoji else selected
                            },
                        ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.toList()) }, enabled = selected.isNotEmpty()) { Text("Сохранить", color = XViolet) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = XMuted) } },
    )
}

@Composable
private fun XDataSettings(api: SupabaseApi, session: CloudSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var photos by rememberXPreference(context, "auto_photos", true)
    var videos by rememberXPreference(context, "auto_videos", false)
    var files by rememberXPreference(context, "auto_files", false)
    var saver by rememberXPreference(context, "data_saver", false)
    XToggleList(
        listOf(
            XToggleSpec(Icons.Outlined.Image, "Автозагрузка фотографий", "Wi‑Fi и мобильная сеть", photos) { photos = it },
            XToggleSpec(Icons.Outlined.VideoFile, "Автозагрузка видео", "Только по Wi‑Fi", videos) { videos = it },
            XToggleSpec(Icons.Outlined.InsertDriveFile, "Автозагрузка файлов", "Документы и архивы", files) { files = it },
            XToggleSpec(Icons.Outlined.DataSaverOn, "Экономия трафика", "Снижает качество медиа", saver) { saver = it },
        ),
        footer = {
            XSettingsRow(Icons.Outlined.CleaningServices, "Очистить кэш", "Временные файлы и миниатюры") {
                scope.launch {
                    val cleared = withContext(Dispatchers.IO) {
                        runCatching {
                            context.cacheDir.deleteRecursively()
                            context.cacheDir.mkdirs()
                        }.isSuccess
                    }
                    status = if (cleared) "Кэш очищен" else "Не удалось очистить кэш"
                }
            }
            XSettingsRow(Icons.Outlined.Folder, "Путь сохранения", "Системная папка «Загрузки»") {
                status = "Медиа сохраняются через системный выбор папки Android"
            }
            status?.let { XInlineMessage(it, error = it.startsWith("Не удалось")) }
        },
    )
}

@Composable
private fun XDevicesSettings(api: SupabaseApi, session: CloudSession) {
    var devices by remember { mutableStateOf<List<CloudDevice>>(emptyList()) }
    var initialLoading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    val currentInstallationId = remember(context) { DeviceIdentity.id(context) }

    suspend fun fetchDevices() {
        refreshing = true
        runCatching { withContext(Dispatchers.IO) { api.userDevices(session.accessToken, session.userId) } }
            .onSuccess {
                devices = it
                status = null
            }
            .onFailure { status = localizedNetworkError(it) }
        refreshing = false
        initialLoading = false
    }

    LaunchedEffect(session.userId, session.accessToken) {
        while (true) {
            fetchDevices()
            delay(15_000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                XMiniTitle("Активные устройства")
                Spacer(Modifier.weight(1f))
                if (refreshing && !initialLoading) {
                    CircularProgressIndicator(color = XCyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (initialLoading && devices.isEmpty()) {
            item {
                XDeviceCard(
                    title = currentName.ifBlank { "Android" },
                    subtitle = "Текущее устройство · синхронизация…",
                    current = true,
                )
            }
        } else {
            val currentPresent = devices.any { it.installationId == currentInstallationId }
            if (!currentPresent) {
                item { XDeviceCard(currentName.ifBlank { "Android" }, "Текущее устройство · активен сейчас", true) }
            }
            items(devices, key = { it.id }) { device ->
                val current = device.installationId == currentInstallationId
                XDeviceCard(
                    title = device.deviceName.ifBlank { "Android" },
                    subtitle = buildString {
                        append(if (current) "Текущее устройство" else "Сеанс")
                        if (device.appVersion.isNotBlank()) append(" · ${device.appVersion}")
                        append(" · ${formatDeviceLastActive(device.lastActiveAt)}")
                    },
                    current = current,
                    onRemove = if (current) null else {
                        {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { api.removeDevice(session.accessToken, device.id) } }
                                    .onSuccess { status = "Устройство отключено"; fetchDevices() }
                                    .onFailure { status = localizedNetworkError(it) }
                            }
                        }
                    },
                )
            }
        }

        if (devices.any { it.installationId != currentInstallationId }) {
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val others = devices.filterNot { it.installationId == currentInstallationId }
                            val result = runCatching {
                                withContext(Dispatchers.IO) { others.forEach { api.removeDevice(session.accessToken, it.id) } }
                            }
                            result.onSuccess { status = "Другие сеансы завершены"; fetchDevices() }
                                .onFailure { status = localizedNetworkError(it) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, XRed.copy(alpha = .55f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = XRed),
                ) { Text("Завершить все другие сеансы", fontWeight = FontWeight.SemiBold) }
            }
        }

        status?.let {
            item { XInlineMessage(it, error = !(it.contains("отключено", true) || it.contains("завершены", true))) }
        }

        item {
            Text(
                "Список обновляется автоматически каждые 15 секунд. Текущее устройство регулярно подтверждает активность.",
                color = XMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun XAdminSettings(api: SupabaseApi, session: CloudSession) {
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var users by remember { mutableStateOf<List<CloudProfile>>(emptyList()) }
    var announcementTitle by remember { mutableStateOf("") }
    var announcementBody by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var changelog by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var mandatory by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.adminStats(session.accessToken) to api.allProfiles(session.accessToken)
                }
            }
            result.onSuccess { stats = it.first; users = it.second }
                .onFailure { status = localizedNetworkError(it) }
        }
    }
    LaunchedEffect(session.accessToken) { reload() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            stats?.let { value ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XStatCard("Пользователи", value.users.toString(), Modifier.weight(1f))
                    XStatCard("Чаты", value.conversations.toString(), Modifier.weight(1f))
                    XStatCard("Сообщения", value.messages.toString(), Modifier.weight(1f))
                }
            } ?: XLoadingCard()
        }
        item { XMiniTitle("Новое объявление") }
        item { XFormField("Заголовок", announcementTitle, { announcementTitle = it }, Icons.Outlined.Title) }
        item { XFormField("Текст", announcementBody, { announcementBody = it }, Icons.Outlined.Notes, singleLine = false) }
        item {
            XPrimaryButton("Опубликовать объявление", enabled = !busy && announcementTitle.isNotBlank() && announcementBody.isNotBlank()) {
                scope.launch {
                    busy = true
                    runCatching {
                        withContext(Dispatchers.IO) {
                            api.publishAnnouncement(session.accessToken, announcementTitle, announcementBody, session.userId)
                        }
                    }.onSuccess {
                        status = "Объявление опубликовано"
                        announcementTitle = ""; announcementBody = ""
                    }.onFailure { status = localizedNetworkError(it) }
                    busy = false
                }
            }
        }
        item { XMiniTitle("Обновление приложения") }
        item { XFormField("Версия", version, { version = it }, Icons.Outlined.NewReleases) }
        item { XFormField("Version code", versionCode, { versionCode = it.filter(Char::isDigit) }, Icons.Outlined.Numbers, KeyboardType.Number) }
        item { XFormField("Что изменилось", changelog, { changelog = it }, Icons.Outlined.Description, singleLine = false) }
        item { XFormField("Ссылка на APK", apkUrl, { apkUrl = it }, Icons.Outlined.Link, KeyboardType.Uri) }
        item {
            XToggleRow(XToggleSpec(Icons.Outlined.PriorityHigh, "Обязательное обновление", "Пользователь должен установить новую версию", mandatory) { mandatory = it })
        }
        item {
            XPrimaryButton("Опубликовать обновление", enabled = !busy && version.isNotBlank() && versionCode.toIntOrNull() != null && changelog.isNotBlank()) {
                scope.launch {
                    busy = true
                    runCatching {
                        withContext(Dispatchers.IO) {
                            api.publishUpdate(
                                session.accessToken,
                                version,
                                versionCode.toInt(),
                                changelog,
                                apkUrl.ifBlank { null },
                                mandatory,
                            )
                        }
                    }.onSuccess {
                        status = "Обновление опубликовано"
                        version = ""; versionCode = ""; changelog = ""; apkUrl = ""; mandatory = false
                    }.onFailure { status = localizedNetworkError(it) }
                    busy = false
                }
            }
        }
        status?.let { item { XInlineMessage(it, error = it.contains("ошиб", true) || it.contains("не удалось", true)) } }
        item { XMiniTitle("Пользователи") }
        items(users, key = { it.id }) { user ->
            Surface(color = XSurfaceSoft, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, XBorder)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    XAvatar(user, 42.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.displayName, color = XText, fontWeight = FontWeight.SemiBold)
                        Text("@${user.nickname} · ${user.role}", color = XMuted, fontSize = 11.sp)
                    }
                    if (!user.email.equals(SupabaseConfig.ADMIN_EMAIL, true)) {
                        Switch(
                            checked = user.blocked,
                            onCheckedChange = { blocked ->
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { api.setUserBlocked(session.accessToken, user.id, blocked) } }
                                        .onSuccess { status = if (blocked) "Пользователь заблокирован" else "Пользователь разблокирован"; reload() }
                                        .onFailure { status = localizedNetworkError(it) }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = XRed, uncheckedTrackColor = XSurfaceRaised),
                        )
                    } else {
                        XBadge("Владелец", XViolet)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun XStatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = XSurfaceSoft, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, XBorder), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = XText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(title, color = XMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun XHelpSettings() {
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            XSettingsRow(Icons.Outlined.SupportAgent, "Служба поддержки", "Связаться с командой OrionX") {
                dialog = "Служба поддержки" to "Для обращения укажите почту аккаунта, версию приложения и подробно опишите проблему. Канал поддержки можно подключить к support@orion-chat.ru после создания этого почтового адреса."
            }
        }
        item {
            XSettingsRow(Icons.Outlined.QuestionAnswer, "Частые вопросы", "Ответы и инструкции") {
                dialog = "Частые вопросы" to "Регистрация подтверждается кодом из письма. Oria находится первой в списке чатов. Профиль, почта, телефон, уведомления, приватность и устройства настраиваются в соответствующих разделах."
            }
        }
        item {
            XSettingsRow(Icons.Outlined.Description, "Условия использования", "Правила сервиса") {
                dialog = "Условия использования" to "Перед публичным выпуском добавьте утверждённый юридический текст условий. Приложение не подменяет его демонстрационным документом."
            }
        }
        item {
            XSettingsRow(Icons.Outlined.PrivacyTip, "Политика конфиденциальности", "Как OrionX обрабатывает данные") {
                dialog = "Политика конфиденциальности" to """OrionX обрабатывает данные аккаунта и профиля, сообщения, реакции, вложения, настройки, сведения об активных устройствах и технические идентификаторы, необходимые для работы сервиса.

Облачная база, авторизация, файлы и realtime-функции работают через Supabase; push-уведомления Android могут использовать Firebase Cloud Messaging. Письма подтверждения отправляются через подключённый SMTP-провайдер.

Сообщения и медиа могут храниться в облаке для синхронизации и доставки. OrionX не заявляет о сквозном шифровании содержимого до реализации и проверки отдельного E2EE-модуля. Передача данных выполняется через защищённые сетевые соединения.

Вы можете изменять профиль и настройки приватности, завершать другие сеансы и обращаться в поддержку по вопросам доступа, исправления или удаления данных. Полная версия политики включена в проект в файле PRIVACY_POLICY_RU.md.

Версия 1.0 · 28 июля 2026 г. Перед публичным коммерческим выпуском документ необходимо юридически проверить для стран распространения."""
            }
        }
        item {
            XContentCard(
                Icons.Outlined.Info,
                "OrionX ${BuildConfig.VERSION_NAME}",
                "Видеокружки, плавные переходы, профиль, сообщения, архив, устройства и Oria AI.",
            )
        }
    }
    dialog?.let { (title, body) -> XInfoDialog(title, body) { dialog = null } }
}

private data class XToggleSpec(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val checked: Boolean,
    val onChecked: (Boolean) -> Unit,
)

@Composable
private fun XToggleList(specs: List<XToggleSpec>, footer: @Composable ColumnScope.() -> Unit = {}) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(specs) { item -> XToggleRow(item) }
        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = footer) }
    }
}

@Composable
private fun XToggleRow(item: XToggleSpec) {
    Row(
        Modifier
            .fillMaxWidth()
            .xShadow(XShape.card)
            .clip(XShape.card)
            .background(XSurface)
            .clickable { item.onChecked(!item.checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        XIconPlate(item.icon)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = XType.rowTitle, color = XText, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(item.subtitle, style = XType.body, color = XMuted)
        }
        XSwitch(checked = item.checked, onCheckedChange = item.onChecked)
    }
}

/**
 * The canvas' «анимация» block: a speed slider from 0.5 to 2 in 0.1 steps, labelled
 * «медленно … мгновенно», and «тип перехода» as content-sized chips.
 */
@Composable
private fun XScreenTransitionRow(motion: XMotionSettings) {
    var draft by remember(motion.speed) { mutableFloatStateOf(motion.speed) }
    val rounded = kotlin.math.round(draft * 10f) / 10f
    Surface(
        color = XSurfaceSoft,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, XBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XIconPlate(Icons.Outlined.Animation)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(xSettingText("anim_title"), color = XText, fontWeight = FontWeight.SemiBold)
                    Text(
                        xSettingText(motion.mode.labelKey),
                        color = XMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // `скорость` ←→ `обычно · 1.2×`
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    xSettingText("anim_speed").uppercase(),
                    style = XType.sectionLabel.copy(fontSize = 9.5.sp, letterSpacing = .14.em),
                    color = XMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${xSettingText(motion.speedWordKey)} · ${xFormatSpeed(rounded)}×",
                    style = XType.rowTitle.copy(fontSize = 11.sp),
                    color = XText,
                )
            }
            Slider(
                value = draft,
                // `min="0.5" max="2" step="0.1"` — 15 intervals, so 14 stops between them.
                onValueChange = { raw -> draft = (kotlin.math.round(raw * 10f) / 10f).coerceIn(.5f, 2f) },
                onValueChangeFinished = { motion.chooseSpeed(draft) },
                valueRange = .5f..2f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = XText,
                    activeTrackColor = XText,
                    inactiveTrackColor = XSurfaceRaised,
                    activeTickColor = XInverse,
                    inactiveTickColor = XMuted,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val edge = XType.meta.copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
                Text(xSettingText("anim_speed_slow"), style = edge, color = XMuted2)
                Text(xSettingText("anim_speed_instant"), style = edge, color = XMuted2)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                xSettingText("anim_kind").uppercase(),
                style = XType.sectionLabel.copy(fontSize = 9.5.sp, letterSpacing = .14.em),
                color = XMuted,
            )
            Spacer(Modifier.height(9.dp))
            // `min-height:34px;padding:0 14px;border-radius:999px;gap:7px` — sized to
            // the label, not stretched, and the label stays lower case.
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                XScreenTransition.entries.forEach { option ->
                    val active = option == motion.mode
                    val background by animateColorAsState(if (active) XText else XSurfaceRaised, tween(300), label = "anim-chip-bg")
                    val foreground by animateColorAsState(if (active) XInverse else XText, tween(300), label = "anim-chip-fg")
                    Box(
                        Modifier
                            .heightIn(min = 34.dp)
                            .clip(CircleShape)
                            .background(background)
                            .clickable { motion.chooseMode(option) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            xSettingText(option.labelKey),
                            style = XType.rowTitle.copy(fontSize = 10.5.sp, lineHeight = 12.sp, letterSpacing = .02.em),
                            color = foreground,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** `Math.round(spd * 10) / 10` — «1×», «1.2×». */
private fun xFormatSpeed(value: Float): String =
    if (value == value.toInt().toFloat()) "${value.toInt()}" else "$value"

@Composable
private fun XFontScaleRow(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueCommitted: (Float) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value.coerceIn(.9f, 1.3f)) }
    Surface(
        color = XSurfaceSoft,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, XBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XIconPlate(Icons.Outlined.FormatSize)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(xSettingText("text_size"), color = XText, fontWeight = FontWeight.SemiBold)
                    Text(xSettingText("text_size_sub"), color = XMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Text("${(draft * 100).toInt()}%", style = XType.rowTitle.copy(fontSize = 13.sp), color = XAccent)
            }
            Slider(
                value = draft,
                onValueChange = { raw ->
                    val snapped = (kotlin.math.round(raw * 10f) / 10f).coerceIn(.9f, 1.3f)
                    draft = snapped
                    onValueChange(snapped)
                },
                onValueChangeFinished = { onValueCommitted(draft) },
                valueRange = .9f..1.3f,
                steps = 3,
                colors = SliderDefaults.colors(
                    thumbColor = XText,
                    activeTrackColor = XText,
                    inactiveTrackColor = XSurfaceRaised,
                    activeTickColor = XInverse,
                    inactiveTickColor = XMuted,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("90%", "100%", "110%", "120%", "130%").forEach { label ->
                    Text(label, color = XMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
internal fun rememberXPreference(context: Context, key: String, default: Boolean): MutableState<Boolean> {
    val prefs = remember(context) {
        context.getSharedPreferences("orionx_redesign_settings", Context.MODE_PRIVATE)
    }
    val state = remember(key) { mutableStateOf(prefs.getBoolean(key, default)) }
    return remember(key, state, prefs) {
        object : MutableState<Boolean> by state {
            override var value: Boolean
                get() = state.value
                set(value) {
                    state.value = value
                    prefs.edit().putBoolean(key, value).apply()
                }
        }
    }
}

@Composable
private fun XProfileScreen(
    api: SupabaseApi,
    session: CloudSession,
    profile: CloudProfile,
    onProfileChanged: (CloudProfile) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var nickname by remember(profile) { mutableStateOf(profile.nickname) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var phone by remember(profile) { mutableStateOf(profile.phone) }
    var birthday by remember(profile) { mutableStateOf(profile.birthday) }
    var location by remember(profile) { mutableStateOf(profile.location) }
    var website by remember(profile) { mutableStateOf(profile.website) }
    var newEmail by remember(profile) { mutableStateOf(profile.email) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var showAdminPassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = runCatching {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Файл не прочитан") }
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                withContext(Dispatchers.IO) { api.uploadProfileAvatar(session.accessToken, session.userId, mime, bytes) }
            }
            result.onSuccess { url ->
                val updated = profile.copy(avatarUrl = url)
                onProfileChanged(updated)
                message = "Аватар обновлён"
                messageIsError = false
            }.onFailure {
                message = localizedNetworkError(it)
                messageIsError = true
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // The canvas profile header is a bare back arrow with the identity centred below it.
        XScreenHeader(title = "", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(108.dp)) {
                        XAvatar(profile, 108.dp)
                        // `width:38px;height:38px;background:var(--sf3)` camera badge, offset off the edge.
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 3.dp, y = 3.dp)
                                .size(38.dp)
                                .xShadow(CircleShape, 10.dp)
                                .clip(CircleShape)
                                .background(XSurfaceSoft)
                                .clickable { avatarPicker.launch("image/*") },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.PhotoCamera, "Сменить аватар", tint = XText, modifier = Modifier.size(19.dp)) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        profile.displayName.ifBlank { profile.nickname },
                        style = XType.screenTitle.copy(fontSize = 24.sp, letterSpacing = (-.02).em),
                        color = XText,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "@${profile.nickname} · orionx ${BuildConfig.VERSION_NAME}",
                        style = XType.meta.copy(fontSize = 13.sp),
                        color = XMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            item { XFormField("Имя", displayName, { displayName = it }, Icons.Outlined.Badge) }
            item { XFormField("Никнейм", nickname, { nickname = it.filterNot(Char::isWhitespace) }, Icons.Outlined.AlternateEmail) }
            item { XFormField("О себе", bio, { bio = it.take(160) }, Icons.Outlined.Notes, singleLine = false) }
            item { XFormField("Телефон", phone, { phone = it }, Icons.Outlined.Phone, KeyboardType.Phone) }
            item { XBirthdayField(birthday) { birthday = it } }
            item { XFormField("Город / страна", location, { location = it }, Icons.Outlined.LocationOn) }
            item { XFormField("Сайт", website, { website = it }, Icons.Outlined.Language, KeyboardType.Uri) }
            item { XFormField("Электронная почта", newEmail, { newEmail = it }, Icons.Outlined.Email, KeyboardType.Email) }
            item {
                XPrimaryButton("Сохранить профиль", enabled = !busy) {
                    val birthdayError = runCatching {
                        birthday.trim().takeIf { it.isNotBlank() }?.let {
                            val parsed = java.time.LocalDate.parse(it)
                            require(!parsed.isAfter(java.time.LocalDate.now())) { "Дата рождения не может быть в будущем" }
                        }
                    }.exceptionOrNull()
                    if (birthdayError != null) {
                        message = "Выберите корректную дату рождения"
                        messageIsError = true
                        return@XPrimaryButton
                    }
                    scope.launch {
                        busy = true
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val updated = api.updateExtendedProfile(
                                    token = session.accessToken,
                                    userId = session.userId,
                                    nickname = nickname,
                                    displayName = displayName,
                                    bio = bio,
                                    phone = phone,
                                    birthday = birthday,
                                    location = location,
                                    website = website,
                                    avatarUrl = profile.avatarUrl,
                                )
                                if (newEmail.trim() != profile.email.trim()) api.requestEmailChange(session.accessToken, newEmail)
                                updated
                            }
                        }
                        result.onSuccess {
                            onProfileChanged(it)
                            message = if (newEmail.trim() != profile.email.trim()) "Профиль сохранён. Подтвердите новую почту." else "Профиль сохранён"
                            messageIsError = false
                            if (isOrionXAdmin(it.displayName) && !isAdminUnlocked(context)) showAdminPassword = true
                        }.onFailure {
                            message = localizedNetworkError(it)
                            messageIsError = true
                        }
                        busy = false
                    }
                }
            }
            message?.let { item { XInlineMessage(it, error = messageIsError) } }
            item { XDangerButton("Выйти", onLogout) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAdminPassword) {
        XAdminPasswordDialog(
            title = "Чтобы использовать это имя, введите пароль",
            onDismiss = { showAdminPassword = false },
            onSuccess = {
                setAdminUnlocked(context, true)
                showAdminPassword = false
                message = "Доступ администратора включён"
                messageIsError = false
            },
        )
    }
}

@Composable
private fun XOriaScreen(
    api: SupabaseApi,
    session: CloudSession,
    profile: CloudProfile,
    onBack: () -> Unit,
) {
    var thread by remember { mutableStateOf<OriaThread?>(null) }
    var messages by remember { mutableStateOf<List<OriaMessage>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attachment by remember { mutableStateOf<UploadedFile?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember(session.userId) { mutableStateOf<Uri?>(null) }
    var mediaPlayer by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun refresh(t: OriaThread) {
        messages = withContext(Dispatchers.IO) { api.oriaMessages(session.accessToken, t.id) }
    }

    suspend fun uploadUri(uri: Uri, fallbackName: String): UploadedFile {
        val data = withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Не удалось прочитать файл")
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: fallbackName
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index)
            }
            Triple(name, mime, bytes)
        }
        return withContext(Dispatchers.IO) {
            api.uploadMessageFile(session.accessToken, session.userId, data.first, data.second, data.third)
        }
    }

    fun queueEditedPhoto(edited: OrionEditedMedia) {
        scope.launch {
            uploading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    api.uploadMessageFile(session.accessToken, session.userId, edited.displayName, edited.mimeType, edited.file.readBytes())
                }
            }.onSuccess { attachment = it }
                .onFailure { error = localizedNetworkError(it) }
            edited.file.delete()
            uploading = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> pendingPhotoUri = uri }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            runCatching { uploadUri(uri, "file") }
                .onSuccess { attachment = it }
                .onFailure { error = localizedNetworkError(it) }
            uploading = false
        }
    }
    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            runCatching { uploadUri(uri, "animation.gif") }
                .onSuccess { attachment = it }
                .onFailure { error = localizedNetworkError(it) }
            uploading = false
        }
    }

    LaunchedEffect(session.accessToken) {
        val result = runCatching {
            withContext(Dispatchers.IO) { api.ensureOriaThread(session.accessToken, session.userId) }
        }
        result.onSuccess {
            thread = it
            runCatching { refresh(it) }.onFailure { e -> error = localizedNetworkError(e) }
        }.onFailure { error = localizedNetworkError(it) }
        loading = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // `gap:11px;padding:2px 16px 10px` — кнопка назад, инверсная пилюля «Oria AI»
        // (`background:var(--txt);padding:13px 22px;800 12px/.14em`) и распорка 42×42.
        // Ни аватара, ни подзаголовка, ни «трёх точек» в макете нет; кнопка меню и так
        // висела с пустым обработчиком.
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(XText)
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                ) {
                    Text(
                        "Oria AI",
                        style = XType.sectionLabel.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = .14.em,
                        ),
                        color = XInverse,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.size(42.dp))
        }
        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = XPurple) }
        } else {
            // Приветствие в макете стоит ВВЕРХУ ленты (`padding:6px 18px 14px`, орб с
            // `margin-top:6px`). В обратной раскладке единственный элемент прижимался
            // к низу, к самой строке ввода.
            if (messages.isEmpty()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 14.dp),
                ) {
                    XOriaWelcome(profile.displayName, onPrompt = { text = it })
                }
            } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                    items(messages.asReversed(), key = { it.id }) { item ->
                        XOriaBubble(item, onOpenMedia = { url, name, mime -> mediaPlayer = Triple(url, name, mime) })
                    }
                error?.let { item { XInlineMessage(it, true) } }
            }
            }
            // `flex:none;margin:0 18px 6px` — плашка «думает» лежит ПОД лентой, а не
            // внутри неё: в обратной раскладке она вставала выше последнего ответа.
            if (sending) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 6.dp)
                        .xShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(XSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    // `width:5px;height:5px;background:var(--mut)`
                    XTypingDots(color = XMuted)
                    Text(
                        "секунду, Oria AI думает…",
                        style = XType.bodyStrong.copy(fontSize = 11.5.sp),
                        color = XMuted2,
                    )
                }
            }
        }
        // The picker is part of the input area now, not a sheet over the screen.
        if (showMediaSheet) {
            XEmojiMediaSheet(
                onDismiss = { showMediaSheet = false },
                onEmojiSelected = { emoji -> text += emoji },
                onPickGif = { showMediaSheet = false; gifPicker.launch("image/gif") },
            )
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.fillMaxWidth().imePadding()) {
            XComposer(
                text = text,
                onText = { text = it },
                placeholder = "спроси орию…",
                sending = sending || uploading,
                attachment = attachment,
                onCancelContext = { attachment = null },
                onAttach = { showAttachmentSheet = true },
                onEmoji = { showMediaSheet = true },
                onSend = {
                    val t = thread
                    val prompt = text.trim()
                    val currentAttachment = attachment
                    if (t != null && (prompt.isNotBlank() || currentAttachment != null)) {
                        text = ""
                        attachment = null
                        scope.launch {
                            sending = true
                            val mediaLine = currentAttachment?.let {
                                "\n\n[Вложение: ${it.name}](${it.publicUrl})\nТип: ${it.mimeType}"
                            }.orEmpty()
                            val completePrompt = (prompt.ifBlank { "Посмотри прикреплённый файл." } + mediaLine).trim()
                            val optimistic = OriaMessage("local-${System.nanoTime()}", t.id, "user", completePrompt, "")
                            messages = messages + optimistic
                            val result = runCatching { withContext(Dispatchers.IO) { api.askOria(session.accessToken, t.id, completePrompt) } }
                            result.onSuccess { refresh(t) }.onFailure {
                                val fallback = OriaMessage(
                                    id = "fallback-${System.nanoTime()}",
                                    threadId = t.id,
                                    role = "assistant",
                                    content = "Сервис Oria временно недоступен. Сообщение осталось в чате — повторите отправку немного позже.",
                                    createdAt = "",
                                )
                                messages = messages + fallback
                                error = "Не удалось получить ответ Oria"
                            }
                            sending = false
                        }
                    }
                },
            )
        }
    }

    if (showAttachmentSheet) {
        XAttachmentSheet(
            onDismiss = { showAttachmentSheet = false },
            onPhoto = { showAttachmentSheet = false; imagePicker.launch("image/*") },
            onVideo = { showAttachmentSheet = false; filePicker.launch("video/*") },
            onFile = { showAttachmentSheet = false; filePicker.launch("*/*") },
            onAudio = { showAttachmentSheet = false; filePicker.launch("audio/*") },
        )
    }
    pendingPhotoUri?.let { uri ->
        OrionPhotoEditorDialog(
            source = uri,
            onDismiss = { pendingPhotoUri = null },
            onEdited = { edited -> pendingPhotoUri = null; queueEditedPhoto(edited) },
            onError = { message -> error = message },
        )
    }
    mediaPlayer?.let { media ->
        OrionMediaPlayerDialog(
            url = media.first,
            title = media.second,
            mimeType = media.third,
            senderName = "Oria AI",
            onDismiss = { mediaPlayer = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XOriaWelcome(name: String, onPrompt: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Как в макете: 104dp орб с тёплым градиентом и тенью — вместо неонового лого.
        val firstName = name.trim().substringBefore(' ').ifBlank { name }
        // `radial-gradient(circle at 32% 26%, …)`. Центр в Compose — ПИКСЕЛИ, а не доли:
        // с `Offset(.34f,.28f)` тёплые стопы схлопывались в точку у левого верхнего угла,
        // и орб выглядел плоско-фиолетовым.
        val orbPx = with(LocalDensity.current) { 104.dp.toPx() }
        Box(
            Modifier
                .size(104.dp)
                .xShadow(CircleShape, 18.dp, Color(0x5A5A288C))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFFFFE0B0),
                            .42f to Color(0xFFF79AC6),
                            .72f to Color(0xFF9B8CFF),
                            1f to Color(0xFF3F2585),
                        ),
                        center = Offset(orbPx * .32f, orbPx * .26f),
                        radius = orbPx * 1.01f,
                    )
                ),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "чем помочь, $firstName?",
            style = XType.screenTitle.copy(fontSize = 22.sp, letterSpacing = (-.01).em),
            color = XText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))
        // `flex-wrap:wrap;gap:8px;justify-content:center` — на узком экране третий чип
        // раньше ломался на две строки внутри себя вместо переноса на новую строку.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            XPromptChip("кратко о чате") { onPrompt("Сделай краткую сводку нашего чата") }
            XPromptChip("перевести") { onPrompt("Переведи текст") }
            XPromptChip("написать ответ") { onPrompt("Помоги написать ответ") }
        }
    }
}

@Composable
private fun XPromptChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(XSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        // В макете чипы набраны в нижнем регистре: 700/12.5.
        Text(text, style = XType.bodyStrong.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold), color = XText)
    }
}

@Composable
private fun XOriaBubble(
    message: OriaMessage,
    onOpenMedia: (url: String, name: String, mime: String) -> Unit,
) {
    val mine = message.role == "user"
    val attachmentRegex = remember { Regex("\\[Вложение: ([^]]+)]\\((https?://[^)]+)\\)\\s*\\n?Тип: ([^\\s]+)") }
    val match = attachmentRegex.find(message.content)
    val attachmentName = match?.groupValues?.getOrNull(1).orEmpty()
    val attachmentUrl = match?.groupValues?.getOrNull(2).orEmpty()
    val attachmentMime = match?.groupValues?.getOrNull(3).orEmpty()
    val visibleText = message.content.replace(attachmentRegex, "").trim()

    // В макете у ответа Ории аватара рядом нет — только пузырь.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        val oriaShape = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = if (mine) 24.dp else 8.dp,
            bottomEnd = if (mine) 8.dp else 24.dp,
        )
        Surface(
            color = if (mine) XText else XSurface,
            shape = oriaShape,
            shadowElevation = if (mine) 0.dp else 12.dp,
            modifier = Modifier.widthIn(max = 292.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = if (mine) 13.dp else 14.dp)) {
                if (attachmentUrl.isNotBlank()) {
                    if (attachmentMime.startsWith("image/")) {
                        RemoteImage(
                            attachmentUrl,
                            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).clickable {
                                onOpenMedia(attachmentUrl, attachmentName, attachmentMime)
                            },
                            ContentScale.Crop,
                        )
                    } else {
                        Surface(
                            color = if (mine) XInverse.copy(alpha = .10f) else XSurfaceRaised,
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onOpenMedia(attachmentUrl, attachmentName, attachmentMime) },
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (attachmentMime.startsWith("video/")) Icons.Outlined.PlayCircleOutline else Icons.Outlined.Description,
                                    null,
                                    tint = if (mine) XInverse else XText,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(attachmentName.ifBlank { "Вложение" }, style = XType.bodyStrong.copy(fontSize = 12.sp), color = if (mine) XInverse else XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (visibleText.isNotBlank()) Spacer(Modifier.height(7.dp))
                }
                if (visibleText.isNotBlank()) {
                    Text(
                        visibleText,
                        style = XType.body.copy(fontSize = 14.sp, lineHeight = 19.6.sp),
                        color = if (mine) XInverse else XText,
                    )
                }
                // Время внутри пузыря — как в макете (600/10 + точка статуса у своих).
                Row(
                    Modifier.fillMaxWidth().padding(top = if (mine) 7.dp else 8.dp),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatMessageTime(message.createdAt),
                        style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = if (mine) XOnMuted else XMuted,
                    )
                    if (mine) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (message.id.startsWith("local-")) XMuted else XAccentSoft),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XConversationScreen(
    api: SupabaseApi,
    session: CloudSession,
    profile: CloudProfile,
    conversation: CloudConversation,
    onBack: () -> Unit,
    onStartCall: (conversationId: String, peerName: String, peerAvatarUrl: String, video: Boolean) -> Unit,
    /** Opens the group/channel hub. Never called for a direct chat. */
    onOpenCommunity: () -> Unit = {},
    onOpenCommunitySettings: () -> Unit = {},
    onOpenCommunityAddMembers: () -> Unit = {},
    /** «пост» из профиля канала: экран открывается сразу с редактором поста. */
    openPostComposer: Boolean = false,
    onPostComposerShown: () -> Unit = {},
) {
    val reduceMotion = LocalXRuntimeSettings.current.reduceMotion
    // Paint whatever this chat had last time, then refresh behind it — otherwise
    // re-entering a chat blanks out while the fetch runs.
    var messages by remember(conversation.id) {
        mutableStateOf(XScreenCache.messagesOf(conversation.id) ?: emptyList())
    }
    var text by remember { mutableStateOf("") }
    var loading by remember(conversation.id) {
        mutableStateOf(XScreenCache.messagesOf(conversation.id) == null)
    }
    var chatRefreshing by remember(conversation.id) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember { mutableStateOf<CloudMessage?>(null) }
    var editing by remember { mutableStateOf<CloudMessage?>(null) }
    var selectedMessage by remember { mutableStateOf<CloudMessage?>(null) }
    var attachment by remember { mutableStateOf<UploadedFile?>(null) }
    var typingNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var peerPresence by remember { mutableStateOf<CloudPeerPresence?>(null) }
    var callInfo by remember { mutableStateOf<String?>(null) }
    // Pins are a group/channel feature: the bar under the header and the pin action
    // in the message sheet. `canPin` mirrors the server's `is_conversation_staff`.
    var communityPins by remember(conversation.id) { mutableStateOf<List<CloudPinnedMessage>>(emptyList()) }
    var canPin by remember(conversation.id) { mutableStateOf(false) }
    // The canvas' `more_vert` dropdown for a group/channel.
    var communityMenuOpen by remember(conversation.id) { mutableStateOf(false) }
    var communityMembers by remember(conversation.id) { mutableStateOf(0) }
    // Who runs the place, and the faces beside their messages — both come from the roster
    // the screen already loads, so a bubble needs no request of its own.
    var communityTags by remember(conversation.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var communityFaces by remember(conversation.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var communityMuted by remember(conversation.id) { mutableStateOf(false) }
    // A channel shows a «новый пост» pill where a chat has its input row; the row itself
    // only appears once that pill is tapped.
    var postComposerOpen by remember(conversation.id) { mutableStateOf(openPostComposer) }
    // Оверлею кружка нужна высота строки ввода: он стоит над лентой, а в макете
    // отсчёт идёт от низа экрана.
    var composerHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(openPostComposer) {
        if (openPostComposer) {
            postComposerOpen = true
            onPostComposerShown()
        }
    }
    /** The post whose discussion is open, if any. */
    var commentsPost by remember(conversation.id) { mutableStateOf<CloudMessage?>(null) }
    /** Границы пузыря, у которого открылось меню — в макете оно привязано к сообщению. */
    var selectedAnchor by remember(conversation.id) { mutableStateOf(Rect.Zero) }
    /** «закрепить после публикации» in the post editor. */
    var pinAfterPost by remember(conversation.id) { mutableStateOf(false) }
    /** «подписать автором»: пост уходит с metadata.sign, лента рисует подпись. */
    var signPostAuthor by remember(conversation.id) { mutableStateOf(true) }
    var confirmLeaveCommunity by remember(conversation.id) { mutableStateOf(false) }
    // Меню ⋮ личного чата: поиск по сообщениям, закреп чата, обои, блокировка.
    var chatSearchOpen by remember(conversation.id) { mutableStateOf(false) }
    var chatSearchQuery by remember(conversation.id) { mutableStateOf("") }
    var chatBlocked by remember(conversation.id) { mutableStateOf(false) }
    var chatPinned by remember(conversation.id) { mutableStateOf(false) }
    var clearHistorySheet by remember(conversation.id) { mutableStateOf(false) }
    // «обои чата»: скин фона переписки, общий с настройкой «оформление чата».
    var chatSkin by remember { mutableIntStateOf(0) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var recordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    // Canvas `actDown`: one hold drives both modes — drag left arms cancel, drag up locks.
    var voiceCancelArmed by remember { mutableStateOf(false) }
    var voiceLocked by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var mediaMode by remember(conversation.id, session.userId) { mutableStateOf(XMediaComposeMode.VOICE) }
    // Видеосообщения-кружки: запись, отправка, воспроизведение и отметки «просмотрено».
    val videoNoteRecorder = rememberVideoNoteRecorderState()
    val videoNotePlayback = rememberVideoNotePlaybackController()
    val videoNoteWatch = rememberVideoNoteWatchState(session.userId)
    // Outbox переживает выход из чата — черновики кружков не теряются.
    val videoNoteOutbox = remember(conversation.id, session.userId) { VideoNoteOutbox.forConversation(conversation.id) }
    LaunchedEffect(videoNoteOutbox) { videoNoteOutbox.resetStaleUploads() }
    var videoNoteHint by remember { mutableStateOf<String?>(null) }
    var videoNotePermissionPending by remember { mutableStateOf(false) }
    var expandedVideoNote by remember(conversation.id) { mutableStateOf<CloudMessage?>(null) }
    var showPeerProfile by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember(conversation.id, session.userId) { mutableStateOf<Uri?>(null) }
    var pendingVideoUri by remember(conversation.id, session.userId) { mutableStateOf<Uri?>(null) }
    var mediaPlayerMessage by remember(conversation.id, session.userId) { mutableStateOf<CloudMessage?>(null) }
    var forwardingMessage by remember(conversation.id, session.userId) { mutableStateOf<CloudMessage?>(null) }
    var reactionChoices by remember { mutableStateOf(CloudUserSettings().reactionEmojis) }
    var readReceiptsEnabled by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val readWatermarks = remember(session.userId) {
        context.getSharedPreferences("orionx_read_watermarks_${session.userId}", Context.MODE_PRIVATE)
    }
    LaunchedEffect(conversation.id, session.userId) {
        chatPinned = xChatPinnedIds(context, session.userId).contains(conversation.id)
        chatSkin = context.getSharedPreferences("orionx_redesign", Context.MODE_PRIVATE).getInt("chat_skin", 0).coerceIn(0, 2)
    }
    val activity = context as? MainActivity
    val foreground = activity?.isAppForeground ?: true

    // Пока идёт запись (голос или видеокружок) — не даём экрану гаснуть.
    val xKeepScreenOn = videoNoteRecorder.active || recordingVoice
    DisposableEffect(xKeepScreenOn) {
        val window = (context as? android.app.Activity)?.window
        if (xKeepScreenOn && window != null) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val messageListState = rememberLazyListState()
    var initialDataLoaded by remember(conversation.id) { mutableStateOf(false) }
    // Генерация refresh: фоновый опрос, стартовавший до отправки, не должен перезаписать
    // ленту устаревшим списком после refresh-а, который уже забрал новое сообщение.
    val refreshSeq = remember(conversation.id) { java.util.concurrent.atomic.AtomicInteger(0) }
    // Системный «назад» при открытом поиске сначала сворачивает поиск, а не весь чат.
    BackHandlerCompat(enabled = chatSearchOpen) {
        if (chatSearchQuery.isNotEmpty()) chatSearchQuery = "" else chatSearchOpen = false
    }

    suspend fun refresh() {
        val generation = refreshSeq.incrementAndGet()
        val fetched = withContext(Dispatchers.IO) { api.messages(session.accessToken, conversation.id) }
        if (generation != refreshSeq.get()) return
        val mineIds = fetched.asSequence().filter { it.senderId == session.userId }.map { it.id }.toList()
        val states = runCatching {
            withContext(Dispatchers.IO) { api.messageReceiptStates(session.accessToken, mineIds, session.userId) }
        }.getOrDefault(emptyMap())
        val merged = fetched.map { message ->
            val state = states[message.id]
            if (state == null) message else message.copy(
                deliveredToOthers = state.delivered,
                readByOthers = state.read,
            )
        }
        // Оптимистичный пузырь переживает опрос. Раньше `refresh()` раз в 3 секунды целиком
        // перезаписывал ленту, и при медленной отправке сообщение пропадало с экрана
        // на пару секунд — человек думал, что оно потерялось, и отправлял второй раз.
        val pending = messages.filter { it.id.startsWith("local-") }
        messages = if (pending.isEmpty()) merged else merged + pending
        XScreenCache.putMessages(conversation.id, messages)
        if (foreground) {
            val latest = fetched.maxOfOrNull { runCatching { java.time.Instant.parse(it.createdAt).toEpochMilli() }.getOrDefault(0L) } ?: 0L
            if (latest > 0L) readWatermarks.edit().putLong(conversation.id, latest).apply()
        }
    }

    suspend fun uploadUri(uri: Uri, fallbackName: String = "file"): UploadedFile {
        val data = withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Не удалось прочитать файл")
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: fallbackName
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index)
            }
            Triple(name, mime, bytes)
        }
        return withContext(Dispatchers.IO) {
            api.uploadMessageFile(session.accessToken, session.userId, data.first, data.second, data.third)
        }
    }

    fun queueAttachment(uri: Uri?, fallbackName: String = "file") {
        if (uri == null) return
        scope.launch {
            uploading = true
            runCatching { uploadUri(uri, fallbackName) }
                .onSuccess { attachment = it }
                .onFailure { error = localizedNetworkError(it) }
            uploading = false
        }
    }

    fun queueEditedMedia(edited: OrionEditedMedia) {
        scope.launch {
            uploading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    api.uploadMessageFile(
                        session.accessToken,
                        session.userId,
                        edited.displayName,
                        edited.mimeType,
                        edited.file.readBytes(),
                    )
                }
            }.onSuccess { attachment = it }
                .onFailure { error = localizedNetworkError(it) }
            edited.file.delete()
            uploading = false
        }
    }

    fun startVoiceRecording() {
        if (recordingVoice) return
        runCatching {
            val directory = File(context.cacheDir, "voice_messages").apply { mkdirs() }
            val output = File(directory, "voice_${System.currentTimeMillis()}.m4a")
            val recorder = createOrionMediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(output.absolutePath)
                prepare()
                start()
            }
            voiceFile = output
            voiceRecorder = recorder
            recordingSeconds = 0
            recordingVoice = true
            voiceCancelArmed = false
            voiceLocked = false
        }.onFailure { error = localizedNetworkError(it) }
    }

    fun stopVoiceRecording(send: Boolean) {
        val recorder = voiceRecorder
        val file = voiceFile
        voiceRecorder = null
        voiceFile = null
        recordingVoice = false
        voiceCancelArmed = false
        voiceLocked = false
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        if (!send || file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            return
        }
        scope.launch {
            uploading = true
            val result = runCatching {
                // NonCancellable: уход с экрана во время загрузки не должен терять
                // записанное сообщение — отправка и удаление временного файла доделываются.
                val uploaded = withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                    api.uploadMessageFile(
                        session.accessToken,
                        session.userId,
                        "voice_${System.currentTimeMillis()}.m4a",
                        "audio/mp4",
                        file.readBytes(),
                    )
                }
                withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                    api.sendMessage(session.accessToken, conversation.id, session.userId, "", null, uploaded)
                }
            }
            runCatching { withContext(kotlinx.coroutines.NonCancellable) { file.delete() } }
            result.onSuccess { refresh() }
                .onFailure { error = localizedNetworkError(it) }
            uploading = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingPhotoUri = uri
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingVideoUri = uri
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        queueAttachment(uri, "file")
    }
    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        queueAttachment(uri, "animation.gif")
    }

    /**
     * Загрузка кружка на сервер. Черновик остаётся в исходящих до подтверждения,
     * поэтому обрыв связи не теряет запись — её можно отправить повторно.
     */
    suspend fun uploadVideoNote(pending: PendingVideoNote) {
        // Повторный тап по «Повторить» не должен запускать вторую параллельную загрузку.
        if (videoNoteOutbox.find(pending.localId)?.stage == PendingVideoNote.Stage.UPLOADING) return
        videoNoteOutbox.update(pending.localId) { it.copy(stage = PendingVideoNote.Stage.UPLOADING, error = null) }
        val name = VideoNote.buildFileName(pending.durationMs)
        runCatching {
            val uploaded = withContext(Dispatchers.IO) {
                api.uploadMessageFile(session.accessToken, session.userId, name, VideoNote.MIME, pending.file.readBytes())
            }
            withContext(Dispatchers.IO) {
                api.sendMessage(session.accessToken, conversation.id, session.userId, "", null, uploaded)
            }
            uploaded
        }.onSuccess { uploaded ->
            // Своя превьюшка уже есть — кладём её в кэш, чтобы не тянуть кадр обратно из сети.
            pending.thumbnailFile?.let { VideoNoteThumbnails.seed(context, uploaded.publicUrl, it) }
            // Сначала подтверждаем обновление ленты — иначе при сбое refresh()
            // кружок исчезнет и из ленты, и из исходящих.
            refresh()
            videoNoteOutbox.remove(pending.localId)
            pending.file.delete()
            pending.thumbnailFile?.delete()
        }.onFailure {
            videoNoteOutbox.fail(pending.localId, localizedNetworkError(it))
        }
    }

    /** Запись готова: сразу показываем кружок в ленте, сжатие и отправка идут следом. */
    fun sendVideoNote(raw: File, recordedMs: Long) {
        if (!raw.exists() || raw.length() == 0L) {
            raw.delete()
            return
        }
        val localId = "vnote-${System.nanoTime()}"
        videoNoteOutbox.enqueue(PendingVideoNote(localId = localId, file = raw, durationMs = recordedMs))
        scope.launch {
            val encoded = runCatching { VideoNoteEncoder.encode(context, raw, recordedMs) }
                .getOrElse { failure ->
                    // Слишком короткую запись повторять нечем — просто убираем черновик.
                    if (failure is IllegalArgumentException) {
                        videoNoteOutbox.discard(localId)
                        videoNoteHint = "Удерживайте кнопку, чтобы записать видеосообщение"
                    } else {
                        videoNoteOutbox.fail(localId, "Не удалось обработать видеосообщение")
                    }
                    return@launch
                }
            videoNoteOutbox.update(localId) {
                it.copy(file = encoded.file, thumbnailFile = encoded.thumbnail, durationMs = encoded.durationMs)
            }
            val pending = videoNoteOutbox.find(localId) ?: return@launch
            uploadVideoNote(pending)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecording() else error = "Разрешите доступ к микрофону для голосовых сообщений"
    }
    val videoPermissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val cameraGranted = grants[Manifest.permission.CAMERA] == true || context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val wanted = videoNotePermissionPending
        videoNotePermissionPending = false
        if (cameraGranted && audioGranted) {
            // Разрешение спрашивали уже после нажатия — сразу открываем запись.
            if (wanted) videoNoteRecorder.open()
        } else {
            error = "Для видеосообщений нужен доступ к камере и микрофону"
        }
    }

    fun requestVoiceRecording() {
        mediaMode = XMediaComposeMode.VOICE
        if (recordingVoice) {
            stopVoiceRecording(send = true)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecording()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Кнопка кружка нажата и удерживается — начинаем запись (или сначала спрашиваем доступ). */
    fun beginVideoNoteRecording() {
        mediaMode = XMediaComposeMode.VIDEO
        if (videoNoteRecorder.active) return
        videoNoteHint = null
        val cameraGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audioGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted && audioGranted) {
            // Идущее воспроизведение уступает микрофон записи.
            videoNotePlayback.stop()
            videoNoteRecorder.open()
        } else {
            videoNotePermissionPending = true
            videoPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    fun openAttachment(message: CloudMessage) {
        // Удалённое вложение не открываем и не проигрываем.
        if (message.deletedForAll) return
        val url = message.attachmentUrl ?: return
        if (isVideoNoteMessage(message)) {
            // Кружок играет прямо в ленте — открывать отдельный экран не нужно.
            // «Просмотрено» ставится только по факту доигрывания (onCompleted).
            videoNotePlayback.play(message.id, url)
            return
        }
        val type = message.attachmentType.orEmpty().lowercase()
        if (type.startsWith("audio/") || type.startsWith("video/") || type.startsWith("image/")) {
            mediaPlayerMessage = message
            return
        }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), message.attachmentType ?: "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { error = "Не удалось открыть вложение" }
    }

    // Цепочка каскада играет только чужие кружки — свои отправленные
    // автоматически со звуком включаться не должны. Колбэк ставится один раз,
    // свежий список читается через rememberUpdatedState.
    val currentMessages by rememberUpdatedState(messages)
    LaunchedEffect(videoNotePlayback) {
        videoNotePlayback.onCompleted = { finishedId ->
            videoNoteWatch.markWatched(finishedId)
            val notes = currentMessages.filter { isVideoNoteMessage(it) }
            val index = notes.indexOfFirst { it.id == finishedId }
            val next = if (index >= 0) {
                notes.drop(index + 1).firstOrNull {
                    it.senderId != session.userId &&
                        !videoNoteWatch.isWatched(it.id) &&
                        !it.attachmentUrl.isNullOrBlank()
                }
            } else {
                null
            }
            if (next != null) {
                if (expandedVideoNote != null) expandedVideoNote = next
                videoNotePlayback.play(next.id, next.attachmentUrl.orEmpty())
            } else {
                expandedVideoNote = null
                videoNotePlayback.stop()
            }
        }
    }

    LaunchedEffect(videoNoteHint) {
        if (videoNoteHint != null) {
            delay(2_200)
            videoNoteHint = null
        }
    }

    LaunchedEffect(recordingVoice) {
        while (recordingVoice) {
            delay(1_000)
            recordingSeconds++
        }
    }

    // Groups and channels only: the roster decides whether the pin action is offered,
    // and the pins feed the bar under the header. A direct chat skips both requests.
    LaunchedEffect(conversation.id, session.accessToken) {
        if (conversation.type == "direct") return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                val roster = api.communityMembers(session.accessToken, conversation.id)
                val pins = api.communityPins(session.accessToken, conversation.id)
                roster to pins
            }
        }.onSuccess { (roster, pins) ->
            canPin = conversation.ownerId == session.userId ||
                roster.firstOrNull { it.userId == session.userId }?.isStaff == true
            communityPins = pins
            communityMembers = roster.size
            communityTags = roster.filter { it.isStaff || it.userId == conversation.ownerId }
                .associate { member ->
                    member.userId to (member.adminTag.takeIf { it.isNotBlank() } ?: tr("community_admin_tag_placeholder"))
                }
            communityFaces = roster.filter { it.avatarUrl.isNotBlank() }.associate { it.userId to it.avatarUrl }
            communityMuted = roster.firstOrNull { it.userId == session.userId }?.notificationsEnabled == false
        }
    }

    LaunchedEffect(conversation.id, session.accessToken, foreground) {
        val chatSettingsDeferred = async(Dispatchers.IO) {
            runCatching { api.userSettings(session.accessToken, session.userId) }.getOrDefault(CloudUserSettings())
        }
        val draftDeferred = async(Dispatchers.IO) {
            runCatching { api.loadDraft(session.accessToken, conversation.id, session.userId) }.getOrNull()
        }
        val presenceJob = if (conversation.type == "direct") {
            launch {
                peerPresence = runCatching {
                    withContext(Dispatchers.IO) { api.peerPresence(session.accessToken, conversation.id, session.userId) }
                }.getOrNull()
            }
        } else null
        // Delivery/read acknowledgement is best-effort; failure here must not masquerade as an
        // internet outage when message loading itself succeeds.
        launch(Dispatchers.IO) {
            runCatching {
                api.markConversationDelivered(session.accessToken, conversation.id)
                if (foreground) api.markConversationRead(session.accessToken, conversation.id)
            }
        }
        run {
            var lastError: Throwable? = null
            repeat(3) { attempt ->
                val r = runCatching { refresh() }
                if (r.isSuccess) { lastError = null; return@run }
                val ex = r.exceptionOrNull()
                if (ex is kotlinx.coroutines.CancellationException) throw ex
                lastError = ex
                delay(500L * (attempt + 1))
            }
            if (lastError != null && messages.isEmpty()) error = localizedNetworkError(lastError!!)
        }
        val chatSettings = chatSettingsDeferred.await()
        reactionChoices = normalizeReactionList(chatSettings.reactionEmojis)
        readReceiptsEnabled = chatSettings.readReceipts
        val draft = draftDeferred.await()
        if (draft != null && text.isBlank()) {
            text = draft.first
            replyingTo = messages.firstOrNull { it.id == draft.second }
        }
        // Присутствие и блокировка приходят в фоне — они влияют только на шапку
        // и меню, лента не должна ждать их перед показом.
        // «заблокировать» в меню ⋮: подсвечиваем текущее состояние по списку блокировок.
        if (conversation.type == "direct") {
            launch {
                peerPresence?.userId?.let { peerId ->
                    chatBlocked = runCatching {
                        withContext(Dispatchers.IO) { peerId in api.blockedUserIds(session.accessToken, session.userId) }
                    }.getOrDefault(false)
                }
            }
        }
        initialDataLoaded = true
    }

    LaunchedEffect(conversation.id, session.accessToken, foreground) {
        // Свёрнутое приложение сервер не опрашивает. Раньше цикл продолжал работать в фоне:
        // каждые 3 секунды уходило пять запросов, включая полную выгрузку истории переписки —
        // радио не засыпало, а трафик и батарея тратились впустую.
        if (!foreground) return@LaunchedEffect
        while (true) {
            delay(3_000)
            // Background receipt/presence polling is best-effort. A short WebSocket/HTTP hiccup
            // must not be shown as a persistent "internet" error while the chat itself still works.
            runCatching {
                withContext(Dispatchers.IO) {
                    api.markConversationDelivered(session.accessToken, conversation.id)
                    if (foreground) api.markConversationRead(session.accessToken, conversation.id)
                }
            }
            runCatching { refresh() }
            typingNames = runCatching {
                withContext(Dispatchers.IO) { api.typingUsers(session.accessToken, conversation.id, session.userId) }
            }.getOrDefault(emptyList())
            if (conversation.type == "direct") {
                peerPresence = runCatching {
                    withContext(Dispatchers.IO) { api.peerPresence(session.accessToken, conversation.id, session.userId) }
                }.getOrNull()
            }
        }
    }

    val showJumpToBottom by remember(messageListState) { derivedStateOf { messageListState.canScrollBackward } }
    var atBottom by remember(conversation.id) { mutableStateOf(true) }
    var unreadWhileUp by remember(conversation.id) { mutableIntStateOf(0) }
    var didInitialScroll by remember(conversation.id) { mutableStateOf(false) }

    // Live "am I pinned to the newest message" flag (reverseLayout: index 0 == bottom).
    LaunchedEffect(messageListState) {
        androidx.compose.runtime.snapshotFlow { !messageListState.canScrollBackward }
            .collect { atBottom = it }
    }
    LaunchedEffect(atBottom) { if (atBottom) unreadWhileUp = 0 }
    LaunchedEffect(initialDataLoaded) { if (initialDataLoaded) loading = false }

    // First time messages arrive: snap straight to the newest (no animation, no delay).
    // reverseLayout keeps index 0 pinned to the bottom afterwards.
    LaunchedEffect(initialDataLoaded, messages.size) {
        if (initialDataLoaded && messages.isNotEmpty() && !didInitialScroll) {
            runCatching { messageListState.scrollToItem(0) }
            didInitialScroll = true
            atBottom = true
        }
    }

    // On a new message: follow only if the user is at the bottom; otherwise count unread
    // incoming messages on the jump button (never yank the user out of history).
    LaunchedEffect(messages.size) {
        if (!initialDataLoaded || messages.isEmpty() || !didInitialScroll) return@LaunchedEffect
        if (atBottom) {
            runCatching { messageListState.scrollToItem(0) }
        } else {
            val last = messages.lastOrNull()
            if (last != null && last.senderId != session.userId) unreadWhileUp++
        }
    }

    LaunchedEffect(text, replyingTo?.id) {
        delay(650)
        runCatching {
            withContext(Dispatchers.IO) {
                api.saveDraft(session.accessToken, conversation.id, session.userId, text, replyingTo?.id)
                api.setTyping(session.accessToken, conversation.id, text.isNotBlank())
            }
        }
    }

    DisposableEffect(conversation.id) {
        onDispose {
            runCatching { voiceRecorder?.release() }
            voiceRecorder = null
            voiceFile?.delete()
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching { api.setTyping(session.accessToken, conversation.id, false) }
            }
        }
    }

    // Один путь отправки на оба ввода: строку сообщения и редактор поста канала.
    val submitMessage: (Boolean) -> Unit = { pinIt ->
        val body = text.trim()
        val editTarget = editing
        if (body.isNotBlank() || attachment != null) {
            text = ""
            if (editTarget != null) {
                // Правка: без сетевого ответа нечего показать заранее.
                scope.launch {
                    sending = true
                    val result = runCatching {
                        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                            api.editMessage(session.accessToken, editTarget.id, body)
                            api.saveDraft(session.accessToken, conversation.id, session.userId, "", null)
                        }
                    }
                    result.onSuccess {
                        editing = null
                        refresh()
                    }.onFailure {
                        error = localizedNetworkError(it)
                        text = body
                    }
                    sending = false
                }
            } else {
                // Оптимистичная отправка: сообщение видно в ленте сразу, запрос идёт в фоне.
                val reply = replyingTo
                val att = attachment
                val channelSign = conversation.type == "channel" && signPostAuthor
                val localId = "local-${java.util.UUID.randomUUID()}"
                val local = CloudMessage(
                    id = localId,
                    conversationId = conversation.id,
                    senderId = session.userId,
                    senderName = "",
                    body = body,
                    createdAt = java.time.Instant.now().toString(),
                    replyToId = reply?.id,
                    replyPreview = reply?.body?.takeIf { it.isNotBlank() } ?: reply?.attachmentName,
                    replySenderName = reply?.senderName,
                    attachmentUrl = att?.publicUrl,
                    attachmentName = att?.name,
                    attachmentType = att?.mimeType,
                )
                messages = messages + local
                replyingTo = null
                attachment = null
                postComposerOpen = false
                if (atBottom) scope.launch { messageListState.scrollToItem(0) }
                scope.launch {
                    sending = true
                    val result = runCatching {
                        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                            api.sendMessage(
                                session.accessToken,
                                conversation.id,
                                session.userId,
                                body,
                                reply?.id,
                                att,
                                metadata = if (channelSign) org.json.JSONObject().put("sign", true) else null,
                            )
                        }
                    }
                    // Черновик чистим параллельно — не тормозим подтверждение отправки.
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        runCatching { api.saveDraft(session.accessToken, conversation.id, session.userId, "", null) }
                    }
                    result.onSuccess {
                        messages = messages.filterNot { it.id == localId }
                        refresh()
                        // «закрепить после публикации»: свежий пост — последний свой в ленте.
                        if (pinIt) {
                            val fresh = messages.lastOrNull { it.senderId == session.userId && it.id != localId }
                            if (fresh != null) {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        api.setMessagePinned(session.accessToken, conversation.id, fresh.id, true)
                                    }
                                }.onSuccess { pinAfterPost = false }
                            }
                        }
                    }.onFailure {
                        // Возвращаем текст и вложение — сообщение не потеряется.
                        messages = messages.filterNot { it.id == localId }
                        error = localizedNetworkError(it)
                        text = body
                        replyingTo = reply
                        attachment = att
                    }
                    sending = false
                }
            }
        }
    }

    val peerAvatarForCall = peerPresence?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: conversation.peerAvatarUrl.takeIf { it.isNotBlank() }
        ?: ""
    val directPeerTitle = peerPresence?.displayName?.takeIf { it.isNotBlank() }
        ?: conversation.peerDisplayName.takeIf { it.isNotBlank() }
        ?: peerPresence?.nickname?.takeIf { it.isNotBlank() }
        ?: conversation.peerNickname.takeIf { it.isNotBlank() }
        ?: when (OrionI18n.current) {
            OrionLanguage.RUSSIAN -> "Собеседник"
            OrionLanguage.UKRAINIAN -> "Співрозмовник"
            OrionLanguage.BELARUSIAN -> "Суразмоўца"
            OrionLanguage.KAZAKH -> "Сұхбаттасушы"
            else -> "Contact"
        }

    // The canvas floats the header over the feed — messages slide under it and are dimmed
    // by `--tscr` — so the list runs the full height of the screen and the chrome sits on
    // top of it, measured rather than laid out in a column.
    val xDensity = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var chromeHeight by remember { mutableStateOf(0.dp) }
    // Высота пилюли «поиск в чате» — лента должна начинаться ниже неё.
    var searchChromeHeight by remember { mutableStateOf(0.dp) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    Column(
        Modifier
            .fillMaxSize()
            // «обои чата»: лиловый — штатный градиент, графит темнит, рассвет греет фон.
            .drawBehind {
                when (chatSkin) {
                    1 -> drawRect(Color(0xFF0B0C12).copy(alpha = .32f))
                    2 -> drawRect(Color(0xFFFFB48A).copy(alpha = .10f))
                }
            },
    ) {
        // `position:absolute;top:110px;right:16px;width:240px;background:var(--sf3);
        // border-radius:22px` — the canvas' group/channel dropdown.
        if (communityMenuOpen) {
            val density = LocalDensity.current
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopEnd,
                offset = with(density) { IntOffset(x = -(16.dp.roundToPx()), y = 56.dp.roundToPx()) },
                onDismissRequest = { communityMenuOpen = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                val channel = conversation.type == "channel"
                val direct = conversation.type == "direct"
                Column(
                    Modifier
                        .width(240.dp)
                        .xShadow(RoundedCornerShape(22.dp), elevation = 22.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(XSurfaceSoft),
                ) {
                    if (direct) {
                        XCommunityMenuRow(Icons.Outlined.Videocam, tr("video_call")) {
                            communityMenuOpen = false
                            onStartCall(conversation.id, directPeerTitle, peerAvatarForCall, true)
                        }
                    }
                    XCommunityMenuRow(Icons.Outlined.Search, tr("search_in_chat")) {
                        communityMenuOpen = false
                        chatSearchOpen = true
                    }
                    if (direct) {
                        // «закрепить чат» — локальный закреп, строка поднимается в списке.
                        XCommunityMenuRow(
                            icon = Icons.Outlined.PushPin,
                            title = if (chatPinned) tr("unpin_chat") else tr("pin_chat"),
                            trailing = { if (chatPinned) Icon(Icons.Outlined.Check, null, tint = XMuted, modifier = Modifier.size(17.dp)) },
                        ) {
                            val next = !chatPinned
                            xChatPin(context, session.userId, conversation.id, next)
                            chatPinned = next
                            communityMenuOpen = false
                        }
                    }
                    XCommunityMenuRow(
                        icon = Icons.Outlined.NotificationsOff,
                        title = "без звука",
                        trailing = { if (communityMuted) Icon(Icons.Outlined.Check, null, tint = XMuted, modifier = Modifier.size(17.dp)) },
                    ) {
                        val next = !communityMuted
                        communityMuted = next
                        communityMenuOpen = false
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { api.setMemberMuted(session.accessToken, conversation.id, next) } }
                                .onFailure { communityMuted = !next; error = localizedNetworkError(it) }
                        }
                    }
                    if (direct) {
                        // «обои чата» — переключает скин фона переписки, как строка в настройках.
                        val skins = listOf("skin_lilac", "skin_graphite", "skin_dawn")
                        XCommunityMenuRow(
                            icon = Icons.Outlined.Wallpaper,
                            title = tr("chat_wallpaper"),
                            trailing = { Text(xSettingText(skins[chatSkin]), style = XType.meta, color = XMuted) },
                        ) {
                            val next = (chatSkin + 1) % skins.size
                            context.getSharedPreferences("orionx_redesign", Context.MODE_PRIVATE)
                                .edit().putInt("chat_skin", next).apply()
                            chatSkin = next
                        }
                        // «очистить историю»: шит выбора, как в макете — у себя или у всех.
                        XCommunityMenuRow(
                            icon = Icons.Outlined.DeleteSweep,
                            title = tr("clear_history"),
                            divider = false,
                        ) {
                            communityMenuOpen = false
                            clearHistorySheet = true
                        }
                        XCommunityMenuRow(
                            icon = Icons.Outlined.Block,
                            title = if (chatBlocked) tr("unblock_action") else tr("block_action"),
                            tint = XRed,
                            divider = false,
                        ) {
                            communityMenuOpen = false
                            scope.launch {
                                val peerId = peerPresence?.userId
                                    ?: withContext(Dispatchers.IO) {
                                        runCatching { api.peerPresence(session.accessToken, conversation.id, session.userId)?.userId }.getOrNull()
                                    }
                                if (peerId == null) return@launch
                                val next = !chatBlocked
                                chatBlocked = next
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        if (next) api.blockUser(session.accessToken, session.userId, peerId)
                                        else api.unblockUser(session.accessToken, session.userId, peerId)
                                    }
                                }.onFailure { chatBlocked = !next; error = localizedNetworkError(it) }
                            }
                        }
                    }
                    // В личном чате групповых пунктов быть не должно — они про сообщество.
                    if (!direct) {
                        XCommunityMenuRow(
                            icon = Icons.Outlined.Groups,
                            title = if (channel) tr("community_subscribers") else tr("community_members"),
                            trailing = { Text("$communityMembers", style = XType.meta, color = XMuted) },
                        ) {
                            communityMenuOpen = false
                            onOpenCommunity()
                        }
                        XCommunityMenuRow(Icons.Outlined.PersonAdd, tr("community_add_members")) {
                            communityMenuOpen = false
                            onOpenCommunityAddMembers()
                        }
                        XCommunityMenuRow(
                            icon = Icons.Outlined.Tune,
                            title = if (channel) tr("community_channel_settings") else tr("community_group_settings"),
                        ) {
                            communityMenuOpen = false
                            onOpenCommunitySettings()
                        }
                        XCommunityMenuRow(
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            title = if (channel) tr("community_unsubscribe") else tr("community_leave"),
                            tint = XRed,
                            divider = false,
                        ) {
                            communityMenuOpen = false
                            confirmLeaveCommunity = true
                        }
                    }
                }
            }
        }
        // Day chips are their own list entries rather than something tucked inside a
        // bubble: under reverseLayout the order of items is the one unambiguous thing, and
        // an entry emitted after a message sits above it.
        val channelFeed = conversation.type == "channel"
        // «поиск в чате»: пока открыт запрос, лента сужается до совпадений и чипы дат убираются.
        val searchQuery = chatSearchQuery.trim()
        val searchActive = chatSearchOpen && searchQuery.isNotEmpty()
        val chatMatchCount = remember(messages, searchQuery) {
            if (searchQuery.isEmpty()) 0
            else messages.count { !it.deletedForAll && it.body.contains(searchQuery, ignoreCase = true) }
        }
        val feed = remember(messages, channelFeed, searchActive, searchQuery) {
            val out = mutableListOf<Any>()
            val source = when {
                channelFeed -> messages.filter { it.replyToId == null }
                searchActive -> messages.filter { !it.deletedForAll && it.body.contains(searchQuery, ignoreCase = true) }
                else -> messages
            }
            val ordered = source.asReversed()
            ordered.forEachIndexed { index, m ->
                out += m
                if (channelFeed || searchActive) return@forEachIndexed
                val older = ordered.getOrNull(index + 1)
                if (older == null || xDayKey(m.createdAt) != xDayKey(older.createdAt)) {
                    out += XDayMark(xDayKey(m.createdAt), m.createdAt)
                }
            }
            out
        }
        // Comments are replies to a post, so the count comes from the history already loaded.
        val commentCounts = remember(messages) {
            messages.mapNotNull { it.replyToId }.groupingBy { it }.eachCount()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // A discussion takes the screen over: the feed and the header behind it are not
            // drawn at all, so the page gradient stays one piece under the comments.
            if (commentsPost == null) {
            XPullToRefresh(
                listState = messageListState,
                refreshing = chatRefreshing,
                enabled = false,
                onRefresh = {
                    scope.launch {
                        chatRefreshing = true
                        runCatching {
                            withContext(Dispatchers.IO) { api.markConversationRead(session.accessToken, conversation.id) }
                            refresh()
                        }.onFailure { error = localizedNetworkError(it) }
                        chatRefreshing = false
                    }
                },
            ) {
                LazyColumn(
                    state = messageListState,
                    modifier = Modifier.fillMaxSize(),
                    // Отступы ленты у артбордов разные: личный чат — `18px шаг 9`,
                    // группа — `16px шаг 10`, канал — `16px шаг 11`.
                    contentPadding = PaddingValues(
                        start = if (conversation.type == "direct") 18.dp else 16.dp,
                        end = if (conversation.type == "direct") 18.dp else 16.dp,
                        top = chromeHeight + (if (chatSearchOpen) searchChromeHeight else 0.dp) +
                            if (communityPins.isEmpty()) 43.dp else 10.dp,
                        bottom = if (conversation.type == "direct") 14.dp else 20.dp,
                    ),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(
                        when (conversation.type) {
                            "channel" -> 11.dp
                            "group" -> 10.dp
                            else -> 9.dp
                        },
                    ),
                ) {
                    error?.let { item(key = "inline-error") { XInlineMessage(it, true) } }
                    // Ещё не улетевшие кружки стоят у самого низа ленты — как в Telegram,
                    // сообщение видно сразу после записи, а сжатие идёт под ним.
                    items(videoNoteOutbox.items.asReversed(), key = { "pending-${it.localId}" }) { pending ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            PendingVideoNoteBubble(
                                pending = pending,
                                onRetry = { scope.launch { uploadVideoNote(pending) } },
                                onDiscard = { videoNoteOutbox.discard(pending.localId) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    items(feed, key = { if (it is XDayMark) "day-" + it.key else (it as CloudMessage).id }) { entry ->
                        if (entry is XDayMark) {
                            XDayDivider(entry.iso)
                            return@items
                        }
                        val message = entry as CloudMessage
                        val mine = message.senderId == session.userId
                        // Служебная строка — не пузырь, а плашка по центру, как в макете.
                        if (message.messageType == "system") {
                            XSystemPlate(xServiceLine(message.body), Modifier.animateItem())
                            return@items
                        }
                        if (channelFeed) {
                            // Fresh means just published: the canvas green tag is a receipt for
                            // the post you have only now sent, not a badge every post keeps.
                            val fresh = mine && xMinutesSince(message.createdAt) < 5
                            XChannelPost(
                                message = message,
                                pinned = communityPins.any { it.messageId == message.id },
                                fresh = fresh,
                                comments = commentCounts[message.id] ?: 0,
                                onOpenAttachment = { openAttachment(message) },
                                onLongClick = { rect -> if (!message.id.startsWith("local-") && !message.deletedForAll) { selectedAnchor = rect; selectedMessage = message } },
                                onComment = { commentsPost = message; replyingTo = message },
                                onReact = { if (!message.id.startsWith("local-")) selectedMessage = message },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(220),
                                    placementSpec = tween(280, easing = FastOutSlowInEasing),
                                    fadeOutSpec = tween(160),
                                ),
                            )
                            return@items
                        }
                        XMessageBubble(
                            message = message,
                            mine = mine,
                            showSenderName = conversation.type != "direct" && !mine,
                            onOpenAttachment = { openAttachment(message) },
                            onLongClick = { rect -> if (!message.id.startsWith("local-") && !message.deletedForAll) { selectedAnchor = rect; selectedMessage = message } },
                            videoNotePlayback = videoNotePlayback,
                            videoNoteWatch = videoNoteWatch,
                            onExpandVideoNote = { expandedVideoNote = message },
                            community = conversation.type != "direct",
                            adminTag = communityTags[message.senderId],
                            senderAvatarUrl = communityFaces[message.senderId].orEmpty(),
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                placementSpec = tween(280, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(160),
                            ),
                        )
                    }
                }
            }
            if (loading) {
                Box(
                    Modifier.fillMaxSize().background(XBg.copy(alpha = .98f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = XPurple)
                }
            }
            // Поиск без совпадений не должен выглядеть как пустой чат.
            if (searchActive && messages.isNotEmpty() && chatMatchCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr("search_in_chat_empty"), style = XType.body, color = XMuted)
                }
            }
            // `height:130px;background:linear-gradient(180deg,var(--tscr) 0%,var(--tscr) 58%,
            // transparent 100%)` — the feed fades out as it runs under the header.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + headerHeight + (if (chatSearchOpen) searchChromeHeight else 0.dp) + 17.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to XTopScrim,
                            .58f to XTopScrim,
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .onSizeChanged { chromeHeight = with(xDensity) { it.height.toDp() } },
            ) {
                Box(Modifier.onSizeChanged { headerHeight = with(xDensity) { it.height.toDp() } }) {
                XChatHeader(
                    title = if (conversation.type == "direct") directPeerTitle else conversation.title,
                    // The canvas drops the ellipsis — the dots beside the line say it instead.
                    subtitleAccent = typingNames.isNotEmpty(),
                    subtitle = when {
                        typingNames.isNotEmpty() && conversation.type == "direct" -> "печатает"
                        typingNames.isNotEmpty() -> "${typingNames.joinToString()} печатает"
                        conversation.type == "channel" && communityMembers > 0 ->
                            tr("community_subscriber_count", "count" to "$communityMembers")
                        conversation.type == "channel" -> "канал"
                        conversation.type == "group" && communityMembers > 0 ->
                            tr("community_member_count", "count" to "$communityMembers")
                        conversation.type == "group" -> "группа"
                        peerPresence?.isOnline == true -> "в сети"
                        loading && peerPresence == null -> "обновляем статус…"
                        else -> formatPeerLastSeen(peerPresence)
                    },
                    avatar = {
                        val peerAvatar = peerPresence?.avatarUrl?.takeIf { it.isNotBlank() }
                            ?: conversation.peerAvatarUrl.takeIf { it.isNotBlank() }
                            ?: ""
                        if (conversation.type == "direct" && peerAvatar.startsWith("orionx-gradient:")) {
                            XGradientAvatar(peerAvatar.removePrefix("orionx-gradient:").toIntOrNull() ?: 0, directPeerTitle, 40.dp)
                        } else if (conversation.type == "direct" && peerAvatar.isNotBlank()) {
                            RemoteImage(peerAvatar, Modifier.size(40.dp).clip(CircleShape), ContentScale.Crop)
                        } else {
                            XLetterAvatar(if (conversation.type == "direct") directPeerTitle else conversation.title, 40.dp)
                        }
                    },
                    onBack = onBack,
                    // Direct: the peer card. Group/channel: the community hub, matching the
                    // canvas where tapping the title bar opens «о группе».
                    onIdentityClick = if (conversation.type == "direct") ({ showPeerProfile = true }) else onOpenCommunity,
                    // Макет ставит рядом с именем глиф выключенного звука — `volume_off` 15 px в `--mut`.
                    titleTrailing = if (communityMuted) ({
                        Icon(
                            Icons.Outlined.VolumeOff,
                            tr("community_muted"),
                            tint = XMuted,
                            modifier = Modifier.size(15.dp),
                        )
                    }) else null,
                    actions = {

                        // The canvas gives a group/channel header three controls — back, the
                        // identity pill and `more_vert`. Call buttons belong to a direct chat only.
                        if (conversation.type == "direct") {
                            XCirclePainterButton(
                                painter = painterResource(R.drawable.ic_call),
                                size = 45.dp,
                                contentDescription = "Звонок",
                                tint = XText,
                                onClick = { onStartCall(conversation.id, directPeerTitle, peerAvatarForCall, false) },
                            )

                        }
                        XCircleButton(
                            icon = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            size = 45.dp,
                            iconSize = 20.dp,
                            onClick = { communityMenuOpen = true },
                        )
                    },
                )
                }
                // Canvas «закреплено» plate: `top:156px;left:16px;right:16px;height:50px`,
                // `--sf` with `border-radius:18px`, `padding:11px 14px;gap:10px`, and a
                // `chevron_right` at the end. It floats clear of the header, not against it.
                communityPins.firstOrNull()?.let { pin ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 46.dp)
                            .padding(horizontal = 16.dp)
                            .xShadow(RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(XSurface)
                            .clickable { onOpenCommunity() }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.PushPin, null, tint = XAccent, modifier = Modifier.size(17.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr("pinned_bar_label").uppercase(),
                                style = XType.sectionLabel.copy(fontSize = 9.sp, letterSpacing = .14.em),
                                color = XMuted2,
                            )
                            Text(
                                pin.body,
                                style = XType.bodyStrong.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                color = XText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                        if (communityPins.size > 1) {
                            Text("${communityPins.size}", style = XType.meta, color = XMuted)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = XMuted, modifier = Modifier.size(17.dp))
                    }
                }
            }
            // Canvas «поиск в чате»: пилюля встаёт под плавающей шапкой, лента сужается
            // до совпадений, под полем — счётчик найденного.
            if (chatSearchOpen) {
                val searchFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = headerHeight + 12.dp)
                        .onSizeChanged { searchChromeHeight = with(xDensity) { it.height.toDp() } },
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        XSearchPill(
                            value = chatSearchQuery,
                            onValueChange = { chatSearchQuery = it },
                            placeholder = tr("search_in_chat_hint"),
                            modifier = Modifier.focusRequester(searchFocus),
                        )
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                tr("search_in_chat_found", "n" to "$chatMatchCount"),
                                style = XType.meta,
                                color = XMuted,
                                modifier = Modifier.padding(start = 20.dp, top = 7.dp),
                            )
                        }
                    }
                }
            }
            }
            commentsPost?.let { post ->
                XChannelComments(
                    post = messages.firstOrNull { it.id == post.id } ?: post,
                    channelTitle = conversation.title,
                    channelAvatar = {
                        val face = conversation.peerAvatarUrl.takeIf { it.isNotBlank() }
                        if (face != null) RemoteImage(face, Modifier.size(40.dp).clip(CircleShape), ContentScale.Crop)
                        else XLetterAvatar(conversation.title, 40.dp)
                    },
                    comments = messages.filter { it.replyToId == post.id },
                    avatars = communityFaces,
                    onBack = { commentsPost = null; replyingTo = null },
                    onOpenAttachment = { openAttachment(post) },
                    onLongClick = { message ->
                        if (!message.id.startsWith("local-") && !message.deletedForAll) selectedMessage = message
                    },
                )
            }
            // Canvas: `right:14px;bottom:96px` — a 44 px disc on `--sf` under
            // `0 12px 30px var(--shc)`, arriving from `translateY(12px) scale(.8)` with
            // `opacity .26s, transform .51s cubic-bezier(.22,1,.28,1)`.
            val jumpShown = !loading && showJumpToBottom && messages.isNotEmpty()
            val jumpEnter by animateFloatAsState(
                targetValue = if (jumpShown) 1f else 0f,
                animationSpec = tween(510, easing = CubicBezierEasing(.22f, 1f, .28f, 1f)),
                label = "jump-enter",
            )
            val jumpAlpha by animateFloatAsState(
                targetValue = if (jumpShown) 1f else 0f,
                animationSpec = tween(260),
                label = "jump-alpha",
            )
            if (jumpAlpha > .004f) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 7.dp)
                        .graphicsLayer {
                            alpha = jumpAlpha
                            translationY = 12.dp.toPx() * (1f - jumpEnter)
                            scaleX = .8f + .2f * jumpEnter
                            scaleY = .8f + .2f * jumpEnter
                        },
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .xShadow(CircleShape)
                            .clip(CircleShape)
                            .background(XSurface)
                            .xPressable(pressedScale = .93f) {
                                scope.launch {
                                    runCatching {
                                        if (reduceMotion) messageListState.scrollToItem(0)
                                        else messageListState.animateScrollToItem(0)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ArrowDownward,
                            "Вниз",
                            tint = XText,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    // `right:-5px;bottom:-4px;min-width:19px;height:19px;padding:0 5px` — the
                    // unread count hangs off the bottom corner, not the top one.
                    if (unreadWhileUp > 0) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 5.dp, y = 4.dp)
                                .height(19.dp)
                                .defaultMinSize(minWidth = 19.dp)
                                .clip(CircleShape)
                                .background(XAccent)
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (unreadWhileUp > 99) "99+" else "$unreadWhileUp",
                                color = XInverse,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }

            // Кружок уехал за пределы экрана, но продолжает играть — показываем мини-плеер.
            val activeNoteId = videoNotePlayback.activeId
            val activeNoteVisible = remember(activeNoteId) { derivedStateOf {
                activeNoteId != null && messageListState.layoutInfo.visibleItemsInfo.any { it.key == activeNoteId }
            } }
            if (activeNoteId != null && !activeNoteVisible.value && !videoNoteRecorder.active) {
                VideoNoteMiniPlayer(
                    controller = videoNotePlayback,
                    onExpand = { expandedVideoNote = messages.firstOrNull { it.id == activeNoteId } },
                    onClose = { videoNotePlayback.stop() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp),
                )
            }

            videoNoteHint?.let { hint ->
                Surface(
                    color = XSurface.copy(alpha = .96f),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, XBorder),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                ) {
                    Text(
                        hint,
                        color = XText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }

            if (videoNoteRecorder.active) {
                VideoNoteRecorderOverlay(
                    state = videoNoteRecorder,
                    onCaptured = { file, durationMs ->
                        videoNoteRecorder.close()
                        sendVideoNote(file, durationMs)
                    },
                    onCancelled = { videoNoteRecorder.close() },
                    onTooShort = {
                        videoNoteRecorder.close()
                        videoNoteHint = "Удерживайте кнопку, чтобы записать видеосообщение"
                    },
                    onError = { message ->
                        videoNoteRecorder.close()
                        error = message
                    },
                    bottomInset = composerHeight,
                )
            }
        }
        // The picker is part of the input area now, not a sheet over the screen.
        if (showMediaSheet) {
            XEmojiMediaSheet(
                onDismiss = { showMediaSheet = false },
                onEmojiSelected = { emoji -> text += emoji },
                onPickGif = { showMediaSheet = false; gifPicker.launch("image/gif") },
            )
            Spacer(Modifier.height(9.dp))
        }
        val composerDensity = LocalDensity.current
        // Во время поиска лента — список результатов, композер прячу.
        if (!searchActive) Box(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .onSizeChanged {
                    with(composerDensity) { composerHeight = it.height.toDp().coerceAtMost(220.dp) }
                },
        ) {
        if (conversation.type == "channel" && commentsPost == null && postComposerOpen) {
            XChannelPostScreen(
                channelTitle = conversation.title,
                subtitle = tr("post_subscribers", "count" to "$communityMembers"),
                text = text,
                onText = { text = it },
                coverUrl = attachment?.publicUrl?.takeIf {
                    attachment?.mimeType.orEmpty().startsWith("image/")
                },
                onPickCover = { imagePicker.launch("image/*") },
                onDropCover = { attachment = null },
                pinAfter = pinAfterPost,
                onPinAfter = { pinAfterPost = it },
                signAuthor = signPostAuthor,
                onSignAuthor = { signPostAuthor = it },
                sending = sending || uploading,
                onClose = { postComposerOpen = false; pinAfterPost = false },
                onPublish = { submitMessage(pinAfterPost && canPin) },
            )
        } else if (conversation.type == "channel" && commentsPost == null && !recordingVoice) {
            // `padding:6px 18px 22px` around a `--txt` pill: `height:51px;border-radius:26px;
            // gap:8px`, `edit` at 19 px and the label at `800 11.5px` with `.12em` tracking.
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 22.dp)
                    .height(51.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(XText)
                    .xPressable(pressedScale = .98f) { postComposerOpen = true },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Edit, null, tint = XInverse, modifier = Modifier.size(19.dp))
                Text(
                    tr("channel_new_post").uppercase(),
                    style = XType.sectionLabel.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = .12.em,
                    ),
                    color = XInverse,
                    maxLines = 1,
                )
            }
        } else {
        XComposer(
            text = text,
            onText = { value -> text = value; if (editing != null && value.isBlank()) editing = null },
            placeholder = if (editing != null) tr("edit") else tr("composer_placeholder"),
            sending = sending || uploading,
            replyPreview = if (commentsPost != null) null else replyingTo,
            editing = editing != null,
            attachment = attachment,
            recordingVoice = recordingVoice,
            recordingSeconds = recordingSeconds,
            onCancelContext = {
                replyingTo = null
                editing = null
                attachment = null
                if (recordingVoice) stopVoiceRecording(send = false)
            },
            onAttach = { showAttachmentSheet = true },
            onEmoji = { showMediaSheet = true },
            emojiOpen = showMediaSheet,
            mediaMode = mediaMode,
            onMediaModeToggle = { mediaMode = if (mediaMode == XMediaComposeMode.VOICE) XMediaComposeMode.VIDEO else XMediaComposeMode.VOICE },
            onVoice = { requestVoiceRecording() },
            voiceCancelArmed = voiceCancelArmed,
            voiceLocked = voiceLocked,
            onVoiceHoldStart = { requestVoiceRecording() },
            onVoiceHoldDrag = { dx, dy ->
                if (!voiceLocked) {
                    voiceCancelArmed = dx < -X_REC_CANCEL_DP
                    if (dy < -X_REC_LOCK_DP && !voiceCancelArmed) voiceLocked = true
                }
            },
            onVoiceHoldRelease = {
                // A locked take keeps running until the next tap; otherwise the release
                // either sends it or throws it away.
                if (!voiceLocked) stopVoiceRecording(send = !voiceCancelArmed)
            },
            videoNoteRecorder = videoNoteRecorder,
            onVideoNoteHoldStart = { beginVideoNoteRecording() },
            onSend = { submitMessage(false) },
        )
        }
        }
    }

    if (showAttachmentSheet) {
        XAttachmentSheet(
            onDismiss = { showAttachmentSheet = false },
            onPhoto = { showAttachmentSheet = false; imagePicker.launch("image/*") },
            onVideo = { showAttachmentSheet = false; videoPicker.launch("video/*") },
            onFile = { showAttachmentSheet = false; filePicker.launch("*/*") },
            onAudio = { showAttachmentSheet = false; filePicker.launch("audio/*") },
        )
    }


    expandedVideoNote?.let { note ->
        VideoNoteViewerDialog(
            controller = videoNotePlayback,
            senderName = note.senderName,
            sentAt = formatMessageTime(note.createdAt),
            onDismiss = { expandedVideoNote = null },
            onForward = {
                expandedVideoNote = null
                forwardingMessage = note
            },
        )
    }

    pendingPhotoUri?.let { uri ->
        OrionPhotoEditorDialog(
            source = uri,
            onDismiss = { pendingPhotoUri = null },
            onEdited = { edited -> pendingPhotoUri = null; queueEditedMedia(edited) },
            onError = { message -> error = message },
        )
    }

    pendingVideoUri?.let { uri ->
        OrionVideoEditorDialog(
            source = uri,
            onDismiss = { pendingVideoUri = null },
            onEdited = { edited -> pendingVideoUri = null; queueEditedMedia(edited) },
            onError = { message -> error = message },
        )
    }

    mediaPlayerMessage?.let { media ->
        OrionMediaPlayerDialog(
            url = media.attachmentUrl.orEmpty(),
            title = media.attachmentName.orEmpty(),
            mimeType = media.attachmentType.orEmpty(),
            senderName = media.senderName,
            sentAt = formatMessageTime(media.createdAt),
            onDismiss = { mediaPlayerMessage = null },
            onForward = {
                mediaPlayerMessage = null
                forwardingMessage = media
            },
        )
    }

    forwardingMessage?.let { message ->
        XForwardMessageDialog(
            api = api,
            session = session,
            message = message,
            onDismiss = { forwardingMessage = null },
            onForwarded = {
                forwardingMessage = null
                error = null
                android.widget.Toast.makeText(context, "Сообщение переслано", android.widget.Toast.LENGTH_SHORT).show()
            },
            onError = { error = it },
        )
    }

    if (confirmLeaveCommunity) {
        val channel = conversation.type == "channel"
        XCommunityConfirmSheet(
            title = if (channel) tr("community_unsubscribe") else tr("community_leave"),
            message = tr(
                if (channel) "community_unsubscribe_hint" else "community_leave_hint",
                "name" to conversation.title,
            ),
            confirmLabel = if (channel) tr("community_unsubscribe") else tr("community_leave_short"),
            onConfirm = {
                confirmLeaveCommunity = false
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { api.leaveConversation(session.accessToken, conversation.id) } }
                        .onSuccess { onBack() }
                        .onFailure { error = localizedNetworkError(it) }
                }
            },
            onDismiss = { confirmLeaveCommunity = false },
        )
    }

    // Canvas-шит «очистить историю»: «выберите, у кого удалить сообщения» —
    // «только у себя» (hide_message_for_me) или «для всех» (свои сообщения).
    if (clearHistorySheet) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { clearHistorySheet = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.BottomCenter) {
                Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        Modifier.fillMaxWidth().clip(XShape.panel).background(XSurfaceSoft).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(tr("clear_history"), style = XType.rowTitle, color = XText)
                        Text(tr("clear_history_hint"), style = XType.body, color = XMuted)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(XSurfaceSoft)
                            .clickable {
                                clearHistorySheet = false
                                scope.launch {
                                    val ids = messages.filter { !it.id.startsWith("local-") && !it.deletedForAll }.map { it.id }
                                    runCatching {
                                        withContext(Dispatchers.IO) { ids.forEach { api.hideMessageForMe(session.accessToken, it) } }
                                    }
                                        .onSuccess { refresh() }
                                        .onFailure { error = localizedNetworkError(it) }
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Person, null, tint = XText, modifier = Modifier.size(19.dp))
                        Text(tr("clear_history_mine"), style = XType.rowTitle, color = XText)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(XSurfaceSoft)
                            .clickable {
                                clearHistorySheet = false
                                scope.launch {
                                    val ids = messages
                                        .filter { it.senderId == session.userId && !it.id.startsWith("local-") && !it.deletedForAll }
                                        .map { it.id }
                                    runCatching {
                                        withContext(Dispatchers.IO) { ids.forEach { api.deleteMessageForAll(session.accessToken, it) } }
                                    }
                                        .onSuccess { refresh() }
                                        .onFailure { error = localizedNetworkError(it) }
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Group, null, tint = XRed, modifier = Modifier.size(19.dp))
                        Text(tr("clear_history_all"), style = XType.rowTitle, color = XRed)
                    }
                    XSurfacePill(label = tr("cancel"), modifier = Modifier.fillMaxWidth(), height = 52.dp, onClick = { clearHistorySheet = false })
                }
            }
        }
    }

    selectedMessage?.let { message ->
        val mine = message.senderId == session.userId
        XMessageActionsDialog(
            anchor = selectedAnchor,
            message = message,
            mine = mine,
            reactionChoices = reactionChoices,
            onDismiss = { selectedMessage = null },
            onReply = {
                replyingTo = message
                editing = null
                selectedMessage = null
            },
            onEdit = {
                editing = message
                replyingTo = null
                text = message.body
                selectedMessage = null
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OrionX message", message.body))
                selectedMessage = null
            },
            onReaction = { emoji ->
                selectedMessage = null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { api.toggleReaction(session.accessToken, message.id, emoji) } }
                        .onSuccess { refresh() }
                        .onFailure { error = localizedNetworkError(it) }
                }
            },
            onForward = {
                selectedMessage = null
                forwardingMessage = message
            },
            onSaveToNotes = {
                selectedMessage = null
                XNotesStore(context, session.userId).add(
                    title = message.body.lineSequence().firstOrNull().orEmpty().take(60),
                    text = message.body,
                    source = if (conversation.type == "direct") directPeerTitle else conversation.title,
                )
                android.widget.Toast.makeText(context, tr("saved_to_notes"), android.widget.Toast.LENGTH_SHORT).show()
            },
            // Only staff of a group/channel may pin, so the row is hidden otherwise.
            pinned = communityPins.any { it.messageId == message.id },
            onTogglePin = if (canPin) {
                {
                    val shouldPin = communityPins.none { it.messageId == message.id }
                    selectedMessage = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                api.setMessagePinned(session.accessToken, conversation.id, message.id, shouldPin)
                                api.communityPins(session.accessToken, conversation.id)
                            }
                        }
                            .onSuccess { communityPins = it }
                            .onFailure { error = localizedNetworkError(it) }
                    }
                }
            } else null,
            onDeleteForMe = {
                selectedMessage = null
                // Удалённый кружок не должен продолжать играть.
                if (videoNotePlayback.activeId == message.id) {
                    videoNotePlayback.stop()
                    expandedVideoNote = null
                }
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { api.hideMessageForMe(session.accessToken, message.id) } }
                        .onSuccess { refresh() }
                        .onFailure { error = localizedNetworkError(it) }
                }
            },
            onDeleteForAll = if (mine) {
                {
                    selectedMessage = null
                    if (videoNotePlayback.activeId == message.id) {
                        videoNotePlayback.stop()
                        expandedVideoNote = null
                    }
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { api.deleteMessageForAll(session.accessToken, message.id) } }
                            .onSuccess { refresh() }
                            .onFailure { error = localizedNetworkError(it) }
                    }
                }
            } else null,
        )
    }
    callInfo?.let { XInfoDialog("Звонки OrionX", it) { callInfo = null } }
    if (showPeerProfile && conversation.type == "direct") {
        val peerAvatarForProfile = peerPresence?.avatarUrl?.takeIf { it.isNotBlank() }
            ?: conversation.peerAvatarUrl.takeIf { it.isNotBlank() } ?: ""
        XPeerProfileDialog(
            peerPresence = peerPresence,
            fallbackTitle = directPeerTitle,
            fallbackAvatarUrl = peerAvatarForProfile,
            conversationId = conversation.id,
            messages = messages,
            onCall = { video -> onStartCall(conversation.id, directPeerTitle, peerAvatarForProfile, video) },
            onOpenMedia = { media ->
                if (isVideoNoteMessage(media) && !media.attachmentUrl.isNullOrBlank()) {
                    // Из архива кружок открывается своим круглым плеером, а не общим видео.
                    // «Просмотрено» — только по факту доигрывания.
                    showPeerProfile = false
                    videoNotePlayback.play(media.id, media.attachmentUrl.orEmpty())
                    expandedVideoNote = media
                } else {
                    mediaPlayerMessage = media
                }
            },
            onDismiss = { showPeerProfile = false },
        )
    }
}

private fun isVideoNoteMessage(message: CloudMessage): Boolean =
    VideoNote.isVideoNote(message.attachmentName, message.attachmentType)

@Composable
private fun XMessageBubble(
    message: CloudMessage,
    mine: Boolean,
    showSenderName: Boolean,
    onOpenAttachment: () -> Unit,
    /** Меню открывается у самого пузыря, поэтому наверх уходят его границы в окне. */
    onLongClick: (Rect) -> Unit,
    videoNotePlayback: VideoNotePlaybackController,
    videoNoteWatch: VideoNoteWatchState,
    onExpandVideoNote: () -> Unit,
    modifier: Modifier = Modifier,
    /** A group or a channel draws a tighter bubble with an avatar beside it. */
    community: Boolean = false,
    /** The label an owner gave this sender, or the default one; null for ordinary members. */
    adminTag: String? = null,
    senderAvatarUrl: String = "",
) {
    // Кружок — не пузырь: у него нет фона, рамки и хвостика, только круг и подпись поверх.
    if (isVideoNoteMessage(message) && !message.deletedForAll && !message.attachmentUrl.isNullOrBlank()) {
        XVideoNoteMessage(
            message = message,
            mine = mine,
            showSenderName = showSenderName,
            playback = videoNotePlayback,
            watch = videoNoteWatch,
            onPlay = onOpenAttachment,
            onLongClick = onLongClick,
            onExpand = onExpandVideoNote,
            modifier = modifier,
        )
        return
    }
    // Canvas bubbles: theirs is a `--sf` card with a flat bottom-left corner and a drop shadow,
    // mine is filled with `--txt` (so the text is `--inv`) with a flat bottom-right corner.
    // A community bubble is the tighter one — 22 px corners, 11/14 padding, 13.5 px body.
    val corner = if (community) 22.dp else 24.dp
    val bubbleShape = RoundedCornerShape(
        topStart = corner,
        topEnd = corner,
        bottomStart = if (mine) corner else 8.dp,
        bottomEnd = if (mine) 8.dp else corner,
    )
    val onBubble = if (mine) XInverse else XText
    val onBubbleMuted = if (mine) XOnMuted else XMuted
    // `padding:{{ m.bubPad }}` — a photo bubble hugs its picture at 5 px instead of the
    // 13/16 a text bubble uses.
    val mediaBubble = message.attachmentUrl != null && message.attachmentType.orEmpty().lowercase().let {
        it.startsWith("image/") || it.startsWith("video/")
    }
    val bubblePadding = when {
        mediaBubble -> PaddingValues(5.dp)
        community -> PaddingValues(horizontal = 14.dp, vertical = 11.dp)
        else -> PaddingValues(horizontal = 16.dp, vertical = 13.dp)
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Row(
            Modifier
                .fillMaxWidth(.78f)
                .wrapContentWidth(if (mine) Alignment.End else Alignment.Start),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // `width:30px;height:30px;border-radius:11px` — a community message carries the
            // sender beside the bubble; a direct chat has nobody to name.
            if (community && !mine) {
                if (senderAvatarUrl.startsWith("orionx-gradient:")) {
                    XGradientAvatar(senderAvatarUrl.removePrefix("orionx-gradient:").toIntOrNull() ?: 0, message.senderName, 30.dp, RoundedCornerShape(11.dp))
                } else if (senderAvatarUrl.isNotBlank()) {
                    RemoteImage(senderAvatarUrl, Modifier.size(30.dp).clip(RoundedCornerShape(11.dp)), ContentScale.Crop)
                } else {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(11.dp)).background(XAccentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            message.senderName.trim().take(1).uppercase(),
                            style = XType.meta.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                            color = XInverse,
                        )
                    }
                }
            }
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                var bounds by remember(message.id) { mutableStateOf(Rect.Zero) }
                Column(
                    Modifier
                        .onGloballyPositioned { bounds = it.boundsInWindow() }
                        .then(if (mine) Modifier else Modifier.xShadow(bubbleShape))
                        .clip(bubbleShape)
                        .background(if (mine) XText else XSurface)
                        .combinedClickable(
                            onClick = { if (message.attachmentUrl != null && !message.deletedForAll) onOpenAttachment() },
                            onLongClick = { onLongClick(bounds) },
                        )
                        .padding(bubblePadding),
                ) {
                    if (showSenderName && message.senderName.isNotBlank()) {
                        // `display:flex;gap:6px;padding-bottom:5px` — the name and, when the
                        // sender runs the place, the tag its owner chose.
                        Row(
                            Modifier.padding(bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                message.senderName,
                                style = XType.meta.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold),
                                color = XAccentSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            adminTag?.takeIf { it.isNotBlank() }?.let { tag ->
                                Text(
                                    tag.uppercase(),
                                    style = XType.meta.copy(
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = .08.em,
                                    ),
                                    color = XAccentSoft,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(XSurfaceRaised)
                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                    message.replyPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                        if (community) {
                            // `background:--sf2;border-radius:10px;padding:5px 9px;gap:7px`
                            Row(
                                Modifier
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (mine) XInverse.copy(alpha = .12f) else XSurfaceRaised)
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                message.replySenderName?.takeIf { it.isNotBlank() }?.let { name ->
                                    Text(
                                        name,
                                        style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold),
                                        color = XAccentSoft,
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    preview,
                                    style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                                    color = onBubbleMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            // `border-left:2px solid #8FA8FF;padding-left:9px`
                            Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(2.dp).height(30.dp).background(XAccentSoft))
                                Spacer(Modifier.width(9.dp))
                                Column {
                                    message.replySenderName?.takeIf { it.isNotBlank() }?.let { name ->
                                        Text(name, style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold), color = XAccentSoft, maxLines = 1)
                                    }
                                    Text(preview, style = XType.meta.copy(fontSize = 11.5.sp), color = onBubbleMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    if (message.attachmentUrl != null) {
                        XMessageMedia(message = message, mine = mine, onOpen = onOpenAttachment)
                        if (message.body.isNotBlank() && !mediaBubble) Spacer(Modifier.height(6.dp))
                    }

                    if (message.body.isNotBlank() || message.deletedForAll) {
                        Text(
                            if (message.deletedForAll) tr("message_deleted") else message.body,
                            // `font:500 13px/1.35;padding:8px 9px 3px` once it is a photo caption.
                            modifier = if (mediaBubble) Modifier.padding(start = 9.dp, end = 9.dp, top = 8.dp, bottom = 3.dp) else Modifier,
                            style = when {
                                mediaBubble -> XType.body.copy(fontSize = 13.sp, lineHeight = 17.6.sp)
                                community -> XType.body.copy(fontSize = 13.5.sp, lineHeight = 18.9.sp)
                                else -> XType.body.copy(fontSize = 14.sp, lineHeight = 19.6.sp)
                            },
                            color = if (message.deletedForAll) onBubbleMuted else onBubble,
                            fontStyle = if (message.deletedForAll) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                        )
                    }

                    // `display:flex;align-items:center;gap:6px;padding-top:7px` — the canvas marks
                    // delivery with a dot before the time, not with check marks.
                    Row(
                        Modifier.padding(top = if (community) 6.dp else 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (mine) XAckDot(message.readByOthers, message.deliveredToOthers)
                        Text(
                            formatMessageTime(message.createdAt),
                            style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = onBubbleMuted,
                        )
                        if (message.editedAt != null) {
                            Text(
                                tr("edited"),
                                style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = onBubbleMuted,
                            )
                        }
                        // A community reaction rides in the meta row on `--sf2`, not under the
                        // bubble: `border-radius:999px;padding:2px 7px`.
                        if (community && message.reactions.isNotEmpty()) {
                            message.reactions.entries.take(3).forEach { (emoji, count) ->
                                Text(
                                    normalizeEmojiText(emoji) + " " + count,
                                    style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    color = onBubble,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (mine) XInverse.copy(alpha = .10f) else XSurfaceRaised)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                // `align-self:{{ m.reactAlign }};margin-top:-7px;z-index:2;background:var(--sf);
                // border-radius:999px;padding:5px 10px` — in a direct chat the reaction rides the
                // edge of the bubble instead of sitting inside it.
                if (!community && message.reactions.isNotEmpty()) {
                    Row(
                        Modifier.offset(y = (-7).dp).zIndex(2f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        message.reactions.entries.take(4).forEach { (emoji, count) ->
                            Row(
                                Modifier
                                    .xShadow(CircleShape)
                                    .clip(CircleShape)
                                    .background(XSurface)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(normalizeEmojiText(emoji), fontSize = 12.sp, lineHeight = 12.sp)
                                Text(
                                    count.toString(),
                                    style = XType.meta.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                    color = XMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The канва gives every participant a colour of their own; pick it from the id, so it is stable. */
private val X_SPEAKER_INKS = listOf(
    Color(0xFF8FA8FF),
    Color(0xFFE39BD8),
    Color(0xFFFFB48A),
    Color(0xFF3ED27E),
    Color(0xFF9BE0FF),
    Color(0xFFC9B8FF),
)

private fun xSpeakerInk(id: String): Color =
    X_SPEAKER_INKS[((id.hashCode() % X_SPEAKER_INKS.size) + X_SPEAKER_INKS.size) % X_SPEAKER_INKS.size]

/**
 * «новый пост» — the editor a channel opens instead of the input row.
 *
 * Header `padding:0 16px 10px;gap:11px`, body `2px 16px 20px;gap:11px`, and a wide pill at
 * the bottom. The editor card is `--sf` with `border-radius:26px;padding:16px 18px 18px`.
 */
@Composable
private fun XChannelPostScreen(
    channelTitle: String,
    subtitle: String,
    text: String,
    onText: (String) -> Unit,
    coverUrl: String?,
    onPickCover: () -> Unit,
    onDropCover: () -> Unit,
    pinAfter: Boolean,
    onPinAfter: (Boolean) -> Unit,
    signAuthor: Boolean,
    onSignAuthor: (Boolean) -> Unit,
    sending: Boolean,
    onClose: () -> Unit,
    onPublish: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            XCircleButton(
                icon = Icons.Outlined.Close,
                contentDescription = tr("cancel"),
                size = 41.dp,
                iconSize = 20.dp,
                onClick = onClose,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    tr("channel_new_post"),
                    style = XType.rowTitle.copy(fontSize = 16.sp),
                    color = XText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$channelTitle · $subtitle",
                    style = XType.meta.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                    color = XMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .xShadow(RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(XSurface)
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("post_body_label").uppercase(),
                        style = XType.sectionLabel.copy(fontSize = 9.5.sp, letterSpacing = .14.em),
                        color = XMuted2,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${text.length}/4096",
                        style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                        color = XMuted2,
                    )
                }
                // `background:var(--sf2);border-radius:18px;padding:14px;font:500 13.5px/1.5`
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(XSurfaceRaised)
                        .padding(14.dp),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            tr("post_body_placeholder"),
                            style = XType.body.copy(fontSize = 13.5.sp, lineHeight = 20.dp.value.sp),
                            color = XMuted,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { if (it.length <= 4096) onText(it) },
                        textStyle = XType.body.copy(fontSize = 13.5.sp, lineHeight = 20.dp.value.sp, color = XText),
                        cursorBrush = SolidColor(XAccent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // `height:110px;border-radius:18px` — the cover slot.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .xHatch()
                        .xPressable(pressedScale = .98f) { if (coverUrl == null) onPickCover() else onDropCover() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (coverUrl != null) {
                        RemoteImage(coverUrl, Modifier.matchParentSize(), ContentScale.Crop)
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x9E080612)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Close, null, tint = Color(0xFFFCFBFF), modifier = Modifier.size(15.dp))
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(Icons.Outlined.AddPhotoAlternate, null, tint = XMuted, modifier = Modifier.size(22.dp))
                            Text(
                                tr("post_add_cover"),
                                style = XType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = XMuted,
                            )
                        }
                    }
                }
            }

            // `border-radius:26px;padding:6px 18px` — the switches under the editor.
            Column(
                Modifier
                    .fillMaxWidth()
                    .xShadow(RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(XSurface)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Outlined.Draw, null, tint = XText, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("post_sign_author"),
                            style = XType.rowTitle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                            color = XText,
                        )
                        Text(
                            tr("post_sign_author_hint"),
                            style = XType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = XMuted,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    XCanvasSwitch(checked = signAuthor) { onSignAuthor(!signAuthor) }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Outlined.PushPin, null, tint = XText, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("post_pin_after"),
                            style = XType.rowTitle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                            color = XText,
                        )
                        Text(
                            tr("post_pin_after_sub"),
                            style = XType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = XMuted,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    XCanvasSwitch(checked = pinAfter) { onPinAfter(!pinAfter) }
                }
            }
        }

        // `padding:6px 18px 22px` around a `height:51px;border-radius:26px` pill.
        val ready = text.isNotBlank() && !sending
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 22.dp)
                .height(51.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(if (ready) XText else XSurface)
                .xPressable(pressedScale = .98f, enabled = ready, onClick = onPublish),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (if (sending) tr("sending") else tr("post_publish")).uppercase(),
                style = XType.sectionLabel.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .12.em),
                color = if (ready) XInverse else XMuted2,
                maxLines = 1,
            )
        }
    }
}

/** `repeating-linear-gradient(135deg,var(--hairA) 0 10px,var(--hairB) 10px 20px)`. */
private fun Modifier.xHatch(): Modifier = this.drawBehind {
    val step = 10.dp.toPx()
    drawRect(XHairline)
    var x = -size.height
    while (x < size.width + size.height) {
        drawLine(
            color = XBorder,
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = step,
        )
        x += step * 2
    }
}

/**
 * The discussion under a channel post — canvas «комментарии канала».
 *
 * The post stays pinned at the top as a `--sf` card with `border-radius:22px`, and the
 * comments run under it: `padding:2px 16px 16px;gap:10px`, each a 30 px avatar and a
 * `20px 20px 20px 8px` bubble in the speaker's colour.
 */
@Composable
private fun XChannelComments(
    post: CloudMessage,
    channelTitle: String,
    channelAvatar: @Composable () -> Unit,
    comments: List<CloudMessage>,
    avatars: Map<String, String>,
    onBack: () -> Unit,
    onOpenAttachment: () -> Unit,
    onLongClick: (CloudMessage) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // `padding:2px 12px 10px;gap:8px` — back, the post it belongs to, and a way out.
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            XCircleButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = tr("back"),
                size = 45.dp,
                iconSize = 20.dp,
                onClick = onBack,
            )
            Row(
                Modifier
                    .weight(1f)
                    .height(45.dp)
                    .xShadow(CircleShape)
                    .clip(CircleShape)
                    .background(XSurface)
                    .padding(start = 3.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                channelAvatar()
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("channel_discussion_title", "name" to channelTitle),
                        style = XType.rowTitle.copy(fontSize = 14.5.sp),
                        color = XText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        tr("channel_discussion_sub"),
                        style = XType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = XMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            XCircleButton(
                icon = Icons.Outlined.DynamicFeed,
                contentDescription = null,
                size = 45.dp,
                iconSize = 20.dp,
                onClick = onBack,
            )
        }

        // `border-radius:22px;padding:12px 14px 13px;gap:9px` — the post, kept in view.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .xShadow(RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(XSurface)
                .clickable { if (post.attachmentUrl != null) onOpenAttachment() }
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Outlined.Campaign, null, tint = XAccent, modifier = Modifier.size(15.dp))
                Text(
                    tr("channel_post_label").uppercase(),
                    style = XType.meta.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = .14.em,
                    ),
                    color = XMuted2,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatMessageTime(post.createdAt),
                    style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = XMuted2,
                    maxLines = 1,
                )
            }
            post.attachmentUrl?.takeIf { post.attachmentType.orEmpty().lowercase().startsWith("image/") }?.let { cover ->
                var ratio by remember(post.id) { mutableStateOf(302f / 87f) }
                RemoteImage(
                    url = cover,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio.coerceIn(1f, 3.5f))
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                    onAspect = { ratio = it },
                )
            }
            if (post.body.isNotBlank()) {
                Text(
                    post.body,
                    style = XType.body.copy(fontSize = 12.5.sp, lineHeight = 18.1.sp),
                    color = XText,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        Box(Modifier.weight(1f).fillMaxWidth()) {
        // `padding:2px 16px 16px;gap:10px`
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "comments-count") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        tr("channel_comments_count", "count" to comments.size.toString()),
                        style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .08.em),
                        color = XMuted,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(XSurfaceRaised)
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    )
                }
            }
            items(comments, key = { it.id }) { comment ->
                XCommentRow(
                    comment = comment,
                    avatarUrl = avatars[comment.senderId].orEmpty(),
                    onLongClick = { onLongClick(comment) },
                )
            }
        }

        // Та же кнопка «вниз», что и в переписке: `right:14px;bottom:96px`, 44 px на `--sf`.
        val notAtEnd by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                last != null && last.index < info.totalItemsCount - 1
            }
        }
        val jumpAlpha by animateFloatAsState(if (notAtEnd) 1f else 0f, tween(260), label = "comments-jump")
        if (jumpAlpha > .004f) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 14.dp)
                    .graphicsLayer { alpha = jumpAlpha }
                    .size(44.dp)
                    .xShadow(CircleShape)
                    .clip(CircleShape)
                    .background(XSurface)
                    .xPressable(pressedScale = .93f) {
                        scope.launch {
                            runCatching { listState.animateScrollToItem((comments.size).coerceAtLeast(0)) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ArrowDownward, "Вниз", tint = XText, modifier = Modifier.size(22.dp))
            }
        }
        }
    }
}

/** `gap:8px;align-items:flex-end` — a 30 px avatar and a `20/20/20/8` bubble. */
@Composable
private fun XCommentRow(comment: CloudMessage, avatarUrl: String, onLongClick: () -> Unit) {
    val ink = xSpeakerInk(comment.senderId)
    Row(
        Modifier.fillMaxWidth(.78f),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (avatarUrl.startsWith("orionx-gradient:")) {
            XGradientAvatar(avatarUrl.removePrefix("orionx-gradient:").toIntOrNull() ?: 0, comment.senderName, 30.dp, RoundedCornerShape(11.dp))
        } else if (avatarUrl.isNotBlank()) {
            RemoteImage(avatarUrl, Modifier.size(30.dp).clip(RoundedCornerShape(11.dp)), ContentScale.Crop)
        } else {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(11.dp)).background(ink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    comment.senderName.trim().take(1).uppercase(),
                    style = XType.meta.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                    color = XInverse,
                )
            }
        }
        Column(
            Modifier
                .xShadow(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 8.dp))
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 8.dp))
                .background(XSurface)
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                comment.senderName,
                style = XType.meta.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 5.dp),
            )
            Text(
                comment.body,
                style = XType.body.copy(fontSize = 13.sp, lineHeight = 18.2.sp),
                color = XText,
            )
            Text(
                formatMessageTime(comment.createdAt),
                style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                color = XMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * A channel post — canvas «канал» card.
 *
 * `border-radius:26px;padding:14px;gap:11px` on `--sf` under `0 12px 30px var(--shc)`: the
 * tags first, then the cover at `border-radius:18px`, the body at `500 13.5px/1.45`, the
 * reactions, and a comment strip that runs the full width of the card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XChannelPost(
    message: CloudMessage,
    pinned: Boolean,
    fresh: Boolean,
    comments: Int,
    onOpenAttachment: () -> Unit,
    onLongClick: (Rect) -> Unit,
    onComment: () -> Unit,
    onReact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cover = message.attachmentUrl?.takeIf {
        message.attachmentType.orEmpty().lowercase().startsWith("image/")
    }
    var bounds by remember(message.id) { mutableStateOf(Rect.Zero) }
    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .xShadow(RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(XSurface)
            .combinedClickable(
                onClick = { if (message.attachmentUrl != null) onOpenAttachment() },
                onLongClick = { onLongClick(bounds) },
            ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            // `padding:5px 9px;border-radius:999px;font:800 8.5px;letter-spacing:.1em`
            if (pinned || fresh) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (pinned) XPostTag(tr("channel_pinned_tag"), XSurfaceRaised, XAccent)
                    if (fresh) XPostTag(tr("channel_published"), XGreen, Color(0xFF0C1A12))
                }
            }
            if (cover != null) {
                var ratio by remember(message.id) { mutableStateOf(302f / 146f) }
                RemoteImage(
                    url = cover,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio.coerceIn(.7f, 2.4f))
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop,
                    onAspect = { ratio = it },
                )
            } else if (message.attachmentUrl != null) {
                XMessageMedia(message = message, mine = false, onOpen = onOpenAttachment)
            }
            if (message.body.isNotBlank() || message.deletedForAll) {
                Text(
                    if (message.deletedForAll) tr("message_deleted") else message.body,
                    style = XType.body.copy(fontSize = 13.5.sp, lineHeight = 19.6.sp),
                    color = if (message.deletedForAll) XMuted else XText,
                )
            }
            if (message.editedAt != null) {
                // `edit_note` at 14 px with `700 10.5px` beside it, both `--mut2`.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.EditNote, null, tint = XMuted2, modifier = Modifier.size(14.dp))
                    Text(
                        tr("edited"),
                        style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                        color = XMuted2,
                    )
                }
            }
            // «подписать автором»: под постом стоит имя опубликовавшего (`edit_note {{p.sign}}`).
            if (message.authorSigned && message.senderName.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.EditNote, null, tint = XMuted2, modifier = Modifier.size(14.dp))
                    Text(
                        message.senderName,
                        style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                        color = XMuted2,
                    )
                }
            }
            // `display:flex;flex-wrap:wrap;gap:7px` — the chips, the picker, then the time.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                message.reactions.entries.take(6).forEach { (emoji, count) ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(XSurfaceRaised)
                            .xPressable(pressedScale = .94f, onClick = onReact)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(normalizeEmojiText(emoji), fontSize = 14.sp, lineHeight = 14.sp)
                        Text(
                            count.toString(),
                            style = XType.meta.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold),
                            color = XMuted,
                        )
                    }
                }
                Box(
                    Modifier
                        .size(width = 38.dp, height = 34.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(XSurfaceRaised)
                        .xPressable(pressedScale = .94f, onClick = onReact),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.AddReaction, null, tint = XMuted, modifier = Modifier.size(17.dp))
                }
                Text(
                    formatMessageTime(message.createdAt),
                    style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = XMuted2,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        // `border-top:1px solid var(--hair);margin:0 -14px -14px;padding:13px 15px 14px`
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(XBorder, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                }
                .xPressable(onClick = onComment)
                .padding(start = 15.dp, end = 15.dp, top = 13.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = XAccent, modifier = Modifier.size(18.dp))
            Text(
                tr("channel_comment_action").uppercase(),
                style = XType.sectionLabel.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = .1.em,
                ),
                color = XAccent,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (comments > 0) {
                Text(
                    comments.toString(),
                    style = XType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
                    color = XAccent,
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = XAccent, modifier = Modifier.size(17.dp))
        }
    }
}

/** One of the two post tags: pinned on `--sf2` in accent, published on green. */
@Composable
private fun XPostTag(label: String, background: Color, ink: Color) {
    Text(
        label.uppercase(),
        style = XType.meta.copy(
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = .1.em,
        ),
        color = ink,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

/** Тело служебной строки приходит разбором — `событие|имя|имя`, чтобы текст был на языке приложения. */
private fun xServiceLine(body: String): String {
    val parts = body.split("|")
    val one = parts.getOrNull(1).orEmpty()
    val two = parts.getOrNull(2).orEmpty()
    return when (parts.firstOrNull()) {
        "member_joined" -> tr("service_joined", "name" to one)
        "member_added" -> tr("service_added", "actor" to one, "name" to two)
        "member_left" -> tr("service_left", "name" to one)
        "member_removed" -> tr("service_removed", "actor" to one, "name" to two)
        "renamed" -> tr("service_renamed", "actor" to one, "title" to two)
        else -> body
    }
}

/**
 * A join, a rename, a pin — canvas `m.service`.
 *
 * `align-self:center;background:var(--sf2);border-radius:999px;padding:7px 13px;
 * font:700 10.5px;letter-spacing:.08em;color:var(--mut)`.
 */
@Composable
private fun XSystemPlate(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .08.em),
            color = XMuted,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(CircleShape)
                .background(XSurfaceRaised)
                .padding(horizontal = 13.dp, vertical = 7.dp),
        )
    }
}

/**
 * The delivery mark inside a bubble — canvas `ackBg` / `ackGlow` / `ackScale`.
 *
 * Read is green with a 3 px halo, delivered and sent step down in size and in how much
 * of the bubble ink they take, and every change is animated rather than swapped.
 */
@Composable
private fun XAckDot(read: Boolean, delivered: Boolean) {
    val darkTheme = XText.luminance() > .5f
    val target = when {
        read -> XGreen
        delivered -> XInverse.copy(alpha = if (darkTheme) .42f else .55f)
        else -> XInverse.copy(alpha = if (darkTheme) .75f else .22f)
    }
    val ink by animateColorAsState(target, tween(500, easing = CubicBezierEasing(.2f, .8f, .2f, 1f)), label = "ack-ink")
    val scale by animateFloatAsState(
        targetValue = if (read) 1f else if (delivered) .9f else .8f,
        animationSpec = tween(400, easing = CubicBezierEasing(.34f, 1.5f, .64f, 1f)),
        label = "ack-scale",
    )
    val halo by animateFloatAsState(if (read) 1f else 0f, tween(500), label = "ack-halo")
    Box(
        Modifier
            .size(7.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val r = size.minDimension / 2f
                if (halo > .004f) drawCircle(XGreen.copy(alpha = .18f * halo), radius = r + 3.dp.toPx())
                drawCircle(ink, radius = r)
            },
    )
}

/**
 * Видеосообщение в ленте: круг без пузыря, поверх — время и галочки доставки,
 * рядом — реакции и имя отправителя в группах.
 */
@Composable
private fun XVideoNoteMessage(
    message: CloudMessage,
    mine: Boolean,
    showSenderName: Boolean,
    playback: VideoNotePlaybackController,
    watch: VideoNoteWatchState,
    onPlay: () -> Unit,
    onLongClick: (Rect) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (showSenderName && message.senderName.isNotBlank()) {
                Text(
                    message.senderName,
                    color = XViolet,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
                )
            }
            var noteBounds by remember(message.id) { mutableStateOf(Rect.Zero) }
            VideoNoteBubble(
                messageId = message.id,
                url = message.attachmentUrl.orEmpty(),
                durationMs = VideoNote.durationMsFromName(message.attachmentName),
                unwatched = !mine && !watch.isWatched(message.id),
                controller = playback,
                onPlayRequest = onPlay,
                onLongPress = { onLongClick(noteBounds) },
                onExpand = onExpand,
                modifier = Modifier.onGloballyPositioned { noteBounds = it.boundsInWindow() },
                footerAlignment = if (mine) Alignment.End else Alignment.Start,
                footer = {
                    // Canvas: a video note carries no bubble, so its meta sits under the
                    // circle at `left:6px;bottom:-24px` — bare time and a 7 px state dot,
                    // not a dark plate laid over the video.
                    Row(
                        Modifier.padding(start = if (mine) 0.dp else 6.dp, end = if (mine) 6.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            formatMessageTime(message.createdAt),
                            style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = XMuted,
                            maxLines = 1,
                        )
                        if (mine) {
                            // `ackBg` / `ackGlow` / `ackScale`: read is green with a halo,
                            // delivered and sent step down in size and weight.
                            val read = message.readByOthers
                            val delivered = message.deliveredToOthers
                            val dot = when {
                                read -> XGreen
                                delivered -> XMuted
                                else -> XMuted.copy(alpha = .5f)
                            }
                            val dotScale = when {
                                read -> 1f
                                delivered -> .9f
                                else -> .8f
                            }
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .graphicsLayer { scaleX = dotScale; scaleY = dotScale }
                                    .then(
                                        if (read) Modifier.border(3.dp, XGreen.copy(alpha = .18f), CircleShape)
                                        else Modifier,
                                    )
                                    .clip(CircleShape)
                                    .background(dot),
                            )
                        }
                    }
                },
            )
            if (message.reactions.isNotEmpty()) {
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    message.reactions.entries.take(4).forEach { (emoji, count) ->
                        Surface(color = XSurfaceRaised, shape = RoundedCornerShape(50)) {
                            Text(
                                "${normalizeEmojiText(emoji)} $count",
                                color = XText,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** `width:3px;gap:2.5px;border-radius:2px` — the 22 bars the canvas draws for a voice note. */
private val X_VOICE_WAVE = listOf(7, 11, 16, 9, 13, 18, 12, 8, 15, 19, 11, 7, 14, 17, 10, 13, 8, 16, 12, 9, 15, 10)

/**
 * A voice note inside a bubble — canvas `m.voice`.
 *
 * It used to be a Material card: a translucent black plate, a violet play disc, the words
 * «Голосовое сообщение» and a `LinearProgressIndicator`. The canvas has none of that — a
 * 36 px disc and a 119 px waveform that fills as it plays, and the duration under it.
 */
@Composable
private fun XVoiceMessagePlayer(url: String, mine: Boolean, onFallbackOpen: () -> Unit) {
    // Плеер общий на весь процесс: строка только наблюдает. Так голосовая не
    // обрывается при выходе из чата и при прокрутке пузырька за экран.
    val active = XVoicePlayback.activeUrl == url
    val playing = active && XVoicePlayback.playing
    val failed = active && XVoicePlayback.failed
    val durationMs = when {
        active -> XVoicePlayback.durationMs
        else -> XVoiceWave.cachedDurationMs(url) ?: 0
    }
    val positionMs = if (active) XVoicePlayback.positionMs else 0
    val prepared = active && (XVoicePlayback.durationMs > 0 || XVoicePlayback.failed)

    LaunchedEffect(playing) {
        while (playing) {
            XVoicePlayback.refreshPosition()
            delay(180)
        }
    }

    fun seekTo(fraction: Float) {
        XVoicePlayback.seekFraction(url, fraction)
    }

    // Canvas: a 36 px disc and a 119 px waveform, no plate around them. Incoming puts the
    // disc on `--sf2`, outgoing on `--inv`, and the played bars take the bubble's own ink.
    val discBackground = if (mine) XInverse else XSurfaceRaised
    val discTint = if (mine) XText else XText
    val playedInk = if (mine) XInverse else XText
    val restInk = if (mine) XOnMuted else XMuted
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    // Measured from the recording itself; until that comes back the canvas pattern stands in.
    var wave by remember(url) { mutableStateOf(XVoiceWave.cached(url) ?: X_VOICE_WAVE) }
    LaunchedEffect(url) {
        if (url.isBlank() || XVoiceWave.cached(url) != null) return@LaunchedEffect
        val measured = withContext(Dispatchers.IO) { XVoiceWave.of(url, X_VOICE_WAVE.size) }
        if (measured != null) wave = measured
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(discBackground)
                .xPressable(pressedScale = .94f) {
                    if (failed) {
                        onFallbackOpen()
                    } else {
                        XVoicePlayback.toggle(url)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // `prepared` требует `active`, поэтому у КАЖДОЙ неиграющей голосовой вместо
            // треугольника крутился индикатор загрузки. Спиннер нужен только тогда, когда
            // запись уже выбрана и мы ждём плеер.
            when {
                failed -> Icon(Icons.Outlined.OpenInNew, null, tint = discTint, modifier = Modifier.size(18.dp))
                active && !prepared ->
                    CircularProgressIndicator(color = discTint, strokeWidth = 2.dp, modifier = Modifier.size(17.dp))
                playing -> Icon(
                    painterResource(R.drawable.ic_ms_pause),
                    "Пауза",
                    tint = discTint,
                    modifier = Modifier.size(20.dp),
                )
                else -> Icon(
                    painterResource(R.drawable.ic_ms_play_arrow),
                    "Воспроизвести",
                    tint = discTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // `position:relative;height:20px;width:119px` with the played copy clipped on top.
            Box(
                Modifier
                    .size(119.dp, 20.dp)
                    // Tap or drag anywhere on the wave to move through the take.
                    .pointerInput(prepared, durationMs) {
                        detectTapGestures { offset -> seekTo(offset.x / size.width) }
                    }
                    .pointerInput(prepared, durationMs) {
                        detectHorizontalDragGestures { change, _ ->
                            seekTo(change.position.x / size.width)
                            change.consume()
                        }
                    },
            ) {
                XVoiceWaveBars(wave, restInk)
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(0.dp)),
                ) {
                    XVoiceWaveBars(wave, playedInk, Modifier.width(119.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (failed) tr("voice_open_external") else formatMediaDuration(if (playing) positionMs else durationMs),
                    style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                    color = restInk,
                    maxLines = 1,
                )
                if (!playing && positionMs == 0) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(restInk))
                }
            }
        }
    }
}

/** One pass of the canvas waveform in a single colour. */
@Composable
private fun XVoiceWaveBars(bars: List<Int>, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        bars.forEach { height ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

private fun formatMediaDuration(milliseconds: Int): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun XMessageMedia(
    message: CloudMessage,
    mine: Boolean,
    onOpen: () -> Unit,
) {
    // Удалённое для всех вложение не рендерим: «Видео»-плитка ниже кликабельна
    // сама по себе и позволяла проиграть удалённый кружок.
    if (message.deletedForAll) return
    val type = message.attachmentType.orEmpty().lowercase()
    when {
        type.startsWith("audio/") -> {
            XVoiceMessagePlayer(message.attachmentUrl.orEmpty(), mine = mine, onFallbackOpen = onOpen)
        }
        type.startsWith("video/") -> {
            // `width:196px;border-radius:14px` with the canvas badge at `right:8px;bottom:8px`.
            Box(
                Modifier
                    .width(196.dp)
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(XSurfaceRaised)
                    .xPressable(pressedScale = .97f, onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color(0x9E080612)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color(0xFFFCFBFF), modifier = Modifier.size(26.dp))
                }
                Text(
                    tr("video_badge"),
                    style = XType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFFFCFBFF),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color(0x9E080612))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }
        type.startsWith("image/") -> {
            // The canvas card is 196 px wide and takes the shape of the picture it holds.
            var ratio by remember(message.id) { mutableStateOf(4f / 3f) }
            RemoteImage(
                url = message.attachmentUrl.orEmpty(),
                modifier = Modifier
                    .width(196.dp)
                    .aspectRatio(ratio.coerceIn(.62f, 1.9f))
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
                onAspect = { ratio = it },
            )
        }
        else -> {
            Surface(color = Color.Black.copy(alpha = .16f), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Description, null, tint = XCyan, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(message.attachmentName ?: "Файл", color = XCyan, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun XForwardMessageDialog(
    api: SupabaseApi,
    session: CloudSession,
    message: CloudMessage,
    onDismiss: () -> Unit,
    onForwarded: () -> Unit,
    onError: (String) -> Unit,
) {
    var conversations by remember(session.userId) { mutableStateOf<List<CloudConversation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sendingTo by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session.accessToken) {
        runCatching { withContext(Dispatchers.IO) { api.myConversations(session.accessToken, session.userId) } }
            .onSuccess { conversations = it.filterNot { conversation -> conversation.archived } }
            .onFailure { onError(localizedNetworkError(it)) }
        loading = false
    }

    AlertDialog(
        onDismissRequest = { if (sendingTo == null) onDismiss() },
        containerColor = XSurface,
        title = { Text("Переслать", color = XText, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) },
        text = {
            when {
                loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = XPurple)
                }
                conversations.isEmpty() -> Text("Нет доступных чатов", color = XMuted)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        val title = if (conversation.type == "direct") conversation.peerDisplayName.ifBlank { conversation.title } else conversation.title
                        Surface(
                            color = XSurfaceSoft,
                            shape = RoundedCornerShape(17.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, XBorder),
                            modifier = Modifier.fillMaxWidth().clickable(enabled = sendingTo == null) {
                                sendingTo = conversation.id
                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            val forwardedAttachment = message.attachmentUrl?.let { url ->
                                                UploadedFile(
                                                    publicUrl = url,
                                                    name = message.attachmentName ?: "media",
                                                    mimeType = message.attachmentType ?: "application/octet-stream",
                                                )
                                            }
                                            api.sendMessage(
                                                session.accessToken,
                                                conversation.id,
                                                session.userId,
                                                message.body,
                                                null,
                                                forwardedAttachment,
                                            )
                                        }
                                    }
                                    result.onSuccess { onForwarded() }
                                        .onFailure { onError(localizedNetworkError(it)); sendingTo = null }
                                }
                            },
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                XLetterAvatar(title, 42.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(title, color = XText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        when (conversation.type) {
                                            "group" -> "Группа"
                                            "channel" -> "Канал"
                                            else -> "Личный чат"
                                        },
                                        color = XMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                                if (sendingTo == conversation.id) CircularProgressIndicator(color = XViolet, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                                else Icon(Icons.AutoMirrored.Outlined.Forward, null, tint = XViolet)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = sendingTo == null) { Text("Отмена", color = XMuted) } },
    )
}

/**
 * The long-press menu for a message — canvas `ctxin` / `ctxbar`.
 *
 * It used to be a Material `AlertDialog` with a system font, a title and a «Закрыть»
 * button, which is nothing like the canvas. There the menu floats over a blurred scrim:
 * a pill of reactions on top, then a narrow card of actions, both springing in.
 *
 * `left:16px;right:16px;top:50%;transform:translateY(-50%);gap:7px`, panels on `--sf3`
 * with `box-shadow:0 18px 38px var(--shc2)`.
 */
@Composable
private fun XMessageActionsDialog(
    /** Пузырь, у которого открыли меню, в координатах окна. */
    anchor: Rect,
    message: CloudMessage,
    mine: Boolean,
    reactionChoices: List<String>,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onReaction: (String) -> Unit,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    /** «в блокнот» — текст сообщения уходит в заметки, как в макете. */
    onSaveToNotes: (() -> Unit)? = null,
    onDeleteForMe: () -> Unit,
    onDeleteForAll: (() -> Unit)? = null,
) {
    // Canvas `ctx`: one card, not two panels. `width:209px;border-radius:22px;padding:6px`
    // on `--sf3` under `0 26px 60px var(--shc2)`, the reactions inside it at the top.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(320, easing = CubicBezierEasing(.2f, 1f, .28f, 1f)))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val density = LocalDensity.current
        var menuSize by remember { mutableStateOf(IntSize.Zero) }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(XShadowStrong)
                .xPressable(onClick = onDismiss),
        ) {
            // Меню встаёт у пузыря: снизу, если там есть место, иначе сверху; по бокам
            // прижимается к краям экрана с отступом 12.
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            val gap = with(density) { 8.dp.toPx() }
            val edge = with(density) { 12.dp.toPx() }
            val menuW = if (menuSize.width > 0) menuSize.width.toFloat() else with(density) { 209.dp.toPx() }
            val menuH = if (menuSize.height > 0) menuSize.height.toFloat() else screenH * .4f
            val anchored = anchor != Rect.Zero
            val rawX = if (anchored) anchor.left else (screenW - menuW) / 2f
            val x = rawX.coerceIn(edge, (screenW - menuW - edge).coerceAtLeast(edge))
            val below = anchor.bottom + gap
            val above = anchor.top - menuH - gap
            val rawY = when {
                !anchored -> (screenH - menuH) / 2f
                below + menuH <= screenH - edge -> below
                above >= edge -> above
                else -> (screenH - menuH) / 2f
            }
            val y = rawY.coerceIn(edge, (screenH - menuH - edge).coerceAtLeast(edge))
            Column(
                Modifier
                    .offset { IntOffset(x.toInt(), y.toInt()) }
                    .onSizeChanged { menuSize = it }
                    .width(209.dp)
                    .graphicsLayer {
                        alpha = enter.value
                        // `transform-origin:20% 45%` with a .9 → 1 scale.
                        scaleX = .9f + .1f * enter.value
                        scaleY = .9f + .1f * enter.value
                        transformOrigin = TransformOrigin(.2f, .45f)
                    }
                    .xShadow(RoundedCornerShape(22.dp), elevation = 26.dp, shadowColor = XShadowStrong)
                    .clip(RoundedCornerShape(22.dp))
                    .background(XSurfaceSoft)
                    .padding(6.dp),
            ) {
                // `display:flex;gap:2px;padding:1px 1px 6px` — 38×31 cells on `11px` corners.
                Row(
                    Modifier.fillMaxWidth().padding(start = 1.dp, end = 1.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    normalizeReactionList(reactionChoices).take(5).forEach { emoji ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(31.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .xPressable(pressedScale = .88f) { onReaction(emoji) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emoji, fontSize = 16.sp, lineHeight = 16.sp)
                        }
                    }
                }

                // `padding:4px 0 1px` — rows of 30 px on `12px` corners.
                Column(Modifier.padding(top = 4.dp, bottom = 1.dp)) {
                    // У кружка нет текста — «Копировать» копирует пустоту,
                    // «Изменить» прицепляет текст к видеосообщению.
                    val isCircle = isVideoNoteMessage(message)
                    XCtxRow(Icons.Outlined.Reply, tr("reply"), onReply)
                    if (!isCircle) XCtxRow(Icons.Outlined.ContentCopy, tr("copy"), onCopy)
                    if (onForward != null) {
                        XCtxRow(Icons.Outlined.Shortcut, tr("forward"), onForward)
                    }
                    if (onSaveToNotes != null && message.body.isNotBlank()) {
                        XCtxRow(Icons.Outlined.EditNote, tr("save_to_notes"), onSaveToNotes)
                    }
                    if (onTogglePin != null) {
                        XCtxRow(
                            Icons.Outlined.PushPin,
                            if (pinned) tr("community_unpin") else tr("community_pin"),
                            onTogglePin,
                        )
                    }
                    if (mine && !isCircle) XCtxRow(Icons.Outlined.Edit, tr("edit"), onEdit)
                    XCtxRow(Icons.Outlined.DeleteOutline, tr("delete_for_me"), onDeleteForMe, danger = true)
                    if (onDeleteForAll != null) {
                        XCtxRow(Icons.Outlined.DeleteForever, tr("delete_for_all"), onDeleteForAll, danger = true)
                    }
                }
            }
        }
    }
}

/**
 * The bar that stands in for the input pill while a take is running — canvas `recing`.
 *
 * It is the pill, not a plate above it: same `height:54px;border-radius:999px;--sf` with
 * `box-shadow:0 12px 30px var(--shc)`, `padding:0 14px 0 16px;gap:11px`. Three states, and
 * the whole bar drops to `opacity:.72` once cancel is armed.
 */
@Composable
private fun XRecordingBar(
    seconds: Int,
    armed: Boolean,
    locked: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (armed) .72f else 1f, tween(180), label = "rec-bar-alpha")
    Row(
        modifier
            .height(54.dp)
            .graphicsLayer { this.alpha = alpha }
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(XSurface)
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(XRed))
        Text(
            "%d:%02d".format(seconds / 60, seconds % 60),
            style = XType.rowTitle.copy(fontSize = 14.sp),
            color = XText,
            maxLines = 1,
        )
        when {
            armed -> Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Delete, null, tint = XRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    tr("rec_release_to_cancel").uppercase(),
                    style = XType.sectionLabel.copy(fontSize = 9.5.sp, letterSpacing = .1.em),
                    color = XRed,
                    maxLines = 1,
                )
            }

            locked -> Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = XMuted2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(9.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(XSurfaceRaised)
                        .xPressable(pressedScale = .96f, onClick = onCancel)
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Text(
                        tr("cancel").uppercase(),
                        style = XType.sectionLabel.copy(fontSize = 9.5.sp, letterSpacing = .1.em),
                        color = XRed,
                        maxLines = 1,
                    )
                }
            }

            else -> {
                XRecordingWave(Modifier.weight(1f))
                XRecordingSwipeHint()
            }
        }
    }
}

/** Seven bars on `eq .7s ease-in-out` with a 0.1 s stagger — heights straight from the canvas. */
@Composable
private fun XRecordingWave(modifier: Modifier = Modifier) {
    val bars = listOf(9, 15, 7, 13, 10, 16, 8)
    val cycle = rememberInfiniteTransition(label = "rec-wave")
    Row(
        modifier.height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        bars.forEachIndexed { index, height ->
            // `0%,100% { scaleY(.3) }  50% { scaleY(1) }`
            val scale by cycle.animateFloat(
                initialValue = .3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(350, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 100),
                ),
                label = "rec-wave-$index",
            )
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(height.dp * scale)
                    .clip(RoundedCornerShape(2.dp))
                    .background(XRed),
            )
        }
    }
}

/** `swl 1.25s`: the hint slides 7 px left and back, fading between .5 and 1. */
@Composable
private fun XRecordingSwipeHint() {
    val cycle = rememberInfiniteTransition(label = "rec-hint")
    val phase by cycle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(625, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec-hint-phase",
    )
    Row(
        Modifier.graphicsLayer {
            translationX = -7.dp.toPx() * phase
            alpha = .5f + .5f * phase
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Outlined.ChevronLeft, null, tint = XMuted2, modifier = Modifier.size(16.dp))
        Text(
            tr("rec_swipe_cancel").uppercase(),
            style = XType.sectionLabel.copy(fontSize = 9.sp, letterSpacing = .09.em),
            color = XMuted2,
            maxLines = 1,
        )
    }
}

/** `padding:7px 9px;gap:9px;border-radius:12px`, glyph 16 px, label `700 11.5px`. */
@Composable
private fun XCtxRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = if (danger) XRed else XText
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .xPressable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            // В макете все пункты меню — строчными.
            label.lowercase(),
            style = XType.bodyStrong.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun XDialogAction(icon: ImageVector, text: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) XRed else XViolet)
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (danger) XRed else XText, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun XComposer(
    text: String,
    onText: (String) -> Unit,
    placeholder: String,
    sending: Boolean,
    replyPreview: CloudMessage? = null,
    editing: Boolean = false,
    attachment: UploadedFile? = null,
    recordingVoice: Boolean = false,
    recordingSeconds: Int = 0,
    onCancelContext: () -> Unit = {},
    onAttach: (() -> Unit)? = null,
    onEmoji: (() -> Unit)? = null,
    /** Панель эмодзи открыта — в макете кнопка становится лиловой. */
    emojiOpen: Boolean = false,
    mediaMode: XMediaComposeMode = XMediaComposeMode.VOICE,
    onMediaModeToggle: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    voiceCancelArmed: Boolean = false,
    voiceLocked: Boolean = false,
    onVoiceHoldStart: (() -> Unit)? = null,
    onVoiceHoldDrag: ((Float, Float) -> Unit)? = null,
    onVoiceHoldRelease: (() -> Unit)? = null,
    videoNoteRecorder: VideoNoteRecorderState? = null,
    onVideoNoteHoldStart: (() -> Unit)? = null,
    onSend: () -> Unit,
) {
    val recordingVideoNote = videoNoteRecorder?.active == true
    val canSend = (text.isNotBlank() || attachment != null) && !sending && !recordingVoice && !recordingVideoNote
    val density = LocalDensity.current
    // Canvas `CANCEL_AT = -74` — the swipe that arms cancel is short.
    val cancelDistancePx = remember(density) { with(density) { 74.dp.toPx() } }
    val lockDistancePx = remember(density) { with(density) { 72.dp.toPx() } }
    // The canvas has no composer bar: a single `--sf` pill floats over the feed with the send
    // button beside it, so the background gradient stays visible right down to the bottom edge.
    Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            // A running take is announced by the bar that replaces the input pill, so the
            // context plate stays out of it — otherwise both said the same thing at once.
            if (replyPreview != null || editing || attachment != null) {
                // `margin:0 16px 6px;border-radius:18px;padding:10px 14px;gap:10px` on `--sf`
                // with `0 12px 30px var(--shc)` — the label is a caption, the quote is the text.
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp)
                        .fillMaxWidth()
                        .xShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(XSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        when {
                            recordingVoice -> Icons.Outlined.GraphicEq
                            editing -> Icons.Outlined.Edit
                            attachment != null -> Icons.Outlined.AttachFile
                            else -> Icons.Outlined.Reply
                        },
                        null,
                        tint = if (recordingVoice) XRed else XAccentSoft,
                        modifier = Modifier.size(17.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                recordingVoice -> tr("composer_recording")
                                editing -> tr("composer_editing")
                                attachment != null -> tr("composer_attached")
                                else -> tr("composer_replying")
                            }.uppercase(),
                            style = XType.sectionLabel.copy(fontSize = 8.5.sp, letterSpacing = .14.em),
                            color = if (recordingVoice) XRed else XMuted,
                            maxLines = 1,
                        )
                        Text(
                            when {
                                recordingVoice -> "%02d:%02d".format(recordingSeconds / 60, recordingSeconds % 60)
                                attachment != null -> attachment.name
                                else -> replyPreview?.body.orEmpty()
                            },
                            style = XType.meta.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.4.sp),
                            color = XText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = tr("cancel"),
                        tint = XMuted,
                        modifier = Modifier.size(17.dp).xPressable(pressedScale = .9f, onClick = onCancelContext),
                    )
                }
            }

            val noteLockedForBar = videoNoteRecorder?.locked == true
            Row(
                Modifier.padding(start = 10.dp, end = 18.dp, top = 6.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                val inputsEnabled = !recordingVoice && !recordingVideoNote
                if (!inputsEnabled) {
                    XRecordingBar(
                        seconds = if (recordingVideoNote) ((videoNoteRecorder?.elapsedMs ?: 0L) / 1000L).toInt() else recordingSeconds,
                        armed = if (recordingVideoNote) videoNoteRecorder?.cancelling == true else voiceCancelArmed,
                        locked = if (recordingVideoNote) noteLockedForBar else voiceLocked,
                        onCancel = {
                            if (recordingVideoNote) videoNoteRecorder?.cancel() else onVoice?.invoke()
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                // `height:54px;border-radius:999px;background:var(--sf);padding:0 8px`
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp)
                        .xShadow(CircleShape)
                        .clip(CircleShape)
                        .background(XSurface)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(XSurfaceRaised)
                            .clickable(enabled = onAttach != null && !sending && inputsEnabled) { onAttach?.invoke() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            "Вложения",
                            tint = if (inputsEnabled) XText else XMuted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(placeholder, style = XType.body.copy(fontSize = 14.sp), color = XMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = onText,
                            enabled = inputsEnabled,
                            maxLines = 4,
                            textStyle = XType.body.copy(fontSize = 14.sp, color = XText),
                            cursorBrush = SolidColor(XAccent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // `emojiBtnBg` / `emojiBtnFg`: открытая панель красит кнопку в #5B3FD6.
                    val emojiBg by animateColorAsState(
                        if (emojiOpen) Color(0xFF5B3FD6) else XSurfaceRaised,
                        tween(200),
                        label = "composer-emoji-bg",
                    )
                    val emojiFg = when {
                        emojiOpen -> Color(0xFFFFFFFF)
                        inputsEnabled -> XText
                        else -> XMuted
                    }
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(emojiBg)
                            .clickable(enabled = onEmoji != null && inputsEnabled) { onEmoji?.invoke() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_ms_mood),
                            "Эмодзи, GIF и стикеры",
                            tint = emojiFg,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                }
                // ОДНА кнопка на все состояния. Раньше запись подменяла ветку `when`,
                // кнопка пересоздавалась — и жест «удерживать → отпустить» обрывался
                // ещё до отправки. Теперь composable один, меняются только параметры.
                val videoMode = mediaMode == XMediaComposeMode.VIDEO
                val noteLocked = videoNoteRecorder?.locked == true
                // `which = (hasText || locked) ? send : (vid ? vid : mic)` — an unlocked
                // recording keeps its own glyph and only changes colour.
                val showSend = canSend || noteLocked || (recordingVoice && voiceLocked)
                XComposerCircleButton(
                    // `which = (hasText || locked) ? send : (vid ? vid : mic)` — три глифа
                    // лежат друг на друге и перетекают, а не подменяются.
                    glyph = when {
                        showSend -> XComposerGlyph.SEND
                        videoMode || recordingVideoNote -> XComposerGlyph.VIDEO
                        else -> XComposerGlyph.MIC
                    },
                    description = when {
                        recordingVideoNote && noteLocked -> "Отправить видеосообщение"
                        recordingVideoNote -> "Отпустите, чтобы отправить видеосообщение"
                        recordingVoice -> "Остановить и отправить голосовое"
                        canSend -> "Отправить"
                        videoMode -> "Видеосообщение: нажмите для смены режима, удерживайте для записи"
                        else -> "Голосовое: нажмите для смены режима, удерживайте для записи"
                    },
                    active = !sending || recordingVideoNote,
                    danger = recordingVoice || (recordingVideoNote && !noteLocked),
                    locked = noteLocked || (recordingVoice && voiceLocked),
                    swollen = recordingVoice || recordingVideoNote,
                    // Canvas: the hold starts a take in whichever mode is current.
                    holdEnabled = !canSend && !recordingVoice && !recordingVideoNote,
                    onHoldStart = {
                        if (videoMode) {
                            if (!recordingVideoNote) onVideoNoteHoldStart?.invoke()
                        } else {
                            onVoiceHoldStart?.invoke()
                        }
                    },
                    onHoldMove = { dx, dy ->
                        if (videoMode) videoNoteRecorder?.onHoldDrag(dx, dy, cancelDistancePx, lockDistancePx)
                        else onVoiceHoldDrag?.invoke(dx, dy)
                    },
                    onHoldRelease = {
                        if (videoMode) videoNoteRecorder?.onHoldRelease() else onVoiceHoldRelease?.invoke()
                    },
                    onClick = {
                        when {
                            recordingVideoNote -> videoNoteRecorder?.send()
                            recordingVoice -> onVoice?.invoke()
                            canSend -> onSend()
                            else -> onMediaModeToggle?.invoke()
                        }
                    },
                )
            }
        }
        }
    }
}

/** Какой из трёх глифов кнопки сейчас наверху. */
private enum class XComposerGlyph { MIC, VIDEO, SEND }

/**
 * Кнопка «отправить / голосовое / видеосообщение».
 *
 * Обработчик жеста ставится ровно один раз (`pointerInput(Unit)`) и читает актуальные
 * колбэки через [rememberUpdatedState]. Это принципиально: во время записи параметры
 * кнопки меняются каждый кадр, и если бы жест перезапускался, отпускание пальца
 * не доходило бы до отправки.
 */
@Composable
private fun XComposerCircleButton(
    glyph: XComposerGlyph,
    description: String,
    active: Boolean,
    danger: Boolean = false,
    /** Canvas `recLock`: a held recording pinned open, so the next tap sends it. */
    locked: Boolean = false,
    /** Canvas `actScale` — the circle swells while it is recording. */
    swollen: Boolean = false,
    size: Dp = 54.dp,
    holdEnabled: Boolean = false,
    onHoldStart: (() -> Unit)? = null,
    onHoldMove: ((Float, Float) -> Unit)? = null,
    onHoldRelease: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val currentActive by rememberUpdatedState(active)
    val currentHoldEnabled by rememberUpdatedState(holdEnabled)
    val currentHoldStart by rememberUpdatedState(onHoldStart)
    val currentHoldMove by rememberUpdatedState(onHoldMove)
    val currentHoldRelease by rememberUpdatedState(onHoldRelease)
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentClick by rememberUpdatedState(onClick)

    val gesture = Modifier.pointerInput(Unit) {
        kotlinx.coroutines.coroutineScope {
            while (true) {
                val down = awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }
                if (!currentActive) {
                    awaitPointerEventScope {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                        }
                    }
                    continue
                }
                var holding = false
                var holdIsRecording = false
                var dx = 0f
                var dy = 0f
                val holdTimer = launch {
                    delay(180)
                    if (!holding) {
                        holding = true
                        holdIsRecording = currentHoldEnabled
                        if (holdIsRecording) currentHoldStart?.invoke() else currentLongPress?.invoke()
                    }
                }
                awaitPointerEventScope {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (holding && holdIsRecording) currentHoldMove?.invoke(dx, dy)
                        change.consume()
                    }
                }
                holdTimer.cancel()
                when {
                    !holding -> currentClick()
                    holdIsRecording -> currentHoldRelease?.invoke()
                    else -> Unit
                }
            }
        }
    }
    // The send button is the one `--txt`-filled circle on the screen, so it reads as the primary
    // action next to the pale composer pill.
    // `actBg` / `actFg`: violet while locked, red while recording, otherwise the one
    // `--txt`-filled circle on the screen.
    val background by animateColorAsState(
        when {
            !active -> XSurface
            locked -> Color(0xFF5B3FD6)
            danger -> XRed
            else -> XText
        },
        tween(200),
        label = "composer-bg",
    )
    val tint = when {
        !active -> XMuted
        locked || danger -> Color(0xFFFCFBFF)
        else -> XInverse
    }
    val scale by animateFloatAsState(
        targetValue = if (swollen) 1.14f else 1f,
        animationSpec = tween(280, easing = CubicBezierEasing(.33f, 1f, .68f, 1f)),
        label = "composer-scale",
    )
    // `<sc-if recing>` рисует `inset:-6px;border:2px solid #FF5C76` с `ring 1.3s ease-out`.
    // Кольцо абсолютное: в раскладке кнопка остаётся ровно 54, поэтому до поля ввода
    // ровно 9, а до правого края 18 — как на артборде.
    val ringPhase = if (swollen) {
        val pulse = rememberInfiniteTransition(label = "composer-ring")
        val phase by pulse.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearOutSlowInEasing)),
            label = "composer-ring-phase",
        )
        phase
    } else {
        0f
    }
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (ringPhase <= 0f) return@drawBehind
                // `0% {opacity:.55;scale(.9)} 100% {opacity:0;scale(1.5)}`
                val radius = (this.size.minDimension / 2f + 6.dp.toPx()) * (.9f + .6f * ringPhase)
                drawCircle(
                    color = Color(0xFFFF5C76),
                    radius = radius,
                    alpha = (.55f * (1f - ringPhase)).coerceIn(0f, 1f),
                    style = Stroke(2.dp.toPx()),
                )
            }
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(background)
            .then(gesture),
        contentAlignment = Alignment.Center,
    ) {
        // `font-size:26px` on a 54 circle; кружок в макете нарисован коробкой 30px.
        val glyphSize = size * .48f
        val videoSize = size * .56f
        XComposerGlyphLayer(glyph == XComposerGlyph.MIC) {
            Icon(
                painterResource(R.drawable.ic_ms_mic),
                if (glyph == XComposerGlyph.MIC) description else null,
                tint = tint,
                modifier = Modifier.size(glyphSize),
            )
        }
        XComposerGlyphLayer(glyph == XComposerGlyph.VIDEO) {
            Icon(
                painterResource(R.drawable.ic_video_note),
                if (glyph == XComposerGlyph.VIDEO) description else null,
                tint = tint,
                modifier = Modifier.size(videoSize),
            )
        }
        XComposerGlyphLayer(glyph == XComposerGlyph.SEND) {
            Icon(
                painterResource(R.drawable.ic_ms_arrow_upward),
                if (glyph == XComposerGlyph.SEND) description else null,
                tint = tint,
                modifier = Modifier.size(glyphSize),
            )
        }
    }
}

/**
 * Один из трёх глифов кнопки. В макете они лежат стопкой и перетекают друг в друга:
 * `opacity 1/0` и `transform:scale(1/.72)` на `.26s cubic-bezier(.33,1,.68,1)`.
 */
@Composable
private fun XComposerGlyphLayer(visible: Boolean, content: @Composable () -> Unit) {
    val spec = tween<Float>(260, easing = CubicBezierEasing(.33f, 1f, .68f, 1f))
    val alpha by animateFloatAsState(if (visible) 1f else 0f, spec, label = "glyph-alpha")
    val scale by animateFloatAsState(if (visible) 1f else .72f, spec, label = "glyph-scale")
    if (alpha <= .01f) return
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun XAttachmentSheet(
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onFile: () -> Unit,
    onAudio: (() -> Unit)? = null,
) {
    // Canvas: a `--sf3` sheet with `border-radius:34px 34px 45px 45px`, a 41×4 handle, an
    // «ОТПРАВИТЬ / ЗАКРЫТЬ» line and a row of 76×69 tiles on `22px` corners.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(XShadowStrong)
                .xPressable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                    .background(XSurfaceSoft)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 22.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 41.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(XSurfaceRaised),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr("attach_send").uppercase(),
                        style = XType.sectionLabel.copy(fontSize = 10.5.sp, letterSpacing = .16.em),
                        color = XMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        tr("close").uppercase(),
                        style = XType.sectionLabel.copy(fontSize = 9.5.sp, letterSpacing = .1.em),
                        color = XMuted,
                        modifier = Modifier.xPressable(onClick = onDismiss),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XAttachmentTile(Icons.Outlined.PhotoCamera, tr("attach_photo"), Modifier.weight(1f), onPhoto)
                    XAttachmentTile(Icons.Outlined.Videocam, tr("attach_video"), Modifier.weight(1f), onVideo)
                    XAttachmentTile(Icons.Outlined.AttachFile, tr("attach_file"), Modifier.weight(1f), onFile)
                    if (onAudio != null) {
                        XAttachmentTile(Icons.Outlined.MusicNote, tr("attach_music"), Modifier.weight(1f), onAudio)
                    }
                }
            }
        }
    }
}

/** `width:76px;height:69px;border-radius:22px;padding:15px 4px;gap:8px` on `--sf2`. */
@Composable
private fun XAttachmentTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(69.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(XSurfaceRaised)
            .xPressable(pressedScale = .94f, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = XText, modifier = Modifier.size(22.dp))
        Text(
            label.uppercase(),
            style = XType.sectionLabel.copy(fontSize = 9.5.sp, letterSpacing = .09.em),
            color = XText,
            maxLines = 1,
        )
    }
}

private enum class XMediaPickerTab { EMOJI, GIF, STICKERS }

@Composable
private fun XEmojiMediaSheet(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    onPickGif: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("orionx_recent_emoji", Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf(XMediaPickerTab.EMOJI) }
    var categoryIndex by remember { mutableIntStateOf(0) }
    var recent by remember {
        mutableStateOf(
            prefs.getString("recent", "").orEmpty().split("|").filter { it.isNotBlank() }.take(24)
        )
    }
    val categories: List<Triple<String, List<String>, ImageVector>> = remember {
        listOf(
            Triple("Недавние", emptyList(), Icons.Outlined.Schedule),
            Triple("Смайлы", listOf("😀","😃","😄","😁","😆","😅","😂","🤣","😊","🙂","🙃","😉","😍","🥰","😘","😎","🤓","🥳","😭","😡","🤯","😴","🤔","🫡"), Icons.Outlined.Mood),
            Triple("Жесты", listOf("👍","👎","👌","✌️","🤞","🤟","🤘","👏","🙌","🫶","🙏","💪","👋","🤝","🫵","✍️","🤳","💅","👐","👊","✊","🤲","🖐️","☝️"), Icons.Outlined.ThumbUp),
            Triple("Сердца", listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","♥️"), Icons.Outlined.FavoriteBorder),
            Triple("Предметы", listOf("🔥","⭐","✨","⚡","🎉","🎁","🎵","🎧","📱","💻","⌚","📷","🎮","🚀","🌙","☀️","🌈","💡","🔒","✅","❌","💬","📌","🔔"), Icons.Outlined.EmojiObjects),
        )
    }

    fun selectEmoji(emoji: String) {
        val updated = (listOf(emoji) + recent).distinct().take(24)
        recent = updated
        prefs.edit().putString("recent", updated.joinToString("|")).apply()
        onEmojiSelected(emoji)
    }

    // Canvas: not a bottom sheet at all — a `--sf5` panel that opens above the input row.
    // `border-radius:22px;padding:12px 12px 10px`, three 85×34 tabs on `11px` corners and a
    // close button, a strip of 34×34 category plates, then a grid of 37×36 cells.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(XSurfaceDeep)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            XEmojiTab(tr("picker_emoji"), tab == XMediaPickerTab.EMOJI, Modifier.weight(1f)) { tab = XMediaPickerTab.EMOJI }
            XEmojiTab(tr("picker_gif"), tab == XMediaPickerTab.GIF, Modifier.weight(1f)) { tab = XMediaPickerTab.GIF }
            XEmojiTab(tr("picker_stickers"), tab == XMediaPickerTab.STICKERS, Modifier.weight(1f)) { tab = XMediaPickerTab.STICKERS }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(XSurfaceRaised)
                    .xPressable(pressedScale = .92f, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, null, tint = XText, modifier = Modifier.size(17.dp))
            }
        }

        when (tab) {
            XMediaPickerTab.EMOJI -> {
                // `display:flex;gap:4px;padding:10px 0 8px` — the category plates.
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    categories.forEachIndexed { index, category ->
                        val active = categoryIndex == index
                        Box(
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) XSurfaceRaised else Color.Transparent)
                                .xPressable(pressedScale = .92f) { categoryIndex = index },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                category.third,
                                category.first,
                                tint = if (active) XText else XMuted2,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
                val emojis = if (categoryIndex == 0) recent.ifEmpty { categories[1].second.take(16) }
                else categories[categoryIndex].second
                // `grid;gap:1px` with 37×36 cells on `9px` corners.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxWidth().height(172.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    gridItems(emojis) { emoji ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .xPressable(pressedScale = .88f) { selectEmoji(emoji) },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 21.sp, lineHeight = 21.sp) }
                    }
                }
            }

            XMediaPickerTab.GIF -> {
                Column(
                    Modifier.fillMaxWidth().height(213.dp).padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(XSurfaceRaised),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.GifBox, null, tint = XText, modifier = Modifier.size(38.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        tr("picker_gif_hint"),
                        style = XType.meta.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = XMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(XText)
                            .xPressable(pressedScale = .97f, onClick = onPickGif)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            tr("picker_gif_pick").uppercase(),
                            style = XType.sectionLabel.copy(fontSize = 10.5.sp, letterSpacing = .12.em),
                            color = XInverse,
                        )
                    }
                }
            }

            XMediaPickerTab.STICKERS -> {
                val stickers = listOf("😎","😂","🥰","🔥","👍","🎉","🤝","🚀","💜","🤯","🥳","🫶","✅","👀","💯","✨","😭","😴")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(213.dp).padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    gridItems(stickers) { sticker ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(XSurfaceRaised)
                                .xPressable(pressedScale = .9f) { selectEmoji(sticker); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) { Text(sticker, fontSize = 30.sp, lineHeight = 30.sp) }
                    }
                }
            }
        }
    }
}

/** `width:85px;height:34px;border-radius:11px` — violet while it is the open tab. */
@Composable
private fun XEmojiTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) XCommunityReactionChip else XSurfaceRaised,
        tween(200),
        label = "picker-tab",
    )
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .xPressable(pressedScale = .94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = XType.sectionLabel.copy(fontSize = 10.sp, letterSpacing = .1.em),
            color = if (selected) Color(0xFFFCFBFF) else XText,
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.XMediaTabButton(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) XPurple.copy(alpha = .32f) else XSurfaceSoft,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) XViolet else XBorder),
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (selected) XText else XMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = if (selected) XText else XMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * One row of the group/channel dropdown —
 * `display:flex;gap:12px;padding:13px 16px;border-bottom:1px solid var(--hair)`.
 */
@Composable
private fun XCommunityMenuRow(
    icon: ImageVector,
    title: String,
    tint: Color = XText,
    divider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            Text(
                title,
                style = XType.rowTitle.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(XHairline))
    }
}

@Composable
private fun XChatHeader(
    title: String,
    subtitle: String,
    avatar: @Composable () -> Unit,
    onBack: () -> Unit,
    onIdentityClick: (() -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    /** Someone is typing: the canvas turns the line accent and adds three bouncing dots. */
    subtitleAccent: Boolean = false,
    actions: @Composable RowScope.() -> Unit,
) {
    // The canvas floats the header over the feed as separate `--sf` pills instead of a bar.
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        XCircleButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = tr("back"),
            size = 45.dp,
            iconSize = 20.dp,
            onClick = onBack,
        )
        Row(
            Modifier
                .weight(1f)
                .height(45.dp)
                .xShadow(CircleShape)
                .clip(CircleShape)
                .background(XSurface)
                .clickable(enabled = onIdentityClick != null) { onIdentityClick?.invoke() }
                .padding(start = 3.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            avatar()
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = XType.rowTitle.copy(fontSize = 15.sp),
                        color = XText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (titleTrailing != null) {
                        Spacer(Modifier.width(5.dp))
                        titleTrailing()
                    }
                }
                // `display:flex;gap:6px;padding-top:4px`
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        subtitle,
                        style = XType.meta.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                        color = if (subtitleAccent) XAccent else XMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (subtitleAccent) XTypingDots(color = XAccent, dot = 4.dp)
                }
            }
        }
        actions()
    }
}

@Composable
private fun XPeerProfileDialog(
    peerPresence: CloudPeerPresence?,
    fallbackTitle: String,
    fallbackAvatarUrl: String,
    conversationId: String,
    messages: List<CloudMessage>,
    onCall: (video: Boolean) -> Unit,
    onOpenMedia: (CloudMessage) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val title = peerPresence?.displayName?.takeIf { it.isNotBlank() } ?: fallbackTitle
    val avatarUrl = peerPresence?.avatarUrl?.takeIf { it.isNotBlank() } ?: fallbackAvatarUrl
    var muted by remember(conversationId) { mutableStateOf(OrionMutedChats.isMuted(context, conversationId)) }

    // Shared content pulled from the loaded chat history (newest first).
    val media = remember(messages) {
        messages.filter {
            val t = it.attachmentType.orEmpty().lowercase()
            !it.attachmentUrl.isNullOrBlank() && (t.startsWith("image/") || t.startsWith("video/")) && !isVideoNoteMessage(it)
        }.reversed()
    }
    val voices = remember(messages) {
        messages.filter { it.attachmentType.orEmpty().lowercase().startsWith("audio/") || isVideoNoteMessage(it) }.reversed()
    }
    val files = remember(messages) {
        messages.filter {
            val t = it.attachmentType.orEmpty().lowercase()
            !it.attachmentUrl.isNullOrBlank() && !t.startsWith("image/") && !t.startsWith("video/") && !t.startsWith("audio/") && !isVideoNoteMessage(it)
        }.reversed()
    }
    val links = remember(messages) {
        messages.filter { it.body.contains("http://", true) || it.body.contains("https://", true) }.reversed()
    }
    var tab by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(XBg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", tint = XText) }
                Text("Профиль", color = XText, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (avatarUrl.startsWith("orionx-gradient:")) {
                        XGradientAvatar(avatarUrl.removePrefix("orionx-gradient:").toIntOrNull() ?: 0, title, 96.dp)
                    } else if (avatarUrl.isNotBlank()) {
                        RemoteImage(avatarUrl, Modifier.size(96.dp).clip(CircleShape).border(1.dp, Color(0xFF3A2A6B), CircleShape), ContentScale.Crop)
                    } else {
                        XLetterAvatar(title, 96.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(title, color = XText, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (peerPresence?.isOnline == true) "в сети" else formatPeerLastSeen(peerPresence),
                        color = if (peerPresence?.isOnline == true) XCyan else XMuted,
                        fontSize = 12.sp,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    XProfileAction(Icons.Outlined.ChatBubbleOutline, "Чат", Modifier.weight(1f), onClick = onDismiss)
                    XProfileAction(
                        if (muted) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                        if (muted) "Вкл. звук" else "Без звука",
                        Modifier.weight(1f),
                        highlight = muted,
                    ) {
                        val next = !muted; muted = next; OrionMutedChats.setMuted(context, conversationId, next)
                    }
                    XProfileAction(Icons.Outlined.Call, "Звонок", Modifier.weight(1f)) { onCall(false) }
                    XProfileAction(Icons.Outlined.Videocam, "Видео", Modifier.weight(1f)) { onCall(true) }
                }

                peerPresence?.nickname?.takeIf { it.isNotBlank() }?.let { nick ->
                    Spacer(Modifier.height(16.dp))
                    Surface(color = XSurfaceSoft, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, XBorder), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("@$nick", color = XText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Имя пользователя", color = XMuted, fontSize = 12.sp)
                        }
                    }
                }
                peerPresence?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    Spacer(Modifier.height(10.dp))
                    Surface(color = XSurfaceSoft, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, XBorder), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(bio, color = XText, fontSize = 14.sp, lineHeight = 19.sp)
                            Text("О себе", color = XMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                val tabs = listOf("Медиа" to media.size, "Файлы" to files.size, "Ссылки" to links.size, "Голосовые" to voices.size)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { i, pair ->
                        val selected = tab == i
                        Surface(
                            onClick = { tab = i },
                            color = if (selected) XPurple else XSurfaceSoft,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) XPurple else XBorder),
                        ) {
                            Text("${pair.first} ${pair.second}", color = if (selected) Color.White else XMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                when (tab) {
                    0 -> XProfileMediaGrid(media, onOpenMedia)
                    1 -> XProfileAttachmentList(files, Icons.Outlined.InsertDriveFile, onOpenMedia)
                    2 -> XProfileLinkList(links)
                    else -> XProfileAttachmentList(voices, Icons.Outlined.Mic, onOpenMedia)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun XProfileAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (highlight) XPurple.copy(alpha = .22f) else XSurfaceSoft,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (highlight) XViolet else XBorder),
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = if (highlight) XViolet else XViolet, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, color = XText, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun XProfileEmpty(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = XMuted, fontSize = 13.sp)
    }
}

@Composable
private fun XProfileMediaGrid(items: List<CloudMessage>, onOpen: (CloudMessage) -> Unit) {
    if (items.isEmpty()) { XProfileEmpty("Общих медиа пока нет"); return }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { msg ->
                    val isVideo = msg.attachmentType.orEmpty().lowercase().startsWith("video/")
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(XSurfaceSoft)
                            .clickable { onOpen(msg) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isVideo) {
                            Icon(Icons.Filled.PlayCircle, "Видео", tint = Color.White, modifier = Modifier.size(34.dp))
                        } else {
                            RemoteImage(msg.attachmentUrl.orEmpty(), Modifier.fillMaxSize(), ContentScale.Crop)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun XProfileAttachmentList(items: List<CloudMessage>, icon: ImageVector, onOpen: (CloudMessage) -> Unit) {
    if (items.isEmpty()) { XProfileEmpty("Пока ничего нет"); return }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { msg ->
            val videoNote = isVideoNoteMessage(msg)
            val noteDuration = if (videoNote) VideoNote.durationMsFromName(msg.attachmentName) else 0L
            Surface(onClick = { onOpen(msg) }, color = XSurfaceSoft, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, XBorder), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Имя файла кружка (`vnote_5400_…mp4`) человеку ничего не говорит.
                    Icon(
                        if (videoNote) Icons.Outlined.Videocam else icon,
                        null,
                        tint = XViolet,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                videoNote && noteDuration > 0L -> "Видеосообщение · ${VideoNote.formatDuration(noteDuration)}"
                                videoNote -> "Видеосообщение"
                                else -> msg.attachmentName?.takeIf { it.isNotBlank() } ?: msg.senderName
                            },
                            color = XText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(msg.senderName, color = XMuted, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun XProfileLinkList(items: List<CloudMessage>) {
    val context = LocalContext.current
    if (items.isEmpty()) { XProfileEmpty("Ссылок пока нет"); return }
    val urlRegex = remember { Regex("https?://\\S+") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { msg ->
            val url = urlRegex.find(msg.body)?.value ?: msg.body
            Surface(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                color = XSurfaceSoft, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, XBorder), modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Link, null, tint = XViolet, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(url, color = XCyan, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun XPullToRefresh(
    listState: androidx.compose.foundation.lazy.LazyListState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    var pullDistance by remember { mutableFloatStateOf(0f) }
    var refreshArmed by remember { mutableStateOf(false) }
    val thresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 68.dp.toPx() }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            pullDistance = 0f
            refreshArmed = false
        }
    }

    val connection = remember(listState, refreshing, thresholdPx, enabled) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): Offset {
                if (!enabled) return Offset.Zero
                val atTop = !listState.canScrollBackward
                if (!refreshing && atTop && available.y > 0f) {
                    val consumedY = available.y
                    pullDistance = (pullDistance + consumedY * .54f).coerceAtMost(thresholdPx * 1.7f)
                    refreshArmed = pullDistance >= thresholdPx
                    return Offset(0f, consumedY)
                }
                if (pullDistance > 0f && available.y < 0f) {
                    val consumedY = available.y.coerceAtLeast(-pullDistance / .54f)
                    pullDistance = (pullDistance + consumedY * .54f).coerceAtLeast(0f)
                    refreshArmed = pullDistance >= thresholdPx
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                if (pullDistance <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                val shouldRefresh = !refreshing && refreshArmed
                pullDistance = 0f
                refreshArmed = false
                if (shouldRefresh) onRefresh()
                return available
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(connection)) {
        content()
        if (refreshing || pullDistance > 4f) {
            Surface(
                color = if (refreshArmed) XPurple.copy(alpha = .95f) else XSurfaceRaised,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (refreshArmed) XViolet else XBorder),
                shadowElevation = 5.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 7.dp)
                    .size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            color = XCyan,
                            trackColor = XBorder,
                            strokeWidth = 2.4.dp,
                            modifier = Modifier.size(23.dp),
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { (pullDistance / thresholdPx).coerceIn(.06f, 1f) },
                            color = if (refreshArmed) Color.White else XCyan,
                            trackColor = if (refreshArmed) Color.White.copy(alpha = .20f) else XBorder,
                            strokeWidth = 2.4.dp,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XBackHeader(title: String, onBack: () -> Unit) {
    XScreenHeader(title = title, onBack = onBack)
}

@Composable
private fun XSectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = XType.screenTitle, color = XText)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(subtitle, style = XType.body, color = XMuted)
        }
    }
}

@Composable
private fun XMiniTitle(title: String) {
    XCardLabel(title, Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp))
}

@Composable
private fun XSearchField(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = tr("search_hint"),
) {
    XSearchPill(value = value, onValueChange = onValue, placeholder = placeholder, modifier = modifier)
}

@Composable
private fun XFormField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = XSurfaceSoft,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (focused) 1.5.dp else 1.dp,
            if (focused) XPurple else XBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
            Text(
                label,
                color = if (focused) XCyan else XMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                Icon(icon, null, tint = XViolet, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValue,
                    singleLine = singleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    cursorBrush = SolidColor(XCyan),
                    textStyle = TextStyle(
                        color = XText,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .then(if (singleLine) Modifier else Modifier.heightIn(min = 86.dp))
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                        ) {
                            if (value.isBlank()) {
                                Text(
                                    when (label) {
                                        "О себе" -> "Расскажите немного о себе"
                                        "Телефон" -> "+358 …"
                                        else -> label
                                    },
                                    color = XMuted.copy(alpha = .74f),
                                    fontSize = 16.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun XBirthdayField(value: String, onValue: (String) -> Unit) {
    var showCalendar by remember { mutableStateOf(false) }
    val parsed = remember(value) {
        runCatching { java.time.LocalDate.parse(value) }.getOrNull()
    }
    val displayValue = remember(value) {
        parsed?.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "Выберите дату"
    }

    Surface(
        color = XSurfaceSoft,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, XBorder),
        modifier = Modifier.fillMaxWidth().clickable { showCalendar = true },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
            Text("Дата рождения", color = XMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cake, null, tint = XViolet, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    displayValue,
                    color = if (parsed == null) XMuted else XText,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.CalendarMonth, null, tint = XViolet, modifier = Modifier.size(23.dp))
            }
        }
    }

    if (showCalendar) {
        XOrionCalendarDialog(
            initialDate = parsed ?: java.time.LocalDate.of(2000, 1, 1),
            onDismiss = { showCalendar = false },
            onSelect = {
                onValue(it.toString())
                showCalendar = false
            },
        )
    }
}

@Composable
private fun XOrionCalendarDialog(
    initialDate: java.time.LocalDate,
    onDismiss: () -> Unit,
    onSelect: (java.time.LocalDate) -> Unit,
) {
    val today = remember { java.time.LocalDate.now() }
    var selected by remember(initialDate) { mutableStateOf(if (initialDate.isAfter(today)) today else initialDate) }
    var month by remember(initialDate) { mutableStateOf(java.time.YearMonth.from(selected)) }
    val monthNames = remember {
        listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    }
    val weekdays = remember { listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс") }
    val minMonth = remember { java.time.YearMonth.of(1900, 1) }
    val maxMonth = remember(today) { java.time.YearMonth.from(today) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = XSurface,
            shape = RoundedCornerShape(30.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, XViolet.copy(alpha = .50f)),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Календарь OrionX", color = XViolet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            selected.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ru"))),
                            color = XText,
                            fontSize = 25.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Закрыть", tint = XMuted)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Surface(
                    color = XSurfaceSoft,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, XBorder),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { if (month > minMonth) month = month.minusMonths(1) },
                            enabled = month > minMonth,
                        ) { Icon(Icons.Outlined.ChevronLeft, "Предыдущий месяц", tint = if (month > minMonth) XText else XMuted.copy(alpha = .35f)) }
                        Text(
                            "${monthNames[month.monthValue - 1]} ${month.year}",
                            color = XText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { if (month < maxMonth) month = month.plusMonths(1) },
                            enabled = month < maxMonth,
                        ) { Icon(Icons.Outlined.ChevronRight, "Следующий месяц", tint = if (month < maxMonth) XText else XMuted.copy(alpha = .35f)) }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { if (month.minusYears(1) >= minMonth) month = month.minusYears(1) },
                        enabled = month.minusYears(1) >= minMonth,
                    ) { Text("−1 год", color = if (month.minusYears(1) >= minMonth) XViolet else XMuted.copy(alpha = .35f)) }
                    Text("Быстрый выбор года", color = XMuted, fontSize = 11.sp)
                    TextButton(
                        onClick = { if (month.plusYears(1) <= maxMonth) month = month.plusYears(1) },
                        enabled = month.plusYears(1) <= maxMonth,
                    ) { Text("+1 год", color = if (month.plusYears(1) <= maxMonth) XViolet else XMuted.copy(alpha = .35f)) }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    weekdays.forEach { day ->
                        Text(
                            day,
                            color = XMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                        )
                    }
                }

                val first = month.atDay(1)
                val leading = first.dayOfWeek.value - 1
                val days = month.lengthOfMonth()
                repeat(6) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val index = week * 7 + column
                            val dayNumber = index - leading + 1
                            val date = if (dayNumber in 1..days) month.atDay(dayNumber) else null
                            val enabled = date != null && !date.isAfter(today)
                            val isSelected = date == selected
                            val isToday = date == today
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (date != null) {
                                    Surface(
                                        color = when {
                                            isSelected -> XPurple
                                            isToday -> XViolet.copy(alpha = .16f)
                                            else -> Color.Transparent
                                        },
                                        shape = CircleShape,
                                        border = if (isToday && !isSelected) androidx.compose.foundation.BorderStroke(1.dp, XViolet.copy(alpha = .65f)) else null,
                                        modifier = Modifier.fillMaxSize().clickable(enabled = enabled) { selected = date },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                dayNumber.toString(),
                                                color = when {
                                                    isSelected -> Color.White
                                                    enabled -> XText
                                                    else -> XMuted.copy(alpha = .28f)
                                                },
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена", color = XMuted) }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { onSelect(selected) },
                        colors = ButtonDefaults.buttonColors(containerColor = XPurple),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Выбрать", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
internal fun XPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(if (enabled) XText else XSurfaceRaised)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = XType.pill, color = if (enabled) XInverse else XMuted, maxLines = 1)
    }
}

@Composable
private fun XDangerButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(XSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = XType.pill, color = XRed, maxLines = 1)
    }
}

@Composable
private fun XSettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .xShadow(XShape.card)
            .clip(XShape.card)
            .background(XSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        XIconPlate(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = XType.rowTitle, color = XText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = XType.body, color = XMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = XMuted, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun XIconPlate(icon: ImageVector) {
    Box(
        Modifier.size(38.dp).clip(XShape.plateSmall).background(XSurfaceRaised),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = XText, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun XDeviceCard(
    title: String,
    subtitle: String,
    current: Boolean,
    onRemove: (() -> Unit)? = null,
) {
    Surface(color = XSurfaceSoft, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, XBorder)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            XIconPlate(Icons.Outlined.Smartphone)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = XText, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = XMuted, fontSize = 12.sp)
            }
            when {
                current -> XBadge("Сейчас", XGreen)
                onRemove != null -> IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Logout, contentDescription = "Отключить устройство", tint = XRed)
                }
            }
        }
    }
}

@Composable
private fun XPasswordDialog(
    api: SupabaseApi,
    session: CloudSession,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = XSurface,
        titleContentColor = XText,
        textContentColor = XMuted,
        title = { Text("Новый пароль", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; message = null },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = xTextFieldColors(),
                    shape = RoundedCornerShape(18.dp),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; message = null },
                    label = { Text("Повторите пароль") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = xTextFieldColors(),
                    shape = RoundedCornerShape(18.dp),
                )
                message?.let { Text(it, color = if (it == "Пароль изменён") XGreen else XRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    when {
                        password.length < 8 -> message = "Минимум 8 символов"
                        password != confirm -> message = "Пароли не совпадают"
                        else -> scope.launch {
                            busy = true
                            runCatching { withContext(Dispatchers.IO) { api.updatePassword(session.accessToken, password) } }
                                .onSuccess { message = "Пароль изменён" }
                                .onFailure { message = localizedNetworkError(it) }
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "Сохранение…" else "Сохранить", color = XViolet) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена", color = XMuted) } },
    )
}

@Composable
private fun XBlockedUsersDialog(
    api: SupabaseApi,
    session: CloudSession,
    onDismiss: () -> Unit,
) {
    var users by remember { mutableStateOf<List<CloudProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val ids = api.blockedUserIds(session.accessToken, session.userId)
                    api.allProfiles(session.accessToken).filter { it.id in ids }
                }
            }
            result.onSuccess { users = it }.onFailure { message = localizedNetworkError(it) }
            loading = false
        }
    }
    LaunchedEffect(session.accessToken) { reload() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XSurface,
        titleContentColor = XText,
        textContentColor = XMuted,
        title = { Text("Заблокированные пользователи", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = XPurple, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp)); Text("Загрузка…")
                    }
                    users.isEmpty() -> Text("Список блокировок пуст")
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(users, key = { it.id }) { user ->
                            Surface(color = XSurfaceRaised, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, XBorder)) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    XLetterAvatar(user.displayName.ifBlank { user.nickname }, 38.dp)
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(user.displayName, color = XText, fontWeight = FontWeight.SemiBold)
                                        Text("@${user.nickname}", color = XMuted, fontSize = 11.sp)
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { api.unblockUser(session.accessToken, session.userId, user.id) } }
                                                .onSuccess { message = "Пользователь разблокирован"; reload() }
                                                .onFailure { message = localizedNetworkError(it) }
                                        }
                                    }) { Text("Разблокировать", color = XRed, fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                }
                message?.let { Text(it, color = XMuted, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово", color = XViolet) } },
    )
}

@Composable
private fun XInfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XSurface,
        titleContentColor = XText,
        textContentColor = XMuted,
        title = { Text(title, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) },
        text = {
            Box(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                Text(body, lineHeight = 19.sp)
            }
        },
        shape = XShape.panel,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно", color = XAccent, style = XType.rowTitle) } },
    )
}

@Composable
private fun xTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = XSurfaceRaised,
    unfocusedContainerColor = XSurfaceRaised,
    focusedBorderColor = XAccent,
    unfocusedBorderColor = Color.Transparent,
    focusedTextColor = XText,
    unfocusedTextColor = XText,
    focusedLabelColor = XAccent,
    unfocusedLabelColor = XMuted,
    cursorColor = XAccent,
)

@Composable
private fun XContentCard(icon: ImageVector, title: String, body: String, badges: @Composable RowScope.() -> Unit = {}) {
    XCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XIconPlate(icon)
                Spacer(Modifier.width(12.dp))
                Text(title, style = XType.screenTitle, color = XText, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text(body, style = XType.bodyStrong, color = XMuted)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), content = badges)
        }
    }
}

@Composable
private fun XHeroEmptyState(
    title: String,
    subtitle: String,
    illustration: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().heightIn(min = 310.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            illustration()
            Spacer(Modifier.height(16.dp))
            Text(title, style = XType.screenTitle, color = XText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(7.dp))
            Text(subtitle, style = XType.bodyStrong, color = XMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun XIllustrationCard(
    title: String,
    subtitle: String,
    illustration: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    XCard(modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            illustration()
            Spacer(Modifier.height(14.dp))
            Text(title, style = XType.screenTitle, color = XText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = XType.bodyStrong, color = XMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun XLoadingCard() {
    XCard(shape = XShape.card) {
        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = XAccent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Text("Загрузка…", style = XType.bodyStrong, color = XMuted)
        }
    }
}

@Composable
private fun XInlineMessage(message: String, error: Boolean) {
    val accent = if (error) XRed else XMuted
    Row(
        Modifier
            .fillMaxWidth()
            .xShadow(XShape.card)
            .clip(XShape.card)
            .background(XSurface)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
            null,
            tint = accent,
            modifier = Modifier.size(19.dp),
        )
        Text(message, style = XType.bodyStrong, color = accent, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun XBadge(text: String, color: Color) {
    Surface(color = XSurfaceRaised, shape = CircleShape) {
        Text(text.uppercase(), style = XType.pillSmall.copy(fontSize = 9.5.sp), color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
internal fun XAvatar(profile: CloudProfile, size: Dp, shape: Shape = CircleShape) {
    val url = profile.avatarUrl
    when {
        url.startsWith("orionx-gradient:") -> XGradientAvatar(
            url.removePrefix("orionx-gradient:").toIntOrNull() ?: 0,
            profile.displayName.ifBlank { profile.nickname },
            size,
            shape,
        )
        url.isNotBlank() -> RemoteImage(url = url, modifier = Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
        else -> XLetterAvatar(profile.displayName.ifBlank { profile.nickname }, size, shape)
    }
}

/** Плашка выбранного градиента (`orionx-gradient:N`) с инициалом поверх. */
@Composable
internal fun XGradientAvatar(gradientIndex: Int, text: String, size: Dp, shape: Shape = CircleShape) {
    Box(
        Modifier.size(size).clip(shape).background(xAvatarGradientBrush(gradientIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.trim().take(1).uppercase(),
            style = XType.rowTitle.copy(fontSize = (size.value * .34f).sp),
            color = Color(0xFF101014),
        )
    }
}

/**
 * Аватар по произвольному url: фото, выбранная плашка градиента `orionx-gradient:N`
 * или инициал на стандартном градиенте. Эту функцию используют места, которые раньше
 * рисовали RemoteImage напрямую.
 */
@Composable
internal fun XAvatarUrl(url: String, name: String, size: Dp, shape: Shape = CircleShape, letterColor: Color = XText) {
    when {
        url.startsWith("orionx-gradient:") -> XGradientAvatar(url.removePrefix("orionx-gradient:").toIntOrNull() ?: 0, name, size, shape)
        url.isNotBlank() -> RemoteImage(url = url, modifier = Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
        else -> XLetterAvatar(name, size, shape, letterColor)
    }
}

/**
 * `linear-gradient(140deg,#FFB48A,#E39BD8 55%,#8FA8FF)` with the initial on top — the canvas uses
 * a rounded square for rooms and a circle for people, so the shape is a parameter.
 */
@Composable
internal fun XLetterAvatar(
    text: String,
    size: Dp,
    shape: Shape = CircleShape,
    // The canvas sets the letter per context: `#101014` on the light avatar gradient
    // in a roster row, `#FCFBFF` on the solid hero plate.
    letterColor: Color = XText,
) {
    Box(
        Modifier.size(size).clip(shape).background(XAvatarBrush),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.trim().take(1).uppercase(),
            style = XType.rowTitle.copy(fontSize = (size.value * .34f).sp),
            color = letterColor,
        )
    }
}

@Composable
internal fun RemoteImage(
    url: String,
    modifier: Modifier,
    contentScale: ContentScale,
    /** Width over height once the bitmap is in hand, so a photo bubble can take its shape. */
    onAspect: ((Float) -> Unit)? = null,
) {
    // Seeded from the memory cache, so an image already fetched paints on the first frame
    // instead of blinking through the placeholder and hitting the network again.
    var bitmap by remember(url) { mutableStateOf(XImageCache.get(url)) }
    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            // Disk first: after a relaunch the memory cache is empty but the bytes are
            // still here, so the avatar appears without waiting for the network.
            XImageCache.readDisk(url)?.let { return@withContext it }
            runCatching {
                // Без таймаутов зависший сервер держит корутину вечно.
                val connection = java.net.URL(url).openConnection().apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                }
                val bytes = connection.getInputStream().use { it.readBytes() }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    ?.also { XImageCache.writeDisk(url, bytes) }
            }.getOrNull()
        }
        if (loaded != null) XImageCache.put(url, loaded)
        bitmap = loaded
    }
    LaunchedEffect(bitmap) {
        val loaded = bitmap ?: return@LaunchedEffect
        if (loaded.height > 0) onAspect?.invoke(loaded.width.toFloat() / loaded.height.toFloat())
    }
    if (bitmap != null) androidx.compose.foundation.Image(bitmap!!, null, modifier = modifier, contentScale = contentScale)
    else Box(modifier.background(XSurfaceRaised))
}

@Composable
private fun OrionLogo(diameter: Dp) {
    Image(
        painter = painterResource(R.drawable.orionx_mark),
        contentDescription = "OrionX",
        modifier = Modifier.size(diameter).clip(RoundedCornerShape(diameter * 0.28f)),
    )
}

private fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit) =
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )

/** Robot-with-speech-bubble glyph shown next to the Oria AI title (built in code, no drawable). */
private val OriaBotIcon: ImageVector by lazy {
    ImageVector.Builder(name = "OriaBot", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        // Antenna
        strokePath { moveTo(12f, 7f); lineTo(12f, 4.6f) }
        strokePath {
            moveTo(13.15f, 3.45f)
            arcTo(1.15f, 1.15f, 0f, true, true, 10.85f, 3.45f)
            arcTo(1.15f, 1.15f, 0f, true, true, 13.15f, 3.45f)
            close()
        }
        // Ears
        strokePath { moveTo(3.7f, 11f); lineTo(3.7f, 13.4f) }
        strokePath { moveTo(20.3f, 11f); lineTo(20.3f, 13.4f) }
        // Head with speech-bubble tail
        strokePath {
            moveTo(8f, 7f)
            lineTo(16f, 7f)
            arcTo(3f, 3f, 0f, false, true, 19f, 10f)
            lineTo(19f, 14.5f)
            arcTo(3f, 3f, 0f, false, true, 16f, 17.5f)
            lineTo(15.4f, 17.5f)
            lineTo(14.1f, 20f)
            lineTo(12.9f, 17.5f)
            lineTo(8f, 17.5f)
            arcTo(3f, 3f, 0f, false, true, 5f, 14.5f)
            lineTo(5f, 10f)
            arcTo(3f, 3f, 0f, false, true, 8f, 7f)
            close()
        }
        // Eyes
        strokePath {
            moveTo(8.2f, 12.5f)
            curveTo(8.2f, 11.8f, 8.75f, 11.3f, 9.4f, 11.3f)
            curveTo(10.05f, 11.3f, 10.6f, 11.8f, 10.6f, 12.5f)
        }
        strokePath {
            moveTo(13.4f, 12.5f)
            curveTo(13.4f, 11.8f, 13.95f, 11.3f, 14.6f, 11.3f)
            curveTo(15.25f, 11.3f, 15.8f, 11.8f, 15.8f, 12.5f)
        }
        // Smile
        strokePath {
            moveTo(9.2f, 14.4f)
            curveTo(10.2f, 15.6f, 13.8f, 15.6f, 14.8f, 14.4f)
        }
    }.build()
}

@Composable
private fun OriaOrb(diameter: Dp) {
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Color(0xFF09051B))
            .border(1.dp, Color(0xFF3A2A6B), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.oria_avatar),
            contentDescription = "Oria AI",
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize().padding(1.dp),
        )
    }
}

@Composable
private fun OriaBrand(size: Dp) {
    Image(
        painter = painterResource(R.drawable.oria_brand_full),
        contentDescription = "Oria AI",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun ChatPlanet(diameter: Dp) {
    Canvas(Modifier.size(diameter)) {
        val s = diameter.toPx()
        val c = Offset(s * .50f, s * .48f)
        drawCircle(Brush.radialGradient(listOf(XPurple.copy(alpha = .35f), Color.Transparent)), radius = s * .48f, center = c)
        drawOval(XViolet.copy(alpha = .34f), Offset(s * .06f, s * .32f), Size(s * .88f, s * .34f), style = Stroke(s * .010f))
        drawOval(XCyan.copy(alpha = .14f), Offset(s * .17f, s * .19f), Size(s * .66f, s * .58f), style = Stroke(s * .007f))
        drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFE8DEFF), Color(0xFFB58CFF), XPurple, Color(0xFF29126C))), radius = s * .28f, center = c)
        drawCircle(Color.White.copy(alpha = .22f), radius = s * .12f, center = Offset(s * .42f, s * .37f))
        val bubble = Path().apply {
            moveTo(s * .31f, s * .35f)
            quadraticBezierTo(s * .32f, s * .24f, s * .50f, s * .24f)
            quadraticBezierTo(s * .70f, s * .24f, s * .70f, s * .42f)
            quadraticBezierTo(s * .70f, s * .57f, s * .52f, s * .57f)
            lineTo(s * .40f, s * .66f)
            lineTo(s * .42f, s * .56f)
            quadraticBezierTo(s * .31f, s * .53f, s * .31f, s * .35f)
            close()
        }
        drawPath(bubble, Brush.linearGradient(listOf(Color(0xFFA276FF), Color(0xFF6E35FF), Color(0xFF4C21C8))))
        drawPath(bubble, Color.White.copy(alpha = .28f), style = Stroke(s * .010f))
        repeat(3) { i -> drawCircle(Color.White, radius = s * .024f, center = Offset(s * (.43f + i * .08f), s * .41f)) }
        drawCircle(Color.White, radius = s * .010f, center = Offset(s * .18f, s * .24f))
        drawCircle(XCyan, radius = s * .008f, center = Offset(s * .81f, s * .72f))
    }
}

@Composable
private fun SearchOrb(diameter: Dp) {
    Canvas(Modifier.size(diameter)) {
        val s = diameter.toPx()
        val lensCenter = Offset(s * .42f, s * .38f)
        drawCircle(Brush.radialGradient(listOf(XPurple.copy(alpha = .32f), Color.Transparent)), radius = s * .47f, center = lensCenter)
        drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFF5F0FF), Color(0xFFBE9EFF), XViolet, XPurple, Color(0xFF190837))), radius = s * .29f, center = lensCenter)
        drawCircle(Color.White.copy(alpha = .24f), radius = s * .13f, center = Offset(s * .34f, s * .29f))
        drawCircle(Color.White.copy(alpha = .16f), radius = s * .065f, center = Offset(s * .50f, s * .44f))
        drawCircle(XViolet, radius = s * .31f, center = lensCenter, style = Stroke(s * .028f))
        drawCircle(Color.White.copy(alpha = .38f), radius = s * .295f, center = lensCenter, style = Stroke(s * .008f))
        drawLine(XViolet, Offset(s * .62f, s * .59f), Offset(s * .84f, s * .82f), strokeWidth = s * .085f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = .32f), Offset(s * .64f, s * .61f), Offset(s * .82f, s * .79f), strokeWidth = s * .025f, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = s * .009f, center = Offset(s * .18f, s * .68f))
        drawCircle(XCyan, radius = s * .008f, center = Offset(s * .74f, s * .18f))
    }
}

@Composable
private fun NewsGlyph(diameter: Dp) {
    Canvas(Modifier.size(diameter)) {
        val s = size.minDimension
        // Atmospheric glow behind a layered, glass-like newspaper.
        drawCircle(Brush.radialGradient(listOf(XViolet.copy(alpha = .42f), XPurple.copy(alpha = .14f), Color.Transparent)), radius = s * .52f)
        drawCircle(XCyan.copy(alpha = .13f), radius = s * .31f, center = Offset(s * .43f, s * .43f), style = Stroke(s * .012f))

        val backTop = Offset(s * .30f, s * .25f)
        val backSize = Size(s * .48f, s * .52f)
        drawRoundRect(Color(0xFF181536), backTop + Offset(s * .055f, s * .04f), backSize, androidx.compose.ui.geometry.CornerRadius(s * .065f))
        drawRoundRect(XPurple.copy(alpha = .45f), backTop + Offset(s * .055f, s * .04f), backSize, androidx.compose.ui.geometry.CornerRadius(s * .065f), style = Stroke(s * .018f))

        val top = Offset(s * .20f, s * .20f)
        val card = Size(s * .52f, s * .56f)
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF2A175F), Color(0xFF141832), Color(0xFF0E1329))), top, card, androidx.compose.ui.geometry.CornerRadius(s * .07f))
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFFC39AFF), XViolet, XPurple)), top, card, androidx.compose.ui.geometry.CornerRadius(s * .07f), style = Stroke(s * .024f))

        // Hero thumbnail.
        val thumbTop = Offset(s * .285f, s * .30f)
        val thumbSize = Size(s * .29f, s * .17f)
        drawRoundRect(Brush.linearGradient(listOf(XPurple.copy(alpha = .72f), Color(0xFF312061))), thumbTop, thumbSize, androidx.compose.ui.geometry.CornerRadius(s * .032f))
        drawRoundRect(Color.White.copy(alpha = .30f), thumbTop, thumbSize, androidx.compose.ui.geometry.CornerRadius(s * .032f), style = Stroke(s * .009f))
        drawCircle(XCyan.copy(alpha = .85f), radius = s * .025f, center = Offset(s * .34f, s * .355f))
        drawLine(Color.White.copy(alpha = .7f), Offset(s * .39f, s * .345f), Offset(s * .53f, s * .345f), strokeWidth = s * .012f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = .42f), Offset(s * .39f, s * .392f), Offset(s * .49f, s * .392f), strokeWidth = s * .010f, cap = StrokeCap.Round)

        val lineStarts = listOf(.285f to .55f, .285f to .62f, .285f to .69f)
        val lineEnds = listOf(.63f, .59f, .52f)
        lineStarts.forEachIndexed { i, pair ->
            drawLine(
                if (i == 0) Color.White.copy(alpha = .78f) else XViolet.copy(alpha = .74f - i * .13f),
                Offset(s * pair.first, s * pair.second),
                Offset(s * lineEnds[i], s * pair.second),
                strokeWidth = s * if (i == 0) .015f else .012f,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(Color.White.copy(alpha = .9f), radius = s * .012f, center = Offset(s * .72f, s * .22f))
        drawCircle(XCyan.copy(alpha = .75f), radius = s * .008f, center = Offset(s * .16f, s * .62f))
    }
}

@Composable
private fun CloudUpdateGlyph(diameter: Dp) {
    Canvas(Modifier.size(diameter)) {
        val s = diameter.toPx()
        drawCircle(Brush.radialGradient(listOf(XPurple.copy(alpha = .40f), XPurple.copy(alpha = .08f), Color.Transparent)), radius = s * .5f)
        val cloud = Path().apply {
            moveTo(s * .24f, s * .62f)
            cubicTo(s * .09f, s * .61f, s * .09f, s * .40f, s * .26f, s * .37f)
            cubicTo(s * .32f, s * .16f, s * .61f, s * .16f, s * .69f, s * .38f)
            cubicTo(s * .88f, s * .38f, s * .91f, s * .62f, s * .70f, s * .64f)
        }
        drawPath(cloud, Brush.linearGradient(listOf(Color(0xFFB88DFF), XViolet, XPurple)), style = Stroke(s * .043f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(cloud, Color.White.copy(alpha = .24f), style = Stroke(s * .010f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(XViolet, Offset(s * .50f, s * .70f), Offset(s * .50f, s * .38f), strokeWidth = s * .045f, cap = StrokeCap.Round)
        drawLine(XViolet, Offset(s * .50f, s * .38f), Offset(s * .39f, s * .50f), strokeWidth = s * .045f, cap = StrokeCap.Round)
        drawLine(XViolet, Offset(s * .50f, s * .38f), Offset(s * .61f, s * .50f), strokeWidth = s * .045f, cap = StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = .72f), radius = s * .011f, center = Offset(s * .78f, s * .29f))
        drawCircle(XCyan.copy(alpha = .85f), radius = s * .008f, center = Offset(s * .22f, s * .72f))
    }
}

@Composable
private fun XCosmicBackground(content: @Composable BoxScope.() -> Unit) {
    // `--scr` from the redesign canvas: warm at the top, cooling through violet into near-black
    // (or into off-white in the light theme). Every screen sits on this one gradient.
    // Rebuilt every frame of the theme animation. Caching it by the setting string froze
    // the gradient for the whole 620 ms, so the background snapped while everything above
    // it cross-faded — and under «система» it never changed at all.
    val brush = xScreenBrush()
    Box(Modifier.fillMaxSize().background(brush)) {
        content()
    }
}

@Suppress("DEPRECATION")
private fun createOrionMediaRecorder(context: Context): MediaRecorder {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
}

/** Minutes since an ISO timestamp, or a large number when it cannot be read. */
private fun xMinutesSince(value: String): Long = runCatching {
    java.time.Duration.between(java.time.Instant.parse(value), java.time.Instant.now()).toMinutes().coerceAtLeast(0)
}.getOrDefault(Long.MAX_VALUE)

private fun formatMessageTime(value: String): String {
    return runCatching {
        val instant = java.time.Instant.parse(value)
        java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }.getOrElse {
        value.substringAfter('T', value).take(5).ifBlank { "--:--" }
    }
}

/** Время последнего сообщения в списках: «сегодня» — HH:mm, вчера, дальше — дата. */
private fun formatListTime(value: String): String {
    if (value.isBlank()) return ""
    val date = runCatching {
        java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }.getOrNull() ?: return formatMessageTime(value)
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> formatMessageTime(value)
        today.minusDays(1) -> tr("day_yesterday")
        else -> java.time.format.DateTimeFormatter
            .ofPattern("d.MM", java.util.Locale(OrionI18n.current.code))
            .format(date)
    }
}

private fun formatDeviceLastActive(value: String): String {
    if (value.isBlank()) return "время неизвестно"
    val seconds = runCatching {
        java.time.Duration.between(java.time.Instant.parse(value), java.time.Instant.now()).seconds.coerceAtLeast(0)
    }.getOrNull() ?: return "время неизвестно"
    return when {
        seconds < 75 -> "активен сейчас"
        seconds < 3600 -> "${seconds / 60} мин назад"
        seconds < 86_400 -> "${seconds / 3600} ч назад"
        else -> "${seconds / 86_400} дн назад"
    }
}

private fun formatPeerLastSeen(peer: CloudPeerPresence?): String {
    if (peer == null || peer.lastSeenAt.isBlank()) return "не в сети"
    val seconds = runCatching {
        java.time.Duration.between(java.time.Instant.parse(peer.lastSeenAt), java.time.Instant.now()).seconds.coerceAtLeast(0)
    }.getOrNull() ?: return "не в сети"
    return when {
        seconds < 60 -> "был(а) только что"
        seconds < 3600 -> "был(а) ${seconds / 60} мин назад"
        seconds < 86_400 -> "был(а) ${seconds / 3600} ч назад"
        else -> "был(а) ${seconds / 86_400} дн назад"
    }
}

private fun formatBirthdayInput(value: String): String {
    val digits = value.filter(Char::isDigit).take(8)
    return buildString {
        digits.forEachIndexed { index, char ->
            if (index == 4 || index == 6) append('-')
            append(char)
        }
    }
}

@Composable
private fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
