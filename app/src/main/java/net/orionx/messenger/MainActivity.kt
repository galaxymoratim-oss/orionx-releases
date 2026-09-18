package net.orionx.messenger

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import android.os.Build

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import java.nio.ByteBuffer
import java.net.URL
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.*
import net.orionx.messenger.push.PushTokenSync
import net.orionx.messenger.media.MediaProcessor

class MainActivity : ComponentActivity() {
    internal var isAppForeground by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The redesign paints one gradient from the top of the status bar to the bottom of the
        // navigation bar, so the window has to draw behind both. Seed the palette before the
        // first frame; the shell re-applies it whenever the theme setting changes.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // Seed the palette before the first frame so the launch does not flash the wrong
        // theme. «система» follows the OS, the same rule the composition uses afterwards.
        val savedTheme = getSharedPreferences("orionx_redesign_settings", MODE_PRIVATE)
            .getString("appearance_theme", "dark") ?: "dark"
        val systemDark = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        applyXPalette(
            when (savedTheme) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
        )
        setContent { OrionXNeonTheme { OrionXApp() } }
    }

    override fun onStart() {
        super.onStart()
        isAppForeground = true
        // Canvas «скриншоты в чатах» — the window flag has to be re-applied on every
        // start, since the setting outlives the window.
        xApplyScreenshotBlock(this, xLoadScreenshotBlock(this))
    }

    override fun onStop() {
        isAppForeground = false
        super.onStop()
    }
}

private enum class Tab { Chats, Search, Contacts, Updates, Admin, Settings }
private enum class MediaTab { Emoji, Gif, Sticker }

@Composable
private fun OrionXApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val api = remember { SupabaseApi() }
    val store = remember { CloudSessionStore(context) }
    // Points the image cache at `cache/xi` before the first avatar is requested.
    remember(context) { XImageCache.attach(context); XVoiceWave.attach(context); true }
    // Debug-only: pref «auth_preview» показывает auth-флоу без выхода из аккаунта.
    val debugAuthPreview = BuildConfig.DEBUG &&
        context.getSharedPreferences("orionx_debug", android.content.Context.MODE_PRIVATE)
            .getBoolean("auth_preview", false)
    var session by remember { mutableStateOf(if (debugAuthPreview) null else store.load()) }
    // Seeded from disk so a relaunch paints the real screen instead of a spinner.
    var profile by remember { mutableStateOf(session?.let { XColdStart.loadProfile(context, it.userId) }) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val languagePrefs = remember { context.getSharedPreferences("orionx_language", android.content.Context.MODE_PRIVATE) }
    var language by remember {
        mutableStateOf(
            OrionLanguage.fromCode(languagePrefs.getString("language", null))
                ?: OrionLanguage.fromCode(java.util.Locale.getDefault().language)
                ?: OrionLanguage.ENGLISH
        )
    }
    val changeLanguage: (OrionLanguage) -> Unit = { next ->
        language = next
        languagePrefs.edit().putString("language", next.code).apply()
    }

    LaunchedEffect(session?.accessToken) {
        var active = session ?: return@LaunchedEffect

        // Сессия, которую не спасти: сервер явно отказал в refresh-токене.
        // Одноразовый 401 у запроса профиля сюда не попадает — он лечится
        // повторным refresh и не должен разлогинивать.
        fun isDeadSession(failure: Throwable): Boolean {
            val m = (failure.message ?: "").lowercase()
            return "invalid_grant" in m || "invalid refresh token" in m || "invalid claim" in m
        }
        fun killSession() {
            store.clear()
            XScreenCache.clear()
            XImageCache.clear()
            XColdStart.clear(context)
            profile = null
            session = null
        }

        suspend fun refreshSession(): CloudSession? {
            return runCatching { withContext(Dispatchers.IO) { api.refresh(active.refreshToken) } }
                .onSuccess { renewed ->
                    if (store.isPersisted()) store.save(renewed)
                    session = renewed
                    error = null
                }
                .onFailure { failure -> error = localizedNetworkError(failure) }
                .getOrNull()
        }

        if (active.accessTokenNeedsRefresh()) {
            if (refreshSession() != null) return@LaunchedEffect
        }

        launch(Dispatchers.IO) { runCatching { PushTokenSync.syncSession(context, api, active) } }
        val loadedProfile = runCatching { withContext(Dispatchers.IO) { api.profile(active.accessToken, active.userId) } }
        loadedProfile.onSuccess {
            profile = it
            error = null
            launch(Dispatchers.IO) { XColdStart.saveProfile(context, it) }
        }.onFailure { failure ->
            if (failure.isExpiredJwt()) {
                if (refreshSession() != null) return@LaunchedEffect
            }
            // Раньше протухшая сессия оставляла пользователя на спиннере; теперь
            // отклонённый сервером аккаунт честно выбрасывает на экран входа.
            if (isDeadSession(failure)) {
                killSession()
                return@LaunchedEffect
            }
            error = localizedNetworkError(failure)
        }

        while (true) {
            delay(30_000)
            active = session ?: break
            if (active.accessTokenNeedsRefresh()) {
                if (refreshSession() != null) return@LaunchedEffect
            }
        }
    }

        OrionI18n.current = language
        CompositionLocalProvider(
            LocalOrionLanguage provides language,
            LocalOrionLanguageSetter provides changeLanguage
        ) {
            NeonBackground {
                // Единый выход: сначала отвязываем устройство (пуши старого аккаунта
                // прекращаются), потом чистим локальные данные.
                val logout: () -> Unit = {
                    val s = session
                    if (s != null) {
                        scope.launch(Dispatchers.IO) {
                            runCatching { api.revokeDeviceByInstallation(s.accessToken, s.userId, net.orionx.messenger.cloud.DeviceIdentity.id(context)) }
                        }
                    }
                    store.clear()
                    XScreenCache.clear()
                    XImageCache.clear()
                    XColdStart.clear(context)
                    profile = null
                    session = null
                }
                when {
                    session == null -> UnifiedAuthScreen(api) { s, rememberMe -> if (rememberMe) store.save(s) else store.clear(); session = s }
                    profile == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
                                TextButton(onClick = logout) { Text(tr("logout")) }
                            }
                        }
                    }
                    else -> OrionRedesignedApp(api, session!!, profile!!, onLogout = logout)
                }
            }
        }
}

@Composable
private fun LegacyAuthScreen(api: SupabaseApi, onAuth: (CloudSession, Boolean) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var acceptedTerms by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var otpEmail by remember { mutableStateOf<String?>(null) }
    var otpCode by remember { mutableStateOf("") }
    var resendIn by remember { mutableIntStateOf(0) }
    var cardEpoch by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resendIn) {
        if (resendIn > 0) {
            delay(1_000)
            resendIn--
        }
    }

    fun switchMode(nextRegister: Boolean) {
        register = nextRegister
        message = null
        cardEpoch++
    }

    fun validateRegistration(): String? = when {
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> tr("invalid_email")
        nick.trim().removePrefix("@").length < 3 -> tr("nick_min")
        name.trim().length < 2 -> tr("enter_name")
        password.length < 8 -> tr("password_min")
        password != confirmPassword -> tr("password_mismatch")
        !acceptedTerms -> tr("accept_terms")
        else -> null
    }

    AuthBackdrop {
        if (otpEmail != null) {
            OtpVerificationCard(
                email = otpEmail!!,
                code = otpCode,
                onCodeChange = { otpCode = it.filter(Char::isDigit).take(6) },
                busy = busy,
                message = message,
                resendIn = resendIn,
                onBack = { otpEmail = null; otpCode = ""; message = null; cardEpoch++ },
                onVerify = {
                    if (otpCode.length != 6) {
                        message = tr("enter_otp")
                    } else {
                        busy = true; message = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { api.verifySignupOtp(otpEmail!!, otpCode) }
                            }.onSuccess { onAuth(it, rememberMe) }
                                .onFailure { message = it.userFriendlyAuthMessage() }
                            busy = false
                        }
                    }
                },
                onResend = {
                    if (resendIn == 0) {
                        busy = true; message = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { api.resendSignupOtp(otpEmail!!) }
                            }.onSuccess {
                                resendIn = 60
                                message = tr("new_code_sent")
                            }.onFailure { message = it.userFriendlyAuthMessage() }
                            busy = false
                        }
                    }
                }
            )
            return@AuthBackdrop
        }

        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (register) 8.dp else 38.dp))

            AnimatedContent(
                targetState = register to cardEpoch,
                transitionSpec = {
                    (fadeIn(tween(360)) + scaleIn(initialScale = .97f, animationSpec = tween(420, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { if (targetState.first) it / 8 else -it / 8 })
                        .togetherWith(
                            fadeOut(tween(220)) + scaleOut(targetScale = .985f, animationSpec = tween(260)) +
                                slideOutHorizontally(tween(300)) { if (targetState.first) -it / 10 else it / 10 }
                        )
                },
                label = "authMode"
            ) { state ->
                val isRegister = state.first
                AuthGlassCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isRegister) {
                            AuthRoundIconButton(Icons.Default.ArrowBack, tr("back")) { switchMode(false) }
                        } else {
                            AuthSecurityBadge()
                        }
                        Text(
                            if (isRegister) "CREATE ID" else "ORION ID",
                            color = Color(0xFF8E94B2),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    OrionAuthLogo()
                    Spacer(Modifier.height(13.dp))
                    Text(
                        "OrionX 5",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-1.1).sp
                    )
                    Text(
                        tr("tagline"),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF9297B0),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.05.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))

                    Text(
                        if (isRegister) tr("create_account_title") else tr("login_title"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (isRegister) tr("register_subtitle") else tr("welcome_back"),
                        color = Color(0xFF9EA3BC),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(25.dp))

                    AuthField(
                        value = email,
                        onValueChange = { email = it },
                        label = tr("email"),
                        icon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email,
                        valid = isRegister && android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
                    )
                    Spacer(Modifier.height(13.dp))

                    AnimatedVisibility(
                        visible = isRegister,
                        enter = fadeIn(tween(300)) + scaleIn(initialScale = .98f),
                        exit = fadeOut(tween(180)) + scaleOut(targetScale = .98f)
                    ) {
                        Column {
                            AuthField(
                                value = nick,
                                onValueChange = { nick = it.replace(" ", "") },
                                label = tr("nickname"),
                                icon = Icons.Default.AlternateEmail,
                                valid = nick.trim().removePrefix("@").length >= 3
                            )
                            Spacer(Modifier.height(13.dp))
                            AuthField(
                                value = name,
                                onValueChange = { name = it },
                                label = tr("name"),
                                icon = Icons.Default.PersonOutline,
                                valid = name.trim().length >= 2
                            )
                            Spacer(Modifier.height(13.dp))
                        }
                    }

                    AuthField(
                        value = password,
                        onValueChange = { password = it },
                        label = tr("password"),
                        icon = Icons.Default.Lock,
                        isPassword = true,
                        passwordVisible = showPassword,
                        onTogglePassword = { showPassword = !showPassword }
                    )

                    AnimatedVisibility(
                        visible = isRegister,
                        enter = fadeIn(tween(300)) + scaleIn(initialScale = .98f),
                        exit = fadeOut(tween(180)) + scaleOut(targetScale = .98f)
                    ) {
                        Column {
                            Spacer(Modifier.height(13.dp))
                            AuthField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = tr("confirm_password"),
                                icon = Icons.Default.LockReset,
                                isPassword = true,
                                passwordVisible = showConfirmPassword,
                                onTogglePassword = { showConfirmPassword = !showConfirmPassword },
                                valid = confirmPassword.isNotEmpty() && confirmPassword == password
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    if (isRegister) {
                        AuthChoiceRow(
                            checked = acceptedTerms,
                            onCheckedChange = { acceptedTerms = it },
                            text = tr("agree_terms")
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AuthChoiceRow(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                text = tr("remember_me"),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                tr("forgot_password"),
                                color = Color(0xFFB57CFF),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable {
                                    if (email.isBlank()) message = tr("enter_email_first")
                                    else {
                                        busy = true; message = null
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { api.requestPasswordReset(email) } }
                                                .onSuccess { message = tr("reset_sent") }
                                                .onFailure { message = it.userFriendlyAuthMessage() }
                                            busy = false
                                        }
                                    }
                                }
                            )
                        }
                    }

                    AnimatedVisibility(visible = message != null) {
                        message?.let {
                            Spacer(Modifier.height(13.dp))
                            AuthMessageBubble(
                                text = it,
                                positive = it == tr("new_code_sent") || it == tr("code_resent") || it == tr("reset_sent")
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    AuthGradientButton(
                        text = if (busy) tr("please_wait") else if (isRegister) tr("register") else tr("sign_in"),
                        enabled = !busy,
                        onClick = {
                            message = null
                            if (isRegister) {
                                val validation = validateRegistration()
                                if (validation != null) {
                                    message = validation
                                    return@AuthGradientButton
                                }
                            } else if (email.isBlank() || password.isBlank()) {
                                message = tr("enter_email_password")
                                return@AuthGradientButton
                            }

                            busy = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        if (isRegister) api.signUp(email, password, nick, name)
                                        else api.signIn(email, password)
                                    }
                                }.onSuccess { session ->
                                    if (isRegister && session == null) {
                                        otpEmail = email.trim().lowercase()
                                        otpCode = ""
                                        resendIn = 60
                                        message = null
                                    } else if (session != null) {
                                        onAuth(session, rememberMe)
                                    }
                                }.onFailure { error ->
                                    val friendly = error.userFriendlyAuthMessage()
                                    message = friendly
                                    if (!isRegister && error.message.orEmpty().contains("Email not confirmed", true) && email.isNotBlank()) {
                                        runCatching { withContext(Dispatchers.IO) { api.resendSignupOtp(email) } }
                                            .onSuccess {
                                                otpEmail = email.trim().lowercase()
                                                otpCode = ""
                                                resendIn = 60
                                                message = tr("code_resent")
                                            }
                                    }
                                }
                                busy = false
                            }
                        }
                    )

                    Spacer(Modifier.height(19.dp))
                    if (!isRegister) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f), color = Color(0x334B5170))
                            Text(tr("or"), modifier = Modifier.padding(horizontal = 14.dp), color = Color(0xFF8187A2))
                            HorizontalDivider(Modifier.weight(1f), color = Color(0x334B5170))
                        }
                        Spacer(Modifier.height(16.dp))
                        AuthSecondaryButton(Icons.Default.PersonAdd, tr("create_account")) { switchMode(true) }
                        Spacer(Modifier.height(17.dp))
                        Text(
                            tr("continue_terms"),
                            color = Color(0xFF737A98),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("already_account_prefix"), color = Color(0xFF9297B0))
                            Text(
                                tr("sign_in"),
                                color = Color(0xFFB57CFF),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.clickable { switchMode(false) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AuthGlassCard(content: @Composable ColumnScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "cardGlow")
    val glow by transition.animateFloat(
        initialValue = .36f,
        targetValue = .72f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "glow"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .shadow(34.dp, RoundedCornerShape(40.dp), ambientColor = Color(0x553E18A8), spotColor = Color(0x665F2BFF))
            .border(1.25.dp, Color(0xFF805CE7).copy(alpha = glow), RoundedCornerShape(40.dp)),
        shape = RoundedCornerShape(40.dp),
        color = Color(0xF013172B),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 25.dp, vertical = 27.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun AuthSecurityBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0x66231A48),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x555E4AA0))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Security, null, tint = Color(0xFFBFA8FF), modifier = Modifier.size(15.dp))
            Text("SECURE", color = Color(0xFFBFA8FF), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun AuthRoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(46.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color(0xAA171B31),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x665A6080))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AuthChoiceRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF7D42FF),
                uncheckedColor = Color(0xFF757A95),
                checkmarkColor = Color.White
            )
        )
        Text(text, color = Color(0xFFA9AEC5), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AuthMessageBubble(text: String, positive: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = if (positive) Color(0x223DD98A) else Color(0x22FF5579),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (positive) Color(0x5542D98C) else Color(0x55FF6685))
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (positive) Icons.Default.CheckCircle else Icons.Default.Info,
                null,
                tint = if (positive) Color(0xFF52E39B) else Color(0xFFFF7C98),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(text, color = if (positive) Color(0xFFB7F7D5) else Color(0xFFFFB1C0), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AuthSecondaryButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(60.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = Color(0x4413172B),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF7042C8))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun Throwable.userFriendlyAuthMessage(): String {
    val raw = message.orEmpty()
    val lang = OrionI18n.current
    return when {
        raw.contains("Email not confirmed", true) -> tr("email_not_confirmed")
        raw.contains("Invalid login credentials", true) -> tr("invalid_login")
        raw.contains("User already registered", true) -> tr("already_registered")
        raw.contains("rate limit", true) || raw.contains("security purposes", true) -> tr("rate_limit")
        raw.contains("Password should be", true) -> tr("password_too_short")
        raw.isBlank() -> tr("request_failed")
        raw.contains("Unauthorized", true) || raw.contains("401", true) -> when (lang) {
            OrionLanguage.RUSSIAN -> "Необходимо войти в аккаунт снова"
            OrionLanguage.UKRAINIAN -> "Необхідно знову увійти в акаунт"
            OrionLanguage.BELARUSIAN -> "Неабходна зноў увайсці ва ўліковы запіс"
            OrionLanguage.KAZAKH -> "Аккаунтқа қайта кіру қажет"
            else -> "Please sign in again"
        }
        raw.contains("Forbidden", true) || raw.contains("403", true) -> when (lang) {
            OrionLanguage.RUSSIAN -> "Недостаточно прав для этого действия"
            OrionLanguage.UKRAINIAN -> "Недостатньо прав для цієї дії"
            OrionLanguage.BELARUSIAN -> "Недастаткова правоў для гэтага дзеяння"
            OrionLanguage.KAZAKH -> "Бұл әрекетке құқық жеткіліксіз"
            else -> "You don't have permission for this action"
        }
        raw.contains("Title is too short", true) -> when (lang) {
            OrionLanguage.RUSSIAN -> "Название слишком короткое"
            OrionLanguage.UKRAINIAN -> "Назва надто коротка"
            OrionLanguage.BELARUSIAN -> "Назва занадта кароткая"
            OrionLanguage.KAZAKH -> "Атауы тым қысқа"
            else -> "The title is too short"
        }
        else -> when (lang) {
            OrionLanguage.RUSSIAN -> "Произошла ошибка. Повторите попытку."
            OrionLanguage.UKRAINIAN -> "Сталася помилка. Спробуйте ще раз."
            OrionLanguage.BELARUSIAN -> "Адбылася памылка. Паўтарыце спробу."
            OrionLanguage.KAZAKH -> "Қате орын алды. Қайталап көріңіз."
            else -> "Something went wrong. Try again."
        }
    }
}

@Composable
private fun OrionAuthLogo() {
    val transition = rememberInfiniteTransition(label = "logoMotion")
    val pulse by transition.animateFloat(
        initialValue = .94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "logoPulse"
    )
    val rotation by transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "logoRotation"
    )
    Box(
        modifier = Modifier
            .size(92.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .rotate(rotation)
            .shadow(34.dp, CircleShape, ambientColor = Color(0xFF7A3CFF), spotColor = Color(0xFF7A3CFF))
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color.White, Color(0xFFB090FF), Color(0xFF672DFF), Color(0xFF22133F))))
            .border(1.25.dp, Color(0xFFD8C8FF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xAAFFFFFF), Color(0x225D2EFF), Color.Transparent)))
        )
        Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(43.dp))
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    valid: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val focusProgress by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "fieldFocus")
    val borderColor = if (focused) Color(0xFF8B5BFF) else Color(0xFF343B5E)
    val background = if (focused) Color(0xD01B2038) else Color(0xA914192D)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .shadow((8 + 12 * focusProgress).dp, RoundedCornerShape(22.dp), ambientColor = Color(0x554A22B4), spotColor = Color(0x665E2DFF))
            .border((1 + .35f * focusProgress).dp, borderColor, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 17.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(15.dp),
                color = if (focused) Color(0x663F2184) else Color(0x44362167),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (focused) Color(0x887A50DB) else Color(0x334D426F))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = if (focused) Color(0xFFD7C2FF) else Color(0xFFB9A5E9), modifier = Modifier.size(23.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = if (focused) Color(0xFFC9B7FF) else Color(0xFF8E94AF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .25.sp
                )
                Spacer(Modifier.height(2.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                    cursorBrush = Brush.verticalGradient(listOf(Color(0xFFD9C6FF), Color(0xFF7C43FF))),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text("•••", color = Color(0xFF535A78), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            }
                            inner()
                        }
                    }
                )
            }
            Spacer(Modifier.width(6.dp))
            when {
                isPassword && onTogglePassword != null -> IconButton(onClick = onTogglePassword, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (focused) Color(0xFFC9B7FF) else Color(0xFF9EA3BC),
                        modifier = Modifier.size(22.dp)
                    )
                }
                valid -> Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF45D994), modifier = Modifier.size(21.dp))
                }
            }
        }
    }
}

