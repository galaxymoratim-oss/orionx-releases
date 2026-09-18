package net.orionx.messenger

import android.content.Context
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/*
 * Screen transitions, transcribed from the canvas.
 *
 * The canvas animates only the *entering* screen — `animation: var(--scrAnim)` with
 * `both` — and drops the leaving one on the next render. Which keyframe it picks is
 * `scrKf` in the canvas logic:
 *
 *   растворение → scrinF        (always)
 *   глубина     → scrinT        (always)
 *   слайд       → scrinB when going back, scrin when going forward
 *
 * and the duration is the base divided by a 0.5–2× speed multiplier.
 */

/** `@keyframes` easing — `cubic-bezier(.22,1,.28,1)`, shared by every screen transition. */
private val XScreenEasing = CubicBezierEasing(.22f, 1f, .28f, 1f)

/** The canvas' «слайд / растворение / глубина» picker. */
internal enum class XScreenTransition(val key: String, val labelKey: String, val baseDurationMs: Int) {
    /** `scrin` / `scrinB` — direction-aware horizontal slide. */
    SLIDE("slide", "anim_slide", 500),

    /** `scrinF` — opacity .35→1 with a 1.012→1 scale, no travel. */
    DISSOLVE("dissolve", "anim_dissolve", 420),

    /** `scrinT` — rises 16px with a .965→1 scale. */
    DEPTH("depth", "anim_depth", 540),
    ;

    companion object {
        fun fromKey(key: String?): XScreenTransition = entries.firstOrNull { it.key == key } ?: SLIDE
    }
}

/**
 * Mode and speed, kept locally like [XHaloSettings] — they describe this device's
 * motion, not the account, so they never go to the server.
 */
@Stable
internal class XMotionSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("orionx_redesign", Context.MODE_PRIVATE)

    var mode by mutableStateOf(XScreenTransition.fromKey(prefs.getString("anim_mode", null)))
        private set

    /** `Math.max(0.5, Math.min(2, …))` in the canvas. */
    var speed by mutableFloatStateOf(prefs.getFloat("anim_speed", 1f).coerceIn(.5f, 2f))
        private set

    fun chooseMode(next: XScreenTransition) {
        mode = next
        prefs.edit().putString("anim_mode", next.key).apply()
    }

    fun chooseSpeed(next: Float) {
        speed = next.coerceIn(.5f, 2f)
        prefs.edit().putFloat("anim_speed", speed).apply()
    }

    /** `spdWord` — the canvas labels the slider in words, not numbers. */
    val speedWordKey: String
        get() = when {
            speed < .85f -> "anim_speed_slow"
            speed < 1.2f -> "anim_speed_normal"
            speed < 1.65f -> "anim_speed_fast"
            else -> "anim_speed_instant"
        }
}

/**
 * What each screen last loaded, kept for the life of the process.
 *
 * Navigation disposes a screen's composables, so every `remember` inside it dies and
 * its `LaunchedEffect` re-runs on the way back — which is why re-entering a chat used
 * to blank out and reload. Holding the last page here lets a screen paint immediately
 * from what it had and refresh behind that, instead of starting from nothing.
 *
 * Entries are least-recently-used and capped, so a long session cannot grow without
 * bound. [clear] runs on sign-out, since the data belongs to that account.
 */
internal object XScreenCache {
    private const val MAX_CONVERSATIONS = 12

    private val messages = object : LinkedHashMap<String, List<net.orionx.messenger.cloud.CloudMessage>>(0, .75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<net.orionx.messenger.cloud.CloudMessage>>?,
        ): Boolean = size > MAX_CONVERSATIONS
    }

    private val rosters = object : LinkedHashMap<String, List<net.orionx.messenger.cloud.CloudMember>>(0, .75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<net.orionx.messenger.cloud.CloudMember>>?,
        ): Boolean = size > MAX_CONVERSATIONS
    }

    fun messagesOf(conversationId: String): List<net.orionx.messenger.cloud.CloudMessage>? =
        synchronized(messages) { messages[conversationId] }

    fun putMessages(conversationId: String, list: List<net.orionx.messenger.cloud.CloudMessage>) {
        synchronized(messages) { messages[conversationId] = list }
    }

    fun rosterOf(conversationId: String): List<net.orionx.messenger.cloud.CloudMember>? =
        synchronized(rosters) { rosters[conversationId] }

    fun putRoster(conversationId: String, list: List<net.orionx.messenger.cloud.CloudMember>) {
        synchronized(rosters) { rosters[conversationId] = list }
    }

    /**
     * Sender display names for the chats list.
     *
     * The canvas prefixes a group row's preview with who wrote it («артём: релиз в
     * четверг»); the conversation view returns only the id. Names are stable, so one
     * lookup per sender per process is enough.
     */
    private val senderNames = HashMap<String, String>()

    fun senderNamesOf(ids: Collection<String>): Map<String, String> =
        synchronized(senderNames) { ids.mapNotNull { id -> senderNames[id]?.let { id to it } }.toMap() }

    fun unknownSenders(ids: Collection<String>): List<String> =
        synchronized(senderNames) { ids.filter { it.isNotBlank() && it !in senderNames } }

    fun putSenderNames(names: Map<String, String>) {
        synchronized(senderNames) { senderNames.putAll(names) }
    }

    /** The profile screen's three tiles, so re-entering does not flash `0 0 0`. */
    private var counters: Pair<String, Triple<Int, Int, Int>>? = null

    fun countersOf(userId: String): Triple<Int, Int, Int>? =
        synchronized(this) { counters?.takeIf { it.first == userId }?.second }

    fun putCounters(userId: String, value: Triple<Int, Int, Int>) {
        synchronized(this) { counters = userId to value }
    }

    fun clear() {
        synchronized(messages) { messages.clear() }
        synchronized(rosters) { rosters.clear() }
        synchronized(senderNames) { senderNames.clear() }
        synchronized(this) { counters = null }
    }
}

