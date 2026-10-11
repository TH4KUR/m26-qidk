package com.megathon.incident.speech

import com.megathon.incident.testing.SpeechEngine
import com.megathon.incident.testing.Speaker
import com.megathon.incident.testing.Transcript
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Stops the mic from hearing the app's own voice: the loop mutes it while the speaker talks. */
interface MicGate {
    fun mute()
    fun unmute()
}

/** What the app says after a turn; null (or blank) means stay quiet and keep listening. */
data class TurnReply(val speak: String? = null)

/** The app's side of a turn: extraction, follow-up questions, submit. [command] is set when the user spoke one. */
fun interface TurnHandler {
    suspend fun handle(transcript: Transcript, command: VoiceCommand?): TurnReply
}

/**
 * Hands-free conversation loop (plan task A10): mic chunks in, whole utterances out to the speech engine,
 * the transcript to the app's [TurnHandler], the reply spoken with the mic muted, then listening again.
 *
 * - A blank transcript is answered with [blankPrompt] and never reaches the handler (so no LLM call).
 * - An engine or handler failure (an `Error` such as UnsatisfiedLinkError from a native library included) is recorded in [errors], spoken as [failurePrompt], and the loop continues.
 * - When the chunk flow ends (the user tapped stop in tap-to-talk), an unfinished utterance is still processed.
 * - The segmenter is flushed after every spoken reply, so the tail of the app's own voice is never transcribed.
 */
class HandsFreeLoop(
    private val engine: SpeechEngine,
    private val speaker: Speaker,
    private val gate: MicGate,
    private val handler: TurnHandler,
    private val language: () -> String,
    private val segmenter: UtteranceSegmenter = UtteranceSegmenter(),
    private val graceMs: Long = 400,
    private val blankPrompt: String = "I didn't catch that. Could you say it again?",
    private val failurePrompt: String = "Something went wrong on my side. Could you say it again?",
) {
    private val failed = ArrayDeque<String>()

    /** The most recent failures (at most [MAX_ERRORS]); a native library failing every turn must not grow this forever. */
    val errors: List<String> get() = synchronized(failed) { failed.toList() }

    private fun record(message: String) = synchronized(failed) {
        failed += message
        while (failed.size > MAX_ERRORS) failed.removeFirst()
    }

    suspend fun run(chunks: Flow<ShortArray>) {
        chunks.collect { chunk -> segmenter.push(chunk)?.let { handle(it) } }
        segmenter.flush()?.let { handle(it) }
    }

    private suspend fun handle(pcm: ShortArray) {
        val transcript = try {
            engine.transcribe(pcm, language())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            record("speech engine ${engine.name}: ${error.message}")
            say(failurePrompt)
            return
        }
        if (transcript.text.isBlank()) {
            say(blankPrompt)
            return
        }
        val reply = try {
            handler.handle(transcript, VoiceCommandParser.parse(transcript.text))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            record("turn handler: ${error.message}")
            say(failurePrompt)
            return
        }
        reply.speak?.takeIf { it.isNotBlank() }?.let { say(it) }
    }

    private suspend fun say(text: String) {
        gate.mute()
        try {
            speaker.speak(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            record("speaker: ${error.message}")
        } finally {
            withContext(NonCancellable) {
                if (graceMs > 0) delay(graceMs)
                segmenter.flush()
                gate.unmute()
            }
        }
    }

    private companion object {
        const val MAX_ERRORS = 20
    }
}
