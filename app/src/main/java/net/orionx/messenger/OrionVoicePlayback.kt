package net.orionx.messenger

import android.media.MediaPlayer
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Общий плеер голосовых сообщений на уровне процесса.
 *
 * Раньше каждый пузырёк владел своим MediaPlayer внутри композиции: выход из чата
 * или просто прокрутка строки за экран убивали плеер — голосовая резко обрывалась.
 * Теперь игрок живёт здесь, а строка только наблюдает за состоянием, поэтому
 * воспроизведение переживает и уход с экрана, и скролл ленты.
 */
@Stable
internal object XVoicePlayback {

    /** Вызывается при старте новой голосовой — приложение глушит музыку. */
    var onVoiceStarted: (() -> Unit)? = null

    private var player: MediaPlayer? = null
    private var prepared = false

    var activeUrl by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var durationMs by mutableIntStateOf(0)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var failed by mutableStateOf(false)
        private set

    /** Играет / ставит на паузу; запуск новой голосовой глушит музыку. */
    fun toggle(url: String) {
        if (activeUrl == url) {
            val p = player ?: return
            runCatching {
                if (playing) p.pause() else p.start()
                playing = !playing
            }.onFailure { failed = true }
            return
        }
        start(url)
    }

    fun seekFraction(url: String, fraction: Float) {
        val p = player ?: return
        if (activeUrl != url || durationMs <= 0) return
        val target = (durationMs * fraction.coerceIn(0f, 1f)).toInt()
        runCatching { p.seekTo(target) }
        positionMs = target
    }

    /** Позиция для бегущего таймкода; вызывается из композиции активной строки. */
    fun refreshPosition() {
        if (!playing) return
        val current = runCatching { player?.currentPosition }.getOrNull() ?: return
        positionMs = current
    }

    private fun start(url: String) {
        releasePlayer()
        activeUrl = url
        playing = false
        durationMs = 0
        positionMs = 0
        failed = false
        onVoiceStarted?.invoke()
        val p = MediaPlayer()
        player = p
        runCatching {
            p.setDataSource(url)
            p.setOnPreparedListener { mp ->
                if (player !== p) return@setOnPreparedListener
                prepared = true
                durationMs = mp.duration.coerceAtLeast(0)
                mp.start()
                playing = true
            }
            p.setOnCompletionListener { mp ->
                if (player !== p) return@setOnCompletionListener
                playing = false
                positionMs = 0
                runCatching { mp.seekTo(0) }
            }
            p.setOnErrorListener { _, _, _ ->
                if (player === p) {
                    failed = true
                    prepared = false
                    playing = false
                }
                true
            }
            p.prepareAsync()
        }.onFailure {
            failed = true
            prepared = false
            playing = false
        }
    }

    private fun releasePlayer() {
        player?.let { p ->
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        player = null
        prepared = false
    }
}