/**
 * Decoded remote images, kept in memory for the life of the process.
 *
 * `RemoteImage` used to re-download on every composition, so an avatar blinked grey
 * and fetched itself again each time its screen was rebuilt. Sized by bytes rather
 * than entry count, because one chat photo can outweigh a hundred avatars.
 */
internal object XImageCache {
    private val cache = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(
        // An eighth of the heap, in KB — the usual budget for a bitmap cache.
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceAtLeast(4 * 1024),
    ) {
        override fun sizeOf(key: String, value: androidx.compose.ui.graphics.ImageBitmap): Int =
            (value.width.toLong() * value.height.toLong() * 4L / 1024L).toInt().coerceAtLeast(1)
    }

    /*
     * Disk half of the cache. The memory half dies with the process, so before this every
     * launch re-downloaded the avatar in the header and in every row — that is the empty
     * circle visible for the first second after a cold start. Bytes are stored verbatim
     * under `cache/xi`, so a relaunch decodes locally instead of going to the network.
     */
    private const val DISK_BUDGET_BYTES = 48L * 1024 * 1024

    @Volatile
    private var diskDir: java.io.File? = null

    fun attach(context: Context) {
        if (diskDir != null) return
        diskDir = java.io.File(context.cacheDir, "xi").apply { mkdirs() }
    }

    fun get(url: String): androidx.compose.ui.graphics.ImageBitmap? = cache.get(url)

    fun put(url: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        cache.put(url, bitmap)
    }

    /** Blocking — call from `Dispatchers.IO`. Null when this url was never stored. */
    fun readDisk(url: String): androidx.compose.ui.graphics.ImageBitmap? {
        val file = fileFor(url)?.takeIf { it.isFile } ?: return null
        val decoded = runCatching {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }.getOrNull() ?: return null
        // Touch it so the trim below evicts the genuinely stale entries first.
        runCatching { file.setLastModified(System.currentTimeMillis()) }
        cache.put(url, decoded)
        return decoded
    }

    /** Blocking — call from `Dispatchers.IO`. */
    fun writeDisk(url: String, bytes: ByteArray) {
        val file = fileFor(url) ?: return
        runCatching {
            val tmp = java.io.File(file.absolutePath + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) tmp.delete()
        }
        trim()
    }

    fun clear() {
        cache.evictAll()
        runCatching { diskDir?.listFiles()?.forEach { it.delete() } }
    }

    private fun fileFor(url: String): java.io.File? {
        val dir = diskDir ?: return null
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return java.io.File(dir, digest.joinToString("") { "%02x".format(it) })
    }

    /** Oldest-first eviction once the folder passes the budget. */
    private fun trim() {
        val files = diskDir?.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= DISK_BUDGET_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= DISK_BUDGET_BYTES) return
            total -= file.length()
            file.delete()
        }
    }
}

/**
 * A canvas-style tap target.
 *
 * The canvas never draws a highlight under a press — controls are plain
 * `cursor:pointer`, and where they react at all it is
 * `style-active="transform:scale(…)"`, a squeeze that lasts only while the finger is
 * down. Compose's default `clickable` adds a Material ripple instead, which is the
 * grey blob that appears under the dock and the tabs; `indication = null` removes it.
 *
 * @param pressedScale the canvas' `style-active` scale, or 1f for controls that have none.
 */
