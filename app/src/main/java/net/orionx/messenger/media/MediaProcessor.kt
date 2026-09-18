package net.orionx.messenger.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object MediaProcessor {
    data class StickerOptions(
        val removeBackground: Boolean = true,
        val addOutline: Boolean = false,
        val backgroundTolerance: Int = 46,
        val canvasSize: Int = 512,
        val padding: Int = 32,
    )

    fun prepareSticker(context: Context, uri: Uri, options: StickerOptions): ByteArray {
        val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Не удалось открыть изображение")
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
        if (options.removeBackground) removeSimpleEdgeBackground(mutable, options.backgroundTolerance)
        val trimmed = trimTransparent(mutable)
        val output = fitOnSquareCanvas(trimmed, options.canvasSize, options.padding)
        val finalBitmap = if (options.addOutline) addWhiteOutline(output, radius = 7) else output
        return ByteArrayOutputStream().use { out ->
            check(finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Не удалось сохранить стикер" }
            out.toByteArray()
        }
    }

    fun videoToGif(
        context: Context,
        uri: Uri,
        durationMs: Long = 3_000L,
        fps: Int = 8,
        maxSide: Int = 420,
    ): ByteArray {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        try {
            val sourceDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: durationMs
            val usedDuration = min(durationMs, sourceDuration.coerceAtLeast(250L))
            val frameCount = max(2, ((usedDuration / 1000.0) * fps).roundToInt())
            val frames = ArrayList<Bitmap>(frameCount)
            for (i in 0 until frameCount) {
                val timeUs = ((usedDuration * 1000L) * i / frameCount)
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                frames += scaleDown(raw, maxSide)
            }
            require(frames.isNotEmpty()) { "Не удалось получить кадры из видео" }
            val first = frames.first()
            val output = ByteArrayOutputStream()
            FixedPaletteGifEncoder(output, first.width, first.height, delayCs = max(2, 100 / fps)).use { gif ->
                frames.forEach { frame ->
                    val normalized = if (frame.width == first.width && frame.height == first.height) frame
                    else Bitmap.createScaledBitmap(frame, first.width, first.height, true)
                    gif.addFrame(normalized)
                }
            }
            return output.toByteArray()
        } finally {
            retriever.release()
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
    }

    private fun colorDistance(a: Int, b: Int): Int =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))

    private fun removeSimpleEdgeBackground(bitmap: Bitmap, tolerance: Int) {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 2 || h < 2) return
        val corners = intArrayOf(bitmap.getPixel(0,0), bitmap.getPixel(w-1,0), bitmap.getPixel(0,h-1), bitmap.getPixel(w-1,h-1))
        val bg = Color.rgb(
            corners.map { Color.red(it) }.average().roundToInt(),
            corners.map { Color.green(it) }.average().roundToInt(),
            corners.map { Color.blue(it) }.average().roundToInt(),
        )
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun offer(x: Int, y: Int) {
            if (x !in 0 until w || y !in 0 until h) return
            val idx = y * w + x
            if (visited[idx]) return
            visited[idx] = true
            if (colorDistance(bitmap.getPixel(x, y), bg) <= tolerance * 3) queue[tail++] = idx
        }
        for (x in 0 until w) { offer(x, 0); offer(x, h - 1) }
        for (y in 0 until h) { offer(0, y); offer(w - 1, y) }
        while (head < tail) {
            val idx = queue[head++]
            val x = idx % w
            val y = idx / w
            val old = bitmap.getPixel(x, y)
            bitmap.setPixel(x, y, old and 0x00FFFFFF)
            offer(x + 1, y); offer(x - 1, y); offer(x, y + 1); offer(x, y - 1)
        }
    }

    private fun trimTransparent(bitmap: Bitmap): Bitmap {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 8) {
                left = min(left, x); top = min(top, y); right = max(right, x); bottom = max(bottom, y)
            }
        }
        if (right < left || bottom < top) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    private fun fitOnSquareCanvas(bitmap: Bitmap, size: Int, padding: Int): Bitmap {
        val available = (size - padding * 2).coerceAtLeast(64)
        val scale = min(available.toFloat() / bitmap.width, available.toFloat() / bitmap.height)
        val w = max(1, (bitmap.width * scale).roundToInt())
        val h = max(1, (bitmap.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { out ->
            Canvas(out).drawBitmap(scaled, ((size - w) / 2f), ((size - h) / 2f), Paint(Paint.ANTI_ALIAS_FLAG))
        }
    }

    private fun addWhiteOutline(bitmap: Bitmap, radius: Int): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val alpha = bitmap.extractAlpha()
        for (dy in -radius..radius step 2) for (dx in -radius..radius step 2) {
            if (dx * dx + dy * dy <= radius * radius) canvas.drawBitmap(alpha, dx.toFloat(), dy.toFloat(), paint)
        }
        alpha.recycle()
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return out
    }
}

private class FixedPaletteGifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    private val delayCs: Int,
) : AutoCloseable {
    init {
        writeAscii("GIF89a")
        writeShort(width); writeShort(height)
        out.write(0xF7); out.write(0); out.write(0)
        for (i in 0..255) {
            out.write(((i shr 5) and 0x7) * 255 / 7)
            out.write(((i shr 2) and 0x7) * 255 / 7)
            out.write((i and 0x3) * 255 / 3)
        }
        out.write(byteArrayOf(0x21,0xFF.toByte(),0x0B)); writeAscii("NETSCAPE2.0")
        out.write(byteArrayOf(0x03,0x01,0x00,0x00,0x00))
    }

    fun addFrame(bitmap: Bitmap) {
        out.write(0x21); out.write(0xF9); out.write(4); out.write(0)
        writeShort(delayCs); out.write(0); out.write(0)
        out.write(0x2C); writeShort(0); writeShort(0); writeShort(width); writeShort(height); out.write(0)
        out.write(8)
        val indices = ByteArray(width * height)
        var p = 0
        for (y in 0 until height) for (x in 0 until width) {
            val c = bitmap.getPixel(x, y)
            indices[p++] = (((Color.red(c) shr 5) shl 5) or ((Color.green(c) shr 5) shl 2) or (Color.blue(c) shr 6)).toByte()
        }
        writeLiteralLzw(indices)
        out.write(0)
    }

    private fun writeLiteralLzw(data: ByteArray) {
        val bytes = ByteArrayOutputStream()
        var current = 0
        var bits = 0
        fun code(value: Int) {
            current = current or (value shl bits)
            bits += 9
            while (bits >= 8) { bytes.write(current and 0xFF); current = current ushr 8; bits -= 8 }
        }
        code(256)
        var sinceClear = 0
        for (b in data) {
            code(b.toInt() and 0xFF)
            if (++sinceClear >= 200) { code(256); sinceClear = 0 }
        }
        code(257)
        if (bits > 0) bytes.write(current and 0xFF)
        val encoded = bytes.toByteArray()
        var offset = 0
        while (offset < encoded.size) {
            val n = min(255, encoded.size - offset)
            out.write(n); out.write(encoded, offset, n); offset += n
        }
    }

    private fun writeShort(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }
    private fun writeAscii(s: String) { out.write(s.toByteArray(Charsets.US_ASCII)) }
    override fun close() { out.write(0x3B); out.flush() }
}
