package net.orionx.messenger

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Design tokens taken 1:1 from the "OrionX Redesign" canvas (`OrionX Redesign.dc.html`).
 *
 * The canvas keeps two CSS variable sets — `DARK_VARS` and `LIGHT_VARS` — and every screen is
 * painted from them. This file is the Compose mirror of those two sets, so a colour only ever
 * changes in one place. `applyXPalette` swaps the whole set when the theme toggles.
 */

internal val OrionFont = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

// ---------------------------------------------------------------------------
// Theme-dependent tokens. Names match the canvas variables in the comments.
// ---------------------------------------------------------------------------

/** `--txt` — primary text, and the fill of "inverted" pills (create button, bottom dock). */
private val xtextState = mutableStateOf(Color(0xFFF2F1F5))
internal val XText: Color get() = xtextState.value

/** `--inv` — reads on top of [XText]-filled surfaces. */
private val xinverseState = mutableStateOf(Color(0xFF101014))
internal val XInverse: Color get() = xinverseState.value

/** `--onmut` — muted content on top of an [XText]-filled surface. */
private val xonmutedState = mutableStateOf(Color(0x73101014))
internal val XOnMuted: Color get() = xonmutedState.value

/** `--mut` */
private val xmutedState = mutableStateOf(Color(0xFF948EA1))
internal val XMuted: Color get() = xmutedState.value

/** `--mut2` */
private val xmuted2State = mutableStateOf(Color(0xFF7F7A8C))
internal val XMuted2: Color get() = xmuted2State.value

/** `--mut3` */
private val xmuted3State = mutableStateOf(Color(0xFF565364))
internal val XMuted3: Color get() = xmuted3State.value

/** `--sf` — the standard card. */
private val xsurfaceState = mutableStateOf(Color(0xFF191920))
internal val XSurface: Color get() = xsurfaceState.value

/** `--sf2` — insets inside a card: chips, segmented controls, icon plates. */
private val xsurfaceraisedState = mutableStateOf(Color(0xFF26252D))
internal val XSurfaceRaised: Color get() = xsurfaceraisedState.value

/** `--sf3` — sheets. */
private val xsurfacesoftState = mutableStateOf(Color(0xFF201F27))
internal val XSurfaceSoft: Color get() = xsurfacesoftState.value

/** `--sf4` */
private val xsurfacealtState = mutableStateOf(Color(0xFF22212A))
internal val XSurfaceAlt: Color get() = xsurfacealtState.value

/** `--sf5` */
private val xsurfacedeepState = mutableStateOf(Color(0xFF17161D))
internal val XSurfaceDeep: Color get() = xsurfacedeepState.value

/** `--sf6` */
private val xsurfacesunkenState = mutableStateOf(Color(0xFF151319))
internal val XSurfaceSunken: Color get() = xsurfacesunkenState.value

/** `--bd` — hairline separators and card borders. */
private val xborderState = mutableStateOf(Color(0x17FFFFFF))
internal val XBorder: Color get() = xborderState.value

/** `--hairA` — the faintest separator. */
private val xhairlineState = mutableStateOf(Color(0x0FFFFFFF))
internal val XHairline: Color get() = xhairlineState.value

/** `--ghost` — pressed/ghost button fill. */
private val xghostState = mutableStateOf(Color(0x12FFFFFF))
internal val XGhost: Color get() = xghostState.value

/** `--shc` — the colour behind every `0 12px 30px` drop shadow. */
private val xshadowcolorState = mutableStateOf(Color(0x73000000))
internal val XShadowColor: Color get() = xshadowcolorState.value

/** `--shc2` — the heavier shadow under sheets and dropdowns. */
private val xshadowstrongState = mutableStateOf(Color(0x99000000))
internal val XShadowStrong: Color get() = xshadowstrongState.value

/** `--tscr` — the scrim that dims a feed as it slides under the chat header. */
private val xtopscrimState = mutableStateOf(Color(0x80000000))
internal val XTopScrim: Color get() = xtopscrimState.value

/** `--g6` — the last gradient stop, used wherever a flat window background is needed. */
private val xbgState = mutableStateOf(Color(0xFF0B0C12))
internal val XBg: Color get() = xbgState.value

private val xGradientStopsState = mutableStateOf<List<Pair<Float, Color>>>(emptyList())
private val xGradientStops: List<Pair<Float, Color>> get() = xGradientStopsState.value

// ---------------------------------------------------------------------------
// Accents — identical in both themes.
// ---------------------------------------------------------------------------

