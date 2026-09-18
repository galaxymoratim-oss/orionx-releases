@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package net.orionx.messenger

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.orionx.messenger.cloud.CloudProfile
import net.orionx.messenger.cloud.CloudSession
import net.orionx.messenger.cloud.SupabaseApi

/** The link the canvas encodes into the profile QR: `orionx.me/@handle`. */
internal fun orionQrLink(nickname: String): String = "orionx.me/@${nickname.removePrefix("@")}"

/**
 * Pulls a handle out of anything that looks like an OrionX profile code — the canvas link,
 * a full URL, a deep link or a bare `@handle`. Returns null for codes that are not ours.
 */
internal fun orionHandleFromQr(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val withoutScheme = text
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("orionx://")
    val marker = withoutScheme.indexOf('@')
    val handle = when {
        marker >= 0 -> withoutScheme.substring(marker + 1)
        withoutScheme.startsWith("orionx.me/", ignoreCase = true) -> withoutScheme.substringAfter('/')
        else -> return null
    }
    val cleaned = handle.takeWhile { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' }
    return cleaned.takeIf { it.isNotBlank() }
}

private enum class XScanState { SCANNING, FOUND, MISSING }

/**
 * QR scanner from the canvas: a close button, the «сканер» pill and a torch toggle over a
 * rounded camera card with corner brackets and a sweeping line, then «мой qr-код» /
 * «из галереи» along the bottom.
 */
@Composable
internal fun XQrScannerScreen(
    api: SupabaseApi,
    session: CloudSession,
    profile: CloudProfile,
    onBack: () -> Unit,
    onOpenPeer: (CloudProfile) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var state by remember { mutableStateOf(XScanState.SCANNING) }
    var found by remember { mutableStateOf<CloudProfile?>(null) }
    var missingHandle by remember { mutableStateOf("") }
    var showMyQr by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val scanner: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    // Пока ProcessCameraProvider резолвится асинхронно, экран может уйти с композиции —
    // в этом случае привязывать камеру нельзя, иначе она утечёт.
    var disposed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            // Камера привязана к активити (single-activity), поэтому без unbindAll она
            // продолжает работать на остальных экранах до выхода из приложения.
            runCatching { cameraProvider?.unbindAll() }
            scanner.close()
        }
    }

    fun resolve(raw: String) {
        if (busy || state != XScanState.SCANNING) return
        val handle = orionHandleFromQr(raw) ?: return
        busy = true
        scope.launch {
            val match = runCatching {
                withContext(Dispatchers.IO) { api.searchProfiles(session.accessToken, handle) }
            }.getOrDefault(emptyList()).firstOrNull { it.nickname.equals(handle, ignoreCase = true) }
            when {
                match == null -> {
                    missingHandle = handle
                    state = XScanState.MISSING
                }
                match.id == session.userId -> {
                    // Отсканировали свой QR — открываем свой код вместо «не найдено».
                    showMyQr = true
                }
                else -> {
                    found = match
                    state = XScanState.FOUND
                }
            }
            busy = false
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        state = XScanState.SCANNING
        runCatching { InputImage.fromFilePath(context, uri) }
            .onSuccess { image ->
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        val value = codes.firstNotNullOfOrNull { it.rawValue }
                        if (value != null) resolve(value) else {
                            missingHandle = ""
                            state = XScanState.MISSING
                        }
                    }
                    .addOnFailureListener {
                        missingHandle = ""
                        state = XScanState.MISSING
                    }
            }
            .onFailure {
                missingHandle = ""
                state = XScanState.MISSING
            }
    }

    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Header: close, the «сканер» pill, torch.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            XQrCircleButton(icon = Icons.Outlined.Close, description = tr("back"), onClick = onBack)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.xShadow(CircleShape).clip(CircleShape).background(XText)
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                ) {
                    Text(
                        xSettingText("scanner_title").uppercase(),
                        style = XType.pill.copy(letterSpacing = .18.em),
                        color = XInverse,
                    )
                }
            }
            XQrCircleButton(
                icon = if (torch) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                description = xSettingText("torch"),
                background = if (torch) XText else XSurface,
                tint = if (torch) XInverse else XText,
                onClick = { torch = !torch },
            )
        }

        // Camera card.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(
                    Brush.linearGradient(
                        colorStops = arrayOf(0f to Color(0xFF2A2736), .48f to Color(0xFF1A1824), 1f to Color(0xFF0E0D14)),
                    )
                ),
        ) {
            if (granted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val view = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            if (disposed) {
                                runCatching { provider.unbindAll() }
                                return@addListener
                            }
                            cameraProvider = provider
                            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                                val media = proxy.image
                                if (media == null) {
                                    proxy.close()
                                } else {
                                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { codes ->
                                            codes.firstNotNullOfOrNull { it.rawValue }?.let(::resolve)
                                        }
                                        .addOnCompleteListener { proxy.close() }
                                }
                            }
                            runCatching {
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        view
                    },
                )
            }

            // The two soft glows behind the frame.
            Box(
                Modifier.size(200.dp).offset(x = (-40).dp, y = (-30).dp).background(
                    Brush.radialGradient(listOf(Color(0x47FF9E76), Color.Transparent)), CircleShape,
                )
            )
            Box(
                Modifier.align(Alignment.BottomEnd).size(220.dp).offset(x = 50.dp, y = (-60).dp).background(
                    Brush.radialGradient(listOf(Color(0x428DA8FF), Color.Transparent)), CircleShape,
                )
            )

            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                XQrViewfinder(scanning = state == XScanState.SCANNING)
                if (state == XScanState.SCANNING) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (granted) xSettingText("scanner_hint") else xSettingText("scanner_no_camera"),
                        style = XType.meta.copy(fontSize = 12.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = Color(0xFFF7F5FF).copy(alpha = .72f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            // Result card.
            val peer = found
            if (state == XScanState.FOUND && peer != null) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                        .fillMaxWidth()
                        .xShadow(RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp))
                        .background(XSurface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    XQrAvatar(peer)
                    Column(Modifier.weight(1f)) {
                        Text(
                            peer.displayName.ifBlank { peer.nickname },
                            style = XType.rowTitle,
                            color = XText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "@${peer.nickname} · ${xSettingText("profile_found")}",
                            style = XType.meta.copy(fontSize = 11.5.sp),
                            color = XMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(XText)
                            .clickable { onOpenPeer(peer) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(xSettingText("open_action").uppercase(), style = XType.pillSmall, color = XInverse)
                    }
                }
            } else if (state == XScanState.MISSING) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                        .fillMaxWidth()
                        .xShadow(RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp))
                        .background(XSurface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(xSettingText("qr_not_orionx"), style = XType.rowTitle, color = XText)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (missingHandle.isBlank()) xSettingText("qr_unreadable") else "@$missingHandle",
                            style = XType.meta.copy(fontSize = 11.5.sp),
                            color = XMuted,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(XSurfaceRaised)
                            .clickable { state = XScanState.SCANNING }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(xSettingText("scan_again").uppercase(), style = XType.pillSmall, color = XText)
                    }
                }
            }
        }

        // Bottom actions.
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            XQrActionPill(
                icon = Icons.Outlined.QrCode2,
                label = xSettingText("my_qr"),
                modifier = Modifier.weight(1f),
                onClick = { showMyQr = true },
            )
            XQrActionPill(
                icon = Icons.Outlined.Image,
                label = xSettingText("from_gallery"),
                modifier = Modifier.weight(1f),
                onClick = { galleryPicker.launch("image/*") },
            )
        }
    }

    if (showMyQr) {
        XMyQrDialog(profile = profile, onDismiss = { showMyQr = false })
    }
}

