package net.orionx.messenger

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.CloudSession
import net.orionx.messenger.cloud.SupabaseApi

/**
 * Auth-флоу по макету «OrionX Redesign»: тёмная пилюля --txt как главная кнопка (58dp, r999),
 * поля-карточки --sf радиусом 26, заголовки 800 26-34px с отрицательным трекингом,
 * контурные пилюли inset-рамкой --hairB. Логика шагов та же, что и раньше: логин по
 * почте+паролю, регистрация с кодом из письма, профиль, аватар и «готово».
 */

private enum class AuthPage { WELCOME, PICK, LOGIN, REGISTER_CONTACT, OTP, REGISTER_PROFILE, AVATAR, DONE }

@Composable
internal fun UnifiedAuthScreen(api: SupabaseApi, onAuth: (CloudSession, Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // «приветствие» показывается один раз: дальше экран выбора входа.
    val authPrefs = remember { context.getSharedPreferences("orionx_auth_prefs", android.content.Context.MODE_PRIVATE) }
    // Всё состояние шагов — saveable: поворот экрана или смерть процесса не должны
    // терять уже созданную на сервере сессию и введённые данные.
    // Debug-only: pref «debug_auth_page» открывает нужный шаг сразу (скриншоты/qa).
    val debugStartPage = if (net.orionx.messenger.BuildConfig.DEBUG) {
        authPrefs.getString("debug_auth_page", null)
    } else null
    var pageName by rememberSaveable {
        mutableStateOf(
            debugStartPage ?: if (authPrefs.getBoolean("welcome_seen", false)) AuthPage.PICK.name else AuthPage.WELCOME.name
        )
    }
    fun currentPage(): AuthPage = AuthPage.entries.firstOrNull { it.name == pageName } ?: AuthPage.PICK
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var pendingAccessToken by rememberSaveable { mutableStateOf("") }
    var pendingRefreshToken by rememberSaveable { mutableStateOf("") }
    var pendingUserId by rememberSaveable { mutableStateOf("") }
    var pendingUserEmail by rememberSaveable { mutableStateOf("") }
    fun pendingSession(): CloudSession? = pendingAccessToken.takeIf { it.isNotBlank() }?.let {
        CloudSession(it, pendingRefreshToken, pendingUserId, pendingUserEmail)
    }
    var rememberMe by rememberSaveable { mutableStateOf(true) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableIntStateOf(0) }
    // Шаг «аватар»: загруженная картинка или выбранный градиент («orionx-gradient:N»).
    var avatarUrl by rememberSaveable { mutableStateOf("") }
    val lastEmail by remember { mutableStateOf(authPrefs.getString("last_email", null)) }
    val scope = rememberCoroutineScope()

    /** Сохраняет сессию регистрации так, чтобы она пережила пересоздание композиции. */
    fun keepSession(session: CloudSession) {
        pendingAccessToken = session.accessToken
        pendingRefreshToken = session.refreshToken
        pendingUserId = session.userId
        pendingUserEmail = session.email
    }

    LaunchedEffect(resendIn) {
        if (resendIn > 0) {
            delay(1_000)
            resendIn--
        }
    }

    fun resetMessage() { message = null }
    fun go(next: AuthPage) { resetMessage(); pageName = next.name }

    AuthCosmicBackground {
        when (currentPage()) {
            AuthPage.WELCOME -> AuthWelcomePage(
                onContinue = {
                    authPrefs.edit().putBoolean("welcome_seen", true).apply()
                    go(AuthPage.PICK)
                },
            )
            AuthPage.PICK -> AuthPickPage(
                lastEmail = lastEmail,
                busy = busy,
                message = message,
                onBack = { go(AuthPage.WELCOME) },
                onQuickLogin = { email = lastEmail.orEmpty(); go(AuthPage.LOGIN) },
                onEmailLogin = { go(AuthPage.LOGIN) },
                onCreate = { go(AuthPage.REGISTER_CONTACT) },
            )
            AuthPage.LOGIN -> AuthLoginPage(
                email = email,
                password = password,
                rememberMe = rememberMe,
                busy = busy,
                message = message,
                onEmail = { email = it },
                onPassword = { password = it },
                onRemember = { rememberMe = it },
                onBack = { go(AuthPage.PICK) },
                onForgot = {
                    if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        message = "Сначала введите корректную почту"
                    } else {
                        busy = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { api.requestPasswordReset(email) } }
                                .onSuccess { message = "Письмо для восстановления отправлено" }
                                .onFailure { message = it.authError() }
                            busy = false
                        }
                    }
                },
                onCreate = { go(AuthPage.REGISTER_CONTACT) },
                onLogin = {
                    if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() || password.isBlank()) {
                        message = "Введите почту и пароль"
                    } else {
                        busy = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { api.signIn(email, password) } }
                                .onSuccess {
                                    authPrefs.edit().putString("last_email", email.trim().lowercase()).apply()
                                    onAuth(it, rememberMe)
                                }
                                .onFailure { message = it.authError() }
                            busy = false
                        }
                    }
                },
            )
            AuthPage.REGISTER_CONTACT -> AuthRegisterContactPage(
                email = email,
                password = password,
                confirmPassword = confirmPassword,
                accepted = accepted,
                busy = busy,
                message = message,
                onEmail = { email = it },
                onPassword = { password = it },
                onConfirm = { confirmPassword = it },
                onAccepted = { accepted = it },
                onBack = { go(AuthPage.PICK) },
                onSubmit = {
                    when {
                        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> message = "Введите корректную почту"
                        password.length < 8 -> message = "Пароль должен содержать минимум 8 символов"
                        password != confirmPassword -> message = "Пароли не совпадают"
                        !accepted -> message = "Примите условия использования"
                        else -> {
                            busy = true
                            val local = suggestedNickname(email)
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { api.signUp(email, password, local, local) } }
                                    .onSuccess { session ->
                                        if (session != null) {
                                            keepSession(session)
                                            if (nickname.isBlank()) nickname = local
                                            if (name.isBlank()) name = local.replaceFirstChar { it.uppercase() }
                                            go(AuthPage.REGISTER_PROFILE)
                                        } else {
                                            resendIn = 60
                                            go(AuthPage.OTP)
                                        }
                                    }
                                    .onFailure { message = it.authError() }
                                busy = false
                            }
                        }
                    }
                },
            )
            AuthPage.OTP -> AuthOtpPage(
                email = email,
                code = otp,
                resendIn = resendIn,
                busy = busy,
                message = message,
                onCode = { otp = it.filter(Char::isDigit).take(6) },
                onBack = { go(AuthPage.REGISTER_CONTACT) },
                onResend = {
                    if (resendIn == 0 && !busy) {
                        busy = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { api.resendSignupOtp(email) } }
                                .onSuccess { resendIn = 60; message = "Новый код отправлен" }
                                .onFailure { message = it.authError() }
                            busy = false
                        }
                    }
                },
                onVerify = {
                    if (otp.length != 6) {
                        message = "Введите шестизначный код"
                    } else {
                        busy = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { api.verifySignupOtp(email, otp) } }
                                .onSuccess { session ->
                                    keepSession(session)
                                    // Повторная верификация не должна затирать уже введённые данные.
                                    val local = suggestedNickname(email)
                                    if (nickname.isBlank()) nickname = local
                                    if (name.isBlank()) name = local.replaceFirstChar { it.uppercase() }
                                    go(AuthPage.REGISTER_PROFILE)
                                }
                                .onFailure { message = it.authError() }
                            busy = false
                        }
                    }
                },
            )
            AuthPage.REGISTER_PROFILE -> AuthProfilePage(
                name = name,
                nickname = nickname,
                busy = busy,
                message = message,
                onName = { name = it },
                onNickname = { nickname = it.filterNot(Char::isWhitespace).removePrefix("@") },
                onBack = null,
                onFinish = {
                    when {
                        name.trim().length < 2 -> message = "Введите имя"
                        nickname.trim().length < 3 -> message = "Ник должен содержать минимум 3 символа"
                        else -> go(AuthPage.AVATAR)
                    }
                },
            )
            AuthPage.AVATAR -> AuthAvatarPage(
                initial = name.ifBlank { nickname },
                avatarUrl = avatarUrl,
                busy = busy,
                message = message,
                onPickPhoto = { uri ->
                    busy = true
                    val session = pendingSession()
                    if (session == null) {
                        message = "Сеанс регистрации потерян. Повторите вход."
                        busy = false
                    } else scope.launch {
                        runCatching {
                            val bytes = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    ?: error("Не удалось прочитать файл")
                            }
                            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                            withContext(Dispatchers.IO) { api.uploadProfileAvatar(session.accessToken, session.userId, mime, bytes) }
                        }
                            .onSuccess { avatarUrl = it; message = null }
                            .onFailure { message = it.message ?: "Не удалось загрузить фото" }
                        busy = false
                    }
                },
                onPickGradient = { avatarUrl = "orionx-gradient:$it" },
                onDone = {
                    val session = pendingSession()
                    when {
                        session == null -> message = "Сеанс регистрации потерян. Повторите вход."
                        else -> {
                            busy = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        api.updateExtendedProfile(
                                            session.accessToken,
                                            session.userId,
                                            nickname,
                                            name,
                                            bio = "", phone = "", birthday = "", location = "", website = "",
                                            avatarUrl = avatarUrl,
                                        )
                                    }
                                }
                                    .onSuccess { go(AuthPage.DONE) }
                                    .onFailure { message = it.authError() }
                                busy = false
                            }
                        }
                    }
                },
                onSkip = { go(AuthPage.DONE) },
                onBack = { go(AuthPage.REGISTER_PROFILE) },
            )
            AuthPage.DONE -> AuthDonePage(
                avatarUrl = avatarUrl,
                name = name.ifBlank { nickname },
                nickname = nickname,
                email = pendingSession()?.email.orEmpty(),
                busy = busy,
                onStart = {
                    val session = pendingSession()
                    if (session == null) {
                        message = "Сеанс регистрации потерян. Повторите вход."
                        go(AuthPage.PICK)
                    } else {
                        session.email.takeIf { it.isNotBlank() }?.let {
                            authPrefs.edit().putString("last_email", it.lowercase()).apply()
                        }
                        onAuth(session, rememberMe)
                    }
                },
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Общий каркас страницы: круглая стрелка назад (44dp) слева, справа — */
/* заголовок шага или полоса прогресса; дальше контент на градиенте.   */
/* ------------------------------------------------------------------ */

@Composable
private fun AuthPageFrame(
    onBack: (() -> Unit)? = null,
    headerTitle: String? = null,
    progress: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        if (onBack != null || headerTitle != null || progress != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .xShadow(CircleShape)
                            .clip(CircleShape)
                            .background(XSurface)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = XText, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                if (headerTitle != null) {
                    Text(
                        headerTitle.lowercase(),
                        style = XType.rowTitle.copy(fontSize = 15.sp, letterSpacing = (-.01).em),
                        color = XText,
                    )
                }
                if (progress != null) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(XHairline),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(XText),
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AuthHeading(title: String, subtitle: String) {
    Text(
        title,
        style = XType.screenTitle.copy(fontSize = 26.sp, letterSpacing = (-.03).em, lineHeight = 30.sp),
        color = XText,
    )
    Spacer(Modifier.height(11.dp))
    Text(
        subtitle,
        style = XType.bodyStrong.copy(fontSize = 13.sp, lineHeight = 19.sp),
        color = XMuted,
    )
    Spacer(Modifier.height(22.dp))
}

/** Поле ввода: карточка --sf радиусом 26, высота 58, иконка слева, 700 15px. */
@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    var showPassword by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .xShadow(RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(XSurface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = XMuted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = XType.rowTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = XMuted2,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = XType.rowTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = XText),
                cursorBrush = Brush.verticalGradient(listOf(XAccent, XAccent)),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = if (password && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (password) {
            Icon(
                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                null,
                tint = XMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { showPassword = !showPassword },
            )
        }
    }
}

/** Главная кнопка макета: тёмная пилюля --txt, 58dp, r999, текст --inv. */
@Composable
private fun AuthPrimary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) XText else XSurfaceRaised)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            style = XType.pill.copy(letterSpacing = .16.em),
            color = if (enabled) XInverse else XMuted2,
            maxLines = 1,
        )
    }
}