@Composable
private fun AuthGradientButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val gradient = if (enabled) {
        Brush.horizontalGradient(listOf(Color(0xFF5B21FF), Color(0xFF7A2EFF), Color(0xFF6925F5)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF302746), Color(0xFF342949)))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x443E16C8), spotColor = Color(0x553E16C8))
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .border(1.dp, Color(0x667C5BFF), RoundedCornerShape(18.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (text == tr("please_wait")) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        if (text != tr("please_wait")) {
            Icon(Icons.Default.ArrowForward, null, tint = Color.White, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(23.dp))
        }
    }
}

@Composable
private fun OtpVerificationCard(
    email: String,
    code: String,
    onCodeChange: (String) -> Unit,
    busy: Boolean,
    message: String?,
    resendIn: Int,
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit
) {
    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050817))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .fillMaxHeight()
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(40.dp),
                    ambientColor = Color(0x552C18FF),
                    spotColor = Color(0x664E23FF)
                ),
            shape = RoundedCornerShape(40.dp),
            color = Color(0xFF080B1C),
            border = androidx.compose.foundation.BorderStroke(1.35.dp, Color(0xFF6653C7)),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Подтверждение Email",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.3f).sp
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    "Введите код, отправленный на",
                    color = Color(0xFF9B9DB2),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF10142A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B2F56))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = Color(0xFFC7B9FF),
                            modifier = Modifier.size(25.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            email,
                            color = Color(0xFFF2F1F8),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Изменить",
                            color = Color(0xFF9A58FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable(onClick = onBack)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                OtpProgressSteps()

                Spacer(Modifier.height(20.dp))

                OtpEnvelopeIllustration()

                Spacer(Modifier.height(16.dp))

                OtpCodeField(code = code, onCodeChange = onCodeChange)

                Spacer(Modifier.height(22.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        progress = {
                            // Таймер стартует с 60 с — шкала должна начинаться полной,
                            // а не наполовину заполненной.
                            if (resendIn > 0) (resendIn.coerceAtMost(60) / 60f) else 0f
                        },
                        modifier = Modifier.size(30.dp),
                        color = Color(0xFF6AD7FF),
                        trackColor = Color(0xFF242943),
                        strokeWidth = 3.4.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (resendIn > 0)
                            "Код действителен ${resendIn / 60}:${(resendIn % 60).toString().padStart(2, '0')}"
                        else
                            "Можно отправить новый код",
                        color = Color(0xFF9A9DB3),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Отправить повторно",
                        color = if (resendIn == 0 && !busy) Color(0xFF9A58FF) else Color(0xFF66697F),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = if (resendIn == 0 && !busy) Modifier.clickable(onClick = onResend) else Modifier
                    )
                }

                AnimatedVisibility(visible = message != null) {
                    message?.let {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            AuthMessageBubble(
                                it,
                                it == tr("new_code_sent") || it == tr("code_resent")
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                AuthGradientButton(
                    if (busy) tr("please_wait") else "Продолжить",
                    !busy && code.length == 6,
                    onVerify
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    "←  Назад",
                    color = Color(0xFFA7A9BC),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF10142A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B2F56))
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFB7A7FF),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Код никому не передаётся",
                                color = Color(0xFFD8D8E4),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Защищаем ваш аккаунт современным шифрованием",
                                color = Color(0xFF8D90A5),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun OtpProgressSteps() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        listOf("Контакт", "Код", "Профиль").forEachIndexed { index, title ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(if (index <= 1) Color(0xFF7D36FF) else Color(0xFF262844))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(
                                if (index == 1) 20.dp else 0.dp,
                                CircleShape,
                                ambientColor = Color(0xFF8B4DFF),
                                spotColor = Color(0xFF8B4DFF)
                            )
                            .clip(CircleShape)
                            .background(
                                if (index == 1) {
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFFCD95FF),
                                            Color(0xFF8844FF),
                                            Color(0xFF5F22E6)
                                        )
                                    )
                                } else {
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFF171A31),
                                            Color(0xFF090C19)
                                        )
                                    )
                                }
                            )
                            .border(
                                1.5.dp,
                                if (index == 1) Color(0xFFD6B2FF) else Color(0xFF4C3B98),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (index + 1).toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }

                    if (index < 2) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(if (index == 0) Color(0xFF7D36FF) else Color(0xFF262844))
                        )
                    }
                }

                Spacer(Modifier.height(7.dp))

                Text(
                    title,
                    color = if (index == 1) Color.White else Color(0xFF8D90A5),
                    fontSize = 14.sp,
                    fontWeight = if (index == 1) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun OtpEnvelopeIllustration() {
    Image(
        painter = painterResource(id = R.drawable.otp_envelope_reference),
        contentDescription = "OTP",
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun OtpCodeField(code: String, onCodeChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        value = code,
        onValueChange = { onCodeChange(it.filter(Char::isDigit).take(6)) },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
        cursorBrush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        decorationBox = { inner ->
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(6) { index ->
                        val value = code.getOrNull(index)?.toString().orEmpty()
                        val active = focused && index == code.length.coerceAtMost(5)
                        val filled = value.isNotEmpty()

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF0C1020),
                            border = androidx.compose.foundation.BorderStroke(
                                if (active || filled) 1.8.dp else 1.4.dp,
                                if (active || filled) Color(0xFF8D4BFF) else Color(0xFF5638A9)
                            ),
                            shadowElevation = if (active || filled) 10.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    value,
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxSize().alpha(.01f)) {
                    inner()
                }
            }
        }
    )
}

private fun maskEmail(email: String): String {
    val parts = email.split("@", limit = 2)
    if (parts.size != 2) return email
    val name = parts[0]
    val visible = name.take(2)
    return "$visible${"•".repeat((name.length - visible.length).coerceAtLeast(4))}@${parts[1]}"
}