/** The single accent of the redesign: bolt, unread dot, active links. */
internal val XAccent = Color(0xFF6D7BFF)
internal val XAccentSoft = Color(0xFF8FA8FF)
internal val XGreen = Color(0xFF3ED27E)
internal val XRed = Color(0xFFFF5C76)

// Legacy aliases so screens that were written against the old neon palette keep compiling
// while they are migrated screen by screen.
internal val XPurple = XAccent
internal val XViolet = XAccentSoft
internal val XCyan = Color(0xFF9BE0FF)

/** `linear-gradient(140deg,#FFB48A,#E39BD8 55%,#8FA8FF)` — every avatar plate in the canvas. */
internal val XAvatarBrush: Brush
    get() = Brush.linearGradient(
        colorStops = arrayOf(0f to Color(0xFFFFB48A), .55f to Color(0xFFE39BD8), 1f to Color(0xFF8FA8FF)),
        start = Offset.Zero,
        end = Offset(0f, Float.POSITIVE_INFINITY),
    )

/**
 * Четыре градиента аватара из макета (шаг «аватар» и пикер профиля). Выбор хранится в
 * `avatar_url` профиля как `orionx-gradient:N`, поэтому его видят все клиенты приложения.
 */
internal val xAvatarGradientBrushes: List<Brush>
    get() = listOf(
        Brush.linearGradient(0f to Color(0xFFFFB48A), .55f to Color(0xFFE39BD8), 1f to Color(0xFF8FA8FF), start = Offset.Zero, end = Offset(0f, Float.POSITIVE_INFINITY)),
        Brush.linearGradient(0f to Color(0xFFFFD3A8), .55f to Color(0xFFFF9FC4), 1f to Color(0xFFB79CFF), start = Offset.Zero, end = Offset(0f, Float.POSITIVE_INFINITY)),
        Brush.linearGradient(0f to Color(0xFF9BE0FF), .55f to Color(0xFF8FA8FF), 1f to Color(0xFF6D7BFF), start = Offset.Zero, end = Offset(0f, Float.POSITIVE_INFINITY)),
        Brush.linearGradient(0f to Color(0xFFB8F0C2), .55f to Color(0xFF8FD8C9), 1f to Color(0xFF8FA8FF), start = Offset.Zero, end = Offset(0f, Float.POSITIVE_INFINITY)),
    )

internal fun xAvatarGradientBrush(index: Int): Brush =
    xAvatarGradientBrushes[index.coerceIn(0, xAvatarGradientBrushes.lastIndex)]

/** `radial-gradient(circle at 32% 26%,#9BE0FF,#6D7BFF 52%,#2A1750)` — the profile bubble. */
internal val XProfileOrbBrush: Brush
    get() = Brush.radialGradient(
        colorStops = arrayOf(0f to Color(0xFF9BE0FF), .52f to Color(0xFF6D7BFF), 1f to Color(0xFF2A1750)),
    )

/**
 * One complete palette. The canvas keeps `DARK_VARS` and `LIGHT_VARS` as two CSS blocks
 * and transitions every variable between them, so both sets are held here as data and
 * mixed rather than assigned.
 */
private class XPalette(
    val text: Color,
    val inverse: Color,
    val onMuted: Color,
    val muted: Color,
    val muted2: Color,
    val muted3: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSoft: Color,
    val surfaceAlt: Color,
    val surfaceDeep: Color,
    val surfaceSunken: Color,
    val border: Color,
    val hairline: Color,
    val ghost: Color,
    val shadow: Color,
    val shadowStrong: Color,
    val topScrim: Color,
    val bg: Color,
    val gradient: List<Pair<Float, Color>>,
)

private val XDarkPalette = XPalette(
    text = Color(0xFFF2F1F5),
    inverse = Color(0xFF101014),
    onMuted = Color(0x73101014),
    muted = Color(0xFF948EA1),
    muted2 = Color(0xFF7F7A8C),
    muted3 = Color(0xFF565364),
    surface = Color(0xFF191920),
    surfaceRaised = Color(0xFF26252D),
    surfaceSoft = Color(0xFF201F27),
    surfaceAlt = Color(0xFF22212A),
    surfaceDeep = Color(0xFF17161D),
    surfaceSunken = Color(0xFF151319),
    border = Color(0x17FFFFFF),
    hairline = Color(0x0FFFFFFF),
    ghost = Color(0x12FFFFFF),
    shadow = Color(0x73000000),
    shadowStrong = Color(0x99000000),
    topScrim = Color(0x80000000),
    bg = Color(0xFF0B0C12),
    gradient = listOf(
        0f to Color(0xFF4A1C11),
        .14f to Color(0xFF3A1A1D),
        .32f to Color(0xFF2C1628),
        .52f to Color(0xFF1E1533),
        .72f to Color(0xFF141630),
        1f to Color(0xFF0B0C12),
    ),
)

