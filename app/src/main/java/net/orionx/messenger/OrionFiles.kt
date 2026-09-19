package net.orionx.messenger

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class XFileEntry(
    val uri: Uri,
    val name: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val mimeType: String,
)

private enum class XFileSort { NAME, DATE, SIZE }

private fun humanSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.1f МБ".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.0f КБ".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes Б"
}

private fun entryIcon(entry: XFileEntry): ImageVector = when {
    entry.directory -> Icons.Outlined.Folder
    entry.mimeType.startsWith("image/") -> Icons.Outlined.Image
    entry.mimeType.startsWith("video/") -> Icons.Outlined.Videocam
    entry.mimeType.startsWith("audio/") -> Icons.Outlined.MusicNote
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

/**
 * «файлы»: storage bar, search, sort chips and the folder listing.
 *
 * Android does not hand apps the filesystem any more, so the folder comes from the system picker
 * (Storage Access Framework) and everything below works on that tree.
 */
@Composable
internal fun XFilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var stack by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var entries by remember { mutableStateOf<List<XFileEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(XFileSort.NAME) }
    var selected by remember { mutableStateOf<XFileEntry?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            // Оба права: без WRITE после перезапуска процесса удаление файлов падало.
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        DocumentFile.fromTreeUri(context, uri)?.let { stack = listOf(it) }
    }

    val currentFolder = stack.lastOrNull()

    LaunchedEffect(currentFolder) {
        val folder = currentFolder ?: run {
            entries = emptyList()
            return@LaunchedEffect
        }
        loading = true
        // Отозванный системой persistable-грант бросает SecurityException — показываем
        // пустую папку вместо крша.
        entries = runCatching {
            withContext(Dispatchers.IO) {
                folder.listFiles().map { file ->
                    XFileEntry(
                        uri = file.uri,
                        name = file.name.orEmpty().ifBlank { "без имени" },
                        directory = file.isDirectory,
                        sizeBytes = file.length(),
                        modifiedAt = file.lastModified(),
                        mimeType = file.type.orEmpty(),
                    )
                }
            }
        }.getOrElse { emptyList() }
        loading = false
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2000)
            toast = null
        }
    }

    val visible = remember(entries, query, sort) {
        val needle = query.trim().lowercase()
        entries
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
            .sortedWith(
                compareByDescending<XFileEntry> { it.directory }.thenBy {
                    when (sort) {
                        XFileSort.NAME -> it.name.lowercase()
                        XFileSort.DATE -> ""
                        XFileSort.SIZE -> ""
                    }
                }.thenByDescending {
                    when (sort) {
                        XFileSort.DATE -> it.modifiedAt
                        XFileSort.SIZE -> it.sizeBytes
                        XFileSort.NAME -> 0L
                    }
                }
            )
    }

    val deletedLabel = xSettingText("file_deleted")
    val deleteFailedLabel = xSettingText("file_delete_failed")

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            XScreenHeader(title = xSettingText("app_files"), onBack = onBack)
            Column(
                Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (stack.size > 1) {
                        Box(
                            Modifier.size(36.dp).xShadow(RoundedCornerShape(13.dp)).clip(RoundedCornerShape(13.dp))
                                .background(XSurface)
                                .clickable { stack = stack.dropLast(1) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = XText, modifier = Modifier.size(19.dp)) }
                    }
                    Text(
                        currentFolder?.name.orEmpty().ifBlank { xSettingText("files_no_folder") },
                        style = XType.rowTitle.copy(fontSize = 15.sp, letterSpacing = (-.02).em),
                        color = XText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Device storage, read straight from the data partition.
                val stats = remember {
                    runCatching {
                        val fs = StatFs(Environment.getDataDirectory().path)
                        val total = fs.blockCountLong * fs.blockSizeLong
                        val free = fs.availableBlocksLong * fs.blockSizeLong
                        (total - free) to total
                    }.getOrDefault(0L to 0L)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .xShadow(RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(XSurface)
                        .padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Storage, null, tint = XMuted, modifier = Modifier.size(17.dp))
                        Text(
                            "${humanSize(stats.first)} ${xSettingText("of_total")} ${humanSize(stats.second)}",
                            style = XType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = XMuted,
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(XSurfaceRaised)) {
                        val fraction = if (stats.second > 0) stats.first.toFloat() / stats.second else 0f
                        Box(
                            Modifier
                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF8FB4FF), Color(0xFFB79CFF)))),
                        )
                    }
                }

                if (currentFolder == null) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.size(70.dp).xShadow(RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(XSurface),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.FolderOpen, null, tint = XMuted, modifier = Modifier.size(32.dp)) }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            xSettingText("files_pick_hint"),
                            style = XType.bodyStrong,
                            color = XMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 240.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        XPrimaryButton(xSettingText("files_pick")) { picker.launch(null) }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(16.dp)).background(XSurfaceRaised)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Search, null, tint = XMuted, modifier = Modifier.size(18.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(xSettingText("files_search"), style = XType.rowTitle.copy(fontSize = 13.sp), color = XMuted)
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = XType.rowTitle.copy(fontSize = 13.sp, color = XText),
                                cursorBrush = SolidColor(XAccent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(
                            XFileSort.NAME to xSettingText("sort_name"),
                            XFileSort.DATE to xSettingText("sort_date"),
                            XFileSort.SIZE to xSettingText("sort_size"),
                        ).forEach { (value, label) ->
                            val active = value == sort
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) XText else XSurfaceRaised)
                                    .clickable { sort = value },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = XType.rowTitle.copy(fontSize = 10.5.sp),
                                    color = if (active) XInverse else XText,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    if (visible.isEmpty() && !loading) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier.size(70.dp).xShadow(RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(XSurface),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.FolderOpen, null, tint = XMuted, modifier = Modifier.size(32.dp)) }
                            Spacer(Modifier.height(10.dp))
                            Text(xSettingText("files_empty"), style = XType.rowTitle.copy(fontSize = 14.sp), color = XText)
                        }
                    } else {
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(visible, key = { it.uri.toString() }) { entry ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .xShadow(RoundedCornerShape(20.dp))
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(XSurface)
                                        .clickable {
                                            if (entry.directory) {
                                                // fromTreeUri на child-URI возвращает корень дерева,
                                                // поэтому спускаемся через findFile по имени.
                                                currentFolder?.findFile(entry.name)?.let { stack = stack + it }
                                            } else {
                                                selected = entry
                                            }
                                        }
                                        .padding(horizontal = 15.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                                ) {
                                    Box(
                                        Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(XSurfaceRaised),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(entryIcon(entry), null, tint = XText, modifier = Modifier.size(20.dp)) }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            entry.name,
                                            style = XType.rowTitle.copy(fontSize = 13.5.sp),
                                            color = XText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            // Макет: папка — «N объектов», файл — «размер · дата».
                                        if (entry.directory)
                                            xSettingText("folder")
                                        else
                                            humanSize(entry.sizeBytes) + " · " + java.text.SimpleDateFormat("d.MM", java.util.Locale.getDefault()).format(java.util.Date(entry.modifiedAt)),
                                            style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                            color = XMuted,
                                        )
                                    }
                                    Icon(
                                        // Макет: у папки — chevron_right, у файла — more_horiz.
                                        if (entry.directory) Icons.Outlined.ChevronRight else Icons.Outlined.MoreHoriz,
                                        null,
                                        tint = XMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        selected?.let { entry ->
            XFileActionSheet(
                entry = entry,
                onDismiss = { selected = null },
                onShare = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = entry.mimeType.ifBlank { "*/*" }
                        putExtra(Intent.EXTRA_STREAM, entry.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, null))
                    selected = null
                },
                onDelete = {
                    val removed = runCatching {
                        android.provider.DocumentsContract.deleteDocument(context.contentResolver, entry.uri)
                    }.getOrDefault(false)
                    if (removed) {
                        entries = entries.filterNot { it.uri == entry.uri }
                        toast = deletedLabel
                    } else {
                        toast = deleteFailedLabel
                    }
                    selected = null
                },
            )
        }

        toast?.let { message ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(XText)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, style = XType.rowTitle.copy(fontSize = 12.sp), color = XInverse)
            }
        }
    }
}

@Composable
private fun XFileActionSheet(
    entry: XFileEntry,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .xShadow(RoundedCornerShape(26.dp), 26.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(XSurface)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(XSurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) { Icon(entryIcon(entry), null, tint = XText, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        entry.name,
                        style = XType.rowTitle.copy(fontSize = 14.sp),
                        color = XText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        humanSize(entry.sizeBytes),
                        style = XType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = XMuted,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(16.dp)).background(XText).clickable(onClick = onShare),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, null, tint = XInverse, modifier = Modifier.size(18.dp))
                    Text(xSettingText("send_action"), style = XType.rowTitle.copy(fontSize = 11.sp), color = XInverse)
                }
                Box(
                    Modifier.size(54.dp, 46.dp).clip(RoundedCornerShape(16.dp)).background(XSurfaceRaised).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Delete, xSettingText("delete_action"), tint = XRed, modifier = Modifier.size(19.dp)) }
            }
        }
    }
}
