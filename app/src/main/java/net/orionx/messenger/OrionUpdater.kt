package net.orionx.messenger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.AppRelease
import net.orionx.messenger.cloud.CloudSession
import net.orionx.messenger.cloud.SupabaseApi
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

const val ORIONX_ADMIN_NAME = "OrionX Administrator"

// The admin password is NOT stored in the source (a string literal would be visible in
// a decompiled APK). Instead the app expects its SHA-256 hash, provided at build time
// through the ORIONX_ADMIN_PASSWORD_SHA256 property in gradle.properties. Generate one:
//   python -c "import hashlib;print(hashlib.sha256('ВАШ_ПАРОЛЬ'.encode()).hexdigest())"
// then set ORIONX_ADMIN_PASSWORD_SHA256=<hex> in gradle.properties. Without the property
// the password dialog cannot be unlocked on any build produced from this source tree.

private val UPurple = XAccent
private val UViolet = XAccentSoft
private val UBg = Color(0xFF0B0C12)
private val USurface = Color(0xFF191920)
private val UBorder = Color(0x17FFFFFF)
private val UText = Color(0xFFF2F1F5)
private val UMuted = Color(0xFF948EA1)

fun isOrionXAdmin(displayName: String): Boolean = displayName.trim() == ORIONX_ADMIN_NAME

/**
 * Checks the entered admin password against the SHA-256 hash baked into the build
 * (BuildConfig.ORIONX_ADMIN_PASSWORD_SHA256, injected from gradle.properties). The
 * plaintext password never exists in the source or in the compiled constant pool.
 */
internal fun isAdminPassword(candidate: String): Boolean {
    val expected = BuildConfig.ORIONX_ADMIN_PASSWORD_SHA256
    if (expected.isBlank()) return false
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(candidate.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }
    return hex.equals(expected.trim().lowercase(), ignoreCase = false)
}

private fun queryDisplayName(context: android.content.Context, uri: Uri, fallback: String): String {
    var name = fallback
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.let { name = it }
        }
    }
    return name
}

private const val ADMIN_PREFS = "orionx_admin"
private const val KEY_RELEASE_UNLOCKED = "release_unlocked"

/** Whether the admin release panel has already been unlocked with the password on this device. */
internal fun isAdminUnlocked(context: android.content.Context): Boolean =
    context.getSharedPreferences(ADMIN_PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY_RELEASE_UNLOCKED, false)

internal fun setAdminUnlocked(context: android.content.Context, unlocked: Boolean) {
    context.getSharedPreferences(ADMIN_PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY_RELEASE_UNLOCKED, unlocked).apply()
}

/** Password dialog that unlocks admin features. Calls onSuccess only on the correct password. */
@Composable
internal fun XAdminPasswordDialog(
    title: String = "Пароль администратора",
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = USurface,
        titleContentColor = UText,
        textContentColor = UMuted,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it; error = false },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    placeholder = { Text("Введите пароль", color = UMuted) },
                    isError = error,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error) Text("Неверный пароль", color = Color(0xFFFF5C76), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isAdminPassword(pass)) onSuccess() else error = true
            }) { Text("Войти", color = UViolet) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = UMuted) } },
    )
}

/** Settings entry (only rendered for the admin). Opens directly once unlocked. */
@Composable
fun XAdminReleaseEntry(api: SupabaseApi, session: CloudSession, displayName: String) {
    if (!isOrionXAdmin(displayName)) return
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }

    Surface(
        color = USurface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp,
        onClick = { if (isAdminUnlocked(context)) showPanel = true else showPassword = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = Color(0xFF26252D), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.CloudUpload, null, tint = UText, modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Выпуск обновления", style = XType.rowTitle, color = UText)
                Spacer(Modifier.height(4.dp))
                Text("Загрузить APK и опубликовать для всех", style = XType.body, color = UMuted, maxLines = 2)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = UMuted, modifier = Modifier.size(19.dp))
        }
    }

    if (showPassword) {
        XAdminPasswordDialog(
            onDismiss = { showPassword = false },
            onSuccess = { setAdminUnlocked(context, true); showPassword = false; showPanel = true },
        )
    }

    if (showPanel) {
        AdminReleasePanel(api = api, session = session, onDismiss = { showPanel = false })
    }
}