@Composable
internal fun Modifier.xPressable(
    pressedScale: Float = 1f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // CSS `:active` with no transition snaps; 90 ms reads the same but avoids a hard pop.
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = tween(90, easing = LinearEasing),
        label = "press-scale",
    )
    return this
        .then(
            if (pressedScale == 1f) Modifier
            else Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        )
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * `dep()` in the canvas: the four root screens sit at 0, settings at 1, everything
 * else at 2. Direction is then decided by comparing depths.
 */
private fun xNavDepth(destination: XDestination): Int = when (destination) {
    XDestination.Root, XDestination.Oria, XDestination.Profile -> 0
    is XDestination.SettingsPanel -> 1
    else -> 2
}

/**
 * `TABS = ['chats','search','oria','profile']` — among the root screens the canvas
 * orders the direction by tab index. `Root` holds chats and search, so it takes the
 * first slot.
 */
private fun xRootOrder(destination: XDestination): Int = when (destination) {
    XDestination.Root -> 0
    XDestination.Oria -> 2
    XDestination.Profile -> 3
    else -> -1
}

/**
 * +1 forward, -1 back. Note the canvas' own quirk: two screens at depth 2 always
 * compare equal, so moving between them — group info → settings → admins and back —
 * is always treated as forward.
 */
internal fun xNavDirection(from: XDestination, to: XDestination): Int {
    val a = xNavDepth(from)
    val b = xNavDepth(to)
    return if (a == 0 && b == 0) {
        if (xRootOrder(to) < xRootOrder(from)) -1 else 1
    } else {
        if (b < a) -1 else 1
    }
}

/**
 * The same rule for the tabs inside the root shell. `chats` and `search` are both
 * root screens, so their order decides; `settings` sits one level deeper, so it is
 * entered forward and left backward.
 */
internal fun xTabDirection(from: XTab, to: XTab): Int {
    val a = if (from == XTab.SETTINGS) 1 else 0
    val b = if (to == XTab.SETTINGS) 1 else 0
    if (a == 0 && b == 0) {
        val fromOrder = if (from == XTab.SEARCH) 1 else 0
        val toOrder = if (to == XTab.SEARCH) 1 else 0
        return if (toOrder < fromOrder) -1 else 1
    }
    return if (b < a) -1 else 1
}

/**
 * Builds the transform for one navigation.
 *
 * Only the entering screen animates: the canvas removes the outgoing one immediately,
 * and because the gradient background lives outside the animated container, the new
 * screen visibly fades in over it. A 1 ms fade-out reproduces that.
 */
internal fun xScreenTransition(
    mode: XScreenTransition,
    speed: Float,
    direction: Int,
    reduceMotion: Boolean,
    density: Density,
): ContentTransform {
    if (reduceMotion) {
        return fadeIn(snap()) togetherWith fadeOut(snap())
    }

    val duration = (mode.baseDurationMs / speed.coerceIn(.5f, 2f)).toInt().coerceAtLeast(1)
    val exit = fadeOut(tween(durationMillis = 1))

    // `0%{opacity:.42} 35%{opacity:.86} 100%{opacity:1}` — a three-stop curve, so the
    // alpha needs keyframes rather than a plain tween.
    fun slideAlpha() = fadeIn(
        animationSpec = keyframes {
            durationMillis = duration
            .42f at 0 using XScreenEasing
            .86f at (duration * .35f).toInt()
            1f at duration
        }
    )

    return when (mode) {
        XScreenTransition.SLIDE -> {
            // `translateX(±30px) scale(.988)`
            val travel = with(density) { 30.dp.roundToPx() } * if (direction < 0) -1 else 1
            (slideInHorizontally(tween(duration, easing = XScreenEasing)) { travel } +
                slideAlpha() +
                scaleIn(initialScale = .988f, animationSpec = tween(duration, easing = XScreenEasing))) togetherWith exit
        }

        XScreenTransition.DISSOLVE -> {
            // `0%{opacity:.35;transform:scale(1.012)}` — settles down onto the screen.
            (fadeIn(initialAlpha = .35f, animationSpec = tween(duration, easing = XScreenEasing)) +
                scaleIn(initialScale = 1.012f, animationSpec = tween(duration, easing = XScreenEasing))) togetherWith exit
        }

        XScreenTransition.DEPTH -> {
            // `translateY(16px) scale(.965)`, opacity .4 → .85 at 35% → 1
            val rise = with(density) { 16.dp.roundToPx() }
            (slideInVertically(tween(duration, easing = XScreenEasing)) { rise } +
                fadeIn(
                    animationSpec = keyframes {
                        durationMillis = duration
                        .4f at 0 using XScreenEasing
                        .85f at (duration * .35f).toInt()
                        1f at duration
                    }
                ) +
                scaleIn(initialScale = .965f, animationSpec = tween(duration, easing = XScreenEasing))) togetherWith exit
        }
    }
}