@Composable
private fun AuthBackdrop(content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "authBackground")
    val driftX by transition.animateFloat(
        initialValue = -24f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(7600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "driftX"
    )
    val driftY by transition.animateFloat(
        initialValue = -16f,
        targetValue = 34f,
        animationSpec = infiniteRepeatable(tween(9200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "driftY"
    )
    val starAlpha by transition.animateFloat(
        initialValue = .28f,
        targetValue = .95f,
        animationSpec = infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "starAlpha"
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF070817), Color(0xFF090B1D), Color(0xFF050611))
            )
        )
    ) {
        Box(
            Modifier
                .size(330.dp)
                .offset(x = 250.dp, y = (-120).dp)
                .graphicsLayer { translationX = driftX; translationY = driftY }
                .background(Brush.radialGradient(listOf(Color(0xB45B21FF), Color(0x44351B8E), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier
                .size(290.dp)
                .offset(x = (-130).dp, y = 560.dp)
                .graphicsLayer { translationX = -driftX * .65f; translationY = driftY * .72f }
                .background(Brush.radialGradient(listOf(Color(0x66415CFF), Color(0x22343EE0), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier
                .size(250.dp)
                .align(Alignment.Center)
                .graphicsLayer { translationY = -driftY * .45f }
                .background(Brush.radialGradient(listOf(Color(0x332D1B77), Color.Transparent)), CircleShape)
        )

        val stars = remember {
            listOf(
                Triple(44.dp, 122.dp, 3.dp), Triple(112.dp, 78.dp, 2.dp), Triple(268.dp, 160.dp, 3.dp),
                Triple(326.dp, 252.dp, 2.dp), Triple(58.dp, 440.dp, 2.dp), Triple(310.dp, 520.dp, 3.dp),
                Triple(128.dp, 690.dp, 2.dp), Triple(280.dp, 760.dp, 3.dp), Triple(188.dp, 300.dp, 2.dp),
                Triple(342.dp, 660.dp, 2.dp)
            )
        }
        stars.forEachIndexed { index, (x, y, size) ->
            Box(
                Modifier
                    .size(size)
                    .offset(x = x, y = y)
                    .alpha(if (index % 2 == 0) starAlpha else 1f - starAlpha * .45f)
                    .background(if (index % 3 == 0) Color(0xFFD7CAFF) else Color(0xFF8D6FFF), CircleShape)
            )
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(api: SupabaseApi, session: CloudSession, me: CloudProfile, onLogout: () -> Unit) {
    DisposableEffect(session.userId) {
        // Скоуп живёт ровно столько, сколько экран — без этого onDispose оставляет
        // осиротевшую корутину без отмены.
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        scope.launch { runCatching { api.updatePresence(session.accessToken, true) } }
        onDispose { scope.launch { runCatching { api.updatePresence(session.accessToken, false) } } }
    }
    var tab by remember { mutableStateOf(Tab.Chats) }
    var opened by remember { mutableStateOf<CloudConversation?>(null) }
    val tabs = buildList { add(Tab.Chats); add(Tab.Search); add(Tab.Contacts); add(Tab.Updates); if (me.role == "admin" && me.email.equals(SupabaseConfig.ADMIN_EMAIL, true)) add(Tab.Admin); add(Tab.Settings) }

    if (opened != null) {
        // Системный «назад» закрывает чат, а не выходит из приложения.
        BackHandler { opened = null }
        ChatScreen(api, session, opened!!) { opened = null }
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { OrionTopHeader(me.nickname) },
        bottomBar = { OrionBottomNavigation(tabs, tab) { tab = it } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when(tab) {
                Tab.Chats -> ChatsScreen(api, session, me, onOpen = { opened = it })
                Tab.Search -> SearchScreen(api, session, me, onOpen = { opened = it })
                Tab.Contacts -> SearchScreen(api, session, me, onOpen = { opened = it })
                Tab.Updates -> UpdatesScreen(api, session, me)
                Tab.Admin -> if (me.role == "admin" && me.email.equals(SupabaseConfig.ADMIN_EMAIL, true)) AdminScreen(api, session)
                Tab.Settings -> SettingsScreen(api, session, me, onLogout)
            }
        }
    }
}

@Composable
private fun OrionTopHeader(nickname: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(31.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.radialGradient(listOf(Color.White, OrionCyan, OrionPurple)))
                .border(1.dp, OrionGlow.copy(alpha=.75f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(8.dp))
        Text("OrionX", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xB80A0C18), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x553F346F))) {
            Text("@$nickname", color = OrionText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 12.sp)
        }
    }
}

@Composable
private fun OrionBottomNavigation(tabs: List<Tab>, selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
        color = Color(0xF50A0B18), shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x553A315F)), tonalElevation = 0.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEach { item ->
                val icon = when(item) { Tab.Chats -> Icons.Default.Chat; Tab.Search -> Icons.Default.Search; Tab.Contacts -> Icons.Default.Contacts; Tab.Updates -> Icons.Default.SystemUpdate; Tab.Admin -> Icons.Default.AdminPanelSettings; Tab.Settings -> Icons.Default.Settings }
                val label = when(item){Tab.Chats->tr("chats");Tab.Search->tr("search");Tab.Contacts->tr("contacts");Tab.Updates->tr("updates");Tab.Admin->tr("admin");Tab.Settings->tr("settings")}
                val active = item == selected
                Column(Modifier.weight(1f).clickable { onSelect(item) }.padding(vertical = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.height(30.dp).widthIn(min = 42.dp).clip(RoundedCornerShape(13.dp))
                            .background(if(active) Color(0xFF4923B7) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, tint = if(active) Color.White else Color(0xFF918AA8), modifier = Modifier.size(19.dp)) }
                    Spacer(Modifier.height(2.dp))
                    Text(label, color = if(active) Color.White else Color(0xFF918AA8), fontSize = 9.sp, fontWeight = if(active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ChatsScreen(api: SupabaseApi, session: CloudSession, me: CloudProfile, onOpen: (CloudConversation) -> Unit) {
    var chats by remember { mutableStateOf<List<CloudConversation>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun reload() { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { api.myConversations(session.accessToken, session.userId) } }.onSuccess { chats = it }; busy = false } }
    LaunchedEffect(Unit) { reload() }
    Box(Modifier.fillMaxSize()) {
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center), color = OrionPurple)
        else if (chats.isEmpty()) {
            OrionHeroEmptyState(
                title = tr("no_chats"), subtitle = tr("create_first_chat"), icon = Icons.Default.ChatBubbleOutline,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 26.dp)
            )
        } else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { items(chats, key = { it.id }) { c -> ConversationCard(c, onClick = { onOpen(c) }) } }
        FloatingActionButton(containerColor = OrionPurple, contentColor = Color.White, onClick = { showCreate = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).shadow(26.dp, CircleShape, ambientColor = OrionPurple, spotColor = OrionPurple)) { Icon(Icons.Default.Add, null) }
    }
    if (showCreate) CreateCommunityDialog(api, session, onDismiss = { showCreate = false }, onCreated = { showCreate = false; reload() })
}

@Composable
private fun SearchScreen(api: SupabaseApi, session: CloudSession, me: CloudProfile, onOpen: (CloudConversation) -> Unit) {
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<CloudProfile>>(emptyList()) }
    var channels by remember { mutableStateOf<List<CloudConversation>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var blockedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    fun doSearch() { scope.launch { runCatching { withContext(Dispatchers.IO) { api.searchProfiles(session.accessToken, query) to api.searchChannels(session.accessToken, query) } }.onSuccess { users = it.first.filterNot { u -> u.id == me.id }; channels = it.second }.onFailure { error = it.message } } }
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { api.blockedUserIds(session.accessToken, me.id) } }.onSuccess { blockedIds = it }.onFailure { error = it.message } }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(query, { query = it }, placeholder = { Text(tr("search_hint"), color = OrionMuted) }, shape = RoundedCornerShape(22.dp), colors = neonTextFieldColors(), modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = { IconButton(onClick = ::doSearch) { Icon(Icons.Default.Search, null, tint = OrionText) } })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (query.isBlank() && users.isEmpty() && channels.isEmpty()) {
            OrionHeroEmptyState(title = tr("search"), subtitle = tr("search_hint"), icon = Icons.Default.Search, modifier = Modifier.fillMaxWidth().weight(1f))
        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (users.isNotEmpty()) item { Text(tr("people"), color = OrionText, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp)) }
            items(users, key = { it.id }) { u ->
                NeonGlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        OrionAvatar(u.displayName)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) { Text(u.displayName, color = OrionText, fontWeight = FontWeight.Bold); Text("@${u.nickname}", color = OrionMuted, fontSize = 13.sp) }
                        TextButton(onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { api.createDirect(session.accessToken, u.id) } }.onSuccess(onOpen).onFailure { error = it.message } } }, enabled = u.id !in blockedIds) { Text(tr("chat"), color = OrionGlow, fontWeight = FontWeight.Bold) }
                        IconButton(onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { if (u.id in blockedIds) api.unblockUser(session.accessToken, me.id, u.id) else api.blockUser(session.accessToken, me.id, u.id) } }.onSuccess { blockedIds = if (u.id in blockedIds) blockedIds - u.id else blockedIds + u.id }.onFailure { error = it.message } } }) { Icon(if (u.id in blockedIds) Icons.Default.PersonAdd else Icons.Default.Block, null, tint = OrionMuted) }
                    }
                }
            }
            if (channels.isNotEmpty()) item { Text(tr("channels"), color = OrionText, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 7.dp)) }
            items(channels, key = { it.id }) { c -> ConversationCard(c) { scope.launch { runCatching { withContext(Dispatchers.IO) { api.joinChannel(session.accessToken, c.id) } }.onSuccess { onOpen(c) }.onFailure { error = it.message } } } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(api: SupabaseApi, session: CloudSession, conversation: CloudConversation, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var messages by remember { mutableStateOf<List<CloudMessage>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CloudMessage?>(null) }
    var replying by remember { mutableStateOf<CloudMessage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var typingNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var showConversationMenu by remember { mutableStateOf(false) }
    var mediaPanelOpen by remember { mutableStateOf(false) }
    var mediaTab by remember { mutableStateOf(MediaTab.Emoji) }
    var mediaAssets by remember { mutableStateOf<List<OriaMediaAsset>>(emptyList()) }
    var mediaLoading by remember { mutableStateOf(false) }
    var showOriaMediaDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadMedia(kind: String) {
        mediaLoading = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.oriaMediaAssets(session.accessToken, kind) } }
                .onSuccess { mediaAssets = it }
                .onFailure { error = it.message }
            mediaLoading = false
        }
    }

    fun reload() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.messages(session.accessToken, conversation.id) } }
                .onSuccess { messages = it; runCatching { withContext(Dispatchers.IO) { api.markConversationRead(session.accessToken, conversation.id) } } }
                .onFailure { error = it.message }
        }
    }
    fun action(block: () -> Unit) {
        scope.launch { runCatching { withContext(Dispatchers.IO) { block() } }.onSuccess { reload() }.onFailure { error = it.message } }
    }
    fun send(attachment: UploadedFile? = null) {
        if (text.isBlank() && attachment == null) return
        val sent = text
        val replyId = replying?.id
        text = ""
        replying = null
        action { api.sendMessage(session.accessToken, conversation.id, session.userId, sent, replyId, attachment); api.clearDraft(session.accessToken, conversation.id, session.userId) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException(tr("file_read_failed"))
                    require(bytes.size <= 50 * 1024 * 1024) { tr("file_too_large") }
                    api.uploadMessageFile(session.accessToken, session.userId, name, mime, bytes)
                }
            }.onSuccess { send(it) }.onFailure { error = it.message }
            uploading = false
        }
    }

    LaunchedEffect(conversation.id) {
        runCatching { withContext(Dispatchers.IO) { api.loadDraft(session.accessToken, conversation.id, session.userId) } }.onSuccess { (draft, _) -> if (text.isBlank()) text = draft }
        while (true) {
            reload()
            runCatching { withContext(Dispatchers.IO) { api.typingUsers(session.accessToken, conversation.id, session.userId) } }.onSuccess { typingNames = it }
            delay(1500)
        }
    }
    // Индикатор набора и черновик — с дебаунсом, а не по каждому символу:
    // запросы уходят через 800 мс после паузы в вводе.
    LaunchedEffect(replying?.id) {
        snapshotFlow { text }
            .debounce(800)
            .collectLatest { value ->
                if (value.isNotBlank()) {
                    runCatching { withContext(Dispatchers.IO) { api.setTyping(session.accessToken, conversation.id, true) } }
                    runCatching { withContext(Dispatchers.IO) { api.saveDraft(session.accessToken, conversation.id, session.userId, value, replying?.id) } }
                }
            }
        // При уходе с экрана снимаем индикатор набора.
        runCatching { withContext(Dispatchers.IO) { api.setTyping(session.accessToken, conversation.id, false) } }
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xF20A0C1B)),
            title = { Row(verticalAlignment = Alignment.CenterVertically) { OrionAvatar(conversation.title); Spacer(Modifier.width(10.dp)); Column { Text(conversation.title, color = OrionText, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(if(conversation.type=="direct") tr("online") else conversation.type, color = OrionCyan, style = MaterialTheme.typography.labelSmall) } } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = OrionText) } },
            actions = {
                IconButton(onClick = { error = tr("audio_call_unavailable") }) { Icon(Icons.Default.Call, null, tint = Color(0xFFC9C1FF)) }
                IconButton(onClick = { error = tr("video_call_unavailable") }) { Icon(Icons.Default.Videocam, null, tint = Color(0xFFC9C1FF)) }
                Box {
                    IconButton(onClick = { showConversationMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = OrionText) }
                    DropdownMenu(showConversationMenu, { showConversationMenu = false }) {
                        DropdownMenuItem(text = { Text(tr("leave_chat")) }, onClick = { showConversationMenu = false; scope.launch { runCatching { withContext(Dispatchers.IO) { api.leaveConversation(session.accessToken, conversation.id) } }.onSuccess { onBack() }.onFailure { error = it.message } } }, leadingIcon = { Icon(Icons.Default.ExitToApp, null) })
                    }
                }
            }
        ) },
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                if (typingNames.isNotEmpty()) Text(tr("typing", "names" to typingNames.joinToString()), color = OrionCyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp))
                replying?.let { reply ->
                    Surface(tonalElevation = 2.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(tr("reply_to", "name" to reply.senderName), fontWeight = FontWeight.Bold); Text(reply.body.take(120), maxLines = 1) }
                            IconButton(onClick = { replying = null }) { Icon(Icons.Default.Close, null) }
                        }
                    }
                }
                if (mediaPanelOpen) {
                    MediaPickerPanel(
                        selected = mediaTab,
                        assets = mediaAssets,
                        loading = mediaLoading,
                        onTab = { tab ->
                            mediaTab = tab
                            if (tab == MediaTab.Gif) loadMedia("gif")
                            if (tab == MediaTab.Sticker) loadMedia("sticker")
                        },
                        onEmoji = { emoji -> text += emoji },
                        onMedia = { asset ->
                            send(UploadedFile(asset.publicUrl, asset.title.ifBlank { if (asset.kind == "gif") "Oria.gif" else "Oria-sticker.png" }, asset.mimeType))
                            mediaPanelOpen = false
                        },
                        onCreateWithOria = { showOriaMediaDialog = true },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { picker.launch("*/*") }, enabled = !uploading) { Icon(Icons.Default.AttachFile, null, tint = OrionMuted) }
                    OutlinedTextField(
                        text, { text = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), colors = neonTextFieldColors(), singleLine = true,
                        placeholder = { Text(if(uploading) tr("uploading_file") else tr("write_message"), color = OrionMuted) },
                        trailingIcon = { IconButton(onClick = { mediaPanelOpen = !mediaPanelOpen }) { Icon(Icons.Default.EmojiEmotions, null, tint = if(mediaPanelOpen) OrionCyan else OrionMuted) } }
                    )
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF4A1ED9), OrionPurple))).clickable(enabled = !uploading && text.isNotBlank()) { send() }.shadow(18.dp, CircleShape, ambientColor = OrionPurple, spotColor = OrionPurple), contentAlignment = Alignment.Center) { Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
                }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            items(messages, key = { it.id }) { m ->
                val mine = m.senderId == session.userId
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                Surface(shape = RoundedCornerShape(22.dp, 22.dp, if(mine) 6.dp else 22.dp, if(mine) 22.dp else 6.dp), color = if(mine) OrionPurple.copy(alpha=.86f) else OrionPanel, border = androidx.compose.foundation.BorderStroke(1.dp, if(mine) OrionGlow else OrionBorder), modifier = Modifier.widthIn(max=330.dp).shadow(if(mine) 16.dp else 4.dp, RoundedCornerShape(22.dp), ambientColor = if(mine) OrionPurple else Color.Transparent, spotColor = if(mine) OrionPurple else Color.Transparent)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(m.senderName, fontWeight = FontWeight.Bold)
                        m.replyPreview?.let { Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 2.dp) { Text(it, Modifier.fillMaxWidth().padding(8.dp), style = MaterialTheme.typography.bodySmall) } }
                        Text(if (m.deletedForAll) tr("message_deleted") else m.body)
                        if (!m.deletedForAll && m.attachmentUrl != null) {
                            val isImageMedia = m.attachmentType?.startsWith("image/") == true
                            if (isImageMedia) {
                                RemoteMediaPreview(
                                    url = m.attachmentUrl,
                                    animated = m.attachmentType == "image/gif",
                                    modifier = Modifier.widthIn(max = 260.dp).heightIn(min = 110.dp, max = 230.dp).clip(RoundedCornerShape(16.dp)).clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.attachmentUrl))) },
                                )
                            } else {
                                AssistChip(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.attachmentUrl))) }, label = { Text("📎 ${m.attachmentName ?: tr("file")}") })
                            }
                        }
                        if (m.editedAt != null && !m.deletedForAll) Text(tr("edited"), style = MaterialTheme.typography.labelSmall)
                        if (!m.deletedForAll) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("👍","❤️","😂","😮").forEach { emoji -> AssistChip(onClick = { action { api.toggleReaction(session.accessToken, m.id, emoji) } }, label = { Text("$emoji ${m.reactions[emoji] ?: 0}") }) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(onClick = { replying = m }) { Text(tr("reply")) }
                                TextButton(onClick = { val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("OrionX message", m.body)) }) { Text(tr("copy")) }
                                TextButton(onClick = { action { api.hideMessageForMe(session.accessToken, m.id) } }) { Text(tr("delete_for_me")) }
                            }
                            if (m.senderId == session.userId) {
                                Row { TextButton(onClick = { editing = m }) { Text(tr("edit")) }; TextButton(onClick = { action { api.deleteMessageForAll(session.accessToken, m.id) } }) { Text(tr("delete_for_all")) } }
                            }
                        }
                    }
                }
                }
            }
        }
    }
    if (showOriaMediaDialog) {
        OriaMediaCreateDialog(
            api = api,
            session = session,
            initialKind = if (mediaTab == MediaTab.Sticker) "sticker" else "gif",
            onDismiss = { showOriaMediaDialog = false },
            onCreated = { asset ->
                mediaTab = if (asset.kind == "sticker") MediaTab.Sticker else MediaTab.Gif
                mediaAssets = listOf(asset) + mediaAssets.filterNot { it.id == asset.id }
                mediaPanelOpen = true
                showOriaMediaDialog = false
            },
        )
    }

    editing?.let { message ->
        var editedText by remember(message.id) { mutableStateOf(message.body) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(tr("editing")) }, text = { OutlinedTextField(editedText, { editedText = it }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { action { api.editMessage(session.accessToken, message.id, editedText) }; editing = null }, enabled = editedText.isNotBlank()) { Text(tr("save")) } }, dismissButton = { TextButton(onClick = { editing = null }) { Text(tr("cancel")) } })
    }
}