@Composable
private fun AdminReleasePanel(api: SupabaseApi, session: CloudSession, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apkUri by remember { mutableStateOf<Uri?>(null) }
    var apkName by remember { mutableStateOf("") }
    var apkUrlText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageName by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(BuildConfig.VERSION_NAME) }
    var versionCode by remember { mutableStateOf((BuildConfig.VERSION_CODE + 1).toString()) }
    var description by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { apkUri = uri; apkName = queryDisplayName(context, uri, "update.apk") }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { imageUri = uri; imageName = queryDisplayName(context, uri, "image.jpg") }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = UBg, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Выпуск обновления", color = UText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (!busy) onDismiss() }) { Icon(Icons.Outlined.Close, null, tint = UMuted) }
                }
                Text("APK можно загрузить файлом (лимит Supabase) или указать ссылку на APK — так надёжнее для больших файлов.", color = UMuted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Важно: клиенты проверяют обновления сначала в GitHub Releases " +
                        "(galaxymoratim-oss/orionx-releases). Эта публикация видна пользователям, " +
                        "только если GitHub недоступен. Для гарантированной доставки опубликуйте релиз и там.",
                    color = Color(0xFFFFC46B),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(18.dp))

                UPickRow("APK файл", apkName.ifBlank { "Не выбран" }, Icons.Outlined.Android, apkName.isNotBlank()) { apkPicker.launch("application/vnd.android.package-archive") }
                Spacer(Modifier.height(8.dp))
                UField("или ссылка на APK (для больших файлов)", apkUrlText, keyboard = KeyboardType.Uri) { apkUrlText = it.trim() }
                Spacer(Modifier.height(10.dp))
                UPickRow("Фото (необязательно)", imageName.ifBlank { "Не выбрано" }, Icons.Outlined.Image, imageName.isNotBlank()) { imagePicker.launch("image/*") }

                Spacer(Modifier.height(16.dp))
                UField("Версия", version) { version = it }
                Spacer(Modifier.height(10.dp))
                UField("versionCode (число, больше текущего ${BuildConfig.VERSION_CODE})", versionCode, keyboard = KeyboardType.Number) { versionCode = it.filter { ch -> ch.isDigit() } }
                Spacer(Modifier.height(10.dp))
                UField("Что изменилось", description, minLines = 4) { description = it }

                status?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = if (done) Color(0xFF37D39A) else if (busy) UViolet else Color(0xFFFF5C76), fontSize = 13.sp)
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val vc = versionCode.toIntOrNull()
                        when {
                            apkUri == null && apkUrlText.isBlank() -> status = "Выберите APK файл или вставьте ссылку"
                            version.isBlank() -> status = "Укажите версию"
                            vc == null || vc <= 0 -> status = "Некорректный versionCode"
                            else -> {
                                busy = true; done = false; status = if (apkUrlText.isNotBlank()) "Публикация…" else "Загрузка APK…"
                                scope.launch {
                                    val result = runCatching {
                                        val apkUrl = if (apkUrlText.isNotBlank()) {
                                            apkUrlText.trim()
                                        } else {
                                            val apkBytes = withContext(Dispatchers.IO) {
                                                context.contentResolver.openInputStream(apkUri!!)!!.use { it.readBytes() }
                                            }
                                            withContext(Dispatchers.IO) {
                                                api.uploadReleaseFile(session.accessToken, apkName.ifBlank { "update.apk" }, "application/vnd.android.package-archive", apkBytes)
                                            }
                                        }
                                        var imageUrl: String? = null
                                        if (imageUri != null) {
                                            status = "Загрузка фото…"
                                            val imgBytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(imageUri!!)!!.use { it.readBytes() } }
                                            val mime = context.contentResolver.getType(imageUri!!) ?: "image/jpeg"
                                            imageUrl = withContext(Dispatchers.IO) { api.uploadReleaseFile(session.accessToken, imageName.ifBlank { "image.jpg" }, mime, imgBytes) }
                                        }
                                        status = "Публикация…"
                                        withContext(Dispatchers.IO) {
                                            api.publishRelease(session.accessToken, version, vc, description, apkUrl, imageUrl)
                                        }
                                    }
                                    busy = false
                                    result.onSuccess { done = true; status = "Обновление выпущено ✓" }
                                        .onFailure { status = "Ошибка: ${it.message ?: "не удалось выпустить"}" }
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = UPurple, disabledContainerColor = UPurple.copy(alpha = .4f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    else Text("Выпустить", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun UPickRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, onClick: () -> Unit) {
    Surface(color = USurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (filled) UViolet.copy(alpha = .6f) else UBorder), onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (filled) UViolet else UMuted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = UMuted, fontSize = 11.sp)
                Text(value, color = UText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Icon(Icons.Outlined.UploadFile, null, tint = UMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun UField(label: String, value: String, minLines: Int = 1, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = UMuted) },
        singleLine = minLines == 1,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = UViolet, unfocusedBorderColor = UBorder,
            focusedTextColor = UText, unfocusedTextColor = UText,
            cursorColor = UViolet,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** GitHub repo that hosts public releases - the primary source of app updates. */
private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/galaxymoratim-oss/orionx-releases/releases/latest"

// GitHub API is rate-limited per IP (60 req/h unauthenticated). Every client hitting it
// on every app launch wastes the quota and slows startup. Cache the latest release in
// memory and only re-fetch when the cache is older than 30 minutes - unless the caller
// explicitly forces a refresh (the "Check for update" button).
private const val GITHUB_CACHE_TTL_MS = 30L * 60L * 1000L
@Volatile private var githubCache: GithubRelease? = null
@Volatile private var githubCacheAt: Long = 0L
@Volatile private var githubCacheFailedAt: Long = 0L

internal fun fetchLatestGithubRelease(forceRefresh: Boolean = false): GithubRelease? {
    val now = System.currentTimeMillis()
    if (!forceRefresh) {
        if (githubCache != null && now - githubCacheAt < GITHUB_CACHE_TTL_MS) return githubCache
        // Don't hammer a failing endpoint on every launch either.
        if (githubCache == null && now - githubCacheFailedAt < GITHUB_CACHE_TTL_MS) return null
    }
    val fetched = runCatching {
    val c = (URL(GITHUB_RELEASES_URL).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 20000
        setRequestProperty("User-Agent", "OrionX-Updater")
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    // Сначала код ответа: при 403/404 inputStream бросил бы IOException раньше проверки.
    if (c.responseCode != 200) return@runCatching null
    val body = c.inputStream.use { it.bufferedReader().readText() }
    val json = JSONObject(body)
    val version = json.optString("tag_name").trim().trimStart('v', 'V')
    var apkUrl = ""
    val assets = json.optJSONArray("assets") ?: JSONArray()
    for (i in 0 until assets.length()) {
        val a = assets.getJSONObject(i)
        val name = a.optString("name")
        if (name.lowercase().endsWith(".apk")) { apkUrl = a.optString("browser_download_url"); break }
    }
    if (version.isBlank() || apkUrl.isBlank()) return@runCatching null
    GithubRelease(
        version = version,
        description = json.optString("body").takeUnless { it == "null" }.orEmpty(),
        apkUrl = apkUrl,
    )
}
val result = fetched.getOrNull()
if (result != null) {
    githubCache = result
    githubCacheAt = System.currentTimeMillis()
} else {
    githubCacheFailedAt = System.currentTimeMillis()
}
return result
}

internal data class GithubRelease(val version: String, val description: String, val apkUrl: String)

/** Numeric component-wise comparison: "6.9.25" > "6.9.24", missing parts count as 0. */
internal fun isNewerVersion(remote: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
    val r = parts(remote); val c = parts(current)
    for (i in 0 until maxOf(r.size, c.size)) {
        val rv = r.getOrElse(i) { 0 }; val cv = c.getOrElse(i) { 0 }
        if (rv != cv) return rv > cv
    }
    return false
}

/**
 * Latest published release. Опрашиваем оба канала — GitHub Releases и Supabase
 * (публикация из админ-панели приложения) — и берём тот, у которого версия новее.
 * Раньше GitHub всегда был приоритетнее, из-за чего релиз, опубликованный из
 * админ-панели, не доходил до клиентов, пока на GitHub стоял старый релиз.
 */
internal suspend fun loadLatestRelease(api: SupabaseApi, accessToken: String, forceRefresh: Boolean = false): AppRelease? =
    withContext(Dispatchers.IO) {
        val gh = fetchLatestGithubRelease(forceRefresh)?.let {
            AppRelease(
                id = "github-${it.version}",
                version = it.version,
                // Not used on this path; update checks compare version strings instead.
                versionCode = 0,
                description = it.description,
                apkUrl = it.apkUrl,
                imageUrl = null,
                createdAt = "",
            )
        }
        val sb = runCatching { api.latestRelease(accessToken) }.getOrNull()
        when {
            gh == null -> sb
            sb == null -> gh
            isNewerVersion(sb.version, gh.version) -> sb
            else -> gh
        }
    }

/** Checks for a newer release once and shows the update dialog to every user. */
@Composable
fun XUpdateChecker(api: SupabaseApi, session: CloudSession) {
    val context = LocalContext.current
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(session.accessToken) {
        val found = loadLatestRelease(api, session.accessToken)
        if (found != null && isNewerVersion(found.version, BuildConfig.VERSION_NAME)) release = found
    }

    val r = release
    if (r != null && !dismissed) {
        var downloading by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf(0f) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        Dialog(onDismissRequest = { if (!downloading) dismissed = true }) {
            Surface(color = USurface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, UBorder)) {
                Column(Modifier.fillMaxWidth().padding(0.dp)) {
                    if (!r.imageUrl.isNullOrBlank()) {
                        RemoteUpdateImage(r.imageUrl, Modifier.fillMaxWidth().height(180.dp))
                    }
                    Column(Modifier.padding(20.dp)) {
                        Text("Доступно обновление", color = UMuted, fontSize = 13.sp)
                        // Version font is 15% larger than the body text (body 16sp -> ~18.4sp).
                        Text(r.version, color = UText, fontSize = 18.4.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        if (r.description.isNotBlank()) {
                            Text(r.description, color = UText.copy(alpha = .82f), fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                        }
                        error?.let { Text(it, color = Color(0xFFFF5C76), fontSize = 12.sp); Spacer(Modifier.height(10.dp)) }
                        if (downloading) {
                            LinearProgressIndicator(progress = { progress }, color = UViolet, trackColor = UBorder, modifier = Modifier.fillMaxWidth().height(6.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("Скачивание ${(progress * 100).toInt()}%", color = UMuted, fontSize = 12.sp)
                        } else {
                            Button(
                                onClick = {
                                    downloading = true; error = null; progress = 0f
                                    scope.launch {
                                        val res = runCatching {
                                            val file = downloadApk(context, r.apkUrl, r.version) { progress = it }
                                            installApk(context, file)
                                        }
                                        downloading = false
                                        res.onFailure { e ->
                                            error = if (e is InstallPermissionRequired)
                                                "Разрешите установку из этого источника и нажмите «Скачать обновление» снова."
                                            else "Не удалось: ${e.message ?: "ошибка сети"}"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = UPurple),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) { Text("Скачать обновление", color = Color.White, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { dismissed = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Позже", color = UMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteUpdateImage(url: String, modifier: Modifier) {
    val context = LocalContext.current
    var bmp by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url) {
        runCatching {
            withContext(Dispatchers.IO) {
                (URL(url).openConnection() as HttpURLConnection).inputStream.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            }
        }.onSuccess { it?.let { b -> bmp = b.asImageBitmap() } }
    }
    val image = bmp
    if (image != null) {
        androidx.compose.foundation.Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(UBorder))
    }
}

/** Thrown when the OS won't let us install because "install unknown apps" is off for OrionX. */
internal class InstallPermissionRequired : Exception("install_permission_required")

internal suspend fun downloadApk(
    context: android.content.Context,
    url: String,
    version: String,
    onTotal: (Long) -> Unit = {},
    onProgress: (Float) -> Unit,
): File =
    withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safeVer = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "orionx-$safeVer.apk")

        // Follow redirects manually. HttpURLConnection refuses to auto-follow a redirect
        // that switches protocol (http<->https), and GitHub Releases / most CDN links rely
        // on a chain of such hops. Without this the download silently fetches the redirect
        // page instead of the APK.
        var currentUrl = url.trim()
        var connection: HttpURLConnection? = null
        var redirects = 0
        while (true) {
            val c = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 60000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "OrionX-Updater")
                setRequestProperty("Accept", "*/*")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location.isNullOrBlank() || redirects >= 5) throw java.io.IOException("Слишком много перенаправлений по ссылке")
                currentUrl = URL(URL(currentUrl), location).toString()
                redirects++
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                c.disconnect()
                throw java.io.IOException("Сервер вернул код $code при скачивании")
            }
            connection = c
            break
        }
        val conn = connection!!
        val total = conn.contentLengthLong
        if (total > 0) withContext(Dispatchers.Main) { onTotal(total) }
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var readTotal = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    readTotal += n
                    if (total > 0) onProgress((readTotal.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()

        // Validate the payload is really an APK (ZIP magic "PK\x03\x04"). Otherwise the
        // installer fails with an opaque "There was a problem parsing the package" — which
        // is exactly what happens when the link points at a release page or a Drive/HTML
        // interstitial instead of a direct .apk download.
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        val looksLikeApk = read == 4 &&
            header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
        if (!looksLikeApk) {
            file.delete()
            throw java.io.IOException("Ссылка не ведёт на APK-файл. Нужна прямая ссылка на скачивание .apk (например, из GitHub Releases).")
        }
        file
    }

internal fun installApk(context: android.content.Context, file: File) {
    // Android 8+ requires per-app permission to install packages. If it isn't granted the
    // install intent just bounces, which reads as a failed install. Send the user straight
    // to the grant screen and report a clear, actionable error.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        throw InstallPermissionRequired()
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
