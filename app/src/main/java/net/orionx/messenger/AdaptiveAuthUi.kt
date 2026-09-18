package net.orionx.messenger

import android.content.Context
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.CloudConversation
import net.orionx.messenger.cloud.CloudProfile
import net.orionx.messenger.cloud.CloudSession
import net.orionx.messenger.cloud.SupabaseApi
import java.util.Locale

private val AuthBg = Color(0xFF030512)
private val AuthCard = Color(0xFF090D20)
private val AuthCardSoft = Color(0xFF0E1329)
private val AuthFieldBg = Color(0xFF0D1226)
private val AuthFieldFocused = Color(0xFF111832)
private val AuthBorder = Color(0xFF343B65)
private val AuthPurple = Color(0xFF6F35FF)
private val AuthPurpleLight = Color(0xFFB991FF)
private val AuthText = Color(0xFFF7F5FF)
private val AuthMuted = Color(0xFF9599B2)
private val AuthGreen = Color(0xFF4DE0A0)

private data class AuthCopy(
    val loginTitle: String,
    val registerTitle: String,
    val welcome: String,
    val registerSubtitle: String,
    val tagline: String,
    val email: String,
    val nickname: String,
    val name: String,
    val password: String,
    val confirmPassword: String,
    val remember: String,
    val forgot: String,
    val signIn: String,
    val register: String,
    val createAccount: String,
    val already: String,
    val or: String,
    val terms: String,
    val agree: String,
    val wait: String,
    val invalidEmail: String,
    val nickShort: String,
    val nameShort: String,
    val passwordShort: String,
    val passwordMismatch: String,
    val acceptTerms: String,
    val enterCredentials: String,
    val resetSent: String,
    val otpTitle: String,
    val otpSubtitle: String,
    val change: String,
    val contact: String,
    val code: String,
    val profile: String,
    val codeValid: String,
    val resend: String,
    val continueText: String,
    val back: String,
    val securityTitle: String,
    val securitySubtitle: String,
    val enterOtp: String,
    val codeSent: String,
    val createGroup: String,
    val createChannel: String,
    val group: String,
    val channel: String,
    val title: String,
    val description: String,
    val members: String,
    val cancel: String,
    val create: String,
    val creating: String,
    val noPeople: String
)

