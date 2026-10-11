package com.megathon.incident.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.Speaker
import com.megathon.incident.tts.PiperLocation
import com.megathon.incident.tts.PiperModelFiles
import com.megathon.incident.tts.PiperModelLocator
import com.megathon.incident.tts.PiperVoice
import com.megathon.incident.tts.PiperVoices
import com.megathon.incident.tts.TtsBackend
import com.megathon.incident.tts.TtsBackendException
import com.megathon.incident.tts.TtsChunker
import com.megathon.incident.tts.TtsPcm
import com.megathon.incident.tts.TtsTextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Piper (open-weight VITS voices) through the sherpa-onnx native library, in this process, on the **CPU**
 * (provider "cpu"; nothing here touches QNN or the NPU, so the compute label is always [Compute.CPU]).
 *
 * Nothing is loaded until the first utterance: the native library is loaded and a voice is built on first use, so an
 * app that never selects Piper never loads the library. Every failure to load or run is reported as a
 * [TtsBackendException], and the caller ([com.megathon.incident.tts.RoutingSpeaker]) falls back to the Android voice.
 *
 * [speak] returns only when the audio has been played (the hands-free loop mutes the mic meanwhile) or [stop] was called.
 * A new [speak] while one is playing stops the old one first, like the system voice's QUEUE_FLUSH.
 *
 * @param requireVerified in a release build the model's size and SHA-256 must match the catalogue ([PiperVoices]);
 *   a debug build only needs the files to be present.
 */