@Composable
private fun MediaPickerPanel(
    selected: MediaTab,
    assets: List<OriaMediaAsset>,
    loading: Boolean,
    onTab: (MediaTab) -> Unit,
    onEmoji: (String) -> Unit,
    onMedia: (OriaMediaAsset) -> Unit,
    onCreateWithOria: () -> Unit,
) {
    NeonGlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(selected == MediaTab.Emoji, { onTab(MediaTab.Emoji) }, { Text(tr("emoji")) }, leadingIcon = { Icon(Icons.Default.EmojiEmotions, null, modifier = Modifier.size(18.dp)) })
                FilterChip(selected == MediaTab.Gif, { onTab(MediaTab.Gif) }, { Text("GIF") }, leadingIcon = { Icon(Icons.Default.Gif, null, modifier = Modifier.size(20.dp)) })
                FilterChip(selected == MediaTab.Sticker, { onTab(MediaTab.Sticker) }, { Text(tr("stickers")) }, leadingIcon = { Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp)) })
            }
            when (selected) {
                MediaTab.Emoji -> {
                    val emoji = listOf("😀","😃","😄","😁","😂","🥹","😊","😍","😘","😎","🤔","😭","😡","👍","👎","❤️","🔥","🎉","✨","🙏","💜","💙","✅","🚀","👀","🤝","👏","💯")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        emoji.chunked(7).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { e -> Text(e, fontSize = 28.sp, modifier = Modifier.clip(CircleShape).clickable { onEmoji(e) }.padding(5.dp)) }
                            }
                        }
                    }
                }
                MediaTab.Gif, MediaTab.Sticker -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(if (selected == MediaTab.Gif) tr("my_gifs") else tr("my_stickers"), fontWeight = FontWeight.Bold)
                            Text(tr("oria_media_hint"), color = OrionMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton(onClick = onCreateWithOria) { Icon(Icons.Default.AutoFixHigh, null); Spacer(Modifier.width(6.dp)); Text(tr("add_or_improve")) }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!loading && assets.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(118.dp), contentAlignment = Alignment.Center) {
                            Text(tr("no_media_upload"), color = OrionMuted)
                        }
                    } else {
                        LazyColumn(Modifier.heightIn(max = 270.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(assets, key = { it.id }) { asset ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { onMedia(asset) },
                                    color = Color(0x661B1930),
                                    shape = RoundedCornerShape(18.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, OrionBorder),
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RemoteMediaPreview(asset.publicUrl, asset.kind == "gif", Modifier.size(74.dp).clip(RoundedCornerShape(14.dp)))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(asset.title.ifBlank { if (asset.kind == "gif") "GIF" else tr("sticker") }, fontWeight = FontWeight.Bold)
                                            Text(asset.prompt, maxLines = 2, overflow = TextOverflow.Ellipsis, color = OrionMuted, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Icon(Icons.Default.Send, null, tint = OrionCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteMediaPreview(url: String, animated: Boolean, modifier: Modifier = Modifier) {
    var bytes by remember(url) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(url) {
        bytes = runCatching {
            withContext(Dispatchers.IO) {
                val connection = URL(url).openConnection().apply { connectTimeout = 10_000; readTimeout = 15_000 }
                connection.getInputStream().use { it.readBytes() }
            }
        }.getOrNull()
    }
    Surface(modifier = modifier, color = Color(0x33111122), shape = RoundedCornerShape(14.dp)) {
        if (bytes == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { view ->
                    // Декод выполняется один раз на загрузку: пере-декодировать
                    // GIF на каждой рекомпозиции недопустимо дорого.
                    if (view.tag == bytes) return@AndroidView
                    val data = bytes
                    if (data != null) {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= 28) {
                                val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data)))
                                view.setImageDrawable(drawable)
                                if (animated && drawable is AnimatedImageDrawable) drawable.start()
                            } else {
                                view.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.size))
                            }
                            view.tag = data
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun OriaMediaCreateDialog(
    api: SupabaseApi,
    session: CloudSession,
    initialKind: String,
    onDismiss: () -> Unit,
    onCreated: (OriaMediaAsset) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(initialKind) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var removeBackground by remember { mutableStateOf(true) }
    var addOutline by remember { mutableStateOf(false) }
    var gifSeconds by remember { mutableStateOf(3f) }

    fun saveBytes(bytes: ByteArray, fileName: String, mime: String, assetKind: String, note: String) {
        busy = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    require(bytes.size <= 15 * 1024 * 1024) { tr("media_too_large") }
                    val uploaded = api.uploadMessageFile(session.accessToken, session.userId, fileName, mime, bytes)
                    api.addOriaMediaAsset(
                        token = session.accessToken,
                        ownerId = session.userId,
                        kind = assetKind,
                        title = fileName.substringBeforeLast('.').replace('_', ' '),
                        note = note,
                        file = uploaded,
                    )
                }
            }.onSuccess(onCreated).onFailure { error = it.message }
            busy = false
        }
    }

    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val mime = context.contentResolver.getType(uri) ?: "image/gif"
                    require(mime == "image/gif" || uri.toString().lowercase().endsWith(".gif")) { tr("choose_gif") }
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException(tr("file_read_failed"))
                    require(bytes.size <= 15 * 1024 * 1024) { tr("media_too_large") }
                    val uploaded = api.uploadMessageFile(session.accessToken, session.userId, "user_${System.currentTimeMillis()}.gif", "image/gif", bytes)
                    api.addOriaMediaAsset(session.accessToken, session.userId, "gif", tr("my_gif"), tr("ready_media_added"), uploaded)
                }
            }.onSuccess(onCreated).onFailure { error = it.message }
            busy = false
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; error = null
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.Default) {
                    MediaProcessor.videoToGif(context, uri, durationMs = (gifSeconds * 1000).toLong(), fps = 8)
                }
                require(bytes.size <= 15 * 1024 * 1024) { tr("media_too_large") }
                withContext(Dispatchers.IO) {
                    val uploaded = api.uploadMessageFile(session.accessToken, session.userId, "video_${System.currentTimeMillis()}.gif", "image/gif", bytes)
                    api.addOriaMediaAsset(session.accessToken, session.userId, "gif", tr("video_gif"), tr("oria_video_processed"), uploaded)
                }
            }.onSuccess(onCreated).onFailure { error = it.message }
            busy = false
        }
    }

    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; error = null
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.Default) {
                    MediaProcessor.prepareSticker(
                        context,
                        uri,
                        MediaProcessor.StickerOptions(removeBackground = removeBackground, addOutline = addOutline),
                    )
                }
                require(bytes.size <= 15 * 1024 * 1024) { tr("media_too_large") }
                withContext(Dispatchers.IO) {
                    val uploaded = api.uploadMessageFile(session.accessToken, session.userId, "sticker_${System.currentTimeMillis()}.png", "image/png", bytes)
                    val operations = buildList {
                        add(tr("sticker_prepared"))
                        if (removeBackground) add(tr("background_removed"))
                        if (addOutline) add(tr("outline_added"))
                    }.joinToString(" · ")
                    api.addOriaMediaAsset(session.accessToken, session.userId, "sticker", tr("my_sticker"), operations, uploaded)
                }
            }.onSuccess(onCreated).onFailure { error = it.message }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Default.AutoFixHigh, null, tint = OrionCyan) },
        title = { Text(tr("oria_media_tools")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("oria_media_nongenerative"), color = OrionMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == "gif", { kind = "gif" }, { Text("GIF") }, leadingIcon = { Icon(Icons.Default.Gif, null) })
                    FilterChip(kind == "sticker", { kind = "sticker" }, { Text(tr("sticker")) }, leadingIcon = { Icon(Icons.Default.Star, null) })
                }
                if (kind == "gif") {
                    Text(tr("gif_import_title"), fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { gifPicker.launch("image/gif") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text(tr("add_ready_gif"))
                    }
                    HorizontalDivider()
                    Text(tr("video_to_gif"), fontWeight = FontWeight.Bold)
                    Text(tr("video_gif_hint"), color = OrionMuted, style = MaterialTheme.typography.bodySmall)
                    Text("${gifSeconds.toInt()} ${tr("seconds")}")
                    Slider(value = gifSeconds, onValueChange = { gifSeconds = it }, valueRange = 2f..5f, steps = 2)
                    Button(onClick = { videoPicker.launch("video/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Movie, null); Spacer(Modifier.width(8.dp)); Text(tr("choose_video"))
                    }
                } else {
                    Text(tr("sticker_import_title"), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Switch(checked = removeBackground, onCheckedChange = { removeBackground = it })
                        Spacer(Modifier.width(8.dp))
                        Column { Text(tr("remove_background")); Text(tr("remove_background_hint"), color = OrionMuted, style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Switch(checked = addOutline, onCheckedChange = { addOutline = it })
                        Spacer(Modifier.width(8.dp))
                        Text(tr("white_outline"))
                    }
                    Button(onClick = { stickerPicker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text(tr("choose_sticker_image"))
                    }
                    Text(tr("sticker_local_processing_note"), color = OrionMuted, style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(tr("close")) } },
    )
}

@Composable
private fun LegacyCreateCommunityDialog(api: SupabaseApi, session: CloudSession, onDismiss: () -> Unit, onCreated: (CloudConversation) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("group") }
    var people by remember { mutableStateOf<List<CloudProfile>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { api.allProfiles(session.accessToken) } }.onSuccess { people = it.filterNot { p -> p.id == session.userId || p.blocked } }.onFailure { error = it.message } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == "group") tr("create_group") else tr("create_channel")) },
        text = {
            Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { FilterChip(type=="group", {type="group"}, {Text(tr("group"))}); Spacer(Modifier.width(8.dp)); FilterChip(type=="channel", {type="channel"}, {Text(tr("channel"))}) }
                OutlinedTextField(title,{title=it},label={Text(tr("title"))},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(description,{description=it},label={Text(tr("description"))},modifier=Modifier.fillMaxWidth())
                if (type == "group") {
                    Text(tr("members"), fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(people, key = { it.id }) { person ->
                            Row(Modifier.fillMaxWidth().clickable { selected = if (person.id in selected) selected - person.id else selected + person.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(person.id in selected, { checked -> selected = if (checked) selected + person.id else selected - person.id })
                                Column { Text(person.displayName); Text("@${person.nickname}", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { busy=true; scope.launch { runCatching { withContext(Dispatchers.IO) { api.createCommunity(session.accessToken,title,description,type,selected.toList()) } }.onSuccess(onCreated).onFailure { error=it.message }; busy=false } }, enabled = !busy && title.isNotBlank()) { Text(if (busy) tr("creating") else tr("create")) } },
        dismissButton = { TextButton(onClick=onDismiss){Text(tr("cancel"))} }
    )
}


@Composable
private fun AnnouncementsScreen(api: SupabaseApi, session: CloudSession) {
    var items by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { api.announcements(session.accessToken) } }.onSuccess { items = it }.onFailure { error = it.message } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(tr("orionx_news"), color = OrionText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (items.isEmpty()) item {
            NeonGlassCard(modifier = Modifier.fillMaxWidth().height(300.dp), shape = RoundedCornerShape(24.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(118.dp).clip(RoundedCornerShape(26.dp)).border(3.dp, Color(0xFF6B32FF), RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Newspaper, null, tint = Color(0xFF9E7BFF), modifier = Modifier.size(66.dp))
                        }
                        Text(tr("no_announcements"), color = OrionText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Мы сообщим вам о важных новостях\nи обновлениях здесь.", color = OrionMuted, textAlign = TextAlign.Center, fontSize = 13.sp)
                    }
                }
            }
        }
        items(items, key = { it.id }) { a ->
            NeonGlassCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Newspaper, null, tint = OrionGlow); Spacer(Modifier.width(8.dp)); Text(a.title, color = OrionText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Text(a.body, color = OrionMuted)
                    Text(a.createdAt, color = OrionMuted.copy(alpha=.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun UpdatesScreen(api: SupabaseApi, session: CloudSession, me: CloudProfile) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var update by remember { mutableStateOf<AppUpdate?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { api.latestUpdate(session.accessToken) } }.onSuccess { update=it }.onFailure { error=it.message } }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("system_updates"), color = OrionText, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if(update==null) NeonGlassCard(modifier = Modifier.fillMaxWidth().height(330.dp), shape = RoundedCornerShape(24.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF8D6EFF), modifier = Modifier.size(110.dp))
                    Text(tr("no_updates"), color = OrionText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Здесь будут появляться новые версии\nи улучшения OrionX.", color = OrionMuted, textAlign = TextAlign.Center, fontSize = 13.sp)
                }
            }
        } else NeonGlassCard(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF8D6EFF), modifier = Modifier.size(54.dp))
                Text(update!!.title, color = OrionText, style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text(update!!.changelog, color = OrionMuted)
                if(update!!.mandatory) AssistChip({}, {Text(tr("mandatory_update"))})
                update!!.apkUrl?.let { link -> Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }, colors = ButtonDefaults.buttonColors(containerColor = OrionPurple), shape = RoundedCornerShape(16.dp)) { Text(tr("download_update")) } }
            }
        }
    }
}

@Composable
private fun AdminScreen(api: SupabaseApi, session: CloudSession) {
    var users by remember { mutableStateOf<List<CloudProfile>>(emptyList()) }
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var version by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }; var changes by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }; var mandatory by remember { mutableStateOf(false) }
    var announcementTitle by remember { mutableStateOf("") }; var announcementBody by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }; val scope=rememberCoroutineScope()
    fun reload(){scope.launch{runCatching{withContext(Dispatchers.IO){api.allProfiles(session.accessToken) to api.adminStats(session.accessToken)}}.onSuccess{users=it.first;stats=it.second}.onFailure{status=it.message}}}
    LaunchedEffect(Unit){reload()}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text(tr("admin_panel"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text(tr("admin_only")) }
        stats?.let { s -> item { NeonGlassCard { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(tr("users_count", "count" to s.users.toString())); Text(tr("chats_count", "count" to s.conversations.toString())); Text(tr("messages_count", "count" to s.messages.toString())); Text(tr("blocks_count", "count" to s.blockedUsers.toString())) } } } }
        item { Text(tr("global_announcement"), fontWeight = FontWeight.Bold); OutlinedTextField(announcementTitle,{announcementTitle=it},label={Text(tr("heading"))},modifier=Modifier.fillMaxWidth()); OutlinedTextField(announcementBody,{announcementBody=it},label={Text(tr("text"))},modifier=Modifier.fillMaxWidth(),minLines=3); Button(onClick={scope.launch{runCatching{withContext(Dispatchers.IO){api.publishAnnouncement(session.accessToken,announcementTitle,announcementBody,session.userId)}}.onSuccess{status=tr("announcement_published");announcementTitle="";announcementBody=""}.onFailure{status=it.message}}}, enabled=announcementTitle.isNotBlank()&&announcementBody.isNotBlank()){Text(tr("publish_announcement"))}; HorizontalDivider() }
        item { Text(tr("cloud_update"), fontWeight = FontWeight.Bold); OutlinedTextField(version,{version=it},label={Text(tr("version_example"))},modifier=Modifier.fillMaxWidth()); OutlinedTextField(code,{code=it},label={Text("Version code")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(changes,{changes=it},label={Text(tr("all_changes"))},modifier=Modifier.fillMaxWidth(),minLines=4); OutlinedTextField(url,{url=it},label={Text(tr("apk_link"))},modifier=Modifier.fillMaxWidth()); Row(verticalAlignment=Alignment.CenterVertically){Switch(mandatory,{mandatory=it}); Text(tr("mandatory"))}; Button(onClick={scope.launch{runCatching{withContext(Dispatchers.IO){api.publishUpdate(session.accessToken,version,code.toInt(),changes,url.ifBlank{null},mandatory)}}.onSuccess{status=tr("published", "title" to it.title)}.onFailure{status=it.message}}}, enabled=version.isNotBlank()&&code.toIntOrNull()!=null&&changes.isNotBlank()){Text(tr("publish_update"))}; status?.let{Text(it)}; HorizontalDivider(); Text(tr("users"),fontWeight=FontWeight.Bold) }
        items(users,key={it.id}){u->ListItem(headlineContent={Text(u.displayName)},supportingContent={Text("${u.email} · @${u.nickname} · ${u.role}")},trailingContent={if(!u.email.equals(SupabaseConfig.ADMIN_EMAIL,true)) Switch(u.blocked,{b->scope.launch{runCatching{withContext(Dispatchers.IO){api.setUserBlocked(session.accessToken,u.id,b)}}.onSuccess{reload()}.onFailure{status=it.message}}})});HorizontalDivider()}
    }
}

@Composable
private fun SettingsScreen(api: SupabaseApi, session: CloudSession, me: CloudProfile, onLogout: () -> Unit) {
    var nickname by remember(me.id) { mutableStateOf(me.nickname) }
    var displayName by remember(me.id) { mutableStateOf(me.displayName) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Text(tr("profile"), color = OrionText, style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) }
        item {
            NeonGlassCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("language"), color = OrionMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    LanguageSelector()
                    OutlinedTextField(displayName,{displayName=it},label={Text(tr("name"))},modifier=Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = neonTextFieldColors())
                    OutlinedTextField(nickname,{nickname=it.removePrefix("@")},label={Text(tr("nick"))},prefix={Text("@")},modifier=Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = neonTextFieldColors())
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Email, null, tint = OrionMuted); Spacer(Modifier.width(8.dp)); Text(me.email, color = OrionText, fontSize = 14.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.VerifiedUser, null, tint = OrionMuted); Spacer(Modifier.width(8.dp)); Text(tr("role", "role" to me.role), color = OrionMuted, fontSize = 14.sp) }
                    status?.let { Text(it, color = if (it.startsWith(tr("error"))) MaterialTheme.colorScheme.error else OrionCyan) }
                    Button(onClick={ busy=true; scope.launch { runCatching { withContext(Dispatchers.IO) { api.updateProfile(session.accessToken,nickname,displayName) } }.onSuccess { status=tr("profile_saved") }.onFailure { status=tr("error_details", "details" to (it.message ?: "")) }; busy=false } }, enabled=!busy && nickname.length>=3 && displayName.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = OrionPurple)) { Text(if(busy) tr("saving") else tr("save_profile"), fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick=onLogout, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8A3047)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF667D))){ Text(tr("logout"), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}




internal fun localizedNetworkError(error: Throwable): String {
    val raw = error.message.orEmpty()
    val lang = OrionI18n.current
    return when {
        error.isExpiredJwt() -> when (lang) {
            OrionLanguage.RUSSIAN -> "Сессия истекла. Обновляем вход…"
            OrionLanguage.UKRAINIAN -> "Сеанс завершився. Оновлюємо вхід…"
            OrionLanguage.BELARUSIAN -> "Сеанс скончыўся. Абнаўляем уваход…"
            OrionLanguage.KAZAKH -> "Сеанс аяқталды. Кіру жаңартылуда…"
            else -> "Session expired. Refreshing sign-in…"
        }
        raw.contains("Unable to resolve host", true) || raw.contains("Failed to connect", true) || raw.contains("timeout", true) -> when (lang) {
            OrionLanguage.RUSSIAN -> "Нет подключения к интернету"
            OrionLanguage.UKRAINIAN -> "Немає підключення до інтернету"
            OrionLanguage.BELARUSIAN -> "Няма падключэння да інтэрнэту"
            OrionLanguage.KAZAKH -> "Интернетке қосылым жоқ"
            else -> "No internet connection"
        }
        raw.contains("connection closed", true) -> when (lang) {
            OrionLanguage.RUSSIAN -> "Соединение прервано. Потяните вниз, чтобы повторить."
            OrionLanguage.UKRAINIAN -> "З'єднання перервано. Потягніть вниз, щоб повторити."
            OrionLanguage.BELARUSIAN -> "Злучэнне перарвана. Пацягніце ўніз, каб паўтарыць."
            OrionLanguage.KAZAKH -> "Байланыс үзілді. Қайталау үшін төмен тартыңыз."
            else -> "Connection closed. Pull down to retry."
        }
        raw.contains("Unauthorized", true) || raw.contains("401", true) -> when (lang) {
            OrionLanguage.RUSSIAN -> "Не удалось подтвердить сессию. Выполните вход повторно."
            OrionLanguage.UKRAINIAN -> "Не вдалося підтвердити сеанс. Увійдіть повторно."
            OrionLanguage.BELARUSIAN -> "Не ўдалося пацвердзіць сеанс. Увайдзіце паўторна."
            OrionLanguage.KAZAKH -> "Сеансты растау мүмкін болмады. Қайта кіріңіз."
            else -> "Your session could not be verified. Sign in again."
        }
        raw.contains("Forbidden", true) || raw.contains("403", true) -> when (lang) {
            OrionLanguage.RUSSIAN -> "Недостаточно прав для этого действия"
            OrionLanguage.UKRAINIAN -> "Недостатньо прав для цієї дії"
            OrionLanguage.BELARUSIAN -> "Недастаткова правоў для гэтага дзеяння"
            OrionLanguage.KAZAKH -> "Бұл әрекетке рұқсат жеткіліксіз"
            else -> "You do not have permission for this action"
        }
        raw.contains("Title is too short", true) -> when (lang) {
            OrionLanguage.RUSSIAN -> "Название должно содержать не менее 3 символов"
            OrionLanguage.UKRAINIAN -> "Назва має містити щонайменше 3 символи"
            OrionLanguage.BELARUSIAN -> "Назва павінна змяшчаць не менш за 3 сімвалы"
            OrionLanguage.KAZAKH -> "Атауда кемінде 3 таңба болуы керек"
            else -> "The title must contain at least 3 characters"
        }
        raw.contains("Community is private", true) -> when (lang) {
            OrionLanguage.RUSSIAN -> "Это закрытое сообщество. Вступление доступно только по приглашению."
            OrionLanguage.UKRAINIAN -> "Це закрита спільнота. Вступ доступний лише за запрошенням."
            OrionLanguage.BELARUSIAN -> "Гэта закрытая супольнасць. Уступленне даступнае толькі па запрашэнні."
            OrionLanguage.KAZAKH -> "Бұл жабық қауымдастық. Тек шақыру арқылы қосылуға болады."
            else -> "This community is private and requires an invitation."
        }
        else -> when (lang) {
            OrionLanguage.RUSSIAN -> "Произошла ошибка. Повторите попытку."
            OrionLanguage.UKRAINIAN -> "Сталася помилка. Спробуйте ще раз."
            OrionLanguage.BELARUSIAN -> "Адбылася памылка. Паўтарыце спробу."
            OrionLanguage.KAZAKH -> "Қате орын алды. Қайталап көріңіз."
            else -> "Something went wrong. Try again."
        }
    }
}

internal enum class OrionLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Русский"),
    UKRAINIAN("uk", "Українська"),
    BELARUSIAN("be", "Беларуская"),
    KAZAKH("kk", "Қазақша");

    companion object {
        fun fromCode(code: String?): OrionLanguage? = entries.firstOrNull { it.code.equals(code, true) }
    }
}

