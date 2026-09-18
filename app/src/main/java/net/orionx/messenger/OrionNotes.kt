package net.orionx.messenger

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

internal data class XNote(
    val id: String,
    val title: String,
    val text: String,
    val updatedAt: Long,
    val pinned: Boolean,
    val source: String,
)

/**
 * Notes live in SharedPreferences as a small JSON array — the canvas keeps them on the device and
 * the stopwatch writes laps here, so nothing goes to the server.
 *
 * Хранилище персональное: у каждого пользователя аккаунта свой файл
 * («orionx_notes_<id>»), при первом входе legacy-заметки переезжают один раз.
 */
@Stable
internal class XNotesStore(private val context: Context, private val userId: String = "") {
    private val prefs = context.getSharedPreferences(
        if (userId.isBlank()) "orionx_notes" else "orionx_notes_$userId",
        Context.MODE_PRIVATE,
    )

    var notes by mutableStateOf(load())
        private set

    private fun load(): List<XNote> = runCatching {
        // Один раз переносим заметки из общего файла (до появления персональных).
        if (userId.isNotBlank() && !prefs.contains("items")) {
            val legacy = context.getSharedPreferences("orionx_notes", Context.MODE_PRIVATE)
                .getString("items", null).orEmpty()
            if (legacy != "[]" && legacy.isNotBlank()) {
                prefs.edit().putString("items", legacy).apply()
            } else {
                prefs.edit().putString("items", "[]").apply()
            }
        }
        val raw = prefs.getString("items", "[]").orEmpty()
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val row = array.getJSONObject(index)
            XNote(
                id = row.optString("id"),
                title = row.optString("title"),
                text = row.optString("text"),
                updatedAt = row.optLong("ts"),
                pinned = row.optBoolean("pin"),
                source = row.optString("src"),
            )
        }
    }.getOrDefault(emptyList())

    private fun persist(list: List<XNote>) {
        notes = list
        val array = JSONArray()
        list.forEach { note ->
            array.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("text", note.text)
                    put("ts", note.updatedAt)
                    put("pin", note.pinned)
                    put("src", note.source)
                }
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    /** Pinned first, then newest — the ordering the canvas applies to the list. */
    val ordered: List<XNote>
        get() = notes.sortedWith(compareByDescending<XNote> { it.pinned }.thenByDescending { it.updatedAt })

    fun add(title: String = "", text: String = "", source: String = ""): XNote {
        val note = XNote(
            id = "n${System.currentTimeMillis()}",
            title = title,
            text = text,
            updatedAt = System.currentTimeMillis(),
            pinned = false,
            source = source,
        )
        persist(listOf(note) + notes)
        return note
    }

    fun update(id: String, title: String, text: String) {
        persist(notes.map { if (it.id == id) it.copy(title = title, text = text, updatedAt = System.currentTimeMillis()) else it })
    }

    fun togglePin(id: String) {
        persist(notes.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
    }

    fun delete(id: String) {
        persist(notes.filterNot { it.id == id })
    }

    fun byId(id: String): XNote? = notes.firstOrNull { it.id == id }
}

/** `14:32` for today, `03.09` for anything older — the canvas's `_fmt`. */
private fun noteStamp(ms: Long): String {
    val then = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val now = java.util.Calendar.getInstance()
    val sameDay = then.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
        then.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        "%02d:%02d".format(then.get(java.util.Calendar.HOUR_OF_DAY), then.get(java.util.Calendar.MINUTE))
    } else {
        "%02d.%02d".format(then.get(java.util.Calendar.DAY_OF_MONTH), then.get(java.util.Calendar.MONTH) + 1)
    }
}

