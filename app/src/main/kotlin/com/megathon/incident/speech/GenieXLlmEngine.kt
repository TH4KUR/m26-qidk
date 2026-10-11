package com.megathon.incident.speech

import android.content.Context
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.ModelManagerWrapper
import com.geniex.sdk.bean.ChatMessage
import com.geniex.sdk.bean.ComputeUnitValue
import com.geniex.sdk.bean.GenerationConfig
import com.geniex.sdk.bean.LlmCreateInput
import com.geniex.sdk.bean.LlmStreamResult
import com.geniex.sdk.bean.ModelConfig
import com.geniex.sdk.bean.SamplerConfig
import com.megathon.incident.engine.EngineUnavailableException
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.llm.LlmTimings
import com.megathon.incident.llm.Warmable
import com.megathon.incident.testing.Compute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The on-device language model, through the GenieX SDK (AI Hub QAIRT bundle on the Hexagon NPU).
 *
 *  - The model is loaded once, lazily, on the first call (or by [warmUp]); loading takes seconds.
 *  - Sampling is near-greedy for extraction. The QAIRT runtime has no grammar-constrained JSON, so the caller validates
 *    and retries (`ToolCallParser`, `PayloadValidator`, one repair retry in `ReportSession`).
 *  - The compute label is not assumed: NPU only when the bundle's runtime is `qairt` and the NPU was requested.
 *  - There is no silent fallback to another model or to the CPU: if loading or generating fails, the call fails with the reason.
 */
class GenieXLlmEngine(
    private val context: Context,
    private val modelName: String = debugModel ?: DEFAULT_MODEL,
    private val maxTokens: Int = 320,
) : LlmEngine, Warmable {
    override val name = if (modelName == DEFAULT_MODEL) "Qwen3-4B-Instruct-2507 (GenieX, QAIRT)" else "$modelName (GenieX, QAIRT)"

    private val loadLock = Mutex()
    /** One generation at a time: two overlapping calls on the same model corrupted its state and crashed the app (kit, 02:57). */
    private val generateLock = Mutex()
    private var wrapper: LlmWrapper? = null
    private var runtimeId: String = ""
    private var sdkReady = false

    /**
     * Loads the model if it is not loaded yet. Returns the time it took in ms (0 when it was already loaded).
     * Safe to start early and from anywhere: a real call that arrives while this is loading waits on [loadLock] and then sees
     * the loaded model (it never loads twice), and a failed load leaves [wrapper] null so the next call tries again.
     * [generate] takes [generateLock] before [loadLock] and this takes only [loadLock], so the two cannot deadlock.
     */
    override suspend fun warmUp(): Long = loadLock.withLock {
        if (wrapper != null) return 0
        val started = System.nanoTime()
        withContext(Dispatchers.IO) {
            initSdk()
            val paths = ModelManagerWrapper.getPaths(modelName)
                ?: throw EngineUnavailableException(
                    "the language model '$modelName' is not in this app's model store. Copy it with app/scripts/llm_bundle.sh push",
                )
            runtimeId = paths.runtime_id
            val isQairt = runtimeId == "qairt"
            // QAIRT bundles fix their context and layers at compile time and reject non-zero values here.
            val config = if (isQairt) ModelConfig(nCtx = 0, nGpuLayers = 0, enable_thinking = false) else ModelConfig(nCtx = 4096, enable_thinking = false)
            val built = LlmWrapper.builder().llmCreateInput(
                LlmCreateInput(
                    model_name = paths.model_name,
                    model_path = paths.model_path,
                    tokenizer_path = paths.tokenizer_path,
                    config = config,
                    runtime_id = runtimeId,
                    compute_unit = ComputeUnitValue.NPU.value,
                ),
            ).build()
            wrapper = built.getOrElse { throw IllegalStateException("could not load the language model: ${it.message}", it) }
        }
        (System.nanoTime() - started) / 1_000_000
    }

    override suspend fun complete(request: LlmRequest): LlmResult = generateLock.withLock { generate(request) }

    private suspend fun generate(request: LlmRequest): LlmResult {
        warmUp()
        val llm = checkNotNull(wrapper)
        val started = System.nanoTime()
        return withContext(Dispatchers.IO) {
            // Every request is self-contained (system prompt + report so far + the new words), so the model's conversation
            // memory must start empty. Without this the context fills up (4096 tokens) after about seven calls.
            val resetCode = llm.reset()
            check(resetCode == 0) { "could not reset the language model's context (code $resetCode)" }
            val messages = arrayOf(ChatMessage("system", request.system), ChatMessage("user", request.user))
            val templated = llm.applyChatTemplate(messages, null, false, true)
                .getOrElse { throw IllegalStateException("chat template failed: ${it.message}", it) }
            val config = GenerationConfig(
                maxTokens = maxTokens,
                samplerConfig = SamplerConfig(temperature = 0.1f, topK = 1, topP = 1.0f, seed = 42),
            )
            val text = StringBuilder()
            var timings: LlmTimings? = null
            var failure: Throwable? = null
            llm.generateStreamFlow(templated.formattedText, config)
                .catch { failure = it }
                .collect { r ->
                    when (r) {
                        is LlmStreamResult.Token -> text.append(r.text)
                        is LlmStreamResult.Completed -> timings = LlmTimings(
                            ttftMs = r.profile.ttftMs,
                            promptTokens = r.profile.promptTokens.toInt(),
                            generatedTokens = r.profile.generatedTokens.toInt(),
                            prefillTokensPerSec = r.profile.prefillSpeed,
                            decodeTokensPerSec = r.profile.decodingSpeed,
                        )
                        is LlmStreamResult.Error -> failure = r.throwable
                    }
                }
            failure?.let { throw IllegalStateException("the language model failed: ${it.message}", it) }
            val compute = if (runtimeId == "qairt") Compute.NPU else Compute.CPU
            LlmResult(text.toString(), (System.nanoTime() - started) / 1_000_000, compute, timings)
        }
    }

    fun release() {
        wrapper?.close()
        wrapper = null
    }

    private suspend fun initSdk() {
        if (sdkReady) return
        val done = CompletableDeferred<Unit>()
        GenieXSdk.getInstance().init(
            context.applicationContext,
            object : GenieXSdk.InitCallback {
                override fun onSuccess() { done.complete(Unit) }
                override fun onFailure(reason: String) { done.completeExceptionally(IllegalStateException("GenieX init failed: $reason")) }
            },
        )
        done.await()
        sdkReady = true
    }

    companion object {
        const val DEFAULT_MODEL = "qualcomm/Qwen3-4B-Instruct-2507"

        /** Debug builds only (set from `--es model <store name>` in MainActivity): measure another bundle without a rebuild. */
        @Volatile var debugModel: String? = null
    }
}
