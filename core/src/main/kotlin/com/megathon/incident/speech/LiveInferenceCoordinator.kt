package com.megathon.incident.speech

import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.SpeechEngine
import com.megathon.incident.testing.Transcript
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Transcribes a recording while it is still being made (one coordinator per recording).
 *
 * The microphone thread calls [offer] with each chunk; it copies the audio into the recording buffer and never waits. A worker
 * ([run]) decodes bounded windows through [engine] when enough new speech has arrived or a phrase has ended, and publishes
 * the growing transcript. This is incremental batch-window decoding with the existing recognizer, not native streaming ASR.
 *
 * - Audio is never dropped. Previews are: at most one decode runs and one request waits, and a waiting request is
 *   rebuilt from the audio present when it finally runs (coalescing), so no queue of stale windows builds up.
 * - Every decode holds [lane], the one accelerator lane shared with the language model and the tag work.
 * - [finish] outranks speculative work: pending previews are skipped, an in-flight decode is allowed to end (a native call
 *   is not cancelled underneath itself), then the tail is decoded. A failed or ambiguous preview, or a backlog larger than
 *   one window, falls back to one whole-recording decode, which is counted in [Final.decodeCalls].
 */
class LiveInferenceCoordinator(
    private val engine: SpeechEngine,
    private val language: String,
    private val config: Config = Config(),
    private val lane: Mutex = Mutex(),
    private val onSnapshot: (Snapshot) -> Unit = {},
) {
    /** Starting values, not measured optimums. */
    data class Config(
        val sampleRate: Int = 16_000,
        /** New audio that triggers a preview, when it holds speech. */
        val triggerMs: Int = 3_000,
        /** A phrase that ended with at least this much new audio triggers a preview early. */
        val minPhraseMs: Int = 1_000,
        /** Audio shared with the previous window, so a word on the boundary is heard whole. */
        val overlapMs: Int = 750,
        val maxWindowMs: Int = 6_000,
        val maxRecordingMs: Int = 30_000,
    )

    /** The transcript so far. [stable] will not change unless a later window disagrees; [tail] is still being heard. */
    data class Snapshot(val revision: Int, val stable: String, val tail: String, val windows: Int)

    class Final(
        val text: String,
        /** The whole recording, exactly once, for the signal engines and the stored proof. */
        val pcm: ShortArray,
        val fullPass: Boolean,
        /** Every engine call made for this recording, previews and final together. */
        val decodeCalls: Int,
        /** Engine time summed over every call (previews and final), and where the engine said it ran; null when nothing was decoded. */
        val decodeMs: Long,
        val compute: Compute?,
    )

    private val lock = Any()
    private val triggers = Channel<Unit>(Channel.CONFLATED)
    private val transcript = IncrementalTranscript()
    private val vad = EndOfSpeechDetector(EndOfSpeechDetector.Config(sampleRate = config.sampleRate))
    private val capSamples = config.maxRecordingMs * config.sampleRate / 1000
    private val overlapSamples = config.overlapMs * config.sampleRate / 1000
    private val windowSamples = config.maxWindowMs * config.sampleRate / 1000
    private val triggerSamples = config.triggerMs * config.sampleRate / 1000
    private val phraseSamples = config.minPhraseMs * config.sampleRate / 1000
    private val maxOverlapWords = Math.ceil(config.overlapMs / 1000.0 * WORDS_PER_SECOND).toInt() + 2

    private var pcm = ShortArray(config.sampleRate * 10)
    private var total = 0
    private var decodedEnd = 0
    /** Sample index after the last loud frame heard so far, and the same for the audio already decoded. */
    private var lastVoicedEnd = 0
    private var decodedVoicedEnd = 0
    private var heardVoice = false
    private var phraseEnded = false
    private var previewFailed = false
    private var windows = 0
    private var calls = 0
    private var decodeMs = 0L
    private var compute: Compute? = null
    private var stopping = false
    private var cancelled = false
    private var result: Final? = null

    /** Called from the audio thread: copies [chunk] (the caller reuses its buffer) and returns at once. */
    fun offer(chunk: ShortArray, n: Int = chunk.size) {
        synchronized(lock) {
            if (stopping || cancelled || n <= 0) return
            val take = minOf(n, capSamples - total)
            if (take <= 0) return
            val copy = chunk.copyOf(take)
            if (total + take > pcm.size) pcm = pcm.copyOf(minOf(capSamples, maxOf(pcm.size * 2, total + take)))
            copy.copyInto(pcm, total)
            total += take
            val event = vad.accept(copy)
            if (vad.lastFrameLoud) lastVoicedEnd = total
            when (event) {
                VadEvent.SPEECH_STARTED -> heardVoice = true
                VadEvent.SPEECH_ENDED, VadEvent.MAX_LENGTH -> { heardVoice = true; phraseEnded = true }
                else -> if (vad.currentVoicedMs > 0) heardVoice = true
            }
            val fresh = total - decodedEnd
            if (heardVoice && (fresh >= triggerSamples || (phraseEnded && fresh >= phraseSamples))) triggers.trySend(Unit)
        }
    }

    /** The preview worker: run it in the recording's scope; it returns after [finish] or [cancel]. */
    suspend fun run() {
        for (ignored in triggers) preview()
    }

    private suspend fun preview() {
        lane.withLock {
            val job = synchronized(lock) {
                if (stopping || cancelled) return
                val start = if (decodedEnd == 0) 0 else maxOf(0, decodedEnd - overlapSamples)
                val end = minOf(total, start + windowSamples)
                if (end <= decodedEnd) return
                if (end == total) { // a window cut at the cap leaves audio behind: it must stay "voiced" so Stop (or the next window) decodes it
                    heardVoice = false
                    phraseEnded = false
                }
                Window(start, end, pcm.copyOfRange(start, end), minOf(lastVoicedEnd, end))
            }
            val heard = try {
                counted(engine.transcribe(job.pcm, language))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(lock) { previewFailed = true }
                return
            }
            val snapshot = synchronized(lock) {
                if (cancelled) return
                windows++
                val words = heard.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                val tail = Math.ceil(words * overlapSamples.toDouble() / (job.end - job.start)).toInt().coerceAtLeast(1)
                // Shared audio only counts when it holds speech: after a pause the overlap is silence and there is nothing to match.
                val overlapped = decodedEnd > 0 && decodedVoicedEnd > job.start
                decodedEnd = job.end
                decodedVoicedEnd = job.voicedEnd
                val changed = transcript.addWindow(heard, overlapped, maxOverlapWords, tailWords = tail)
                if (!stopping && heardVoice && total - decodedEnd >= triggerSamples) triggers.trySend(Unit) // drain a backlog without waiting for more audio
                if (changed) Snapshot(transcript.revision, transcript.stableText, transcript.tailText, windows) else null
            }
            snapshot?.let(onSnapshot)
        }
    }

    private fun counted(t: Transcript): String {
        synchronized(lock) {
            calls++
            decodeMs += t.elapsedMs
            compute = t.compute
        }
        return t.text
    }

    /** Stop was tapped: decode what is left and return the final transcript. Safe to call twice; the second call decodes nothing. */
    suspend fun finish(): Final {
        synchronized(lock) {
            stopping = true
            triggers.close()
        }
        return lane.withLock {
            result?.let { return it }
            val plan = synchronized(lock) { plan() }
            val text = when (plan) {
                is Plan.Nothing -> transcript.text
                is Plan.Tail -> tail(plan)
                Plan.Whole -> whole()
            }
            val out = synchronized(lock) {
                Final(text, pcm.copyOf(total), plan == Plan.Whole || fellBack, calls, decodeMs, compute)
            }
            result = out
            out
        }
    }

    private var fellBack = false

    private sealed interface Plan {
        data object Nothing : Plan
        data object Whole : Plan
        data class Tail(val start: Int, val end: Int) : Plan
    }

    private fun plan(): Plan {
        if (total == 0) return Plan.Nothing
        if (previewFailed || transcript.ambiguous) return Plan.Whole
        if (decodedEnd == 0 && !heardVoice) return Plan.Nothing // silence is never sent to the recognizer
        if (decodedEnd == 0) return if (total <= windowSamples) Plan.Tail(0, total) else Plan.Whole
        if (total == decodedEnd || !heardVoice) return Plan.Nothing
        val start = maxOf(0, decodedEnd - overlapSamples)
        return if (total - start <= windowSamples) Plan.Tail(start, total) else Plan.Whole
    }

    private suspend fun tail(plan: Plan.Tail): String {
        val (audio, overlapped) = synchronized(lock) { pcm.copyOfRange(plan.start, plan.end) to (decodedEnd > 0 && decodedVoicedEnd > plan.start) }
        val heard = try {
            counted(engine.transcribe(audio, language))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return whole()
        }
        return synchronized(lock) {
            transcript.addWindow(heard, overlapped, maxOverlapWords)
            transcript.finish()
            if (transcript.ambiguous) null else transcript.text
        } ?: whole()
    }

    private suspend fun whole(): String {
        val all = synchronized(lock) { pcm.copyOf(total) }
        val heard = counted(engine.transcribe(all, language)).trim()
        synchronized(lock) { fellBack = true }
        return heard
    }

    /** The recording was abandoned: nothing more is published. A decode already running ends on its own and is discarded. */
    fun cancel() {
        synchronized(lock) {
            cancelled = true
            triggers.close()
        }
    }

    private class Window(val start: Int, val end: Int, val pcm: ShortArray, val voicedEnd: Int)

    private companion object {
        /** Fast speech is about 4-5 words a second; used only to bound how many words a shared region can hold. */
        const val WORDS_PER_SECOND = 6.0
    }
}
