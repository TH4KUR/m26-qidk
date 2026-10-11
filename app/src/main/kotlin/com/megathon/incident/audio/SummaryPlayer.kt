package com.megathon.incident.audio

import android.media.MediaPlayer
import java.io.File

/**
 * Plays one rendered summary (a WAV file) with pause and seek. It is a thin wrapper: the screen asks it for the position
 * about five times a second and tells it what the reporter did. Everything is released with [release].
 */
class SummaryPlayer {
    private var player: MediaPlayer? = null
    @Volatile private var completed: (() -> Unit)? = null

    var durationMs: Int = 0
        private set

    /** Opens [file]. Returns false when it cannot be played (nothing is left half open). */
    fun load(file: File, onCompleted: () -> Unit): Boolean {
        release()
        return runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.setOnCompletionListener { completed?.invoke() }
            completed = onCompleted
            durationMs = mp.duration.coerceAtLeast(0)
            player = mp
            true
        }.getOrElse { false }
    }

    fun play() { runCatching { player?.start() } }
    fun pause() { runCatching { if (player?.isPlaying == true) player?.pause() } }
    fun seekTo(ms: Int) { runCatching { player?.seekTo(ms.coerceIn(0, durationMs)) } }
    fun positionMs(): Int = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
    fun isPlaying(): Boolean = runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun release() {
        runCatching { player?.release() }
        player = null
        completed = null
        durationMs = 0
    }
}
