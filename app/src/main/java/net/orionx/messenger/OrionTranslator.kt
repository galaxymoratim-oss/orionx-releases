package net.orionx.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * MyMemory — a translation API that works without a key or an account, which keeps the mini-app
 * usable out of the box. It is rate limited per address, so the screen debounces and only sends
 * once typing settles.
 */
private object OrionTranslateApi {
    fun translate(text: String, from: String, to: String): String {
        val query = URLEncoder.encode(text, "UTF-8")
        val url = "https://api.mymemory.translated.net/get?q=$query&langpair=$from|$to"
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "translate http $code" }
            JSONObject(body).getJSONObject("responseData").getString("translatedText")
        } finally {
            connection.disconnect()
        }
    }
}

private data class XTranslateLang(val code: String, val label: String)

private val X_TRANSLATE_LANGS = listOf(
    XTranslateLang("ru", "русский"),
    XTranslateLang("en", "english"),
)

/** «переводчик»: language bar with swap, input card, result card, phrase chips and saved items. */
@Composable
internal fun XTranslatorScreen(notes: XNotesStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var fromIndex by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val from = X_TRANSLATE_LANGS[fromIndex]
    val to = X_TRANSLATE_LANGS[1 - fromIndex]
    val copiedLabel = xSettingText("copied")

    // Debounce: MyMemory counts every call against a daily quota.
    LaunchedEffect(text, fromIndex) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            output = ""
            failed = false
            return@LaunchedEffect
        }
        delay(600)
        busy = true
        failed = false
        runCatching { withContext(Dispatchers.IO) { OrionTranslateApi.translate(trimmed, from.code, to.code) } }
            .onSuccess { output = it }
            .onFailure {
                // Отмена корутины (пользователь продолжает печатать) — не ошибка перевода.
                if (it is kotlinx.coroutines.CancellationException) throw it
                output = ""
                failed = true
            }
        busy = false
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2000)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            XScreenHeader(title = xSettingText("app_translator"), onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .xShadow(RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(XSurface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        from.label,
                        style = XType.rowTitle.copy(fontSize = 12.sp),
                        color = XText,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(XSurfaceRaised)
                            .clickable {
                                fromIndex = 1 - fromIndex
                                // Swapping keeps the conversation going: the result becomes the input.
                                if (output.isNotBlank()) {
                                    text = output
                                    output = ""
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.SwapHoriz, xSettingText("swap_langs"), tint = XText, modifier = Modifier.size(19.dp)) }
                    Text(
                        to.label,
                        style = XType.rowTitle.copy(fontSize = 12.sp),
                        color = XText,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .xShadow(RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(XSurface)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 78.dp)) {
                        if (text.isEmpty()) {
                            Text(
                                xSettingText("translate_hint"),
                                style = XType.rowTitle.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = (-.01).em),
                                color = XMuted,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it.take(500) },
                            textStyle = XType.rowTitle.copy(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                letterSpacing = (-.01).em,
                                color = XText,
                            ),
                            cursorBrush = SolidColor(XAccent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${text.length} / 500",
                            style = XType.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                            color = XMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            Modifier.height(30.dp).clip(CircleShape).background(XSurfaceRaised)
                                .clickable { text = ""; output = "" }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, null, tint = XMuted, modifier = Modifier.size(15.dp))
                            Text(xSettingText("clear_action"), style = XType.rowTitle.copy(fontSize = 10.sp), color = XMuted)
                        }
                    }
                }

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(XSurfaceRaised).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    XCardLabel(to.label)
                    Text(
                        when {
                            busy -> xSettingText("translating")
                            failed -> xSettingText("translate_failed")
                            output.isNotBlank() -> output
                            else -> xSettingText("translate_result_hint")
                        },
                        style = XType.rowTitle.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = (-.01).em),
                        color = if (output.isNotBlank() && !busy && !failed) XText else XMuted,
                        modifier = Modifier.heightIn(min = 24.dp),
                    )
                    if (output.isNotBlank() && !busy) {
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(15.dp)).background(XText)
                                    .clickable {
                                        copyToClipboard(context, output)
                                        toast = copiedLabel
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            ) {
                                Icon(Icons.Outlined.ContentCopy, null, tint = XInverse, modifier = Modifier.size(17.dp))
                                Text(xSettingText("copy_action"), style = XType.rowTitle.copy(fontSize = 11.sp), color = XInverse)
                            }
                            val savedLabel = xSettingText("saved_to_notes")
                            val sourceLabel = xSettingText("app_translator")
                            Box(
                                Modifier.size(52.dp, 42.dp).xShadow(RoundedCornerShape(15.dp)).clip(RoundedCornerShape(15.dp))
                                    .background(XSurface)
                                    .clickable {
                                        notes.add(title = text.trim().take(40), text = output, source = sourceLabel)
                                        toast = savedLabel
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.BookmarkAdd, xSettingText("save_action"), tint = XText, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        xSettingText("phrase_hello"),
                        xSettingText("phrase_thanks"),
                        xSettingText("phrase_where"),
                    ).forEach { phrase ->
                        Box(
                            Modifier
                                .height(32.dp)
                                .xShadow(CircleShape)
                                .clip(CircleShape)
                                .background(XSurface)
                                .clickable { text = phrase }
                                .padding(horizontal = 13.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(phrase, style = XType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = XMuted, maxLines = 1)
                        }
                    }
                }
            }
        }

        toast?.let { message ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
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

internal fun copyToClipboard(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("OrionX", value))
}