class PiperSpeaker(
    private val context: Context,
    private val requireVerified: Boolean,
    private val language: () -> String = { "en" },
) : TtsBackend, Speaker {
    override val compute = Compute.CPU
    override val offline: Boolean? = true

    private val root = File(context.filesDir, "tts")
    private val lock = ReentrantLock()
    private val generation = AtomicInteger(0)
    private val located = java.util.concurrent.ConcurrentHashMap<String, PiperModelFiles>()

    // Guarded by [lock].
    private var loadedVoice: PiperVoice? = null
    private var loadedTts: OfflineTts? = null

    @Volatile private var libraryError: String? = null
    @Volatile private var closed = false
    @Volatile private var track: AudioTrack? = null

    override suspend fun speak(text: String) = speak(text, language())

    override fun label(language: String): String = PiperVoices.forLanguage(language)?.label ?: "Piper TTS (sherpa-onnx)"

    override suspend fun unavailableReason(language: String): String? = withContext(Dispatchers.IO) {
        libraryError?.let { return@withContext it }
        val voice = PiperVoices.forLanguage(language)
            ?: return@withContext "there is no Piper voice for \"$language\" (Piper has English and Hindi here, not Telugu)"
        if (!File(context.applicationInfo.nativeLibraryDir, LIBRARY_FILE).exists()) {
            return@withContext "the sherpa-onnx library is not in the app (run app/scripts/tts_bundle.sh stage, then rebuild)"
        }
        when (val found = locate(voice)) {
            is PiperLocation.Found -> null
            is PiperLocation.Missing ->
                "the Piper voice files are not on the device: ${found.missing.joinToString(", ")} (run app/scripts/tts_bundle.sh push)"
            is PiperLocation.Corrupt -> "the Piper voice failed its check: ${found.reason}"
        }
    }

    override suspend fun speak(text: String, language: String) {
        val voice = PiperVoices.forLanguage(language)
            ?: throw TtsBackendException("there is no Piper voice for \"$language\"")
        val chunks = TtsChunker.split(TtsTextNormalizer.normalize(text, voice.language))
        if (chunks.isEmpty()) return

        interrupt()
        val mine = generation.incrementAndGet()
        withContext(Dispatchers.Default) {
            val t0 = System.nanoTime()
            val files = withContext(Dispatchers.IO) { locate(voice) }
            val modelFiles = when (files) {
                is PiperLocation.Found -> files.files
                is PiperLocation.Missing -> throw TtsBackendException("the Piper voice files are missing: ${files.missing.joinToString(", ")}")
                is PiperLocation.Corrupt -> throw TtsBackendException("the Piper voice failed its check: ${files.reason}")
            }
            val rate = lock.withLock { engineFor(voice, modelFiles).sampleRate() }
            val loadedMs = (System.nanoTime() - t0) / 1_000_000
            val audio = newTrack(rate)
            track = audio
            var framesWritten = 0
            var synthNs = 0L
            var firstAudioMs = -1L
            try {
                if (generation.get() != mine) return@withContext
                audio.play()
                for (chunk in chunks) {
                    if (generation.get() != mine || !currentCoroutineContext().isActive) return@withContext
                    val s0 = System.nanoTime()
                    val samples = lock.withLock {
                        if (closed) throw TtsBackendException("the Piper voice was shut down", permanent = true)
                        val engine = engineFor(voice, modelFiles)
                        try {
                            engine.generate(chunk, 0, SPEED).samples
                        } catch (failure: LinkageError) {
                            // A Kotlin binding that does not match the native library (NoSuchFieldError and the like) is an
                            // Error, not an Exception: uncaught it would end the app instead of falling back to the Android voice.
                            // It repeats on every call, so it is permanent and the library is not tried again.
                            val reason = "the sherpa-onnx library does not match its Kotlin binding: ${failure.message}"
                            libraryError = reason
                            throw TtsBackendException(reason, spokeSomething = framesWritten > 0, permanent = true, cause = failure)
                        } catch (failure: Exception) {
                            throw TtsBackendException("Piper could not speak: ${failure.message}", spokeSomething = framesWritten > 0, cause = failure)
                        }
                    }
                    synthNs += System.nanoTime() - s0
                    if (samples.isEmpty()) continue
                    val pcm = TtsPcm.toPcm16(samples)
                    var offset = 0
                    while (offset < pcm.size) {
                        if (generation.get() != mine || !currentCoroutineContext().isActive) return@withContext
                        val n = audio.write(pcm, offset, minOf(pcm.size - offset, WRITE_SLICE))
                        if (n < 0) throw TtsBackendException("the audio output failed (code $n)", spokeSomething = framesWritten > 0)
                        offset += n
                    }
                    if (firstAudioMs < 0) firstAudioMs = (System.nanoTime() - t0) / 1_000_000
                    framesWritten += pcm.size
                }
                // Everything is queued; wait for the speaker to play it, so the caller can unmute the mic afterwards.
                val deadline = System.nanoTime() + (TtsPcm.durationMs(framesWritten, rate) + TAIL_MS) * 1_000_000
                while (generation.get() == mine && currentCoroutineContext().isActive &&
                    audio.playbackHeadPosition < framesWritten && System.nanoTime() < deadline
                ) delay(POLL_MS)
            } finally {
                runCatching { audio.pause(); audio.flush(); audio.stop() }
                runCatching { audio.release() }
                if (track === audio) track = null
            }
            val audioMs = TtsPcm.durationMs(framesWritten, rate)
            val synthMs = synthNs / 1_000_000
            Log.i(
                TAG,
                "piper voice=${voice.folder} chars=${text.length} chunks=${chunks.size} loadMs=$loadedMs firstAudioMs=$firstAudioMs " +
                    "synthMs=$synthMs audioMs=$audioMs rtf=${if (audioMs > 0) "%.2f".format(synthMs.toDouble() / audioMs) else "n/a"} threads=$THREADS",
            )
        }
    }

    /** Stops what is playing now (the pending [speak] returns). Safe to call from any thread. */
    override fun stop() = interrupt()

    override fun shutdown() {
        closed = true
        interrupt()
        // Freeing waits for a synthesis in progress, so it is done off the caller's thread.
        thread(name = "piper-release") {
            lock.withLock {
                loadedTts?.release()
                loadedTts = null
                loadedVoice = null
            }
        }
    }

    private fun interrupt() {
        generation.incrementAndGet()
        track?.let { runCatching { it.pause(); it.flush() } }
    }

    private fun locate(voice: PiperVoice): PiperLocation {
        located[voice.folder]?.let { return PiperLocation.Found(it) }
        val result = PiperModelLocator.locate(root, voice, verifyHash = requireVerified)
        if (result is PiperLocation.Found) located[voice.folder] = result.files
        return result
    }

    /** Builds (or reuses) the engine for [voice]. Must hold [lock]. Only one voice is kept in memory. */
    private fun engineFor(voice: PiperVoice, files: PiperModelFiles): OfflineTts {
        loadedTts?.let { if (loadedVoice == voice) return it }
        loadedTts?.release()
        loadedTts = null
        loadedVoice = null
        loadLibrary()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(model = files.model, tokens = files.tokens, dataDir = files.dataDir),
                numThreads = THREADS,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        val engine = try {
            OfflineTts(config = config)
        } catch (failure: LinkageError) {
            val reason = "the sherpa-onnx library could not be used: ${failure.message}"
            libraryError = reason
            throw TtsBackendException(reason, permanent = true, cause = failure)
        } catch (failure: Exception) {
            throw TtsBackendException("Piper could not load the voice ${voice.folder}: ${failure.message}", cause = failure)
        }
        loadedTts = engine
        loadedVoice = voice
        return engine
    }

    private fun loadLibrary() {
        try {
            System.loadLibrary("sherpa-onnx-jni")
        } catch (failure: UnsatisfiedLinkError) {
            val reason = "the sherpa-onnx library could not be loaded: ${failure.message}"
            libraryError = reason
            throw TtsBackendException(reason, permanent = true, cause = failure)
        }
    }

    private fun newTrack(sampleRate: Int): AudioTrack {
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bytes = maxOf(minimum, sampleRate * 2 * BUFFER_SECONDS)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    companion object {
        const val LIBRARY_FILE = "libsherpa-onnx-jni.so"
        private const val TAG = "Incident"
        private const val SPEED = 1.0f
        /** Not measured on the kit. Two threads is the sherpa-onnx default for VITS and leaves cores for the rest of the app. */
        private const val THREADS = 2
        private const val WRITE_SLICE = 4096
        private const val BUFFER_SECONDS = 2
        private const val POLL_MS = 20L
        private const val TAIL_MS = 1500L
    }
}
