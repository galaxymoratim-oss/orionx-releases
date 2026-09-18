package net.orionx.messenger

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Real waveforms for voice notes.
 *
 * The bars used to be the fixed pattern the canvas draws, so every voice note looked
 * identical. This decodes the recording and measures it: the file is pulled once, run
 * through the platform decoder, and reduced to one loudness value per bar. The answer is
 * kept in memory and on disk, so a chat that scrolls past the same note again pays nothing.
 */
internal object XVoiceWave {

    /** Bar heights in dp, matching the range the canvas draws (7…19). */
    private const val MIN_BAR = 5
    private const val MAX_BAR = 20

    /** Отделяет длительность от уровней в файле кэша. */
    private const val DURATION_SEPARATOR = '|'

    private val memory = HashMap<String, List<Int>>()

    /** Длительность в мс — чтобы таймкод голосовой был виден до запуска. */
    private val durations = HashMap<String, Int>()

    fun cachedDurationMs(url: String): Int? = synchronized(durations) { durations[url] }

    @Volatile
    private var cacheDir: File? = null

    fun attach(context: Context) {
        if (cacheDir != null) return
        cacheDir = File(context.cacheDir, "xw").apply { mkdirs() }
    }

    fun cached(url: String): List<Int>? = synchronized(memory) { memory[url] }

    /**
     * Blocking — call from `Dispatchers.IO`. Returns null when the audio cannot be read, and
     * the caller keeps the canvas pattern as a stand-in.
     */
    fun of(url: String, bars: Int): List<Int>? {
        synchronized(memory) { memory[url] }?.let { return it }

        readDisk(url, bars)?.let { stored ->
            synchronized(memory) { memory[url] = stored }
            return stored
        }

        val source = runCatching { download(url) }.getOrNull() ?: return null
        val levels = runCatching { measure(url, source, bars) }.getOrNull()
        source.delete()
        if (levels == null || levels.isEmpty()) return null

        synchronized(memory) { memory[url] = levels }
        writeDisk(url, levels)
        return levels
    }

    /** The decoder needs a file, and a voice note is small enough to just fetch. */
    private fun download(url: String): File {
        val target = File.createTempFile("voice", ".m4a", cacheDir ?: File(System.getProperty("java.io.tmpdir")!!))
        // Без таймаутов зависший сервер держит поток загрузки вечно.
        val connection = java.net.URL(url).openConnection().apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        connection.getInputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    /**
     * Decodes the track and returns one bar per slice of the timeline, each the RMS of the
     * samples that fall in it, scaled into the canvas' height range.
     */
    private fun measure(url: String, file: File, bars: Int): List<Int> {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)

        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(i)
            if (candidate.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                track = i
                format = candidate
                break
            }
        }
        if (track < 0 || format == null) {
            extractor.release()
            return emptyList()
        }
        extractor.selectTrack(track)

        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        // Sum of squares per bucket, so quiet stretches stay short and loud ones stand up.
        val sums = DoubleArray(bars)
        val counts = IntArray(bars)
        val durationUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
            .coerceAtLeast(1L)
        synchronized(durations) { durations[url] = (durationUs / 1000L).toInt() }

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var guard = 0

        while (!outputDone && guard++ < 4000) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(5_000)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, 5_000)
            if (outIndex >= 0) {
                if (info.size > 0) {
                    val out = codec.getOutputBuffer(outIndex)!!
                    val bucket = ((info.presentationTimeUs.toDouble() / durationUs) * bars)
                        .toInt().coerceIn(0, bars - 1)
                    accumulate(out, info, sums, counts, bucket)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }

        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { extractor.release() }

        val rms = DoubleArray(bars) { i ->
            if (counts[i] == 0) 0.0 else sqrt(sums[i] / counts[i])
        }
        val peak = rms.max().takeIf { it > 0.0 } ?: return emptyList()

        return rms.map { level ->
            // A little compression, so a normal voice fills the bar range instead of
            // hugging the bottom.
            val shaped = sqrt(level / peak)
            (MIN_BAR + shaped * (MAX_BAR - MIN_BAR)).roundToInt().coerceIn(MIN_BAR, MAX_BAR)
        }
    }

    private fun accumulate(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        sums: DoubleArray,
        counts: IntArray,
        bucket: Int,
    ) {
        buffer.position(info.offset)
        val shorts = buffer.asShortBuffer()
        var taken = 0
        // Every sixteenth sample is plenty for a 22-bar picture and keeps this cheap.
        var i = 0
        while (i < shorts.limit()) {
            val value = abs(shorts.get(i).toDouble()) / Short.MAX_VALUE
            sums[bucket] += value * value
            taken++
            i += 16
        }
        counts[bucket] += taken
    }

    /**
     * Файл кэша хранит «длительность|уровни». Раньше в нём были только уровни, и после
     * перезапуска приложения волна поднималась с диска, а таймкод — нет: у каждой голосовой
     * стояло 0:00, пока её не включишь. Файл старого формата считаем промахом кэша, чтобы
     * длительность один раз пересчиталась и легла на диск.
     */
    private fun readDisk(url: String, bars: Int): List<Int>? {
        val file = fileFor(url) ?: return null
        if (!file.isFile) return null
        return runCatching {
            val raw = file.readText()
            val split = raw.indexOf(DURATION_SEPARATOR)
            if (split < 0) return@runCatching null
            val stored = raw.substring(0, split).trim().toIntOrNull() ?: return@runCatching null
            if (stored < 0) return@runCatching null
            val parts = raw.substring(split + 1).split(",").mapNotNull { it.trim().toIntOrNull() }
            parts.takeIf { it.size == bars }?.also {
                if (stored > 0) synchronized(durations) { durations[url] = stored }
            }
        }.getOrNull()
    }

    private fun writeDisk(url: String, levels: List<Int>) {
        val file = fileFor(url) ?: return
        // Ноль значит «контейнер не сообщил длительность» — волну всё равно кэшируем,
        // иначе файл качался бы заново при каждой прокрутке.
        val duration = (synchronized(durations) { durations[url] } ?: 0).coerceAtLeast(0)
        runCatching { file.writeText("" + duration + DURATION_SEPARATOR + levels.joinToString(",")) }
    }

    private fun fileFor(url: String): File? {
        val dir = cacheDir ?: return null
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".wave")
    }
}