@Composable
private fun rememberAuthCopy(): AuthCopy {
    val context = LocalContext.current
    val saved = remember {
        context.getSharedPreferences("orionx_language", Context.MODE_PRIVATE)
            .getString("language", null)
    }
    val code = (saved ?: Locale.getDefault().language).lowercase(Locale.ROOT)
    return when (code) {
        "en" -> AuthCopy(
            "Sign in", "Create account", "Welcome back!", "Fill in your registration details",
            "THE NEXT GENERATION OF MESSAGING", "Email", "Nickname", "Name", "Password",
            "Confirm password", "Remember me", "Forgot password?", "Sign in", "Register",
            "Create account", "Already have an account?", "or",
            "By continuing, you agree to the Terms of Use and Privacy Policy",
            "I agree to the Terms of Use and Privacy Policy", "Please wait…",
            "Enter a valid email", "Nickname must be at least 3 characters", "Enter your name",
            "Password must be at least 8 characters", "Passwords do not match", "Accept the terms",
            "Enter email and password", "Password reset email sent", "Email verification",
            "Enter the code sent to", "Change", "Contact", "Code", "Profile", "Code valid",
            "Resend", "Continue", "Back", "Never share this code",
            "Your account is protected with modern encryption", "Enter the six-digit code",
            "A new code has been sent", "Create group", "Create channel", "Group", "Channel",
            "Title", "Description", "Members", "Cancel", "Create", "Creating…", "No users found"
        )
        "uk" -> AuthCopy(
            "Вхід в акаунт", "Створення акаунта", "Раді бачити вас знову!", "Заповніть дані для реєстрації",
            "НОВЕ ПОКОЛІННЯ МЕСЕНДЖЕРА", "Пошта", "Нікнейм", "Ім’я", "Пароль", "Підтвердіть пароль",
            "Запам’ятати мене", "Забули пароль?", "Увійти", "Зареєструватися", "Створити акаунт",
            "Вже є акаунт?", "або", "Продовжуючи, ви погоджуєтесь з Умовами використання і Політикою конфіденційності",
            "Я погоджуюся з Умовами використання і Політикою конфіденційності", "Зачекайте…",
            "Введіть правильну адресу пошти", "Нікнейм має містити щонайменше 3 символи", "Введіть ім’я",
            "Пароль має містити щонайменше 8 символів", "Паролі не збігаються", "Прийміть умови",
            "Введіть пошту і пароль", "Лист для скидання пароля надіслано", "Підтвердження Email",
            "Введіть код, надісланий на", "Змінити", "Контакт", "Код", "Профіль", "Код дійсний",
            "Надіслати повторно", "Продовжити", "Назад", "Нікому не передавайте код",
            "Ваш акаунт захищено сучасним шифруванням", "Введіть шестизначний код",
            "Новий код надіслано", "Створити групу", "Створити канал", "Група", "Канал", "Назва",
            "Опис", "Учасники", "Скасувати", "Створити", "Створення…", "Користувачів не знайдено"
        )
        "be" -> AuthCopy(
            "Уваход у акаўнт", "Стварэнне акаўнта", "Сардэчна запрашаем!", "Запоўніце даныя для рэгістрацыі",
            "НОВАЕ ПАКАЛЕННЕ МЕСЕНДЖАРА", "Пошта", "Нікнэйм", "Імя", "Пароль", "Пацвердзіце пароль",
            "Запомніць мяне", "Забылі пароль?", "Увайсці", "Зарэгістравацца", "Стварыць акаўнт",
            "Ужо ёсць акаўнт?", "або", "Працягваючы, вы згаджаецеся з Умовамі выкарыстання і Палітыкай прыватнасці",
            "Я згаджаюся з Умовамі выкарыстання і Палітыкай прыватнасці", "Пачакайце…",
            "Увядзіце правільную пошту", "Нікнэйм павінен мець не менш за 3 сімвалы", "Увядзіце імя",
            "Пароль павінен мець не менш за 8 сімвалаў", "Паролі не супадаюць", "Прыміце ўмовы",
            "Увядзіце пошту і пароль", "Ліст для скіду пароля адпраўлены", "Пацвярджэнне Email",
            "Увядзіце код, адпраўлены на", "Змяніць", "Кантакт", "Код", "Профіль", "Код дзейнічае",
            "Адправіць паўторна", "Працягнуць", "Назад", "Нікому не перадавайце код",
            "Ваш акаўнт абаронены сучасным шыфраваннем", "Увядзіце шасцізначны код",
            "Новы код адпраўлены", "Стварыць групу", "Стварыць канал", "Група", "Канал", "Назва",
            "Апісанне", "Удзельнікі", "Скасаваць", "Стварыць", "Стварэнне…", "Карыстальнікі не знойдзены"
        )
        "kk" -> AuthCopy(
            "Тіркелгіге кіру", "Тіркелгі жасау", "Қайта қош келдіңіз!", "Тіркелу деректерін толтырыңыз",
            "МЕССЕНДЖЕРДІҢ ЖАҢА БУЫНЫ", "Электрондық пошта", "Лақап ат", "Аты", "Құпиясөз",
            "Құпиясөзді растаңыз", "Мені есте сақтау", "Құпиясөзді ұмыттыңыз ба?", "Кіру", "Тіркелу",
            "Тіркелгі жасау", "Тіркелгіңіз бар ма?", "немесе", "Жалғастыру арқылы пайдалану шарттары мен құпиялық саясатына келісесіз",
            "Пайдалану шарттары мен құпиялық саясатына келісемін", "Күте тұрыңыз…",
            "Дұрыс пошта мекенжайын енгізіңіз", "Лақап ат кемінде 3 таңбадан тұруы керек", "Атыңызды енгізіңіз",
            "Құпиясөз кемінде 8 таңбадан тұруы керек", "Құпиясөздер сәйкес келмейді", "Шарттарды қабылдаңыз",
            "Пошта мен құпиясөзді енгізіңіз", "Құпиясөзді қалпына келтіру хаты жіберілді", "Email растау",
            "Жіберілген кодты енгізіңіз", "Өзгерту", "Байланыс", "Код", "Профиль", "Код жарамды",
            "Қайта жіберу", "Жалғастыру", "Артқа", "Кодты ешкімге бермеңіз",
            "Тіркелгіңіз заманауи шифрлаумен қорғалған", "Алты таңбалы кодты енгізіңіз",
            "Жаңа код жіберілді", "Топ жасау", "Арна жасау", "Топ", "Арна", "Атауы",
            "Сипаттама", "Қатысушылар", "Болдырмау", "Жасау", "Жасалуда…", "Пайдаланушылар табылмады"
        )
        else -> AuthCopy(
            "Вход в аккаунт", "Создание аккаунта", "Добро пожаловать обратно!", "Заполните данные для регистрации",
            "НОВОЕ ПОКОЛЕНИЕ МЕССЕНДЖЕРА", "Почта", "Никнейм", "Имя", "Пароль", "Подтвердите пароль",
            "Запомнить меня", "Забыли пароль?", "Войти", "Зарегистрироваться", "Создать аккаунт",
            "Уже есть аккаунт?", "или", "Продолжая, вы соглашаетесь с Условиями использования и Политикой конфиденциальности",
            "Я согласен с Условиями использования и Политикой конфиденциальности", "Подождите…",
            "Введите корректную почту", "Никнейм должен содержать минимум 3 символа", "Введите имя",
            "Пароль должен содержать минимум 8 символов", "Пароли не совпадают", "Примите условия",
            "Введите почту и пароль", "Письмо для сброса пароля отправлено", "Подтверждение Email",
            "Введите код, отправленный на", "Изменить", "Контакт", "Код", "Профиль", "Код действителен",
            "Отправить повторно", "Продолжить", "Назад", "Код никому не передаётся",
            "Защищаем ваш аккаунт современным шифрованием", "Введите шестизначный код",
            "Новый код отправлен", "Создать группу", "Создать канал", "Группа", "Канал", "Название",
            "Описание", "Участники", "Отмена", "Создать", "Создание…", "Пользователи не найдены"
        )
    }
}