/** Контурная пилюля макета: прозрачная, inset-рамка --hairB. */
@Composable
private fun AuthOutlinedPill(
    label: String,
    icon: ImageVector? = null,
    height: androidx.compose.ui.unit.Dp = 50.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .border(1.5.dp, XHairline, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = XText, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            label.uppercase(),
            style = XType.pillSmall.copy(fontSize = 11.sp, letterSpacing = .12.em),
            color = XText,
            maxLines = 1,
        )
    }
}

/** Строка ошибки/уведомления: иконка + 700 11.5px, слева, как в макете. */
@Composable
private fun AuthMessage(message: String?) {
    if (message.isNullOrBlank()) return
    Spacer(Modifier.height(12.dp))
    val positive = message.contains("отправлено", true) || message.contains("сохран", true)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            if (positive) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            null,
            tint = if (positive) XGreen else XRed,
            modifier = Modifier.size(15.dp),
        )
        Text(
            message,
            style = XType.bodyStrong.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp),
            color = if (positive) XGreen else XRed,
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun AuthCheckboxRow(checked: Boolean, text: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (checked) XText else Color.Transparent)
                .border(1.4.dp, if (checked) XText else XMuted2, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Outlined.Check, null, tint = XInverse, modifier = Modifier.size(14.dp))
        }
        Text(
            text,
            style = XType.meta.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
            color = XMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Шесть ячеек кода: скрытый input поверх карточек 62dp r20 с ring-подсветкой. */
@Composable
private fun AuthOtpCells(code: String, onCode: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(code.length) { if (code.length == 6) keyboard?.hide() }

    BasicTextField(
        value = code,
        onValueChange = { onCode(it.filter(Char::isDigit).take(6)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = Color.Transparent),
        cursorBrush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { index ->
                        val digit = code.getOrNull(index)?.toString().orEmpty()
                        val active = focused && index == code.length.coerceAtMost(5)
                        val highlighted = digit.isNotEmpty() || active
                        // Появление цифры: небольшой scale-«пружинка», как transform:scale в макете.
                        val scale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (digit.isEmpty()) .6f else 1f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                            ),
                            label = "otpDigit",
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(62.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(XSurface)
                                .border(
                                    if (highlighted) 1.6.dp else 1.dp,
                                    if (highlighted) XText else XHairline,
                                    RoundedCornerShape(20.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                digit,
                                style = XType.rowTitle.copy(fontSize = 23.sp, fontWeight = FontWeight.ExtraBold),
                                color = XText,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                            )
                        }
                    }
                }
                Box(Modifier.matchParentSize().alpha(.01f)) { inner() }
            }
        },
    )
}

