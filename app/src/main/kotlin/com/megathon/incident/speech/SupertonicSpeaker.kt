package com.megathon.incident.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.megathon.incident.testing.Compute
import com.megathon.incident.tts.SupertonicLocation
import com.megathon.incident.tts.SupertonicModelFiles
import com.megathon.incident.tts.SupertonicModelLocator
import com.megathon.incident.tts.SupertonicVoice
import com.megathon.incident.tts.SupertonicVoices
import com.megathon.incident.tts.TtsBackend
import com.megathon.incident.tts.TtsBackendException
import com.megathon.incident.tts.SpokenSegment
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
 * Supertonic 3 (one open-weight model for Hindi and English, among others) through the sherpa-onnx native library, in this
 * process, on the **CPU** (provider "cpu"; nothing here touches QNN or the NPU, so the compute label is always [Compute.CPU]).
 *
 * The language is chosen per call (`extra["lang"]`), so one loaded model speaks a Hindi sentence and then an English place name.
 * [sid] picks one of the model's preset voice styles. Nothing is loaded until the first utterance. Every failure to load or run
 * is a [TtsBackendException], and the caller ([com.megathon.incident.tts.RoutingSpeaker]) falls back to the Android voice.
 *
 * [speak] returns only when the audio has been played or [stop] was called; a new [speak] stops the old one first.
 *
 * @param requireVerified in a release build every model file's size and SHA-256 must match the catalogue
 *   ([SupertonicVoices]); a debug build checks sizes only.
 */