private fun Throwable.authFriendly(copy: AuthCopy): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("Email not confirmed", true) -> copy.enterOtp
        raw.contains("Invalid login credentials", true) -> "Неверная почта или пароль"
        raw.contains("User already registered", true) -> "Аккаунт с этой почтой уже существует"
        raw.contains("rate limit", true) || raw.contains("security purposes", true) -> "Слишком много запросов. Повторите позже"
        raw.contains("Password should be", true) -> copy.passwordShort
        raw.isBlank() -> "Не удалось выполнить запрос"
        else -> raw
    }
}

@Composable
internal fun AuthScreen(api: SupabaseApi, onAuth: (CloudSession, Boolean) -> Unit) {
    val copy = rememberAuthCopy()
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(resendIn) {
        if (resendIn > 0) {
            delay(1_000)
            resendIn--
        }
    }

    if (otpEmail != null) {
        AdaptiveOtpScreen(
            copy = copy,
            email = otpEmail.orEmpty(),
            code = otpCode,
            onCodeChange = { otpCode = it.filter(Char::isDigit).take(6) },
            busy = busy,
            message = message,
            resendIn = resendIn,
            onBack = { otpEmail = null; otpCode = ""; message = null },
            onVerify = {
                if (otpCode.length != 6) {
                    message = copy.enterOtp
                } else {
                    busy = true
                    message = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { api.verifySignupOtp(otpEmail.orEmpty(), otpCode) }
                        }.onSuccess { onAuth(it, rememberMe) }
                            .onFailure { message = it.authFriendly(copy) }
                        busy = false
                    }
                }
            },
            onResend = {
                if (resendIn == 0 && !busy) {
                    busy = true
                    message = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { api.resendSignupOtp(otpEmail.orEmpty()) }
                        }.onSuccess {
                            resendIn = 60
                            message = copy.codeSent
                        }.onFailure { message = it.authFriendly(copy) }
                        busy = false
                    }
                }
            }
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF02030C), Color(0xFF070A1B), Color(0xFF030512))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val compact = maxHeight < 760.dp
        val outerPadding = if (compact) 10.dp else 14.dp
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = outerPadding, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .shadow(22.dp, RoundedCornerShape(34.dp), ambientColor = Color(0x332B17B8), spotColor = Color(0x443F22E8)),
                shape = RoundedCornerShape(34.dp),
                color = AuthCard,
                border = BorderStroke(1.1.dp, Color(0xFF5A43B7))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = if (compact) 18.dp else 22.dp, vertical = if (compact) 14.dp else 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (register) {
                            CompactIconButton(Icons.Default.ArrowBack, copy.back) {
                                register = false
                                message = null
                            }
                        } else {
                            Spacer(Modifier.size(40.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x5513172E),
                            border = BorderStroke(1.dp, Color(0x443C3F68))
                        ) {
                            Text(
                                if (register) "CREATE ID" else "ORION ID",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = AuthMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                    StaticAuthLogo(if (register || compact) 68.dp else 78.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "OrionX 5",
                        color = Color.White,
                        fontSize = if (compact) 29.sp else 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        copy.tagline,
                        color = AuthMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .9.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
                    Text(
                        if (register) copy.registerTitle else copy.loginTitle,
                        color = AuthText,
                        fontSize = if (compact) 22.sp else 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (register) copy.registerSubtitle else copy.welcome,
                        color = AuthMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(if (compact) 12.dp else 16.dp))

                    CompactAuthField(
                        value = email,
                        onValueChange = { email = it },
                        label = copy.email,
                        placeholder = "name@example.com",
                        icon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email,
                        valid = register && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
                    )

                    if (register) {
                        Spacer(Modifier.height(8.dp))
                        CompactAuthField(
                            value = nick,
                            onValueChange = { nick = it.replace(" ", "") },
                            label = copy.nickname,
                            placeholder = "@nickname",
                            icon = Icons.Default.AlternateEmail,
                            valid = nick.trim().removePrefix("@").length >= 3
                        )
                        Spacer(Modifier.height(8.dp))
                        CompactAuthField(
                            value = name,
                            onValueChange = { name = it },
                            label = copy.name,
                            placeholder = copy.name,
                            icon = Icons.Default.PersonOutline,
                            valid = name.trim().length >= 2
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    CompactAuthField(
                        value = password,
                        onValueChange = { password = it },
                        label = copy.password,
                        placeholder = "••••••••",
                        icon = Icons.Default.Lock,
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        passwordVisible = showPassword,
                        onTogglePassword = { showPassword = !showPassword }
                    )

                    if (register) {
                        Spacer(Modifier.height(8.dp))
                        CompactAuthField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = copy.confirmPassword,
                            placeholder = "••••••••",
                            icon = Icons.Default.LockReset,
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                            passwordVisible = showConfirmPassword,
                            onTogglePassword = { showConfirmPassword = !showConfirmPassword },
                            valid = confirmPassword.isNotEmpty() && confirmPassword == password
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    if (register) {
                        CompactCheckRow(acceptedTerms, { acceptedTerms = it }, copy.agree)
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CompactCheckRow(rememberMe, { rememberMe = it }, copy.remember, Modifier.weight(1f))
                            Text(
                                copy.forgot,
                                color = AuthPurpleLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    if (email.isBlank()) {
                                        message = copy.invalidEmail
                                    } else {
                                        busy = true
                                        message = null
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { api.requestPasswordReset(email) } }
                                                .onSuccess { message = copy.resetSent }
                                                .onFailure { message = it.authFriendly(copy) }
                                            busy = false
                                        }
                                    }
                                }
                            )
                        }
                    }

                    AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
                        message?.let {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                CompactMessage(it, it == copy.resetSent || it == copy.codeSent)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    StaticGradientButton(
                        text = if (busy) copy.wait else if (register) copy.register else copy.signIn,
                        enabled = !busy,
                        onClick = {
                            message = null
                            if (register) {
                                message = when {
                                    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> copy.invalidEmail
                                    nick.trim().removePrefix("@").length < 3 -> copy.nickShort
                                    name.trim().length < 2 -> copy.nameShort
                                    password.length < 8 -> copy.passwordShort
                                    password != confirmPassword -> copy.passwordMismatch
                                    !acceptedTerms -> copy.acceptTerms
                                    else -> null
                                }
                                if (message != null) return@StaticGradientButton
                            } else if (email.isBlank() || password.isBlank()) {
                                message = copy.enterCredentials
                                return@StaticGradientButton
                            }

                            busy = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        if (register) api.signUp(email, password, nick, name)
                                        else api.signIn(email, password)
                                    }
                                }.onSuccess { session ->
                                    if (register && session == null) {
                                        otpEmail = email.trim().lowercase()
                                        otpCode = ""
                                        resendIn = 60
                                    } else if (session != null) {
                                        onAuth(session, rememberMe)
                                    }
                                }.onFailure { error ->
                                    message = error.authFriendly(copy)
                                    if (!register && error.message.orEmpty().contains("Email not confirmed", true) && email.isNotBlank()) {
                                        runCatching { withContext(Dispatchers.IO) { api.resendSignupOtp(email) } }
                                            .onSuccess {
                                                otpEmail = email.trim().lowercase()
                                                otpCode = ""
                                                resendIn = 60
                                                message = copy.codeSent
                                            }
                                    }
                                }
                                busy = false
                            }
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    if (!register) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f), color = Color(0x333B4262))
                            Text(copy.or, modifier = Modifier.padding(horizontal = 12.dp), color = AuthMuted, fontSize = 13.sp)
                            HorizontalDivider(Modifier.weight(1f), color = Color(0x333B4262))
                        }
                        Spacer(Modifier.height(9.dp))
                        OutlinedButton(
                            onClick = { register = true; message = null },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF6A3CCB)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuthText)
                        ) {
                            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(copy.createAccount, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(copy.terms, color = Color(0xFF737A97), fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(copy.already, color = AuthMuted, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                copy.signIn,
                                color = AuthPurpleLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { register = false; message = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticAuthLogo(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(24.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x775D27FF), spotColor = Color(0x88773DFF))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.radialGradient(listOf(Color.White, Color(0xFFB69BFF), Color(0xFF6C31FF), Color(0xFF241043))))
            .border(1.2.dp, Color(0xFFB89CFF), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(size * .48f))
    }
}

@Composable
private fun CompactIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(40.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = Color(0xFF11172C),
        border = BorderStroke(1.dp, AuthBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = AuthText, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CompactAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    valid: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> Color(0xFF8050FF)
        valid -> Color(0xFF2D8B6B)
        else -> AuthBorder
    }

    Surface(
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(17.dp),
        color = if (focused) AuthFieldFocused else AuthFieldBg,
        border = BorderStroke(if (focused) 1.35.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (focused) Color(0x553D2182) else Color(0x332C2453)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (focused) Color(0xFFD5C2FF) else Color(0xFFB8A5E9), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(label, color = if (focused) Color(0xFFCDBBFF) else AuthMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = LocalTextStyle.current.copy(color = AuthText, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = Brush.verticalGradient(listOf(AuthPurpleLight, AuthPurple)),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) Text(placeholder, color = Color(0xFF535A79), fontSize = 14.sp)
                            inner()
                        }
                    }
                )
            }
            when {
                isPassword && onTogglePassword != null -> IconButton(onClick = onTogglePassword, modifier = Modifier.size(36.dp)) {
                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = AuthMuted, modifier = Modifier.size(19.dp))
                }
                valid -> Icon(Icons.Default.CheckCircle, null, tint = AuthGreen, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun CompactCheckRow(
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
            modifier = Modifier.size(34.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = AuthPurple,
                uncheckedColor = Color(0xFF777C95),
                checkmarkColor = Color.White
            )
        )
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color(0xFFABB0C7), fontSize = 13.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun CompactMessage(text: String, positive: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (positive) Color(0x222FCA82) else Color(0x22FF587C),
        border = BorderStroke(1.dp, if (positive) Color(0x5538D38C) else Color(0x55FF6685))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (positive) Color(0xFFB9F6D8) else Color(0xFFFFB6C4),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun StaticGradientButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(Color(0xFF5A22F7), Color(0xFF7B32FF), Color(0xFF6120EE)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF29263B), Color(0xFF322A45)))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .border(1.dp, if (enabled) Color(0xFF8D60FF) else Color(0xFF454059), RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (text.contains("…")) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ArrowForward, null, tint = Color.White, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(21.dp))
        }
    }
}