@Composable
private fun AuthLogo(diameter: androidx.compose.ui.unit.Dp) {
    // Тема на auth-экранах берётся из того же pref, что и в MainActivity.onCreate:
    // «система» следует за ОС. Тёмная тема показывает лого на светлом фото,
    // светлая — на тёмном.
    val context = androidx.compose.ui.platform.LocalContext.current
    val dark = remember {
        val saved = context.getSharedPreferences("orionx_redesign_settings", android.content.Context.MODE_PRIVATE)
            .getString("appearance_theme", "dark") ?: "dark"
        when (saved) {
            "light" -> false
            "dark" -> true
            else -> context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(
            if (dark) net.orionx.messenger.R.drawable.orionx_logo_light
            else net.orionx.messenger.R.drawable.orionx_logo_dark
        ),
        contentDescription = "OrionX",
        modifier = Modifier
            .size(diameter)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .xShadow(androidx.compose.foundation.shape.CircleShape),
    )
}

@Composable
private fun AuthCosmicBackground(content: @Composable BoxScope.() -> Unit) {
    // Same `--scr` gradient as the signed-in shell, so sign-in and the app are one surface.
    // The app draws behind the system bars, so the auth pages take the inset themselves.
    Box(Modifier.fillMaxSize().background(xScreenBrush())) {
        Box(Modifier.fillMaxSize().systemBarsPadding(), content = content)
    }
}

private fun suggestedNickname(email: String): String {
    val base = email.substringBefore('@')
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '_' }
        .trim('_')
        .take(15)
        .ifBlank { "orionuser" }
    val suffix = (email.trim().lowercase().hashCode().toUInt() % 10_000u).toString().padStart(4, '0')
    return "${base}_$suffix"
}

