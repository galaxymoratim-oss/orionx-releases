package net.orionx.messenger

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.orionx.messenger.cloud.CloudProfile

/**
 * «мой qr-код» from the canvas: the code drawn as dots with three rounded eyes and the OrionX
 * mark punched through the middle, the `orionx.me/@handle` line, then share / close.
 */
@Composable
internal fun XMyQrDialog(profile: CloudProfile, onDismiss: () -> Unit) {
    val link = remember(profile.nickname) { orionQrLink(profile.nickname) }
    XQrShareDialog(
        title = xSettingText("my_qr"),
        hint = xSettingText("my_qr_hint"),
        link = link,
        shareUrl = "https://" + link,
        onDismiss = onDismiss,
    )
}

/** Тот же код, но для произвольной ссылки — например, ссылки-приглашения канала. */
@Composable
internal fun XQrShareDialog(
    title: String,
    hint: String,
    link: String,
    shareUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val code = remember(link) { OrionQrEncoder.build(link) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .xShadow(RoundedCornerShape(30.dp), 26.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(XSurface)
                    .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = XType.screenTitle.copy(fontSize = 17.sp, letterSpacing = (-.02).em),
                    color = XText,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    hint,
                    style = XType.meta.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                    color = XMuted2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .size(214.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(XSurfaceRaised, XSurfaceSunken)))
                        .padding(12.dp),
                ) {
                    XQrCanvas(code, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(16.dp))
                Text(link, style = XType.rowTitle.copy(fontSize = 12.5.sp), color = XText)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(XText)
                            .clickable {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                }
                                context.startActivity(Intent.createChooser(share, null))
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        Icon(Icons.Outlined.IosShare, null, tint = XInverse, modifier = Modifier.size(17.dp))
                        Text(xSettingText("share_action").uppercase(), style = XType.pillSmall, color = XInverse)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(XSurfaceRaised)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(xSettingText("close_action").uppercase(), style = XType.pillSmall, color = XMuted2)
                    }
                }
            }
        }
    }
}

/**
 * Draws the code the way the canvas does: ordinary modules as dots, the three finder patterns as
 * rounded "eyes", and a punched-out centre carrying the OrionX mark.
 */
@Composable
private fun XQrCanvas(code: OrionQrEncoder.QrCode, modifier: Modifier = Modifier) {
    val dots = Brush.linearGradient(listOf(Color(0xFFF4EFFF), Color(0xFFC9B8FF)))
    val mark = Brush.linearGradient(listOf(Color(0xFF5B3FD6), Color(0xFF2A1466)))
    val surface = XSurface
    Canvas(modifier) {
        val n = code.size
        val cell = size.minDimension / n
        val eyes = listOf(0 to 0, 0 to (n - 7), (n - 7) to 0)

        fun inEye(r: Int, c: Int): Boolean = eyes.any { (er, ec) -> r >= er && r < er + 7 && c >= ec && c < ec + 7 }

        // Плашка занимает не больше ~14% стороны: вырез из 23% стирает до 5×5
        // модулей (v1 = 21 модуль), что на грани коррекции ECC уровня M.
        val holeFrac = .14f
        val holeHalf = n * holeFrac / 2f
        val mid = n / 2f
        fun inHole(r: Int, c: Int): Boolean =
            r + .5f > mid - holeHalf && r + .5f < mid + holeHalf && c + .5f > mid - holeHalf && c + .5f < mid + holeHalf

        for (r in 0 until n) for (c in 0 until n) {
            if (!code.modules[r][c] || inEye(r, c) || inHole(r, c)) continue
            drawCircle(
                brush = dots,
                radius = cell * .44f,
                center = Offset((c + .5f) * cell, (r + .5f) * cell),
            )
        }

        eyes.forEach { (er, ec) ->
            val x = ec * cell
            val y = er * cell
            val outer = 7 * cell
            drawRoundRect(
                brush = dots,
                topLeft = Offset(x, y),
                size = Size(outer, outer),
                cornerRadius = CornerRadius(outer * .30f),
            )
            val innerInset = outer * .143f
            val innerSize = outer - innerInset * 2
            drawRoundRect(
                color = surface,
                topLeft = Offset(x + innerInset, y + innerInset),
                size = Size(innerSize, innerSize),
                cornerRadius = CornerRadius(innerSize * .26f),
            )
            val coreInset = innerSize * .20f
            val coreSize = innerSize - coreInset * 2
            drawRoundRect(
                brush = dots,
                topLeft = Offset(x + innerInset + coreInset, y + innerInset + coreInset),
                size = Size(coreSize, coreSize),
                cornerRadius = CornerRadius(coreSize * .26f),
            )
        }

        // Centre mark: white plate, then the gradient tile (same fraction as the hole).
        val plate = size.minDimension * holeFrac
        val plateTopLeft = Offset((size.width - plate) / 2f, (size.height - plate) / 2f)
        drawRoundRect(
            color = Color.White,
            topLeft = plateTopLeft,
            size = Size(plate, plate),
            cornerRadius = CornerRadius(plate * .28f),
        )
        val tile = plate * .72f
        drawRoundRect(
            brush = mark,
            topLeft = Offset((size.width - tile) / 2f, (size.height - tile) / 2f),
            size = Size(tile, tile),
            cornerRadius = CornerRadius(tile * .26f),
        )
    }
}