internal val LocalOrionLanguage = staticCompositionLocalOf { OrionLanguage.ENGLISH }
internal val LocalOrionLanguageSetter = staticCompositionLocalOf<(OrionLanguage) -> Unit> { {} }

internal object OrionI18n {
    var current: OrionLanguage = OrionLanguage.ENGLISH
}

internal fun tr(key: String, vararg args: Pair<String, String>): String {
    val value = OrionTranslations.text(OrionI18n.current, key)
    return args.fold(value) { acc, pair -> acc.replace("{${pair.first}}", pair.second) }
}

@Composable
private fun SyncCurrentLanguage() {
    OrionI18n.current = LocalOrionLanguage.current
}

@Composable
private fun LanguageSelector() {
    val current = LocalOrionLanguage.current
    val setLanguage = LocalOrionLanguageSetter.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Language, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(current.nativeName, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(Icons.Default.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OrionLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.nativeName) },
                    onClick = { setLanguage(language); expanded = false },
                    leadingIcon = { if (language == current) Icon(Icons.Default.Check, contentDescription = null) }
                )
            }
        }
    }
}

private object OrionTranslations {
    private val en = mapOf(
        "invalid_email" to "Enter a valid email address", "nick_min" to "Nickname must be at least 3 characters", "enter_name" to "Enter your name",
        "password_min" to "Password must be at least 8 characters", "password_mismatch" to "Passwords do not match", "accept_terms" to "Accept the Terms of Use and Privacy Policy",
        "enter_otp" to "Enter the six-digit code", "new_code_sent" to "A new code has been sent", "back" to "Back", "tagline" to "THE NEXT GENERATION OF MESSAGING",
        "create_account_title" to "Create account", "login_title" to "Sign in", "register_subtitle" to "Fill in your registration details", "welcome_back" to "Welcome back!",
        "email" to "Email", "nickname" to "Nickname", "name" to "Name", "password" to "Password", "confirm_password" to "Confirm password",
        "agree_terms" to "I agree to the Terms of Use\nand Privacy Policy", "remember_me" to "Remember me", "forgot_password" to "Forgot password?", "enter_email_first" to "Enter your email first",
        "reset_sent" to "Password reset email sent", "please_wait" to "Please wait…", "register" to "Register", "sign_in" to "Sign in", "enter_email_password" to "Enter email and password",
        "code_resent" to "Verification code sent again", "or" to "or", "create_account" to "Create account", "continue_terms" to "By continuing, you agree to the\nTerms of Use and Privacy Policy",
        "already_account_prefix" to "Already have an account? ", "email_verification" to "Email verification", "otp_sent_to" to "We sent a six-digit code to", "verify" to "Verify", "resend_code" to "Resend code",
        "resend_in" to "Resend code in {seconds}s", "search_hint" to "Nickname, name or channel", "contacts_search_hint" to "Search contacts", "people" to "People", "chat" to "Chat", "channels" to "Channels", "join" to "Join", "leave_chat" to "Leave chat",
        "file" to "File", "reply" to "Reply", "copy" to "Copy", "delete_for_me" to "Delete for me", "edit" to "Edit", "delete_for_all" to "Delete for everyone", "editing" to "Editing", "save" to "Save", "cancel" to "Cancel",
        "group" to "Group", "channel" to "Channel", "title" to "Title", "description" to "Description", "members" to "Members", "creating" to "Creating…", "create" to "Create", "create_group" to "Create group", "create_channel" to "Create channel",
        "orionx_news" to "OrionX News", "no_announcements" to "No announcements yet", "system_updates" to "System updates", "no_updates" to "No published updates yet.", "mandatory_update" to "Mandatory update", "download_update" to "Download update",
        "updates_check" to "Check for update", "updates_build_line" to "build {build} · {channel} channel", "updates_last_check" to "last check", "updates_installed" to "current version", "updates_never_checked" to "not checked yet", "updates_auto_wifi" to "auto-update over wi-fi", "updates_checking" to "Checking…", "updates_up_to_date" to "You have the latest version", "updates_up_to_date_sub" to "OrionX is up to date. We'll let you know when a new version is released.", "updates_available" to "Update available", "updates_current_version" to "Installed version", "updates_download_install" to "Download and install", "updates_downloading" to "Downloading {percent}%", "updates_download_failed" to "Could not download the update. Check your connection and try again.", "updates_check_failed" to "Could not check for updates. Check your connection and try again.", "updates_whats_new" to "What's new", "updates_allow_install" to "Allow installing apps from this source in the settings that just opened, then tap Download and install again.", "updates_title_checking" to "checking for updates", "updates_title_found" to "version {version} is available", "updates_title_downloading" to "downloading {version}", "updates_title_ready" to "update downloaded", "updates_title_latest" to "you are on the latest version", "updates_sub_checking" to "talking to the orionx server", "updates_sub_found" to "security and speed", "updates_sub_downloading" to "keep the app open", "updates_sub_ready" to "confirm the install in the system dialog", "updates_sub_latest" to "orionx {version} · build {build}", "updates_btn_downloading" to "Downloading…", "updates_btn_install" to "Install", "updates_btn_check_again" to "Check again", "updates_progress" to "downloaded {percent}%", "updates_whats_new_v" to "what's new in {version}", "updates_size_mb" to "{size} MB",
        "admin_panel" to "Admin panel", "admin_only" to "Available only to galaxymoratim@gmail.com", "global_announcement" to "Global announcement", "heading" to "Heading", "text" to "Text", "publish_announcement" to "Publish announcement",
        "cloud_update" to "Cloud update", "version_example" to "Version, e.g. 4.0.1", "all_changes" to "All changes", "apk_link" to "APK link", "mandatory" to "Mandatory", "publish_update" to "Publish update", "users" to "Users",
        "profile" to "Profile", "nick" to "Nickname", "save_profile" to "Save profile", "logout" to "Sign out", "language" to "Language", "orionx_group" to "OrionX group", "orionx_channel" to "OrionX channel", "direct_chat" to "Direct chat",
        "create_first_chat" to "Create your first chat, group or channel", "no_chats" to "No chats yet",
        "header_syncing" to "Updating", "header_offline" to "No network",
        "compose_hint" to "Who to write to?", "compose_recent" to "Recent", "compose_results" to "Results",
        "day_today" to "today", "day_yesterday" to "yesterday",
        "community_pin" to "Pin", "community_unpin" to "Unpin",
        "video_call" to "video call", "video_badge" to "video", "channel_new_post" to "new post", "channel_published" to "published", "channel_pinned_tag" to "pinned", "channel_comment_action" to "comment", "channel_discussion_title" to "{name} · discussion", "channel_discussion_sub" to "comments on the post", "channel_post_label" to "channel post", "channel_comments_count" to "comments · {count}", "voice_open_external" to "open externally", "rec_swipe_cancel" to "left — cancel", "rec_release_to_cancel" to "release to cancel",
        "compose_count" to "{shown} of {total}", "compose_searching" to "Searching…", "compose_search_all" to "Search everywhere",
        "compose_empty" to "Nobody found for “{query}”", "compose_no_recent" to "No chats yet — find someone through search",
        "no_groups" to "No groups yet", "create_first_group" to "Create a group — a shared chat where everyone writes",
        "no_channels" to "No channels yet", "create_first_channel" to "Create a channel — you publish, everyone reads", "search" to "Search", "contacts" to "Contacts", "news" to "News", "updates" to "Updates", "settings" to "Settings", "chats" to "Chats", "public_channel" to "Public channel", "typing" to "{names} typing…", "edited" to "edited", "admin" to "Admin",
        "write_message" to "Write a message…", "uploading_file" to "Uploading file…", "file_too_large" to "Maximum file size is 50 MB", "file_read_failed" to "Could not read file", "message_deleted" to "Message deleted",
        "audio_call_unavailable" to "Audio calls require the separate WebRTC module", "video_call_unavailable" to "Video calls require the separate WebRTC module", "online" to "online", "error" to "Error", "wait" to "Wait",
        "request_failed" to "Request failed", "invalid_login" to "Incorrect email or password", "already_registered" to "An account with this email already exists", "rate_limit" to "Too many requests. Wait a little and try again.",
        "password_too_short" to "Password is too short", "email_not_confirmed" to "Email is not confirmed. We will send a new verification code now.", "profile_saved" to "Profile saved. Changes will appear after signing in again.", "saving" to "Saving…",
        "announcement_published" to "Announcement published", "reply_to" to "Reply to {name}", "role" to "Role: {role}", "users_count" to "Users\n{count}", "chats_count" to "Chats\n{count}", "messages_count" to "Messages\n{count}", "blocks_count" to "Blocks\n{count}",
        "published" to "Published: {title}", "error_details" to "Error: {details}",
        "emoji" to "Emoji", "stickers" to "Stickers", "sticker" to "Sticker", "my_gifs" to "My GIFs", "my_stickers" to "My stickers", "create_with_oria" to "Create with Oria",
        "oria_media_hint" to "Create and send without leaving the chat", "no_media_yet" to "Nothing here yet — create the first one with Oria", "oria_sticker" to "Oria sticker",
        "oria_media_studio" to "Oria Media Studio", "oria_media_description" to "Describe what you want. Oria prepares a chat-ready GIF or sticker and saves it to your library.",
        "describe_media" to "Describe the result", "gif_prompt_example" to "Example: a tiny purple rocket waves and flies into the stars, seamless 2-second loop",
        "sticker_prompt_example" to "Example: surprised purple cat, clean bold outline, transparent background", "sticker_from_photo" to "Make sticker from photo",
        "sticker_cleanup_note" to "For stickers Oria requests background removal, transparency, clean edges and a square sticker canvas.", "generate" to "Generate",
        "image_required" to "Choose an image", "image_too_large" to "Image must be 15 MB or smaller",
        "add_or_improve" to "Add / improve", "no_media_upload" to "Nothing here yet — add your own media",
        "oria_media_tools" to "Oria media tools", "oria_media_nongenerative" to "Oria does not generate media here. It only prepares your GIFs, videos and sticker images for chat.",
        "gif_import_title" to "Your GIFs", "add_ready_gif" to "Add ready GIF", "video_to_gif" to "Video → GIF",
        "video_gif_hint" to "Oria takes a short fragment, resizes it and makes a looping GIF locally.", "seconds" to "sec", "choose_video" to "Choose video",
        "sticker_import_title" to "Sticker preparation", "remove_background" to "Remove background", "remove_background_hint" to "Best for a plain or mostly uniform background",
        "white_outline" to "Add white outline", "choose_sticker_image" to "Choose image / sticker", "sticker_local_processing_note" to "Processing is local: crop, simple background cleanup, transparent 512×512 canvas and optional outline.",
        "media_too_large" to "Result must be 15 MB or smaller", "choose_gif" to "Choose a GIF file", "my_gif" to "My GIF", "ready_media_added" to "Ready media added",
        "video_gif" to "Video GIF", "oria_video_processed" to "Oria converted your video to a looping GIF", "sticker_prepared" to "Sticker prepared",
        "background_removed" to "background removed", "outline_added" to "outline added", "my_sticker" to "My sticker", "close" to "Close", "you_colon" to "You: ",
        "unread" to "Unread", "messages_one" to "message", "messages_few" to "messages", "messages_many" to "messages",
        "in_chats_one" to "in {n} chat", "in_chats_few" to "in {n} chats", "in_chats_many" to "in {n} chats",
        "in_groups_one" to "in {n} group", "in_groups_few" to "in {n} groups", "in_groups_many" to "in {n} groups",
        "in_channels_one" to "in {n} channel", "in_channels_few" to "in {n} channels", "in_channels_many" to "in {n} channels",
        "groups" to "Groups", "write_action" to "Write", "who_search" to "Who are we looking for?", "oria" to "Oria", "nothing_found" to "Nothing found", "found" to "found",
        // Groups and channels (canvas: groupinfo / gsettings / gadmins / gadd / chinfo / chsettings).
        "community_about_group" to "About the group", "community_about_channel" to "About the channel",
        "community_about_label" to "What this is", "community_about_label_channel" to "description", "community_settings" to "Settings",
        "community_group_settings" to "Group settings", "community_channel_settings" to "Channel settings",
        "community_members" to "Members", "community_subscribers" to "Subscribers",
        "community_member_count" to "group · {count} members", "community_subscriber_count" to "channel · {count} subscribers",
        "community_and_more" to "and {count} more", "community_add_member" to "Add member", "community_add_members" to "Add members",
        "community_add_n" to "Add {count}", "community_search_people" to "Name or @nickname", "community_nobody_found" to "Nobody found",
        "community_pinned" to "Pinned", "community_no_pins" to "Nothing pinned yet", "community_unpin" to "Unpin",
        "community_admins" to "Admins", "community_appointed" to "Appointed", "community_promote" to "Make admin", "community_demote" to "Dismiss",
        "community_demote_hint" to "{name} will lose admin rights but stays a member.",
        "community_all_are_admins" to "Everyone here is already an admin",
        "community_admin_rights_note" to "Admins can change settings, pin messages and delete other people's messages.",
        "community_admin_tag" to "Tag", "community_admin_tag_placeholder" to "admin",
        "community_admin_tag_hint" to "Shown next to the name in the feed. Leave empty for the default label.",
        "community_role_owner" to "owner", "community_role_admin" to "admin", "community_role_moderator" to "moderator",
        "community_role_member" to "member", "community_role_subscriber" to "subscriber",
        "community_name" to "Name", "community_description" to "Description",
        "community_public_link" to "Public link", "community_link_placeholder" to "orionx",
        "community_link_empty_hint" to "Without a link the community stays private.",
        "community_copy_link" to "Copy invite link", "community_link_copied" to "Link copied",
        "community_who_can_post" to "Who can post", "community_who_can_write" to "Who can write", "community_your_channel" to "your channel", "community_channel_admins" to "channel admins", "community_run_channel" to "run the channel", "community_channel_rights_note" to "A channel has one owner. Admins publish posts, edit the description and see the stats.", "community_channel_type" to "channel type", "community_channel_public" to "public", "community_channel_private" to "private", "community_channel_public_hint" to "Anyone can find it by link.", "community_channel_private_hint" to "Invite only.", "community_who_can_comment" to "Who can comment", "community_comments_all" to "everyone", "community_comments_none" to "nobody", "community_comments_all_hint" to "Subscribers can comment on posts.", "community_comments_off_hint" to "Comments are off.", "community_write_all_hint" to "Every member can write here.", "community_write_admins_hint" to "Only admins can write here.", "community_post_admins" to "Admins", "community_post_all" to "Everyone",
        "community_post_all_hint" to "Any subscriber can publish to the channel.",
        "community_post_admins_hint" to "Only admins can publish to the channel.",
        "community_who_can_invite" to "Who can add",
        "community_invite_all_hint" to "Any member can add people to the group.",
        "community_invite_admins_hint" to "Only admins can add people to the group.", "community_invite_toggle" to "invite by link", "community_auto_delete" to "auto-delete", "community_auto_delete_day" to "24 h", "community_auto_delete_week" to "7 d", "community_auto_delete_month" to "30 d", "community_auto_delete_never" to "messages are kept forever", "community_auto_delete_on" to "messages disappear after {window}", "community_reaction_set" to "reaction set", "community_reaction_set_count" to "{count} emoji in the set", "community_reaction_add" to "emoji", "community_reactions" to "reactions", "community_reactions_sub" to "react to messages with emoji", "community_pins_all" to "pin messages", "community_pins_all_sub" to "open to every member", "community_hide_roster" to "hide member list", "community_hide_roster_sub" to "only admins see it",  "community_invite_toggle_sub" to "members can bring others in",
        "community_slow_mode" to "Slow mode", "community_slow_off" to "Off",
        // Canvas create-form switches, editable here too (migration 018).
        "community_history" to "History for new members", "community_history_sub" to "New members see earlier messages",
        "community_media" to "Media and files", "community_media_sub" to "Photos, video and documents are allowed",
        "community_approval" to "Join requests", "community_approval_sub" to "An admin confirms every new member",
        "community_forwarding" to "Allow forwarding", "community_forwarding_sub" to "Posts can be reposted onward",
        "community_signature" to "Author signatures", "community_signature_sub" to "The post shows which admin published it",
        "community_views" to "View counter", "community_views_sub" to "Everyone in the feed sees the view count",
        "community_discussion" to "Discussion chat", "community_discussion_sub" to "Comments go to a separate group chat",
        "community_requests" to "Requests", "community_requests_empty" to "No pending requests",
        "community_slow_seconds" to "{n}s", "community_slow_minutes" to "{n}m",
        "community_slow_off_hint" to "Members can write without a delay.",
        "community_slow_on_hint" to "One message per {value} for everyone except admins.",
        "community_leave" to "Leave group", "community_leave_short" to "Leave",
        "community_leave_hint" to "You will leave «{name}» and stop receiving its messages.",
        "community_unsubscribe" to "Unsubscribe",
        "community_unsubscribe_hint" to "You will stop receiving posts from «{name}».",
        "community_delete_group" to "Delete group", "community_delete_channel" to "Delete channel",
        "community_delete_hint" to "«{name}» will be deleted for everyone along with all its messages. This cannot be undone.",
        "delete" to "Delete",
        "community_pick_people" to "pick contacts", "community_in_group" to "{count} in the group", "community_in_channel" to "{count} in the channel", "picker_emoji" to "emoji", "picker_gif" to "gif", "picker_stickers" to "stickers", "picker_gif_hint" to "Pick a GIF from the device — it attaches to the message.", "picker_gif_pick" to "pick a gif", "composer_placeholder" to "message", "attach_send" to "send", "attach_photo" to "camera", "attach_video" to "video", "attach_file" to "file", "attach_music" to "music", "composer_replying" to "replying to", "composer_editing" to "editing message", "composer_attached" to "attachment ready", "composer_recording" to "recording voice", "forward" to "forward", "community_muted" to "muted", "post_body_label" to "post text", "post_body_placeholder" to "what do we tell the subscribers?", "post_add_cover" to "add a cover", "post_pin_after" to "pin after publishing", "post_pin_after_sub" to "the previous pin is replaced", "post_publish" to "publish", "post_subscribers" to "{count} subscribers", "service_joined" to "{name} joined", "service_added" to "{actor} added {name}", "service_left" to "{name} left", "service_removed" to "{actor} removed {name}", "service_renamed" to "{actor} renamed it to {title}", "pinned_bar_label" to "pinned", "community_picked" to "{count} selected",
        "profile_name" to "name", "profile_username" to "username",
        "profile_chats" to "chats", "profile_media" to "media", "profile_contacts" to "contacts",
        "profile_section" to "profile settings", "profile_bio" to "about me",
        "profile_bio_empty" to "not filled in", "profile_phone" to "phone number",
        "profile_phone_empty" to "not added", "profile_qr" to "my QR code",
        "community_sound_on" to "sound on", "community_sound_off" to "muted",
        "community_call" to "call", "community_search" to "search",
        "community_link" to "link", "community_copied" to "copied", "community_channel_line" to "{type} channel · {count} subscribers", "community_invite_link" to "invite link", "community_link_copied_note" to "link copied", "community_invite" to "invite", "community_post_short" to "post", "community_share" to "share", "community_write_post" to "Write a post", "community_leave_tile" to "leave", "community_qr_hint_channel" to "Scan the code or send the link — the channel opens straight in OrionX.", "community_qr_hint_group" to "Scan the code or send the link — the group opens straight in OrionX.",
        "community_media" to "Media and files", "community_no_media" to "Nothing shared yet",
        "community_call_soon" to "Group calls are a separate stage — one-to-one calls work today.",
        "community_search_soon" to "Search inside a chat is not wired up yet.",
        "search_in_chat" to "search in chat", "search_in_chat_hint" to "what are we looking for?", "search_in_chat_found" to "{n} found",
        "search_in_chat_empty" to "nothing found",
        "pin_chat" to "pin chat", "unpin_chat" to "unpin chat",
        "chat_wallpaper" to "chat wallpaper",
        "block_action" to "block", "unblock_action" to "unblock",
        "clear_history" to "clear history", "clear_history_hint" to "choose whose messages to delete. this cannot be undone.",
        "clear_history_mine" to "only for me", "clear_history_all" to "for everyone",
        "save_to_notes" to "to notes", "saved_to_notes" to "Saved to notes",
        "post_sign_author" to "sign with author", "post_sign_author_hint" to "the post will show who published it",
    )
    private val ru = mapOf(
        "invalid_email" to "Укажи корректную почту", "nick_min" to "Никнейм должен содержать минимум 3 символа", "enter_name" to "Укажи имя", "password_min" to "Пароль должен содержать минимум 8 символов",
        "password_mismatch" to "Пароли не совпадают", "accept_terms" to "Прими Условия использования и Политику конфиденциальности", "enter_otp" to "Введи шестизначный код", "new_code_sent" to "Новый код отправлен", "back" to "Назад",
        "tagline" to "НОВОЕ ПОКОЛЕНИЕ МЕССЕНДЖЕРА", "create_account_title" to "Создание аккаунта", "login_title" to "Вход в аккаунт", "register_subtitle" to "Заполните данные для регистрации", "welcome_back" to "Добро пожаловать обратно!",
        "email" to "Почта", "nickname" to "Никнейм", "name" to "Имя", "password" to "Пароль", "confirm_password" to "Подтвердите пароль", "agree_terms" to "Я согласен с Условиями использования\nи Политикой конфиденциальности",
        "remember_me" to "Запомнить меня", "forgot_password" to "Забыли пароль?", "enter_email_first" to "Сначала введи почту", "reset_sent" to "Письмо для восстановления отправлено", "please_wait" to "Подождите…", "register" to "Зарегистрироваться",
        "sign_in" to "Войти", "enter_email_password" to "Введи почту и пароль", "code_resent" to "Код подтверждения отправлен повторно", "or" to "или", "create_account" to "Создать аккаунт", "continue_terms" to "Продолжая, вы соглашаетесь с\nУсловиями использования и Политикой конфиденциальности",
        "already_account_prefix" to "Уже есть аккаунт? ", "email_verification" to "Подтверждение почты", "otp_sent_to" to "Мы отправили шестизначный код на", "verify" to "Подтвердить", "resend_code" to "Отправить код повторно", "resend_in" to "Отправить код повторно через {seconds}с",
        "search_hint" to "Ник, имя или канал", "contacts_search_hint" to "Поиск контактов", "people" to "Люди", "chat" to "Чат", "channels" to "Каналы", "join" to "Вступить", "leave_chat" to "Покинуть чат", "file" to "Файл", "reply" to "Ответить", "copy" to "Копировать", "delete_for_me" to "Удалить у меня",
        "edit" to "Изменить", "delete_for_all" to "Удалить у всех", "editing" to "Редактирование", "save" to "Сохранить", "cancel" to "Отмена", "group" to "Группа", "channel" to "Канал", "title" to "Название", "description" to "Описание", "members" to "Участники",
        "creating" to "Создание…", "create" to "Создать", "create_group" to "Создать группу", "create_channel" to "Создать канал", "orionx_news" to "Новости OrionX", "no_announcements" to "Объявлений пока нет", "system_updates" to "Обновления системы", "no_updates" to "Опубликованных обновлений пока нет.",
        "updates_check" to "Проверить обновление", "updates_build_line" to "сборка {build} · канал {channel}", "updates_last_check" to "последняя проверка", "updates_installed" to "текущая версия", "updates_never_checked" to "ещё не проверяли", "updates_auto_wifi" to "автообновление по wi-fi", "updates_checking" to "Проверяем…", "updates_up_to_date" to "У вас последняя версия", "updates_up_to_date_sub" to "OrionX обновлён до актуальной версии. Мы сообщим, когда выйдет новая.", "updates_available" to "Доступно обновление", "updates_current_version" to "Установленная версия", "updates_download_install" to "Скачать и установить", "updates_downloading" to "Скачивание {percent}%", "updates_download_failed" to "Не удалось скачать обновление. Проверьте подключение и попробуйте снова.", "updates_check_failed" to "Не удалось проверить обновления. Проверьте подключение и попробуйте снова.", "updates_whats_new" to "Что нового", "updates_allow_install" to "Разрешите установку приложений из этого источника в открывшихся настройках, затем нажмите «Скачать и установить» снова.", "updates_title_checking" to "проверяем обновления", "updates_title_found" to "доступна версия {version}", "updates_title_downloading" to "загружаем {version}", "updates_title_ready" to "обновление загружено", "updates_title_latest" to "установлена последняя версия", "updates_sub_checking" to "связываемся с сервером orionx", "updates_sub_found" to "безопасность и скорость", "updates_sub_downloading" to "не закрывайте приложение", "updates_sub_ready" to "подтвердите установку в системном окне", "updates_sub_latest" to "orionx {version} · сборка {build}", "updates_btn_downloading" to "Загрузка…", "updates_btn_install" to "Установить", "updates_btn_check_again" to "Проверить снова", "updates_progress" to "загружено {percent}%", "updates_whats_new_v" to "что нового в {version}", "updates_size_mb" to "{size} мб",
        "mandatory_update" to "Обязательное обновление", "download_update" to "Скачать обновление", "admin_panel" to "Админ-панель", "admin_only" to "Доступна только galaxymoratim@gmail.com", "global_announcement" to "Глобальное объявление", "heading" to "Заголовок", "text" to "Текст", "publish_announcement" to "Опубликовать объявление",
        "cloud_update" to "Облачное обновление", "version_example" to "Версия, например 4.0.1", "all_changes" to "Все изменения", "apk_link" to "Ссылка на APK", "mandatory" to "Обязательное", "publish_update" to "Опубликовать обновление", "users" to "Пользователи", "profile" to "Профиль", "nick" to "Ник",
        "save_profile" to "Сохранить профиль", "logout" to "Выйти", "language" to "Язык", "orionx_group" to "Группа OrionX", "orionx_channel" to "Канал OrionX", "direct_chat" to "Личный чат", "create_first_chat" to "Создай первый чат, группу или канал", "no_chats" to "Чатов пока нет",
        "header_syncing" to "Обновление", "header_offline" to "Нет сети",
        "compose_hint" to "кому написать?", "compose_recent" to "недавние", "compose_results" to "результаты",
        "day_today" to "сегодня", "day_yesterday" to "вчера",
        "community_pin" to "закрепить", "community_unpin" to "открепить",
        "video_call" to "видеозвонок", "video_badge" to "видео", "channel_new_post" to "новый пост", "channel_published" to "опубликован", "channel_pinned_tag" to "закреплён", "channel_comment_action" to "прокомментировать", "channel_discussion_title" to "{name} · обсуждение", "channel_discussion_sub" to "комментарии к посту", "channel_post_label" to "пост канала", "channel_comments_count" to "комментарии · {count}", "voice_open_external" to "открыть во внешнем плеере", "rec_swipe_cancel" to "влево — отмена", "rec_release_to_cancel" to "отпустите для отмены",
        "compose_count" to "{shown} из {total}", "compose_searching" to "ищем…", "compose_search_all" to "искать везде",
        "compose_empty" to "никого не нашлось по запросу «{query}»", "compose_no_recent" to "чатов пока нет — найдите человека через поиск",
        "no_groups" to "Групп пока нет", "create_first_group" to "Создай группу — общий чат, все пишут",
        "no_channels" to "Каналов пока нет", "create_first_channel" to "Создай канал — вы публикуете, все читают", "search" to "Поиск", "contacts" to "Контакты", "news" to "Новости", "updates" to "Обновления",
        "settings" to "Настройки", "chats" to "Чаты", "public_channel" to "Публичный канал", "typing" to "{names} печатает…", "edited" to "изменено", "admin" to "Админ", "write_message" to "Напишите сообщение…", "uploading_file" to "Загрузка файла…", "file_too_large" to "Максимальный размер файла — 50 МБ", "file_read_failed" to "Не удалось прочитать файл", "message_deleted" to "Сообщение удалено", "audio_call_unavailable" to "Аудиозвонки требуют отдельного WebRTC-модуля",
        "video_call_unavailable" to "Видеозвонки требуют отдельного WebRTC-модуля", "online" to "в сети", "error" to "Ошибка", "wait" to "Подождите", "request_failed" to "Не удалось выполнить запрос", "invalid_login" to "Неверная почта или пароль", "already_registered" to "Аккаунт с этой почтой уже существует", "rate_limit" to "Слишком много запросов. Подожди немного и попробуй снова.",
        "password_too_short" to "Пароль слишком короткий", "email_not_confirmed" to "Почта не подтверждена. Сейчас отправим новый код подтверждения.", "profile_saved" to "Профиль сохранён. Изменения появятся после повторного входа.", "saving" to "Сохранение…", "announcement_published" to "Объявление опубликовано", "reply_to" to "Ответ для {name}", "role" to "Роль: {role}", "users_count" to "Пользователи\n{count}", "chats_count" to "Чаты\n{count}", "messages_count" to "Сообщения\n{count}", "blocks_count" to "Блокировки\n{count}", "published" to "Опубликовано: {title}", "error_details" to "Ошибка: {details}",
        "emoji" to "Эмодзи", "stickers" to "Стикеры", "sticker" to "Стикер", "my_gifs" to "Мои GIF", "my_stickers" to "Мои стикеры", "create_with_oria" to "Создать с Oria",
        "oria_media_hint" to "Создавай и отправляй, не выходя из чата", "no_media_yet" to "Пока пусто — создай первое с Oria", "oria_sticker" to "Стикер Oria",
        "oria_media_studio" to "Медиастудия Oria", "oria_media_description" to "Опиши результат. Oria подготовит GIF или стикер для чата и сохранит его в твою коллекцию.",
        "describe_media" to "Опиши результат", "gif_prompt_example" to "Например: маленькая фиолетовая ракета машет и улетает к звёздам, бесшовный цикл 2 секунды",
        "sticker_prompt_example" to "Например: удивлённый фиолетовый кот, чёткий контур, прозрачный фон", "sticker_from_photo" to "Сделать стикер из фото",
        "sticker_cleanup_note" to "Для стикеров Oria запрашивает удаление фона, прозрачность, чистые края и квадратный холст.", "generate" to "Создать",
        "image_required" to "Выбери изображение", "image_too_large" to "Изображение должно быть не больше 15 МБ",
        "add_or_improve" to "Добавить / доработать", "no_media_upload" to "Пока пусто — добавь свои GIF или стикеры",
        "oria_media_tools" to "Инструменты Oria", "oria_media_nongenerative" to "Здесь Oria ничего не рисует с нуля. Она только подготавливает твои GIF, видео и изображения для стикеров.",
        "gif_import_title" to "Свои GIF", "add_ready_gif" to "Добавить готовый GIF", "video_to_gif" to "Видео → GIF",
        "video_gif_hint" to "Oria берёт короткий фрагмент, уменьшает его и делает зацикленный GIF прямо на устройстве.", "seconds" to "сек", "choose_video" to "Выбрать видео",
        "sticker_import_title" to "Подготовка стикера", "remove_background" to "Убрать фон", "remove_background_hint" to "Лучше всего работает с однотонным или почти однородным фоном",
        "white_outline" to "Добавить белую обводку", "choose_sticker_image" to "Выбрать изображение / стикер", "sticker_local_processing_note" to "Обработка локальная: обрезка, очистка простого фона, прозрачный холст 512×512 и обводка по желанию.",
        "media_too_large" to "Результат должен быть не больше 15 МБ", "choose_gif" to "Выбери файл GIF", "my_gif" to "Мой GIF", "ready_media_added" to "Готовый файл добавлен",
        "video_gif" to "GIF из видео", "oria_video_processed" to "Oria преобразовала видео в зацикленный GIF", "sticker_prepared" to "Стикер подготовлен",
        "background_removed" to "фон удалён", "outline_added" to "обводка добавлена", "my_sticker" to "Мой стикер", "close" to "Закрыть", "you_colon" to "Вы: ",
        "unread" to "Непрочитано", "messages_one" to "сообщение", "messages_few" to "сообщения", "messages_many" to "сообщений",
        "in_chats_one" to "в {n} чате", "in_chats_few" to "в {n} чатах", "in_chats_many" to "в {n} чатах",
        "in_groups_one" to "в {n} группе", "in_groups_few" to "в {n} группах", "in_groups_many" to "в {n} группах",
        "in_channels_one" to "в {n} канале", "in_channels_few" to "в {n} каналах", "in_channels_many" to "в {n} каналах",
        "groups" to "Группы", "write_action" to "Написать", "who_search" to "Кого ищем?", "oria" to "Ория", "nothing_found" to "Ничего не найдено", "found" to "найдено",
        // Группы и каналы — строки взяты из макетов groupinfo / gsettings / gadmins / gadd.
        "community_about_group" to "о группе", "community_about_channel" to "о канале",
        "community_about_label" to "о чём тут", "community_about_label_channel" to "описание", "community_settings" to "настройки",
        "community_group_settings" to "настройки группы", "community_channel_settings" to "настройки канала",
        "community_members" to "участники", "community_subscribers" to "подписчики",
        "community_member_count" to "группа · {count} участников", "community_subscriber_count" to "канал · {count} подписчиков",
        "community_and_more" to "и ещё {count}", "community_add_member" to "добавить участника", "community_add_members" to "добавить участников",
        "community_add_n" to "добавить · {count}", "community_search_people" to "имя или @ник", "community_nobody_found" to "никого не нашли",
        "community_pinned" to "закреплённые", "community_no_pins" to "пока ничего не закреплено", "community_unpin" to "открепить",
        "community_admins" to "администраторы", "community_appointed" to "назначены", "community_promote" to "назначить", "community_demote" to "разжаловать",
        "community_demote_hint" to "{name} потеряет права администратора, но останется участником.",
        "community_all_are_admins" to "все участники уже админы",
        "community_admin_rights_note" to "админы могут менять настройки, закреплять сообщения и удалять чужие сообщения",
        "community_admin_tag" to "тег", "community_admin_tag_placeholder" to "админ",
        "community_admin_tag_hint" to "показывается рядом с именем в ленте. оставьте пустым для обычной подписи.",
        "community_role_owner" to "владелец", "community_role_admin" to "админ", "community_role_moderator" to "модератор",
        "community_role_member" to "участник", "community_role_subscriber" to "подписчик",
        "community_name" to "название", "community_description" to "описание",
        "community_public_link" to "ссылка-приглашение", "community_link_placeholder" to "orionx",
        "community_link_empty_hint" to "без ссылки сообщество остаётся закрытым.",
        "community_copy_link" to "копировать ссылку", "community_link_copied" to "ссылка скопирована",
        "community_who_can_post" to "кто публикует", "community_who_can_write" to "кто может писать", "community_your_channel" to "ваш канал", "community_channel_admins" to "админы канала", "community_run_channel" to "управляют каналом", "community_channel_rights_note" to "владелец канала один. админы публикуют посты, правят описание и видят статистику", "community_channel_type" to "тип канала", "community_channel_public" to "публичный", "community_channel_private" to "частный", "community_channel_public_hint" to "виден всем и ищется по ссылке", "community_channel_private_hint" to "только по приглашению", "community_who_can_comment" to "кто может комментировать", "community_comments_all" to "все", "community_comments_none" to "никто", "community_comments_all_hint" to "подписчики могут комментировать посты.", "community_comments_off_hint" to "комментарии к постам выключены.", "community_write_all_hint" to "писать могут все участники.", "community_write_admins_hint" to "писать могут только админы.", "community_post_admins" to "админы", "community_post_all" to "все",
        "community_post_all_hint" to "публиковать в канал может любой подписчик.",
        "community_post_admins_hint" to "публиковать в канал могут только админы.",
        "community_who_can_invite" to "кто добавляет",
        "community_invite_all_hint" to "любой участник может добавить друга в группу.",
        "community_invite_admins_hint" to "добавлять участников могут только админы.", "community_invite_toggle" to "приглашать по ссылке", "community_auto_delete" to "автоудаление", "community_auto_delete_day" to "24 ч", "community_auto_delete_week" to "7 дн", "community_auto_delete_month" to "30 дн", "community_auto_delete_never" to "сообщения хранятся всегда", "community_auto_delete_on" to "сообщения пропадают через {window}", "community_reaction_set" to "набор реакций", "community_reaction_set_count" to "{count} эмодзи в наборе", "community_reaction_add" to "эмодзи", "community_reactions" to "реакции", "community_reactions_sub" to "ставить эмодзи на сообщения", "community_pins_all" to "закреплять сообщения", "community_pins_all_sub" to "доступно всем участникам", "community_hide_roster" to "скрыть список участников", "community_hide_roster_sub" to "видят только админы",  "community_invite_toggle_sub" to "участники могут звать других",
        "community_slow_mode" to "медленный режим", "community_slow_off" to "выкл",
        // Формулировки взяты из макета — `CRTG_G` / `CRTG_C`.
        "community_history" to "история для новых", "community_history_sub" to "новые участники увидят прошлые сообщения",
        "community_media" to "медиа и файлы", "community_media_sub" to "фото, видео и документы разрешены",
        "community_approval" to "заявки на вступление", "community_approval_sub" to "админ подтверждает каждого нового",
        "community_forwarding" to "разрешить пересылку", "community_forwarding_sub" to "посты можно репостить дальше",
        "community_signature" to "подписи авторов", "community_signature_sub" to "под постом видно, кто из админов опубликовал",
        "community_views" to "счётчик просмотров", "community_views_sub" to "число просмотров видно всем в ленте",
        "community_discussion" to "чат обсуждения", "community_discussion_sub" to "комментарии уйдут в отдельный групповой чат",
        "community_requests" to "заявки", "community_requests_empty" to "заявок пока нет",
        "community_slow_seconds" to "{n} с", "community_slow_minutes" to "{n} мин",
        "community_slow_off_hint" to "участники пишут без задержки.",
        "community_slow_on_hint" to "одно сообщение раз в {value} для всех, кроме админов.",
        "community_leave" to "покинуть группу", "community_leave_short" to "выйти",
        "community_leave_hint" to "вы выйдете из «{name}» и перестанете получать сообщения.",
        "community_unsubscribe" to "отписаться",
        "community_unsubscribe_hint" to "вы перестанете получать посты из «{name}».",
        "community_delete_group" to "удалить группу", "community_delete_channel" to "удалить канал",
        "community_delete_hint" to "«{name}» будет удалён для всех вместе со всеми сообщениями. отменить это нельзя.",
        "delete" to "удалить",
        "community_pick_people" to "выберите контакты", "community_in_group" to "в группе {count}", "community_in_channel" to "в канале {count}", "picker_emoji" to "эмодзи", "picker_gif" to "gif", "picker_stickers" to "стикеры", "picker_gif_hint" to "выберите gif с устройства — он прикрепится к сообщению", "picker_gif_pick" to "выбрать gif", "composer_placeholder" to "сообщение", "attach_send" to "отправить", "attach_photo" to "камера", "attach_video" to "видео", "attach_file" to "файл", "attach_music" to "музыка", "composer_replying" to "ответ на сообщение", "composer_editing" to "изменение сообщения", "composer_attached" to "вложение готово", "composer_recording" to "запись голосового", "forward" to "переслать", "community_muted" to "без звука", "post_body_label" to "текст поста", "post_body_placeholder" to "о чём расскажем подписчикам?", "post_add_cover" to "добавить обложку", "post_pin_after" to "закрепить после публикации", "post_pin_after_sub" to "прежний закреп будет снят", "post_publish" to "опубликовать", "post_subscribers" to "{count} подписчиков", "service_joined" to "{name} присоединился", "service_added" to "{actor} добавил {name}", "service_left" to "{name} вышел", "service_removed" to "{actor} удалил {name}", "service_renamed" to "{actor} переименовал в «{title}»", "pinned_bar_label" to "закреплено", "community_picked" to "выбрано: {count}",
        "profile_name" to "имя", "profile_username" to "имя пользователя",
        "profile_chats" to "чата", "profile_media" to "медиа", "profile_contacts" to "контактов",
        "profile_section" to "настройки профиля", "profile_bio" to "о себе",
        "profile_bio_empty" to "не заполнено", "profile_phone" to "номер телефона",
        "profile_phone_empty" to "не добавлен", "profile_qr" to "мой QR-код",
        "community_sound_on" to "звук вкл", "community_sound_off" to "без звука",
        "community_call" to "созвон", "community_search" to "поиск",
        "community_link" to "ссылка", "community_copied" to "скопировано", "community_channel_line" to "{type} канал · {count} подписчиков", "community_invite_link" to "ссылка-приглашение", "community_link_copied_note" to "ссылка скопирована", "community_invite" to "пригласить", "community_post_short" to "пост", "community_share" to "поделиться", "community_write_post" to "написать пост", "community_leave_tile" to "покинуть", "community_qr_hint_channel" to "Отсканируйте код или отправьте ссылку — канал откроется прямо в OrionX.", "community_qr_hint_group" to "Отсканируйте код или отправьте ссылку — группа откроется прямо в OrionX.",
        "community_media" to "медиа и файлы", "community_no_media" to "пока ничего не присылали",
        "community_call_soon" to "групповые звонки — отдельный этап, сейчас работают личные.",
        "community_search_soon" to "поиск внутри чата пока не подключён.",
        "search_in_chat" to "поиск в чате", "search_in_chat_hint" to "что ищем?", "search_in_chat_found" to "найдено: {n}",
        "search_in_chat_empty" to "ничего не нашлось",
        "pin_chat" to "закрепить чат", "unpin_chat" to "открепить чат",
        "chat_wallpaper" to "обои чата",
        "block_action" to "заблокировать", "unblock_action" to "разблокировать",
        "clear_history" to "очистить историю", "clear_history_hint" to "выберите, у кого удалить сообщения. отменить будет нельзя.",
        "clear_history_mine" to "только у себя", "clear_history_all" to "для всех",
        "save_to_notes" to "в блокнот", "saved_to_notes" to "Сохранено в блокнот",
        "post_sign_author" to "подписать автором", "post_sign_author_hint" to "внизу поста появится, кто его опубликовал",
    )
    private val uk = en + mapOf(
        "tagline" to "НОВЕ ПОКОЛІННЯ МЕСЕНДЖЕРА", "create_account_title" to "Створення облікового запису", "login_title" to "Вхід в обліковий запис", "register_subtitle" to "Заповніть дані для реєстрації", "welcome_back" to "Ласкаво просимо назад!",
        "email" to "Пошта", "nickname" to "Нікнейм", "name" to "Ім’я", "password" to "Пароль", "confirm_password" to "Підтвердьте пароль", "remember_me" to "Запам’ятати мене", "forgot_password" to "Забули пароль?", "register" to "Зареєструватися", "sign_in" to "Увійти",
        "or" to "або", "create_account" to "Створити обліковий запис", "already_account_prefix" to "Вже маєте обліковий запис? ", "email_verification" to "Підтвердження пошти", "otp_sent_to" to "Ми надіслали шестизначний код на", "verify" to "Підтвердити", "resend_code" to "Надіслати код повторно", "resend_in" to "Надіслати код повторно через {seconds}с",
        "search_hint" to "Нік, ім’я або канал", "contacts_search_hint" to "Пошук контактів", "people" to "Люди", "channels" to "Канали", "join" to "Приєднатися", "leave_chat" to "Залишити чат", "reply" to "Відповісти", "copy" to "Копіювати", "delete_for_me" to "Видалити для мене", "delete_for_all" to "Видалити для всіх",
        "save" to "Зберегти", "cancel" to "Скасувати", "group" to "Група", "channel" to "Канал", "title" to "Назва", "description" to "Опис", "members" to "Учасники", "create" to "Створити", "create_group" to "Створити групу", "create_channel" to "Створити канал", "system_updates" to "Оновлення системи", "download_update" to "Завантажити оновлення",
        "users" to "Користувачі", "profile" to "Профіль", "save_profile" to "Зберегти профіль", "saving" to "Збереження…", "logout" to "Вийти", "language" to "Мова", "no_chats" to "Чатів поки немає", "search" to "Пошук", "contacts" to "Контакти", "news" to "Новини", "updates" to "Оновлення", "settings" to "Налаштування", "chats" to "Чати", "public_channel" to "Публічний канал", "typing" to "{names} друкує…", "edited" to "змінено", "write_message" to "Напишіть повідомлення…", "online" to "у мережі",
        "emoji" to "Емодзі", "stickers" to "Стікери", "sticker" to "Стікер", "my_gifs" to "Мої GIF", "my_stickers" to "Мої стікери", "create_with_oria" to "Створити з Oria",
        "oria_media_hint" to "Створюйте й надсилайте, не виходячи з чату", "no_media_yet" to "Поки порожньо — створіть перше з Oria", "oria_sticker" to "Стікер Oria",
        "oria_media_studio" to "Медіастудія Oria", "oria_media_description" to "Опишіть результат. Oria підготує GIF або стікер для чату й збереже його у вашій колекції.",
        "describe_media" to "Опишіть результат", "gif_prompt_example" to "Наприклад: маленька фіолетова ракета махає й летить до зірок, безшовний цикл 2 секунди",
        "sticker_prompt_example" to "Наприклад: здивований фіолетовий кіт, чіткий контур, прозорий фон", "sticker_from_photo" to "Зробити стікер із фото",
        "sticker_cleanup_note" to "Для стікерів Oria запитує видалення фону, прозорість, чисті краї та квадратний формат.", "generate" to "Створити", "image_required" to "Виберіть зображення", "image_too_large" to "Зображення має бути не більше 15 МБ",
        "add_or_improve" to "Додати / допрацювати", "no_media_upload" to "Поки порожньо — додайте свої GIF або стікери", "oria_media_tools" to "Інструменти Oria",
        "oria_media_nongenerative" to "Тут Oria нічого не генерує з нуля. Вона лише готує ваші GIF, відео та зображення для стікерів.", "add_ready_gif" to "Додати готовий GIF", "video_to_gif" to "Відео → GIF", "choose_video" to "Вибрати відео",
        "remove_background" to "Прибрати фон", "white_outline" to "Додати білий контур", "choose_sticker_image" to "Вибрати зображення / стікер", "close" to "Закрити",
        "unread" to "Непрочитано", "messages_one" to "повідомлення", "messages_few" to "повідомлення", "messages_many" to "повідомлень",
        "in_chats_one" to "у {n} чаті", "in_chats_few" to "у {n} чатах", "in_chats_many" to "у {n} чатах",
        "in_groups_one" to "у {n} групі", "in_groups_few" to "у {n} групах", "in_groups_many" to "у {n} групах",
        "in_channels_one" to "у {n} каналі", "in_channels_few" to "у {n} каналах", "in_channels_many" to "у {n} каналах",
        "groups" to "Групи", "write_action" to "Написати", "who_search" to "Кого шукаємо?", "oria" to "Орія", "nothing_found" to "Нічого не знайдено", "found" to "знайдено",
    )
    private val be = en + mapOf(
        "tagline" to "НОВАЕ ПАКАЛЕННЕ МЕСЕНДЖАРА", "create_account_title" to "Стварэнне акаўнта", "login_title" to "Уваход у акаўнт", "register_subtitle" to "Запоўніце даныя для рэгістрацыі", "welcome_back" to "Сардэчна запрашаем!",
        "email" to "Пошта", "nickname" to "Нікнэйм", "name" to "Імя", "password" to "Пароль", "confirm_password" to "Пацвердзіце пароль", "remember_me" to "Запомніць мяне", "forgot_password" to "Забылі пароль?", "register" to "Зарэгістравацца", "sign_in" to "Увайсці",
        "or" to "або", "create_account" to "Стварыць акаўнт", "already_account_prefix" to "Ужо ёсць акаўнт? ", "email_verification" to "Пацвярджэнне пошты", "otp_sent_to" to "Мы адправілі шасцізначны код на", "verify" to "Пацвердзіць", "resend_code" to "Адправіць код паўторна", "resend_in" to "Адправіць код паўторна праз {seconds}с",
        "search_hint" to "Нік, імя або канал", "contacts_search_hint" to "Пошук кантактаў", "people" to "Людзі", "channels" to "Каналы", "join" to "Далучыцца", "leave_chat" to "Пакінуць чат", "reply" to "Адказаць", "copy" to "Капіраваць", "delete_for_me" to "Выдаліць у мяне", "delete_for_all" to "Выдаліць ва ўсіх",
        "save" to "Захаваць", "cancel" to "Скасаваць", "group" to "Група", "channel" to "Канал", "title" to "Назва", "description" to "Апісанне", "members" to "Удзельнікі", "create" to "Стварыць", "create_group" to "Стварыць групу", "create_channel" to "Стварыць канал", "system_updates" to "Абнаўленні сістэмы", "download_update" to "Спампаваць абнаўленне",
        "users" to "Карыстальнікі", "profile" to "Профіль", "save_profile" to "Захаваць профіль", "saving" to "Захаванне…", "logout" to "Выйсці", "language" to "Мова", "no_chats" to "Чатаў пакуль няма", "search" to "Пошук", "contacts" to "Кантакты", "news" to "Навіны", "updates" to "Абнаўленні", "settings" to "Налады", "chats" to "Чаты", "public_channel" to "Публічны канал", "typing" to "{names} друкуе…", "edited" to "зменена", "write_message" to "Напішыце паведамленне…", "online" to "у сетцы",
        "emoji" to "Эмодзі", "stickers" to "Стыкеры", "sticker" to "Стыкер", "my_gifs" to "Мае GIF", "my_stickers" to "Мае стыкеры", "create_with_oria" to "Стварыць з Oria",
        "oria_media_hint" to "Стварайце і адпраўляйце, не выходзячы з чата", "no_media_yet" to "Пакуль пуста — стварыце першае з Oria", "oria_sticker" to "Стыкер Oria",
        "oria_media_studio" to "Медыясцудыя Oria", "oria_media_description" to "Апішыце вынік. Oria падрыхтуе GIF або стыкер для чата і захавае яго ў вашай калекцыі.",
        "describe_media" to "Апішыце вынік", "gif_prompt_example" to "Напрыклад: маленькая фіялетавая ракета махае і ляціць да зорак, бясшвоўны цыкл 2 секунды",
        "sticker_prompt_example" to "Напрыклад: здзіўлены фіялетавы кот, чысты контур, празрысты фон", "sticker_from_photo" to "Зрабіць стыкер з фота",
        "sticker_cleanup_note" to "Для стыкераў Oria запытвае выдаленне фону, празрыстасць, чыстыя краі і квадратны фармат.", "generate" to "Стварыць", "image_required" to "Выберыце выяву", "image_too_large" to "Выява павінна быць не больш за 15 МБ",
        "add_or_improve" to "Дадаць / дапрацаваць", "no_media_upload" to "Пакуль пуста — дадайце свае GIF або стыкеры", "oria_media_tools" to "Інструменты Oria",
        "oria_media_nongenerative" to "Тут Oria нічога не стварае з нуля. Яна толькі рыхтуе вашы GIF, відэа і выявы для стыкераў.", "add_ready_gif" to "Дадаць гатовы GIF", "video_to_gif" to "Відэа → GIF", "choose_video" to "Выбраць відэа",
        "remove_background" to "Прыбраць фон", "white_outline" to "Дадаць белы контур", "choose_sticker_image" to "Выбраць выяву / стыкер", "close" to "Закрыць",
        "unread" to "Непрачытана", "messages_one" to "паведамленне", "messages_few" to "паведамленні", "messages_many" to "паведамленняў",
        "in_chats_one" to "у {n} чаце", "in_chats_few" to "у {n} чатах", "in_chats_many" to "у {n} чатах",
        "in_groups_one" to "у {n} групе", "in_groups_few" to "у {n} групах", "in_groups_many" to "у {n} групах",
        "in_channels_one" to "у {n} канале", "in_channels_few" to "у {n} каналах", "in_channels_many" to "у {n} каналах",
        "groups" to "Групы", "write_action" to "Напісаць", "who_search" to "Каго шукаем?", "oria" to "Орыя", "nothing_found" to "Нічога не знойдзена", "found" to "знойдзена",
    )
    private val kk = en + mapOf(
        "tagline" to "МЕССЕНДЖЕРДІҢ ЖАҢА БУЫНЫ", "create_account_title" to "Тіркелгі жасау", "login_title" to "Тіркелгіге кіру", "register_subtitle" to "Тіркелу деректерін толтырыңыз", "welcome_back" to "Қайта қош келдіңіз!",
        "email" to "Электрондық пошта", "nickname" to "Лақап ат", "name" to "Аты", "password" to "Құпиясөз", "confirm_password" to "Құпиясөзді растаңыз", "remember_me" to "Мені есте сақтау", "forgot_password" to "Құпиясөзді ұмыттыңыз ба?", "register" to "Тіркелу", "sign_in" to "Кіру",
        "or" to "немесе", "create_account" to "Тіркелгі жасау", "already_account_prefix" to "Тіркелгіңіз бар ма? ", "email_verification" to "Поштаны растау", "otp_sent_to" to "Алты таңбалы код жіберілді:", "verify" to "Растау", "resend_code" to "Кодты қайта жіберу", "resend_in" to "Кодты {seconds}с кейін қайта жіберу",
        "search_hint" to "Лақап ат, аты немесе арна", "contacts_search_hint" to "Контактілерді іздеу", "people" to "Адамдар", "chat" to "Чат", "channels" to "Арналар", "join" to "Қосылу", "leave_chat" to "Чаттан шығу", "file" to "Файл", "reply" to "Жауап беру", "copy" to "Көшіру", "delete_for_me" to "Мен үшін жою", "delete_for_all" to "Барлығы үшін жою",
        "save" to "Сақтау", "cancel" to "Болдырмау", "group" to "Топ", "channel" to "Арна", "title" to "Атауы", "description" to "Сипаттама", "members" to "Қатысушылар", "create" to "Жасау", "create_group" to "Топ жасау", "create_channel" to "Арна жасау", "system_updates" to "Жүйе жаңартулары", "download_update" to "Жаңартуды жүктеу",
        "users" to "Пайдаланушылар", "profile" to "Профиль", "save_profile" to "Профильді сақтау", "saving" to "Сақталуда…", "logout" to "Шығу", "language" to "Тіл", "no_chats" to "Әзірге чаттар жоқ", "search" to "Іздеу", "contacts" to "Контактілер", "news" to "Жаңалықтар", "updates" to "Жаңартулар", "settings" to "Баптаулар", "chats" to "Чаттар", "public_channel" to "Ашық арна", "typing" to "{names} жазып жатыр…", "edited" to "өзгертілген", "write_message" to "Хабарлама жазыңыз…", "online" to "желіде", "you_colon" to "Сіз: ",
        "invalid_email" to "Дұрыс электрондық пошта мекенжайын енгізіңіз", "nick_min" to "Лақап ат кемінде 3 таңбадан тұруы керек", "enter_name" to "Атыңызды енгізіңіз", "password_min" to "Құпиясөз кемінде 8 таңбадан тұруы керек", "password_mismatch" to "Құпиясөздер сәйкес келмейді", "enter_otp" to "Алты таңбалы кодты енгізіңіз",
        "emoji" to "Эмодзи", "stickers" to "Стикерлер", "sticker" to "Стикер", "my_gifs" to "Менің GIF-терім", "my_stickers" to "Менің стикерлерім", "create_with_oria" to "Oria арқылы жасау",
        "oria_media_hint" to "Чаттан шықпай жасап, бірден жіберіңіз", "no_media_yet" to "Әзірге бос — Oria арқылы біріншісін жасаңыз", "oria_sticker" to "Oria стикері",
        "oria_media_studio" to "Oria медиастудиясы", "oria_media_description" to "Қалаған нәтижені сипаттаңыз. Oria чатқа дайын GIF немесе стикер жасап, коллекцияңызға сақтайды.",
        "describe_media" to "Нәтижені сипаттаңыз", "gif_prompt_example" to "Мысалы: кішкентай күлгін зымыран қол бұлғап, жұлдыздарға ұшады, 2 секундтық үздіксіз цикл",
        "sticker_prompt_example" to "Мысалы: таңғалған күлгін мысық, анық жиек, мөлдір фон", "sticker_from_photo" to "Фотодан стикер жасау",
        "sticker_cleanup_note" to "Стикер үшін Oria фонды жоюды, мөлдірлікті, таза жиектерді және шаршы форматты сұрайды.", "generate" to "Жасау", "image_required" to "Суретті таңдаңыз", "image_too_large" to "Сурет 15 МБ-тан аспауы керек",
        "add_or_improve" to "Қосу / өңдеу", "no_media_upload" to "Әзірге бос — өз GIF немесе стикерлеріңізді қосыңыз", "oria_media_tools" to "Oria құралдары",
        "oria_media_nongenerative" to "Мұнда Oria медианы нөлден жасамайды. Ол тек сіздің GIF, видео және стикер суреттеріңізді дайындайды.", "add_ready_gif" to "Дайын GIF қосу", "video_to_gif" to "Видео → GIF", "choose_video" to "Видеоны таңдау",
        "remove_background" to "Фонды жою", "white_outline" to "Ақ жиек қосу", "choose_sticker_image" to "Сурет / стикер таңдау", "close" to "Жабу",
        "unread" to "Оқылмаған", "messages_one" to "хабарлама", "messages_few" to "хабарлама", "messages_many" to "хабарлама",
        "in_chats_one" to "{n} чатта", "in_chats_few" to "{n} чатта", "in_chats_many" to "{n} чатта",
        "in_groups_one" to "{n} топта", "in_groups_few" to "{n} топта", "in_groups_many" to "{n} топта",
        "in_channels_one" to "{n} арнада", "in_channels_few" to "{n} арнада", "in_channels_many" to "{n} арнада",
        "groups" to "Топтар", "write_action" to "Жазу", "who_search" to "Кімді іздейміз?", "oria" to "Ория", "nothing_found" to "Ештеңе табылмады", "found" to "табылды",
    )