@Composable
private fun XQrAvatar(peer: CloudProfile) {
    if (peer.avatarUrl.isNotBlank()) {
        RemoteImage(peer.avatarUrl, Modifier.size(46.dp).clip(XShape.plate), ContentScale.Crop)
    } else {
        Box(
            Modifier.size(46.dp).clip(XShape.plate).background(XAvatarBrush),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                peer.displayName.ifBlank { peer.nickname }.trim().take(1).uppercase(),
                style = XType.rowTitle.copy(fontSize = 16.sp),
                color = XText,
            )
        }
    }
}

@Composable
private fun XQrCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    background: Color = XSurface,
    tint: Color = XText,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(42.dp).xShadow(CircleShape).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun XQrActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(54.dp)
            .xShadow(CircleShape)
            .clip(CircleShape)
            .background(XSurface)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = XText, modifier = Modifier.size(20.dp))
        Text(label.uppercase(), style = XType.sectionLabel.copy(letterSpacing = .13.em), color = XText, maxLines = 1)
    }
}

/** The 206 dp frame: four 38 dp corner brackets plus the sweeping line while scanning. */
@Composable
private fun XQrViewfinder(scanning: Boolean) {
    // Переход создаётся только пока идёт сканирование — иначе анимация
    // инвалидирует Canvas каждый кадр даже на карточке результата.
    if (!scanning) {
        XQrBrackets()
        return
    }
    val sweep = rememberInfiniteTransition(label = "qr-sweep")
    val y by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "qr-sweep-y",
    )
    XQrBrackets(sweepY = y)
}

@Composable
private fun XQrBrackets(sweepY: Float? = null) {
    Canvas(Modifier.size(206.dp)) {
        val bracket = 38.dp.toPx()
        val stroke = 3.dp.toPx()
        val radius = 16.dp.toPx()
        val line = Color(0xFFF7F5FF)

        fun corner(x: Float, yy: Float, flipX: Boolean, flipY: Boolean) {
            val sx = if (flipX) -1f else 1f
            val sy = if (flipY) -1f else 1f
            // Horizontal arm
            drawLine(
                color = line,
                start = Offset(x + sx * radius, yy),
                end = Offset(x + sx * bracket, yy),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            // Vertical arm
            drawLine(
                color = line,
                start = Offset(x, yy + sy * radius),
                end = Offset(x, yy + sy * bracket),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            // Rounded elbow
            val left = if (flipX) x - 2 * radius else x
            val top = if (flipY) yy - 2 * radius else yy
            drawArc(
                color = line,
                startAngle = if (!flipX && !flipY) 180f else if (flipX && !flipY) 270f else if (flipX) 0f else 90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(2 * radius, 2 * radius),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        val halfStroke = stroke / 2f
        corner(halfStroke, halfStroke, flipX = false, flipY = false)
        corner(size.width - halfStroke, halfStroke, flipX = true, flipY = false)
        corner(halfStroke, size.height - halfStroke, flipX = false, flipY = true)
        corner(size.width - halfStroke, size.height - halfStroke, flipX = true, flipY = true)

        if (sweepY != null) {
            val inset = 8.dp.toPx()
            val lineY = inset + (size.height - 2 * inset) * sweepY
            drawLine(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        .35f to Color(0xFFFF9E76),
                        .65f to Color(0xFFE8B6E3),
                        1f to Color.Transparent,
                    ),
                    startX = inset,
                    endX = size.width - inset,
                ),
                start = Offset(inset, lineY),
                end = Offset(size.width - inset, lineY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