private fun maskEmail(email: String): String {
    val local = email.substringBefore('@')
    val domain = email.substringAfter('@', "")
    return if (domain.isEmpty() || domain == email) email else "${local.take(1)}***@$domain"
}

private fun Throwable.authError(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("Invalid login credentials", true) -> "Неверная почта или пароль"
        raw.contains("Email not confirmed", true) -> "Почта ещё не подтверждена"
        raw.contains("already registered", true) -> "Аккаунт с этой почтой уже существует"
        raw.contains("rate", true) || raw.contains("security purposes", true) -> "Слишком много запросов. Повторите позже"
        raw.isBlank() -> localizedNetworkError(this)
        else -> localizedNetworkError(this)
    }
}

/* ------------------------------------------------------------------ */
/* Страницы                                                            */
/* ------------------------------------------------------------------ */

/** «приветствие»: пилюля-логотип, лого, крупный слоган, чипы, язык, «продолжить». */
@Composable
private fun AuthWelcomePage(onContinue: () -> Unit) {
    val language = LocalOrionLanguage.current
    val setLanguage = LocalOrionLanguageSetter.current
    AuthPageFrame {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Пилюля «orionx»: фон --txt, текст --inv, 800 11px / .22em.
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(XText)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                Text(
                    "orionx".uppercase(),
                    style = XType.sectionLabel.copy(fontSize = 11.sp, letterSpacing = .22.em),
                    color = XInverse,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.5.dp, XHairline, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    net.orionx.messenger.BuildConfig.VERSION_NAME,
                    style = XType.pillSmall.copy(fontSize = 9.5.sp, letterSpacing = .1.em),
                    color = XMuted,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AuthLogo(76.dp) }
        Spacer(Modifier.height(18.dp))
        Text(
            "переписки,\nканалы\nи звонки —\nв одном месте",
            style = XType.metric.copy(fontSize = 34.sp, lineHeight = 35.4.sp, letterSpacing = (-.04).em),
            color = XText,
        )
        Spacer(Modifier.height(20.dp))
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AuthFeatureChip(Icons.Outlined.Lock, "сквозное шифрование")
            AuthFeatureChip(Icons.Outlined.Bolt, "без рекламы")
            AuthFeatureChip(Icons.Outlined.Devices, "все устройства")
        }
        Spacer(Modifier.weight(1f))
        Text(
            "язык интерфейса".uppercase(),
            style = XType.pillSmall.copy(fontSize = 9.5.sp, letterSpacing = .18.em),
            color = XMuted2,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OrionLanguage.entries.forEach { lang ->
                val active = lang == language
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .then(if (active) Modifier.xShadow(RoundedCornerShape(20.dp)) else Modifier)
                        .background(if (active) XText else Color.Transparent)
                        .clickable { setLanguage(lang) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        lang.code.uppercase(),
                        style = XType.rowTitle.copy(fontSize = 12.sp, letterSpacing = .06.em),
                        color = if (active) XInverse else XMuted,
                        maxLines = 1,
                    )
                    Text(
                        lang.nativeName,
                        style = XType.meta.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = if (active) XOnMuted else XMuted2,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        AuthPrimary("продолжить", onClick = onContinue)
        Spacer(Modifier.height(14.dp))
        Text(
            "продолжая, вы принимаете условия использования и политику приватности",
            style = XType.body.copy(fontSize = 10.5.sp, lineHeight = 15.5.sp),
            color = XMuted2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun AuthFeatureChip(icon: ImageVector, label: String) {
    Row(
        Modifier
            .xShadow(RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .background(XSurface)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = XText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label.uppercase(),
            style = XType.pillSmall.copy(fontSize = 9.5.sp, letterSpacing = .1.em),
            color = XText,
            maxLines = 1,
        )
    }
}

/** «вход или регистрация»: быстрый вход по последней почте, почта, создание аккаунта. */
@Composable
private fun AuthPickPage(
    lastEmail: String?,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onQuickLogin: () -> Unit,
    onEmailLogin: () -> Unit,
    onCreate: () -> Unit,
) {
    AuthPageFrame(onBack = onBack, headerTitle = "вход в orionx") {
        Text(
            "рады видеть.\nпродолжим?",
            style = XType.screenTitle.copy(fontSize = 27.sp, letterSpacing = (-.035).em, lineHeight = 29.7.sp),
            color = XText,
        )
        Spacer(Modifier.height(11.dp))
        Text(
            "один аккаунт — все устройства. переписки подтянутся сами",
            style = XType.bodyStrong.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
            color = XMuted,
        )
        if (!lastEmail.isNullOrBlank()) {
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .xShadow(RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(XSurface)
                    .clickable(onClick = onQuickLogin)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                XLetterAvatar(lastEmail.substringBefore('@').take(1).uppercase(), 46.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "@${lastEmail.substringBefore('@')}",
                            style = XType.rowTitle.copy(fontSize = 14.sp, lineHeight = 16.8.sp),
                            color = XText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(XSurfaceRaised)
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "недавно".uppercase(),
                                style = XType.pillSmall.copy(fontSize = 8.sp, letterSpacing = .1.em),
                                color = XMuted,
                            )
                        }
                    }
                    Text(
                        lastEmail,
                        style = XType.bodyStrong.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                        color = XMuted2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // Компактная пилюля быстрого входа внутри карточки.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(XText)
                        .clickable(onClick = onQuickLogin)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "войти".uppercase(),
                        style = XType.pillSmall.copy(fontSize = 9.5.sp, letterSpacing = .12.em),
                        color = XInverse,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(XHairline))
                Text(
                    tr("or").uppercase(),
                    style = XType.pillSmall.copy(fontSize = 9.sp, letterSpacing = .18.em),
                    color = XMuted3,
                )
                Box(Modifier.weight(1f).height(1.dp).background(XHairline))
            }
            Spacer(Modifier.height(20.dp))
        }
        AuthPrimary("войти по почте", enabled = !busy, onClick = onEmailLogin)
        AuthMessage(message)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(999.dp))
                .border(1.5.dp, XHairline, RoundedCornerShape(999.dp))
                .clickable(onClick = onCreate),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("нет аккаунта?", style = XType.bodyStrong.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold), color = XMuted)
            Spacer(Modifier.width(7.dp))
            Text(
                "создать",
                style = XType.bodyStrong.copy(fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold),
                color = XText,
            )
        }
    }
}