private val XLightPalette = XPalette(
    text = Color(0xFF101014),
    inverse = Color(0xFFFCFBFF),
    onMuted = Color(0x9EFCFBFF),
    muted = Color(0xFF6E6878),
    muted2 = Color(0xFF8B8496),
    muted3 = Color(0xFFA9A2B4),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEFECF6),
    surfaceSoft = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEDE9F4),
    surfaceDeep = Color(0xFFF6F3FC),
    surfaceSunken = Color(0xFFE7E2F2),
    border = Color(0x140E0A1A),
    hairline = Color(0x0D0E0A1A),
    ghost = Color(0x1A0E0A1A),
    shadow = Color(0x1A1E123C),
    shadowStrong = Color(0x291E123C),
    topScrim = Color(0x2B261648),
    bg = Color(0xFFF8F6FC),
    gradient = listOf(
        0f to Color(0xFFFFE7DC),
        .14f to Color(0xFFFFE1E3),
        .32f to Color(0xFFF7DDEC),
        .52f to Color(0xFFE8E3FB),
        .72f to Color(0xFFE3E8FB),
        1f to Color(0xFFF8F6FC),
    ),
)

/**
 * Applies the palette at [darkness] — 0 is fully light, 1 fully dark.
 *
 * The canvas does not snap between themes: the screen carries
 * `transition: --txt .62s cubic-bezier(.4,0,.2,1), --inv .62s, …` for every variable,
 * so the whole palette travels. Driving this from an animated float reproduces that.
 */
internal fun applyXPalette(darkness: Float) {
    val t = darkness.coerceIn(0f, 1f)
    fun mix(light: Color, dark: Color) = androidx.compose.ui.graphics.lerp(light, dark, t)

    xtextState.value = mix(XLightPalette.text, XDarkPalette.text)
    xinverseState.value = mix(XLightPalette.inverse, XDarkPalette.inverse)
    xonmutedState.value = mix(XLightPalette.onMuted, XDarkPalette.onMuted)
    xmutedState.value = mix(XLightPalette.muted, XDarkPalette.muted)
    xmuted2State.value = mix(XLightPalette.muted2, XDarkPalette.muted2)
    xmuted3State.value = mix(XLightPalette.muted3, XDarkPalette.muted3)
    xsurfaceState.value = mix(XLightPalette.surface, XDarkPalette.surface)
    xsurfaceraisedState.value = mix(XLightPalette.surfaceRaised, XDarkPalette.surfaceRaised)
    xsurfacesoftState.value = mix(XLightPalette.surfaceSoft, XDarkPalette.surfaceSoft)
    xsurfacealtState.value = mix(XLightPalette.surfaceAlt, XDarkPalette.surfaceAlt)
    xsurfacedeepState.value = mix(XLightPalette.surfaceDeep, XDarkPalette.surfaceDeep)
    xsurfacesunkenState.value = mix(XLightPalette.surfaceSunken, XDarkPalette.surfaceSunken)
    xborderState.value = mix(XLightPalette.border, XDarkPalette.border)
    xhairlineState.value = mix(XLightPalette.hairline, XDarkPalette.hairline)
    xghostState.value = mix(XLightPalette.ghost, XDarkPalette.ghost)
    xshadowcolorState.value = mix(XLightPalette.shadow, XDarkPalette.shadow)
    xshadowstrongState.value = mix(XLightPalette.shadowStrong, XDarkPalette.shadowStrong)
    xtopscrimState.value = mix(XLightPalette.topScrim, XDarkPalette.topScrim)
    xbgState.value = mix(XLightPalette.bg, XDarkPalette.bg)
    xGradientStopsState.value = XLightPalette.gradient.indices.map { i ->
        XLightPalette.gradient[i].first to mix(XLightPalette.gradient[i].second, XDarkPalette.gradient[i].second)
    }
}

/** Convenience for the non-animated callers. */
internal fun applyXPalette(dark: Boolean) = applyXPalette(if (dark) 1f else 0f)

/**
 * `--scr` — the full-height screen gradient every artboard sits on.
 *
 * The canvas uses `linear-gradient(179deg, …)`; at phone proportions that one-degree tilt is
 * invisible, so this is a plain vertical gradient.
 */
internal fun xScreenBrush(): Brush {
    if (xGradientStops.isEmpty()) applyXPalette(true)
    return Brush.verticalGradient(colorStops = xGradientStops.toTypedArray())
}

