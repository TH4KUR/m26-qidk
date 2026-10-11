package com.megathon.incident.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Decides when a growing transcript is worth another model call: at most one [extract] runs and one snapshot waits, and a
 * waiting snapshot is replaced by any newer one, so the model never works through a queue of old text. Fewer than
 * [minNewWords] new words since the last call is not worth it. One recording per scheduler.
 */
class PreviewScheduler(
    private val scope: CoroutineScope,
    private val minNewWords: Int = 3,
    private val extract: suspend (String) -> Unit,
) {
    private val lock = Any()
    private var running = false
    private var stopped = false
    private var pending: String? = null
    private var lastText = ""
    private var lastWords = 0

    /** Offer the whole transcript so far (stable text plus the unsettled tail). Safe to call from any thread. */
    fun offer(text: String) {
        val start = synchronized(lock) {
            if (stopped) return
            if (running) { pending = text; return }
            takeIfWorthIt(text)
        } ?: return
        launchFor(start)
    }

    /** Stop was tapped: nothing speculative starts again. The call already running finishes on its own. */
    fun stop() = synchronized(lock) { stopped = true; pending = null }

    private fun takeIfWorthIt(text: String): String? {
        val words = count(text)
        if (text == lastText || words - lastWords < minNewWords) return null
        lastText = text
        lastWords = words
        running = true
        return text
    }

    private fun launchFor(text: String) {
        scope.launch {
            try {
                extract(text)
            } catch (e: CancellationException) {
                // The call was skipped or cancelled; fall through to release the slot.
            } catch (e: Exception) {
                // A failed preview costs only the preview; the next snapshot tries again.
            } finally {
                val next = synchronized(lock) {
                    running = false
                    val waiting = pending
                    pending = null
                    if (stopped || waiting == null) null else takeIfWorthIt(waiting)
                }
                next?.let(::launchFor)
            }
        }
    }

    private fun count(text: String) = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
}