/** «почта»: почта + пароль, полоса прогресса вместо подписи в шапке. */
@Composable
private fun AuthLoginPage(
    email: String,
    password: String,
    rememberMe: Boolean,
    busy: Boolean,
    message: String?,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onRemember: (Boolean) -> Unit,
    onBack: () -> Unit,
    onForgot: () -> Unit,
    onCreate: () -> Unit,
    onLogin: () -> Unit,
) {
    AuthPageFrame(onBack = onBack, progress = .25f) {
        AuthHeading("вход по почте", "введите почту и пароль — переписки подтянутся сами")
        AuthField(email, onEmail, "you@orionx.me", Icons.Outlined.AlternateEmail, KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField(password, onPassword, "пароль", Icons.Outlined.Lock, KeyboardType.Password, password = true)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { onRemember(!rememberMe) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (rememberMe) XText else Color.Transparent)
                        .border(1.4.dp, if (rememberMe) XText else XMuted2, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (rememberMe) Icon(Icons.Outlined.Check, null, tint = XInverse, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.width(7.dp))
                Text("запомнить меня", style = XType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = XMuted)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "забыли пароль?",
                style = XType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = XMuted2,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onForgot)
                    .padding(4.dp),
            )
        }
        AuthMessage(message)
        Spacer(Modifier.weight(1f))
        AuthPrimary("войти", enabled = !busy, onClick = onLogin)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
            Text("нет аккаунта? ", style = XType.bodyStrong.copy(fontSize = 12.sp), color = XMuted)
            Text(
                "создать",
                style = XType.bodyStrong.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                color = XText,
                modifier = Modifier.clickable(onClick = onCreate),
            )
        }
    }
}

