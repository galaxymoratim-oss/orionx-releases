package net.orionx.messenger.videonote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Кружок, который ещё не долетел до сервера.
 *
 * Telegram показывает видеосообщение в ленте сразу после того, как вы отпустили кнопку,
 * а сжатие и загрузка идут уже под кружком. Здесь так же: пузырь появляется мгновенно,
 * а при обрыве связи остаётся на месте с кнопкой «Повторить» — сообщение не теряется.
 */
data class PendingVideoNote(
    val localId: String,
    val file: File,
    val durationMs: Long,
    val thumbnailFile: File? = null,
    val stage: Stage = Stage.PROCESSING,
    val error: String? = null,
) {
    enum class Stage { PROCESSING, UPLOADING, FAILED }
}

@Stable
class VideoNoteOutbox {
    val items = mutableStateListOf<PendingVideoNote>()

    companion object {
        // Outbox живёт на уровне процесса: если выйти из чата во время загрузки,
        // черновик и его файлы не теряются — при возврате можно повторить отправку.
        private val perConversation = HashMap<String, VideoNoteOutbox>()

        fun forConversation(conversationId: String): VideoNoteOutbox =
            perConversation.getOrPut(conversationId) { VideoNoteOutbox() }
    }

    /**
     * Загрузка умирает вместе со скоупом экрана, поэтому при возврате в чат
     * зависшие «UPLOADING» возвращаем в ошибку — «Повторить» снова доступно.
     */
    fun resetStaleUploads() {
        items.toList().forEach { pending ->
            if (pending.stage == PendingVideoNote.Stage.UPLOADING) {
                fail(pending.localId, null)
            }
        }
    }

    fun enqueue(pending: PendingVideoNote) {
        items.add(pending)
    }

    fun update(localId: String, transform: (PendingVideoNote) -> PendingVideoNote) {
        val index = items.indexOfFirst { it.localId == localId }
        if (index >= 0) items[index] = transform(items[index])
    }

    fun fail(localId: String, message: String?) {
        update(localId) { it.copy(stage = PendingVideoNote.Stage.FAILED, error = message) }
    }

    fun remove(localId: String) {
        val index = items.indexOfFirst { it.localId == localId }
        if (index >= 0) items.removeAt(index)
    }

    fun find(localId: String): PendingVideoNote? = items.firstOrNull { it.localId == localId }

    /** Удаляет черновик и его временные файлы — «отменить отправку». */
    fun discard(localId: String) {
        val pending = find(localId) ?: return
        runCatching { pending.file.delete() }
        runCatching { pending.thumbnailFile?.delete() }
        remove(localId)
    }
}

@Composable
fun PendingVideoNoteBubble(
    pending: PendingVideoNote,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = videoNoteBubbleSize()
    var thumbnail by remember(pending.thumbnailFile?.absolutePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pending.thumbnailFile?.absolutePath) {
        val path = pending.thumbnailFile?.takeIf { it.exists() }?.absolutePath ?: return@LaunchedEffect
        thumbnail = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }

    Column(modifier, horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF1B1636), Color(0xFF0B0D1D)))),
            contentAlignment = Alignment.Center,
        ) {
            thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .42f)))

            Canvas(Modifier.matchParentSize().padding(2.dp)) {
                drawVideoNoteRing(
                    progress = 0f,
                    trackColor = Color.White.copy(alpha = .22f),
                    progressColor = Color.White,
                )
            }

            when (pending.stage) {
                PendingVideoNote.Stage.FAILED -> Icon(
                    Icons.Outlined.ErrorOutline,
                    "Не отправлено",
                    tint = Color(0xFFFF5C76),
                    modifier = Modifier.size(34.dp),
                )
                else -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
            }

            Surface(
                color = Color.Black.copy(alpha = .58f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) {
                Text(
                    when (pending.stage) {
                        PendingVideoNote.Stage.PROCESSING -> "Обработка…"
                        PendingVideoNote.Stage.UPLOADING -> "Отправка…"
                        PendingVideoNote.Stage.FAILED -> "Не отправлено"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        if (pending.stage == PendingVideoNote.Stage.FAILED) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                pending.error?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color(0xFFFFB2BE), fontSize = 11.sp, maxLines = 2)
                }
                Surface(onClick = onRetry, color = Color(0xFF733DFF), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Refresh, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Повторить", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    onClick = onDiscard,
                    color = Color.White.copy(alpha = .08f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        "Удалить",
                        tint = Color(0xFFFFB2BE),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).size(16.dp),
                    )
                }
            }
        }
    }
}