// ---------------------------------------------------------------------------
// Shape + elevation
// ---------------------------------------------------------------------------

internal object XShape {
    /** Chat rows, compose panel. */
    val card = RoundedCornerShape(24.dp)
    /** Settings/profile cards. */
    val panel = RoundedCornerShape(28.dp)
    /** The hero card on the chats screen. */
    val hero = RoundedCornerShape(32.dp)
    /** Bottom sheets. */
    val sheet = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    /** Avatars and thumbnails. */
    val plate = RoundedCornerShape(16.dp)
    val plateSmall = RoundedCornerShape(12.dp)
    /** Segmented-control segments. */
    val segment = RoundedCornerShape(13.dp)
    /** Chips inside a card. */
    val chip = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * `box-shadow: 0 12px 30px var(--shc)` — the one drop shadow the canvas uses.
 *
 * `Modifier.shadow` cannot draw this. It asks the platform for an elevation shadow, and
 * the platform multiplies the given colour by its own ambient (~0.04) and spot (~0.19)
 * alphas, so `rgba(0,0,0,.45)` lands at a few percent. Over the dark screen gradient that
 * is invisible: sampled on the device, the background met the card in a single pixel with
 * no falloff at all, which is why the rows read as holes cut out of the background rather
 * than cards lying on top of it.
 *
 * So the CSS shadow is drawn directly instead: the same outline, offset down by the
 * elevation and blurred, in the shadow colour at its true alpha. `BlurMaskFilter` is only
 * honoured by a hardware canvas from API 28, so older devices keep the platform shadow.
 *
 * @param elevation the CSS y-offset; the blur follows it at the canvas' 30:12 ratio.
 */
internal fun Modifier.xShadow(shape: Shape, elevation: Dp = 12.dp, shadowColor: Color = XShadowColor): Modifier =
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
        shadow(elevation, shape, ambientColor = shadowColor, spotColor = shadowColor)
    } else {
        drawWithCache {
            val offsetY = elevation.toPx()
            // CSS blur radius is 2.5 × the offset (30px against 12px). Skia's mask filter
            // takes a radius that blurs about 1.25× as wide, hence the 0.8.
            val blur = offsetY * 2.5f * 0.8f
            val outline = shape.createOutline(size, layoutDirection, this)
            val paint = Paint()
            onDrawBehind {
                paint.asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = shadowColor.toArgb()
                    maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                drawIntoCanvas { canvas ->
                    canvas.save()
                    canvas.translate(0f, offsetY)
                    when (outline) {
                        is Outline.Rounded -> canvas.drawPath(Path().apply { addRoundRect(outline.roundRect) }, paint)
                        is Outline.Generic -> canvas.drawPath(outline.path, paint)
                        is Outline.Rectangle -> canvas.drawRect(outline.rect, paint)
                    }
                    canvas.restore()
                }
            }
        }
    }

// ---------------------------------------------------------------------------
// Type scale
// ---------------------------------------------------------------------------

/**
 * The canvas has exactly five recurring text roles. Sizes are the canvas px values read as sp,
 * which is what the artboards were drawn at (390 px wide phone, 1:1 dp).
 */
internal object XType {
    /** `800 22px` — the OrionX wordmark. */
    val wordmark = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = .01.em)

    /** `800 19px` — screen titles. */
    val screenTitle = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = (-.01).em)

    /** `800 38px` — the unread counter. */
    val metric = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, letterSpacing = (-.03).em)

    /** `800 14px` — card and row titles. */
    val rowTitle = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)

    /** `500 12.5px` — row subtitles and body copy. */
    val body = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 17.sp)

    /** `600 12.5px` — body copy that needs a little more presence. */
    val bodyStrong = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 18.sp)

    /** `600 11px` — timestamps and counters. */
    val meta = TextStyle(fontFamily = OrionFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)

    /** `800 11px / .16em / uppercase` — the section label above every group of cards. */
    val sectionLabel = TextStyle(
        fontFamily = OrionFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = .16.em,
    )

    /** `800 12px / .14em / uppercase` — the large action pills. */
    val pill = TextStyle(
        fontFamily = OrionFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        letterSpacing = .14.em,
    )

    /** `800 10.5px / .12em / uppercase` — the small pills and tab labels. */
    val pillSmall = TextStyle(
        fontFamily = OrionFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.5.sp,
        letterSpacing = .12.em,
    )
}

/** Uppercasing is done in CSS in the canvas; do it once here so call sites stay clean. */
@Composable
internal fun xUpper(text: String): String = text.uppercase()