    fun text(language: OrionLanguage, key: String): String = when (language) {
        OrionLanguage.ENGLISH -> en[key]
        OrionLanguage.RUSSIAN -> ru[key]
        OrionLanguage.UKRAINIAN -> uk[key]
        OrionLanguage.BELARUSIAN -> be[key]
        OrionLanguage.KAZAKH -> kk[key]
    } ?: en[key] ?: key
}

// The redesign is drawn entirely in Manrope. Weights follow the canvas: 800 for anything that
// reads as a heading or a pill, 700 for titles, 600/500 for body copy.
private val OrionTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp),
    displayMedium = Typography().displayMedium.copy(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.0).sp),
    displaySmall = Typography().displaySmall.copy(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.7).sp),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.6).sp),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.4).sp),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.2).sp),
    titleLarge = Typography().titleLarge.copy(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold),
    titleMedium = Typography().titleMedium.copy(fontFamily = OrionFont, fontWeight = FontWeight.Bold),
    titleSmall = Typography().titleSmall.copy(fontFamily = OrionFont, fontWeight = FontWeight.Bold),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = OrionFont, fontWeight = FontWeight.Medium),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = OrionFont, fontWeight = FontWeight.Medium),
    bodySmall = Typography().bodySmall.copy(fontFamily = OrionFont, fontWeight = FontWeight.Medium),
    labelLarge = Typography().labelLarge.copy(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold),
    labelMedium = Typography().labelMedium.copy(fontFamily = OrionFont, fontWeight = FontWeight.Bold),
    labelSmall = Typography().labelSmall.copy(fontFamily = OrionFont, fontWeight = FontWeight.Bold)
)