@Composable
private fun AdaptiveOtpScreen(
    copy: AuthCopy,
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02030C), Color(0xFF050817), Color(0xFF02030D))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val availableHeight = maxHeight
        val compact = availableHeight < 760.dp
        Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(max = (availableHeight - 12.dp).coerceAtLeast(480.dp)),
                shape = RoundedCornerShape(34.dp),
                color = AuthCard,
                border = BorderStroke(1.2.dp, Color(0xFF654FC7))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 13.dp else 17.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(copy.otpTitle, color = Color.White, fontSize = if (compact) 24.sp else 27.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(copy.otpSubtitle, color = AuthMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(if (compact) 9.dp else 12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = AuthCardSoft,
                        border = BorderStroke(1.dp, AuthBorder)
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, null, tint = Color(0xFFC9B9FF), modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(email, color = AuthText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(copy.change, color = AuthPurpleLight, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
                        }
                    }

                    Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                    CompactOtpSteps(copy, compact)
                    Spacer(Modifier.height(if (compact) 7.dp else 10.dp))

                    Image(
                        painter = painterResource(R.drawable.otp_envelope_reference),
                        contentDescription = "OTP",
                        modifier = Modifier.fillMaxWidth().height(if (compact) 128.dp else 150.dp).clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                    CompactOtpCodeField(code, onCodeChange, compact)
                    Spacer(Modifier.height(if (compact) 10.dp else 14.dp))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val resendProgress by animateFloatAsState(
                            targetValue = if (resendIn > 0) resendIn.coerceAtMost(60) / 60f else 0f,
                            animationSpec = tween(1000, easing = LinearEasing),
                            label = "resendProgress"
                        )
                        CircularProgressIndicator(
                            progress = { resendProgress },
                            modifier = Modifier.size(25.dp),
                            color = Color(0xFF69D9FF),
                            trackColor = Color(0xFF252A45),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (resendIn > 0) "${copy.codeValid} ${resendIn / 60}:${(resendIn % 60).toString().padStart(2, '0')}" else copy.codeSent,
                            color = AuthMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            copy.resend,
                            color = if (resendIn == 0 && !busy) AuthPurpleLight else Color(0xFF666A80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = if (resendIn == 0 && !busy) Modifier.clickable(onClick = onResend) else Modifier
                        )
                    }

                    AnimatedVisibility(visible = message != null) {
                        message?.let {
                            Column {
                                Spacer(Modifier.height(7.dp))
                                CompactMessage(it, it == copy.codeSent)
                            }
                        }
                    }

                    Spacer(Modifier.height(if (compact) 10.dp else 13.dp))
                    StaticGradientButton(if (busy) copy.wait else copy.continueText, !busy && code.length == 6, onVerify)
                    Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
                    Text(
                        "←  ${copy.back}",
                        color = Color(0xFFADB0C4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                    Spacer(Modifier.height(if (compact) 5.dp else 8.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth().height(if (compact) 52.dp else 58.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = AuthCardSoft,
                        border = BorderStroke(1.dp, AuthBorder)
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = Color(0xFFB8A7FF), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(copy.securityTitle, color = Color(0xFFDADBE6), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(copy.securitySubtitle, color = AuthMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactOtpSteps(copy: AuthCopy, compact: Boolean) {
    val labels = listOf(copy.contact, copy.code, copy.profile)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) Box(Modifier.weight(1f).height(2.dp).background(if (index == 1) AuthPurple else Color(0xFF282B45)))
                    Box(
                        modifier = Modifier
                            .size(if (compact) 44.dp else 48.dp)
                            .clip(CircleShape)
                            .background(if (index == 1) Brush.radialGradient(listOf(Color(0xFFD19AFF), Color(0xFF8845FF), Color(0xFF5F25D8))) else Brush.radialGradient(listOf(Color(0xFF171A31), Color(0xFF090C19))))
                            .border(1.5.dp, if (index == 1) Color(0xFFDAB9FF) else Color(0xFF4E3C9B), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text((index + 1).toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    if (index < 2) Box(Modifier.weight(1f).height(2.dp).background(if (index == 0) AuthPurple else Color(0xFF282B45)))
                }
                Spacer(Modifier.height(4.dp))
                Text(label, color = if (index == 1) Color.White else AuthMuted, fontSize = 12.sp, fontWeight = if (index == 1) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CompactOtpCodeField(code: String, onCodeChange: (String) -> Unit, compact: Boolean) {
    var focused by remember { mutableStateOf(false) }
    // Автофокус при появлении экрана — иначе поле почти невидимо (alpha .01)
    // и пользователь не понимает, куда вводить код.
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    BasicTextField(
        value = code,
        onValueChange = { onCodeChange(it.filter(Char::isDigit).take(6)) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusRequester(focusRequester),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = LocalTextStyle.current.copy(color = Color.Transparent),
        cursorBrush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                    repeat(6) { index ->
                        val value = code.getOrNull(index)?.toString().orEmpty()
                        val active = focused && index == code.length.coerceAtMost(5)
                        Surface(
                            modifier = Modifier.weight(1f).height(if (compact) 56.dp else 62.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF090D1D),
                            border = BorderStroke(if (active || value.isNotEmpty()) 1.6.dp else 1.1.dp, if (active || value.isNotEmpty()) Color(0xFF824BFF) else Color(0xFF493791))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(value, color = Color.White, fontSize = if (compact) 23.sp else 26.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxSize().alpha(.01f)) { inner() }
            }
        }
    )
}

@Composable
internal fun CreateCommunityDialog(
    api: SupabaseApi,
    session: CloudSession,
    onDismiss: () -> Unit,
    onCreated: (CloudConversation) -> Unit,
    /** Preselected kind — the canvas opens the channel form from the channels tab. */
    initialType: String = "group",
) {
    val copy = rememberAuthCopy()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(if (initialType == "channel") "channel" else "group") }
    var isPublic by remember { mutableStateOf(false) }
    var publicUsername by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var people by remember { mutableStateOf<List<CloudProfile>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var memberQuery by remember { mutableStateOf("") }
    var loadingPeople by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> avatarUri = uri }

    LaunchedEffect(Unit) {
        loadingPeople = true
        runCatching { withContext(Dispatchers.IO) { api.allProfiles(session.accessToken) } }
            .onSuccess { people = it.filterNot { profile -> profile.id == session.userId || profile.blocked }; error = null }
            .onFailure { error = localizedNetworkError(it) }
        loadingPeople = false
    }

    val filteredPeople = remember(people, memberQuery) {
        val q = memberQuery.trim().removePrefix("@").lowercase()
        if (q.isBlank()) people else people.filter {
            it.nickname.lowercase().contains(q) || it.displayName.lowercase().contains(q)
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = .58f)).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp).fillMaxHeight(.94f),
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFF090D20),
                border = BorderStroke(1.2.dp, Color(0xFF5B43A4)),
                shadowElevation = 24.dp
            ) {
                Column(Modifier.fillMaxSize().imePadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (type == "group") copy.createGroup else copy.createChannel,
                                color = AuthText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                if (type == "group") "Настройте группу и выберите участников" else "Настройте канал и его видимость",
                                color = AuthMuted,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = onDismiss, enabled = !busy) {
                            Icon(Icons.Default.Close, "Закрыть", tint = AuthMuted)
                        }
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF11162D)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CommunitySegment(copy.group, type == "group", Modifier.weight(1f)) {
                            type = "group"
                            publicUsername = ""
                        }
                        CommunitySegment(copy.channel, type == "channel", Modifier.weight(1f)) {
                            type = "channel"
                            publicUsername = ""
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { CommunityTextField(title, { title = it.take(80) }, copy.title, Icons.Default.Title) }
                        item { CommunityTextField(description, { description = it.take(300) }, copy.description, Icons.Default.Notes, singleLine = false) }
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { avatarPicker.launch("image/*") },
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFF0F142A),
                                border = BorderStroke(1.dp, if (avatarUri != null) AuthPurple else Color(0xFF303654)),
                            ) {
                                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(50.dp).clip(CircleShape).background(
                                            if (avatarUri != null) Brush.linearGradient(listOf(AuthPurple, Color(0xFF9C58FF))) else Brush.linearGradient(listOf(Color(0xFF25213E), Color(0xFF171B31)))
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(if (avatarUri == null) Icons.Default.AddAPhoto else Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (avatarUri == null) "Аватар ${if (type == "group") "группы" else "канала"}" else "Аватар выбран",
                                            color = AuthText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(if (avatarUri == null) "PNG, JPG или WebP до 8 МБ" else "Нажмите, чтобы заменить изображение", color = AuthMuted, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = AuthMuted)
                                }
                            }
                        }
                        item {
                            Text("Видимость", color = AuthText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(7.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF11162D)).padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CommunityVisibilitySegment(
                                    title = "Частн${if (type == "group") "ая" else "ый"}",
                                    subtitle = "По приглашению",
                                    icon = Icons.Default.Lock,
                                    selected = !isPublic,
                                    modifier = Modifier.weight(1f),
                                ) { isPublic = false }
                                CommunityVisibilitySegment(
                                    title = "Публичн${if (type == "group") "ая" else "ый"}",
                                    subtitle = "Доступен в поиске",
                                    icon = Icons.Default.Public,
                                    selected = isPublic,
                                    modifier = Modifier.weight(1f),
                                ) { isPublic = true }
                            }
                        }
                        if (isPublic) {
                            item {
                                CommunityTextField(
                                    publicUsername,
                                    { publicUsername = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(32) },
                                    "Публичная ссылка",
                                    Icons.Default.AlternateEmail
                                )
                            }
                        }

                        if (type == "group" || type == "channel") {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (type == "group") copy.members else "Первые участники",
                                            color = AuthText,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            if (type == "group") "Выберите людей, которых нужно добавить сразу" else "Можно добавить людей сразу или оставить канал пустым",
                                            color = AuthMuted,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    if (selected.isNotEmpty()) {
                                        Surface(shape = CircleShape, color = Color(0xFF342260)) {
                                            Text("${selected.size} выбрано", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = AuthPurpleLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = memberQuery,
                                    onValueChange = { memberQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("Поиск участников", color = AuthMuted) },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AuthPurple,
                                        unfocusedBorderColor = AuthBorder,
                                        focusedContainerColor = AuthFieldFocused,
                                        unfocusedContainerColor = AuthFieldBg,
                                        focusedTextColor = AuthText,
                                        unfocusedTextColor = AuthText,
                                        cursorColor = AuthPurpleLight,
                                        focusedLeadingIconColor = AuthPurpleLight,
                                        unfocusedLeadingIconColor = AuthMuted,
                                    )
                                )
                            }
                            if (loadingPeople) {
                                item {
                                    Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = AuthPurple, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                                    }
                                }
                            } else if (filteredPeople.isEmpty()) {
                                item {
                                    Surface(color = Color(0xFF0F142A), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, AuthBorder)) {
                                        Text("Пользователи не найдены", color = AuthMuted, modifier = Modifier.fillMaxWidth().padding(18.dp), textAlign = TextAlign.Center)
                                    }
                                }
                            } else {
                                items(filteredPeople, key = { it.id }) { person ->
                                    val checked = person.id in selected
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selected = if (checked) selected - person.id else selected + person.id
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (checked) Color(0xFF18143A) else Color(0xFF0F142A),
                                        border = BorderStroke(1.dp, if (checked) Color(0xFF8050FF) else Color(0xFF303654))
                                    ) {
                                        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { value -> selected = if (value) selected + person.id else selected - person.id },
                                                colors = CheckboxDefaults.colors(checkedColor = AuthPurple, uncheckedColor = AuthMuted)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(person.displayName, color = AuthText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                                Text("@${person.nickname}", color = AuthMuted, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        error?.let {
                            item { CompactMessage(it, false) }
                        }
                    }

                    Surface(color = Color(0xFF080B18), border = BorderStroke(0.dp, Color.Transparent)) {
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !busy,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF494D67)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AuthMuted)
                            ) { Text(copy.cancel, fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                val avatarUrl = avatarUri?.let { uri ->
                                                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось прочитать аватар")
                                                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                                                    api.uploadCommunityAvatar(session.accessToken, session.userId, mime, bytes)
                                                }
                                                api.createCommunity(
                                                    session.accessToken,
                                                    title,
                                                    description,
                                                    type,
                                                    selected.toList(),
                                                    isPublic = isPublic,
                                                    avatarUrl = avatarUrl,
                                                    username = publicUsername.takeIf { isPublic && it.isNotBlank() },
                                                )
                                            }
                                        }.onSuccess(onCreated)
                                            .onFailure { error = localizedNetworkError(it) }
                                        busy = false
                                    }
                                },
                                enabled = !busy && title.trim().length >= 3 && (!isPublic || publicUsername.length >= 3),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AuthPurple, disabledContainerColor = Color(0xFF312D42))
                            ) {
                                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                else Text(copy.create, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityVisibilitySegment(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) Color(0xFF342260) else Color.Transparent,
        shape = RoundedCornerShape(13.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (selected) Color.White else AuthMuted, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(5.dp))
            Text(title, color = if (selected) Color.White else AuthMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = AuthMuted, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CommunitySegment(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(42.dp).clip(RoundedCornerShape(11.dp)).background(if (selected) Color(0xFF342260) else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else AuthMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CommunityTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        maxLines = if (singleLine) 1 else 4,
        label = { Text(label) },
        leadingIcon = {
            if (singleLine) {
                Icon(icon, null, modifier = Modifier.size(20.dp))
            } else {
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Top) {
                    Spacer(Modifier.height(14.dp))
                    Icon(icon, null, modifier = Modifier.size(20.dp))
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF8050FF),
            unfocusedBorderColor = AuthBorder,
            focusedContainerColor = AuthFieldFocused,
            unfocusedContainerColor = AuthFieldBg,
            focusedLabelColor = AuthPurpleLight,
            unfocusedLabelColor = AuthMuted,
            focusedLeadingIconColor = AuthPurpleLight,
            unfocusedLeadingIconColor = AuthMuted,
            focusedTextColor = AuthText,
            unfocusedTextColor = AuthText,
            cursorColor = AuthPurpleLight
        )
    )
}