class SupertonicSpeaker(
    private val context: Context,
    private val requireVerified: Boolean,
    private val sid: Int = 0,
    private val speed: Float = DEFAULT_SPEED,
    private val voice: SupertonicVoice = SupertonicVoices.multilingual,
) : TtsBackend {
    override val compute = Compute.CPU
    override val offline: Boolean? = true

    private val root = File(context.filesDir, "tts")
    private val lock = ReentrantLock()
    private val generation = AtomicInteger(0)

    @Volatile private var located: SupertonicModelFiles? = null

    // Guarded by [lock].
    private var loadedTts: OfflineTts? = null

    @Volatile private var libraryError: String? = null
    @Volatile private var closed = false
    @Volatile private var track: AudioTrack? = null

    override fun label(language: String): String = voice.label

    override suspend fun unavailableReason(language: String): String? = withContext(Dispatchers.IO) {
        libraryError?.let { return@withContext it }
        if (!voice.supports(language)) {
            return@withContext "Supertonic here covers ${voice.languages.sorted().joinToString(" and ")}, not \"$language\""
        }
        if (!File(context.applicationInfo.nativeLibraryDir, JNI_LIBRARY).exists()) {
            return@withContext "the sherpa-onnx library is not in the app (run app/scripts/tts_bundle.sh stage, then rebuild)"
        }
        when (val found = locate()) {
            is SupertonicLocation.Found -> null
            is SupertonicLocation.Missing ->
                "the Supertonic files are not on the device: ${found.missing.joinToString(", ")} (run app/scripts/tts_bundle.sh push-supertonic)"
            is SupertonicLocation.Corrupt -> "the Supertonic model failed its check: ${found.reason}"
        }
    }

    override suspend fun speak(text: String, language: String) {
        if (!voice.supports(language)) throw TtsBackendException("Supertonic here does not cover \"$language\"")
        val lang = languageCode(language)
        val chunks = TtsChunker.split(TtsTextNormalizer.normalize(text, lang))
        if (chunks.isEmpty()) return

        interrupt()
        val mine = generation.incrementAndGet()
        withContext(Dispatchers.Default) {
            val t0 = System.nanoTime()
            val files = withContext(Dispatchers.IO) { locate() }
            val modelFiles = when (files) {
                is SupertonicLocation.Found -> files.files
                is SupertonicLocation.Missing -> throw TtsBackendException("the Supertonic files are missing: ${files.missing.joinToString(", ")}")
                is SupertonicLocation.Corrupt -> throw TtsBackendException("the Supertonic model failed its check: ${files.reason}")
            }
            val rate = lock.withLock { engineFor(modelFiles).sampleRate() }
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
                        if (closed) throw TtsBackendException("the Supertonic voice was shut down", permanent = true)
                        val engine = engineFor(modelFiles)
                        try {
                            engine.generateWithConfig(
                                chunk,
                                GenerationConfig(sid = sid, speed = speed, numSteps = NUM_STEPS, extra = mapOf("lang" to lang)),
                            ).samples
                        } catch (failure: LinkageError) {
                            // A Kotlin binding that does not match the native library is an Error, not an Exception: uncaught it
                            // would end the app instead of falling back to the Android voice. It repeats on every call.
                            val reason = "the sherpa-onnx library does not match its Kotlin binding: ${failure.message}"
                            libraryError = reason
                            throw TtsBackendException(reason, spokeSomething = framesWritten > 0, permanent = true, cause = failure)
                        } catch (failure: Exception) {
                            throw TtsBackendException("Supertonic could not speak: ${failure.message}", spokeSomething = framesWritten > 0, cause = failure)
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
                "supertonic lang=$lang sid=$sid speed=$speed chars=${text.length} chunks=${chunks.size} loadMs=$loadedMs firstAudioMs=$firstAudioMs " +
                    "synthMs=$synthMs audioMs=$audioMs rtf=${if (audioMs > 0) "%.2f".format(synthMs.toDouble() / audioMs) else "n/a"} " +
                    "threads=$THREADS steps=$NUM_STEPS rate=$rate",
            )
        }
    }

    /**
     * Renders [segments], each in its own language, into one WAV file without playing anything, so the saved-report summary can
     * be paused and sought. Returns false, and writes nothing, when the voice does not cover a segment, the model is missing,
     * or synthesis fails; the caller then falls back to speaking it.
     */
    suspend fun renderToWav(segments: List<SpokenSegment>, out: File): Boolean = withContext(Dispatchers.Default) {
        if (segments.isEmpty() || segments.any { !voice.supports(it.language) }) return@withContext false
        val modelFiles = (withContext(Dispatchers.IO) { locate() } as? SupertonicLocation.Found)?.files ?: return@withContext false
        interrupt()
        try {
            val rate = lock.withLock { engineFor(modelFiles).sampleRate() }
            val parts = mutableListOf<ShortArray>()
            for ((index, seg) in segments.withIndex()) {
                val lang = languageCode(seg.language)
                for (chunk in TtsChunker.split(TtsTextNormalizer.normalize(seg.text, lang))) {
                    if (!currentCoroutineContext().isActive) return@withContext false
                    val samples = lock.withLock {
                        if (closed) return@withContext false
                        engineFor(modelFiles).generateWithConfig(
                            chunk,
                            GenerationConfig(sid = sid, speed = speed, numSteps = NUM_STEPS, extra = mapOf("lang" to lang)),
                        ).samples
                    }
                    if (samples.isNotEmpty()) parts += TtsPcm.toPcm16(samples)
                }
                if (index < segments.lastIndex) parts += ShortArray(rate * 3 / 10) // a short breath between segments
            }
            val total = parts.sumOf { it.size }
            if (total == 0) return@withContext false
            val pcm = ShortArray(total)
            var at = 0
            for (part in parts) { part.copyInto(pcm, at); at += part.size }
            out.parentFile?.mkdirs()
            out.writeBytes(com.megathon.incident.audio.WavWriter.encode(pcm, rate))
            Log.i(TAG, "supertonic render segments=${segments.size} audioMs=${TtsPcm.durationMs(total, rate)} rate=$rate")
            true
        } catch (failure: LinkageError) {
            libraryError = "the sherpa-onnx library does not match its Kotlin binding: ${failure.message}"
            false
        } catch (failure: Exception) {
            Log.w(TAG, "supertonic render failed: ${failure.message}")
            false
        }
    }

    /** Stops what is playing now (the pending [speak] returns). Safe to call from any thread. */
    override fun stop() = interrupt()

    override fun shutdown() {
        closed = true
        interrupt()
        // Freeing waits for a synthesis in progress, so it is done off the caller's thread.
        thread(name = "supertonic-release") {
            lock.withLock {
                loadedTts?.release()
                loadedTts = null
            }
        }
    }

    private fun interrupt() {
        generation.incrementAndGet()
        track?.let { runCatching { it.pause(); it.flush() } }
    }

    private fun locate(): SupertonicLocation {
        located?.let { return SupertonicLocation.Found(it) }
        val result = SupertonicModelLocator.locate(root, voice, verifyHash = requireVerified)
        if (result is SupertonicLocation.Found) located = result.files
        return result
    }

    /** Builds (or reuses) the engine. Must hold [lock]. */
    private fun engineFor(files: SupertonicModelFiles): OfflineTts {
        loadedTts?.let { return it }
        loadLibrary()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = files.durationPredictor,
                    textEncoder = files.textEncoder,
                    vectorEstimator = files.vectorEstimator,
                    vocoder = files.vocoder,
                    ttsJson = files.ttsJson,
                    unicodeIndexer = files.unicodeIndexer,
                    voiceStyle = files.voiceStyle,
                ),
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
            throw TtsBackendException("Supertonic could not load the model: ${failure.message}", cause = failure)
        }
        loadedTts = engine
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

    private fun languageCode(language: String): String =
        language.trim().lowercase().split('-', '_').firstOrNull().orEmpty().ifEmpty { "en" }

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

    private companion object {
        const val JNI_LIBRARY = "libsherpa-onnx-jni.so"
        const val TAG = "Incident"
        /** Slow on purpose: 1.0 is the model's natural rate and was reported as still too fast; lower is slower. */
        const val DEFAULT_SPEED = 0.8f
        /** Not measured on the kit: four threads, the model's own default of 5 diffusion steps. Tune from the logged rtf. */
        const val THREADS = 4
        const val NUM_STEPS = 5
        const val WRITE_SLICE = 4096
        const val BUFFER_SECONDS = 2
        const val POLL_MS = 20L
        const val TAIL_MS = 1500L
    }
}