private val OrionBg = Color(0xFF0B0C12)
private val OrionPanel = Color(0xFF191920)
private val OrionPurple = XAccent
private val OrionBlue = XAccentSoft
private val OrionCyan = XAccentSoft
private val OrionGlow = XAccentSoft
private val OrionText = Color(0xFFF2F1F5)
private val OrionMuted = Color(0xFF948EA1)
private val OrionBorder = Color(0x17FFFFFF)

@Composable
private fun OrionXNeonTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = OrionPurple,
        secondary = OrionCyan,
        tertiary = OrionBlue,
        background = OrionBg,
        surface = OrionPanel,
        surfaceVariant = Color(0xFF26252D),
        onPrimary = Color.White,
        onBackground = OrionText,
        onSurface = OrionText,
        outline = OrionBorder,
        error = XRed
    )
    MaterialTheme(colorScheme = scheme, typography = OrionTypography, content = content)
}

@Composable
private fun NeonBackground(content: @Composable BoxScope.() -> Unit) {
    // The redesign replaces the neon blobs with one full-height gradient (`--scr`), which the
    // auth flow and the signed-in shell both sit on.
    val brush = xScreenBrush()
    Box(Modifier.fillMaxSize().background(brush)) {
        content()
    }
}

@Composable
private fun NeonGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.shadow(18.dp, shape, ambientColor = OrionPurple.copy(alpha=.16f), spotColor = OrionBlue.copy(alpha=.12f)),
        shape = shape,
        color = OrionPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, OrionBorder),
        tonalElevation = 0.dp
    ) { Column(content = content) }
}