/** «почта» регистрации: почта + пароль + подтверждение, полоса прогресса. */
@Composable
private fun AuthRegisterContactPage(
    email: String,
    password: String,
    confirmPassword: String,
    accepted: Boolean,
    busy: Boolean,
    message: String?,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onAccepted: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    AuthPageFrame(onBack = onBack, progress = .4f) {
        AuthHeading("создадим аккаунт", "пришлём код из шести цифр на эту почту")
        AuthField(email, onEmail, "you@orionx.me", Icons.Outlined.AlternateEmail, KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField(password, onPassword, "минимум 8 символов", Icons.Outlined.Lock, KeyboardType.Password, password = true)
        Spacer(Modifier.height(12.dp))
        AuthField(confirmPassword, onConfirm, "повторите пароль", Icons.Outlined.Lock, KeyboardType.Password, password = true)
        Spacer(Modifier.height(14.dp))
        AuthCheckboxRow(
            checked = accepted,
            text = "я согласен с условиями использования и политикой конфиденциальности",
            onToggle = { onAccepted(!accepted) },
        )
        AuthMessage(message)
        Spacer(Modifier.weight(1f))
        AuthPrimary("получить код", enabled = !busy, onClick = onSubmit)
    }
}

/** «код из письма»: 6 ячеек, повторная отправка с таймером, «изменить почту». */
@Composable
private fun AuthOtpPage(
    email: String,
    code: String,
    resendIn: Int,
    busy: Boolean,
    message: String?,
    onCode: (String) -> Unit,
    onBack: () -> Unit,
    onResend: () -> Unit,
    onVerify: () -> Unit,
) {
    AuthPageFrame(onBack = onBack, progress = .55f) {
        AuthHeading("код из письма", "отправили на ${maskEmail(email)}")
        AuthOtpCells(code, onCode)
        AuthMessage(message)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(999.dp))
                .border(1.5.dp, if (resendIn > 0) XHairline else XMuted2, RoundedCornerShape(999.dp))
                .clickable(enabled = resendIn == 0 && !busy, onClick = onResend),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (resendIn > 0) Icons.Outlined.Schedule else Icons.Outlined.Refresh,
                null,
                tint = if (resendIn > 0) XMuted2 else XText,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (resendIn > 0) "снова через ${resendIn / 60}:${(resendIn % 60).toString().padStart(2, '0')}" else "отправить код снова",
                style = XType.pillSmall.copy(fontSize = 11.5.sp, letterSpacing = .1.em),
                color = if (resendIn > 0) XMuted2 else XText,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "изменить почту",
            style = XType.bodyStrong.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = XMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy, onClick = onBack)
                .padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        AuthPrimary("продолжить", enabled = !busy && code.length == 6, onClick = onVerify)
    }
}

