package net.orionx.messenger.videonote

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Правила видеосообщений-«кружков» OrionX (поведение как в Telegram).
 *
 * Длительность зашита прямо в имя файла — `vnote_<durationMs>_<timestamp>.mp4`. Благодаря
 * этому лента чата показывает длительность мгновенно и НЕ ходит в сеть за метаданными
 * для каждого кружка (раньше на каждый кружок открывался MediaMetadataRetriever по URL).
 */
object VideoNote {
    /** Короче секунды — это случайное касание, такое не отправляем. */
    const val MIN_DURATION_MS = 1_000L

    /** Жёсткий лимит записи; на нём запись сама останавливается и уходит в отправку. */
    const val MAX_DURATION_MS = 60_000L

    /** Сторона итогового квадрата. Кружок должен быть квадратным ФАЙЛОМ, а не круглой маской. */
    const val SIDE = 384

    const val MIME = "video/mp4"

    private const val PREFIX = "vnote_"

    fun buildFileName(durationMs: Long): String =
        "$PREFIX${durationMs.coerceIn(0L, MAX_DURATION_MS)}_${System.currentTimeMillis()}.mp4"

    /**
     * Кружок ли это. Кроме нового префикса понимаем историю чатов: сообщения,
     * отправленные прошлыми версиями OrionX, назывались `video_circle_*` / `circle_*`.
     */
    fun isVideoNote(attachmentName: String?, attachmentType: String?): Boolean {
        if (!attachmentType.orEmpty().lowercase().startsWith("video/")) return false
        val name = attachmentName.orEmpty().lowercase()
        // Только префикс в начале имени: `contains("circle_")` ловил обычные
        // видео вроде `dance_circle_final.mp4`.
        return name.startsWith(PREFIX) || name.startsWith("video_circle") || name.startsWith("circle_")
    }

    /** Длительность из имени файла; 0 — если имя старого формата и длительность неизвестна. */
    fun durationMsFromName(attachmentName: String?): Long {
        val name = attachmentName.orEmpty()
        if (!name.startsWith(PREFIX)) return 0L
        return name.removePrefix(PREFIX).substringBefore('_').toLongOrNull()
            ?.coerceIn(0L, MAX_DURATION_MS)
            ?: 0L
    }

    /** m:ss — бейдж длительности на кружке. */
    fun formatDuration(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1_000L
        return "%d:%02d".format(total / 60L, total % 60L)
    }

    /** Таймер записи: первые 10 секунд с десятыми (0:03,4) — как в Telegram. */
    fun formatRecordingClock(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val total = safe / 1_000L
        return if (total < 10L) {
            "%d:%02d,%d".format(total / 60L, total % 60L, (safe % 1_000L) / 100L)
        } else {
            "%d:%02d".format(total / 60L, total % 60L)
        }
    }
}

/**
 * Локальная отметка «кружок просмотрен». Сервер такого поля не хранит, а Telegram-подобная
 * точка «не просмотрено» нужна сразу — держим её в SharedPreferences на устройство+пользователя.
 */
@Stable
class VideoNoteWatchState internal constructor(private val prefs: SharedPreferences) {
    private val watched = mutableStateMapOf<String, Unit>()
    private val order = LinkedHashSet<String>()

    init {
        prefs.getStringSet(KEY, emptySet())?.forEach {
            watched[it] = Unit
            order.add(it)
        }
    }

    fun isWatched(messageId: String): Boolean = watched.containsKey(messageId)

    fun markWatched(messageId: String) {
        if (messageId.isBlank() || watched.containsKey(messageId)) return
        watched[messageId] = Unit
        order.add(messageId)
        while (order.size > LIMIT) {
            val oldest = order.first()
            order.remove(oldest)
            watched.remove(oldest)
        }
        prefs.edit().putStringSet(KEY, order.toSet()).apply()
    }

    private companion object {
        const val KEY = "watched_ids"
        const val LIMIT = 600
    }
}

@Composable
fun rememberVideoNoteWatchState(userId: String): VideoNoteWatchState {
    val context = LocalContext.current
    return remember(userId) {
        VideoNoteWatchState(
            context.getSharedPreferences("orionx_video_notes_$userId", Context.MODE_PRIVATE)
        )
    }
}