@Composable
private fun neonTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OrionCyan,
    unfocusedBorderColor = OrionBorder,
    focusedContainerColor = Color(0xAA11101D),
    unfocusedContainerColor = Color(0x8811101D),
    focusedLabelColor = OrionCyan,
    cursorColor = OrionCyan,
    focusedTextColor = OrionText,
    unfocusedTextColor = OrionText
)

@Composable
private fun ConversationCard(c: CloudConversation, onClick: () -> Unit) {
    NeonGlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(19.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            OrionAvatar(c.title)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, color = OrionText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.description.ifBlank { when(c.type){"group"->tr("orionx_group");"channel"->tr("orionx_channel");else->tr("direct_chat")} }, color = OrionMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.MoreHoriz, null, tint = OrionMuted)
        }
    }
}

@Composable
private fun OrionAvatar(name: String) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "OX" }
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF4317D3), OrionPurple))).border(1.dp, Color(0xFF8F75FF), CircleShape).shadow(14.dp, CircleShape, ambientColor = OrionPurple, spotColor = OrionPurple),
        contentAlignment = Alignment.Center
    ) { Text(initials, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp) }
}

@Composable
private fun OrionHeroEmptyState(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(164.dp).background(Brush.radialGradient(listOf(Color(0x493F1BCB), Color.Transparent)), CircleShape))
                Box(Modifier.size(108.dp).clip(CircleShape).background(Color(0xFF0B0C1A)).border(5.dp, Color(0xFF6B32FF), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF4521B5), Color(0xFF131329)))), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color(0xFFD9CEFF), modifier = Modifier.size(52.dp))
                    }
                }
            }
            Text(title, color = OrionText, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(subtitle, color = Color(0xFF8B879B), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        }
    }
}

@Composable
private fun BoxScope.NeonEmptyState(title: String, subtitle: String) {
    OrionHeroEmptyState(title, subtitle, Icons.Default.AutoAwesome, Modifier.align(Alignment.Center).padding(24.dp))
}
