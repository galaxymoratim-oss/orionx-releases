package net.orionx.messenger.videonote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Готовое к отправке видеосообщение: квадратный mp4 + квадратная превьюшка. */
data class EncodedVideoNote(
    val file: File,
    val thumbnail: File?,
    val durationMs: Long,
)

/**
 * Превращает сырую запись камеры в настоящий «кружок»: квадрат [VideoNote.SIDE]×[VideoNote.SIDE],
 * H.264 + AAC, обрезанный по [VideoNote.MAX_DURATION_MS].
 *
 * Это ключевое отличие от прошлой реализации: раньше отправлялся обычный кадр 16:9, а круг
 * рисовался только маской в UI — то есть у получателя (и в полноэкранном плеере) видео было
 * прямоугольным и «съезжало». Теперь круглый вид — свойство самого файла.
 */
object VideoNoteEncoder {

    suspend fun encode(context: Context, raw: File, recordedMs: Long): EncodedVideoNote {
        require(raw.exists() && raw.length() > 0L) { "Пустая запись" }

        val probed = withContext(Dispatchers.IO) { probeDurationMs(raw) }
        val durationMs = (if (probed > 0L) probed else recordedMs)
            .coerceIn(0L, VideoNote.MAX_DURATION_MS)
        require(durationMs >= VideoNote.MIN_DURATION_MS) { "Слишком короткая запись" }

        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val output = File(dir, "vnote_${System.currentTimeMillis()}.mp4")

        // Транскод — «лучшее усилие». Если кодек устройства не справился, отправляем исходник:
        // потерять сообщение из-за неудачного сжатия хуже, чем отправить его неквадратным.
        val squared = runCatching { transcodeToSquare(context, raw, output, durationMs) }
            .fold(onSuccess = { output.takeIf { it.exists() && it.length() > 0L } }, onFailure = {
                output.delete()
                null
            })

        val result = squared ?: raw
        val thumbnail = withContext(Dispatchers.IO) {
            runCatching { extractThumbnail(result, dir) }.getOrNull()
        }
        if (result !== raw) raw.delete()
        return EncodedVideoNote(result, thumbnail, durationMs)
    }

    private suspend fun transcodeToSquare(context: Context, input: File, output: File, durationMs: Long) {
        val item = MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(input))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0L)
                    .setEndPositionMs(min(durationMs, VideoNote.MAX_DURATION_MS))
                    .build()
            )
            .build()

        val edited = EditedMediaItem.Builder(item)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            VideoNote.SIDE,
                            VideoNote.SIDE,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                        )
                    ),
                )
            )
            .build()

        // Transformer требует один Looper-поток для start/cancel и колбэков. Сам транскод
        // всё равно идёт в фоне — здесь мы только ждём завершения.
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .build()
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        output.delete()
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                cont.invokeOnCancellation { runCatching { transformer.cancel() } }
                runCatching { transformer.start(edited, output.absolutePath) }
                    .onFailure { if (cont.isActive) cont.resumeWithException(it) }
            }
        }
    }

    /** Квадратная превьюшка первого кадра — её же кладём в кэш под URL после загрузки. */
    private fun extractThumbnail(source: File, dir: File): File? {
        val frame = readFrame { it.setDataSource(source.absolutePath) } ?: return null
        val square = squareCrop(frame, VideoNote.SIDE)
        val out = File(dir, "thumb_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        if (square !== frame) square.recycle()
        frame.recycle()
        return out.takeIf { it.length() > 0L }
    }

    private fun probeDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private const val DIR = "video_notes"
}

/**
 * Превью кружков: память -> диск -> сеть (первый кадр). Лента чата никогда не создаёт плеер
 * ради статичного кружка, поэтому список остаётся плавным даже при десятках видеосообщений.
 */
object VideoNoteThumbnails {

    private val memory = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Не даём ленте открыть десяток сетевых ретриверов одновременно. */
    private val gate = Semaphore(2)

    fun cached(url: String): Bitmap? = memory.get(key(url))

    suspend fun load(context: Context, url: String): Bitmap? {
        if (url.isBlank()) return null
        val key = key(url)
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = diskFile(context, key)
            if (file.exists() && file.length() > 0L) {
                BitmapFactory.decodeFile(file.absolutePath)?.also { memory.put(key, it) }
            } else {
                gate.withPermit {
                    memory.get(key) ?: run {
                        val frame = readFrame { it.setDataSource(url, emptyMap()) } ?: return@run null
                        val square = squareCrop(frame, VideoNote.SIDE)
                        if (square !== frame) frame.recycle()
                        runCatching {
                            file.parentFile?.mkdirs()
                            file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                        }
                        memory.put(key, square)
                        square
                    }
                }
            }
        }
    }

    /** Кладём собственную превьюшку сразу после загрузки — свои кружки не грузят кадр из сети. */
    fun seed(context: Context, url: String, jpeg: File) {
        if (url.isBlank() || !jpeg.exists()) return
        runCatching {
            val target = diskFile(context, key(url))
            target.parentFile?.mkdirs()
            jpeg.copyTo(target, overwrite = true)
            BitmapFactory.decodeFile(target.absolutePath)?.let { memory.put(key(url), it) }
        }
    }

    private fun diskFile(context: Context, key: String) = File(File(context.cacheDir, "video_note_thumbs"), "$key.jpg")

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
}

private inline fun readFrame(source: (MediaMetadataRetriever) -> Unit): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        source(retriever)
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun squareCrop(source: Bitmap, side: Int): Bitmap {
    val edge = min(source.width, source.height)
    if (edge <= 0) return source
    val cropped = if (source.width == source.height) source else Bitmap.createBitmap(
        source,
        (source.width - edge) / 2,
        (source.height - edge) / 2,
        edge,
        edge,
    )
    if (cropped.width == side) return cropped
    val scaled = Bitmap.createScaledBitmap(cropped, side, side, true)
    if (scaled !== cropped && cropped !== source) cropped.recycle()
    return scaled
}
