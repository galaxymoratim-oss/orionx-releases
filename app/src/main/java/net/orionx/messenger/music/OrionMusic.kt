package net.orionx.messenger.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** One track from the device's own library. */
data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: Uri,
    val albumId: Long,
) {
    /** MediaStore's album art, resolved lazily by the UI. */
    val artworkUri: Uri
        get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}

/**
 * Reads the on-device music library. The canvas "музыка" mini-app is a local player — no
 * streaming, no catalogue — so MediaStore is the whole data source.
 */
object MusicLibrary {

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) android.Manifest.permission.READ_MEDIA_AUDIO
        else android.Manifest.permission.READ_EXTERNAL_STORAGE

    fun load(context: Context): List<MusicTrack> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        // Ringtones and notification blips are not music; MediaStore flags them for us.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 20000"
        val order = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        val out = ArrayList<MusicTrack>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, null, order)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    out.add(
                        MusicTrack(
                            id = id,
                            title = cursor.getString(titleCol).orEmpty().ifBlank { "Без названия" },
                            artist = cursor.getString(artistCol).orEmpty()
                                .takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING } ?: "Неизвестный исполнитель",
                            durationMs = cursor.getLong(durationCol),
                            uri = ContentUris.withAppendedId(collection, id),
                            albumId = cursor.getLong(albumCol),
                        )
                    )
                }
            }
        }
        return out
    }
}

/** Repeat state of the player, matching the canvas's `repeat_one` toggle. */
enum class MusicRepeat { OFF, ONE }

/**
 * Playback state shared by the music screen and by halo — the bar over the status bar shows
 * whatever this controller is playing, so both read from one instance held by the activity.
 */
@Stable
class MusicController(private val context: Context) {

    var tracks by mutableStateOf<List<MusicTrack>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableStateOf(MusicRepeat.OFF)
        private set
    var liked by mutableStateOf<Set<Long>>(emptySet())
        private set

    val current: MusicTrack?
        get() = tracks.getOrNull(index)

    /** True once something has been chosen, which is what halo keys its visibility off. */
    val hasTrack: Boolean
        get() = current != null

    private var player: ExoPlayer? = null

    private fun requirePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().also { created ->
        created.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = created.currentMediaItemIndex
            }
        })
        player = created
    }

    fun replaceLibrary(list: List<MusicTrack>) {
        tracks = list
    }

    fun play(at: Int) {
        if (at !in tracks.indices) return
        val exo = requirePlayer()
        if (exo.mediaItemCount != tracks.size) {
            exo.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) })
            exo.prepare()
        }
        exo.shuffleModeEnabled = shuffle
        exo.repeatMode = if (repeat == MusicRepeat.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exo.seekTo(at, 0L)
        exo.playWhenReady = true
        index = at
    }

    fun toggle() {
        val exo = player
        if (exo == null || index < 0) {
            play(0)
            return
        }
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    /** Голосовые сообщения в чате глушат музыку на время своего звучания. */
    fun pause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause()
    }

    fun next() {
        val exo = player ?: return play(0)
        if (exo.hasNextMediaItem()) exo.seekToNextMediaItem() else exo.seekTo(0, 0L)
        index = exo.currentMediaItemIndex
    }

    fun previous() {
        val exo = player ?: return play(0)
        // Same rule players use everywhere: restart the track unless we are near its start.
        if (exo.currentPosition > 3_000 || !exo.hasPreviousMediaItem()) exo.seekTo(0L)
        else exo.seekToPreviousMediaItem()
        index = exo.currentMediaItemIndex
    }

    fun seekTo(fraction: Float) {
        val exo = player ?: return
        val duration = exo.duration
        if (duration > 0) exo.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        player?.shuffleModeEnabled = shuffle
    }

    fun toggleRepeat() {
        repeat = if (repeat == MusicRepeat.ONE) MusicRepeat.OFF else MusicRepeat.ONE
        player?.repeatMode = if (repeat == MusicRepeat.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun toggleLike() {
        val id = current?.id ?: return
        liked = if (id in liked) liked - id else liked + id
    }

    /** Called on a timer while the UI is visible; ExoPlayer has no position callback. */
    fun syncPosition() {
        val exo = player ?: return
        positionMs = exo.currentPosition
        if (exo.currentMediaItemIndex != index && exo.currentMediaItemIndex >= 0) {
            index = exo.currentMediaItemIndex
        }
    }

    fun durationMs(): Long = player?.duration?.takeIf { it > 0 } ?: current?.durationMs ?: 0L

    fun release() {
        player?.release()
        player = null
        playing = false
    }
}

/** `1:12` / `12:05` — the clock format both the player card and halo use. */
fun formatTrackClock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