/** «блокнот»: the card list with the round add button, and the editor behind it. */
@Composable
internal fun XNotesScreen(store: XNotesStore, onBack: () -> Unit) {
    var openId by remember { mutableStateOf<String?>(null) }
    val open = openId?.let(store::byId)

    if (open == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            XScreenHeader(title = xSettingText("app_note"), onBack = onBack)
            Box(Modifier.fillMaxSize()) {
                val items = store.ordered
                if (items.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.size(76.dp).xShadow(RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(XSurface),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.EditNote, null, tint = XMuted, modifier = Modifier.size(34.dp)) }
                        Spacer(Modifier.height(12.dp))
                        Text(xSettingText("notes_empty"), style = XType.rowTitle.copy(fontSize = 15.sp), color = XText)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            xSettingText("notes_empty_hint"),
                            style = XType.bodyStrong,
                            color = XMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 220.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items, key = { it.id }) { note ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .xShadow(RoundedCornerShape(22.dp))
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(XSurface)
                                    .clickable { openId = note.id }
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                if (note.source.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = XMuted, modifier = Modifier.size(13.dp))
                                        Text(
                                            note.source,
                                            style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                            color = XMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        note.title.ifBlank { xSettingText("note_untitled") },
                                        style = XType.rowTitle.copy(fontSize = 15.sp, letterSpacing = (-.01).em),
                                        color = XText,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(noteStamp(note.updatedAt), style = XType.meta.copy(fontWeight = FontWeight.Bold), color = XMuted)
                                    Icon(
                                        Icons.Outlined.PushPin,
                                        xSettingText("pin_action"),
                                        tint = XText,
                                        modifier = Modifier
                                            .size(17.dp)
                                            .alpha(if (note.pinned) 1f else .3f)
                                            .clickable { store.togglePin(note.id) },
                                    )
                                }
                                Text(
                                    note.text.ifBlank { xSettingText("note_empty") },
                                    style = XType.bodyStrong,
                                    color = XMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 26.dp)
                        .size(58.dp)
                        .xShadow(CircleShape, 20.dp)
                        .clip(CircleShape)
                        .background(XText)
                        .clickable { openId = store.add().id },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Add, xSettingText("note_new"), tint = XInverse, modifier = Modifier.size(28.dp)) }
            }
        }
    } else {
        XNoteEditor(
            note = open,
            store = store,
            onBack = { openId = null },
        )
    }
}

@Composable
private fun XNoteEditor(note: XNote, store: XNotesStore, onBack: () -> Unit) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var text by remember(note.id) { mutableStateOf(note.text) }

    // Persist as the user types; the canvas editor has no explicit save button.
    // Skip the first pass so merely opening a note doesn't bump its updatedAt.
    var dirty by remember(note.id) { mutableStateOf(false) }
    LaunchedEffect(title, text) {
        if (!dirty) {
            dirty = true
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(400)
        store.update(note.id, title, text)
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .height(38.dp)
                    .xShadow(CircleShape)
                    .clip(CircleShape)
                    .background(XSurface)
                    .clickable {
                        store.update(note.id, title, text)
                        onBack()
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = XText, modifier = Modifier.size(18.dp))
                Text(xSettingText("all_notes").uppercase(), style = XType.pillSmall.copy(letterSpacing = .1.em), color = XText)
            }
            Spacer(Modifier.weight(1f))
            Text(noteStamp(note.updatedAt), style = XType.meta.copy(fontWeight = FontWeight.Bold), color = XMuted)
            Box(
                Modifier.size(38.dp).xShadow(CircleShape).clip(CircleShape)
                    .background(if (note.pinned) XText else XSurface)
                    .clickable { store.togglePin(note.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PushPin,
                    xSettingText("pin_action"),
                    tint = if (note.pinned) XInverse else XText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                Modifier.size(38.dp).xShadow(CircleShape).clip(CircleShape).background(XSurface)
                    .clickable {
                        store.delete(note.id)
                        onBack()
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Delete, xSettingText("delete_action"), tint = XRed, modifier = Modifier.size(19.dp)) }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .xShadow(RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(XSurface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (note.source.isNotBlank()) {
                Row(
                    Modifier.clip(CircleShape).background(XSurfaceRaised).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = XMuted, modifier = Modifier.size(13.dp))
                    Text(note.source, style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold), color = XMuted)
                }
            }
            Box {
                if (title.isEmpty()) {
                    Text(xSettingText("note_title_hint"), style = XType.screenTitle.copy(fontSize = 19.sp), color = XMuted)
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = XType.screenTitle.copy(fontSize = 19.sp, color = XText),
                    cursorBrush = SolidColor(XAccent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(xSettingText("note_body_hint"), style = XType.bodyStrong.copy(fontSize = 14.sp), color = XMuted)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = XType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 21.sp, color = XText),
                    cursorBrush = SolidColor(XAccent),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