/** «никнейм»: имя и @никнейм с подсветкой валидности. */
@Composable
private fun AuthProfilePage(
    name: String,
    nickname: String,
    busy: Boolean,
    message: String?,
    onName: (String) -> Unit,
    onNickname: (String) -> Unit,
    onBack: (() -> Unit)?,
    onFinish: () -> Unit,
) {
    AuthPageFrame(onBack = onBack, progress = .7f) {
        AuthHeading("выберите @никнейм", "по нему вас найдут в поиске. можно поменять позже")
        AuthField(name, onName, "как вас зовут", Icons.Outlined.Badge)
        Spacer(Modifier.height(12.dp))
        // Карточка @никнейма: префикс 800 15px, справа — иконка статуса.
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .xShadow(RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(XSurface)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("@", style = XType.rowTitle.copy(fontSize = 15.sp), color = XMuted)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (nickname.isEmpty()) {
                    Text(
                        "onyx",
                        style = XType.rowTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = XMuted2,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = nickname,
                    onValueChange = onNickname,
                    singleLine = true,
                    textStyle = XType.rowTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = XText),
                    cursorBrush = Brush.verticalGradient(listOf(XAccent, XAccent)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (nickname.length >= 3) {
                Icon(Icons.Outlined.Check, null, tint = XGreen, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            when {
                nickname.isBlank() -> "минимум 3 символа"
                nickname.length >= 3 -> "так вас найдут в поиске"
                else -> "минимум 3 символа"
            },
            style = XType.bodyStrong.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp),
            color = when {
                nickname.isBlank() -> XMuted2
                nickname.length >= 3 -> XGreen
                else -> XRed
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp),
        )
        AuthMessage(message)
        Spacer(Modifier.weight(1f))
        AuthPrimary("дальше", enabled = !busy, onClick = onFinish)
    }
}

/**
 * «аватар»: фото или один из градиентов макета. Градиент хранится в avatar_url как
 * `orionx-gradient:N`, поэтому его видят и другие клиенты.
 */
@Composable
private fun AuthAvatarPage(
    initial: String,
    avatarUrl: String,
    busy: Boolean,
    message: String?,
    onPickPhoto: (android.net.Uri) -> Unit,
    onPickGradient: (Int) -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) onPickPhoto(uri) }
    val selectedGradient = avatarUrl.takeIf { it.startsWith("orionx-gradient:") }?.removePrefix("orionx-gradient:")?.toIntOrNull()
    AuthPageFrame(onBack = onBack, progress = .85f) {
        AuthHeading("поставьте аватар", "выберите градиент или загрузите фото")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(132.dp)
                    .xShadow(CircleShape, elevation = 22.dp, shadowColor = XShadowStrong)
                    .clip(CircleShape)
                    .background(
                        when {
                            avatarUrl.startsWith("orionx-gradient:") -> xAvatarGradientBrush(selectedGradient ?: 0)
                            else -> XAvatarBrush
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initial.trim().take(1).ifBlank { "?" }.uppercase(),
                    style = XType.metric.copy(fontSize = 42.sp),
                    color = Color(0xFF101014),
                )
            }
            if (!avatarUrl.startsWith("orionx-gradient:") && avatarUrl.isNotBlank()) {
                RemoteImage(avatarUrl, Modifier.size(132.dp).clip(CircleShape), androidx.compose.ui.layout.ContentScale.Crop)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            xAvatarGradientBrushes.forEachIndexed { index, _ ->
                val selected = selectedGradient == index
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(xAvatarGradientBrush(index))
                        .border(
                            width = if (selected) 2.5.dp else 0.dp,
                            color = XText,
                            shape = CircleShape,
                        )
                        .clickable { onPickGradient(index) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        AuthOutlinedPill("загрузить фото", icon = Icons.Outlined.Image, enabled = !busy, onClick = { launcher.launch("image/*") })
        AuthMessage(message)
        Spacer(Modifier.weight(1f))
        AuthPrimary("дальше", enabled = !busy, onClick = onDone)
        Text(
            "пропустить",
            style = XType.bodyStrong.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = XMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy, onClick = onSkip)
                .padding(vertical = 8.dp),
        )
    }
}

/** «готово»: круглая галочка, карточка аккаунта и вход в приложение. */
@Composable
private fun AuthDonePage(
    avatarUrl: String,
    name: String,
    nickname: String,
    email: String,
    busy: Boolean,
    onStart: () -> Unit,
) {
    AuthPageFrame {
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(96.dp)
                    .xShadow(CircleShape, elevation = 22.dp, shadowColor = XShadowStrong)
                    .clip(CircleShape)
                    .background(XText),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, null, tint = XInverse, modifier = Modifier.size(46.dp))
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "аккаунт готов",
            style = XType.screenTitle.copy(fontSize = 26.sp, letterSpacing = (-.03).em, lineHeight = 29.6.sp),
            color = XText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "осталось написать первое сообщение — контакты уже подтянулись",
            style = XType.bodyStrong.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
            color = XMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(26.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .xShadow(RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(XSurface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (avatarUrl.isNotBlank() && !avatarUrl.startsWith("orionx-gradient:")) {
                RemoteImage(avatarUrl, Modifier.size(44.dp).clip(CircleShape), androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (avatarUrl.startsWith("orionx-gradient:")) xAvatarGradientBrush(
                                avatarUrl.removePrefix("orionx-gradient:").toIntOrNull() ?: 0
                            ) else XAvatarBrush,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.trim().take(1).ifBlank { "?" }.uppercase(),
                        style = XType.rowTitle.copy(fontSize = 16.sp),
                        color = Color(0xFF101014),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "@${nickname.ifBlank { name }}",
                    style = XType.rowTitle.copy(fontSize = 14.sp, lineHeight = 16.8.sp),
                    color = XText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    email,
                    style = XType.bodyStrong.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                    color = XMuted2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Icon(Icons.Outlined.Verified, null, tint = XGreen, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.weight(1f))
        AuthPrimary("начать общение", enabled = !busy, onClick = onStart)
    }
}
