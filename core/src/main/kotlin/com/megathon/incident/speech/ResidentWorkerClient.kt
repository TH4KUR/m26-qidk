package com.megathon.incident.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** The two ends of a worker process: JSON lines out, JSON lines in. The Android side wraps a child process; tests wrap a fake. */
interface WorkerChannel {
    fun send(line: String)

    /** Blocks for the worker's next stdout line; null once the stream has ended. Must return (null or throw) after [destroy]. */
    fun readLine(): String?

    /** Whether the worker's own log (stderr, drained by the channel) has shown [marker], for example the Hexagon provider starting. */
    fun sawLog(marker: String): Boolean

    /** Kills the process and unblocks [readLine]. Idempotent. */
    fun destroy()
}

class WorkerException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** [hexagonEvidence] is true only when the worker's log showed the Hexagon provider; the requested provider alone proves nothing. */
data class WorkerReply(val text: String, val decodeMs: Long, val hexagonEvidence: Boolean)

/**
 * The app's end of the resident speech worker (protocol v1, see tools/sherpa_worker/README.md). Calls are serialized; each
 * carries its own id and a reply with any other id is a protocol failure. A timeout, a malformed or out-of-order reply, a
 * dead worker or a failed start drops the worker (so a late reply can never answer another request) and the next call starts
 * a fresh one. A request-level error (an unreadable file) fails that call and keeps the worker, because its models are loaded.
 */
class ResidentWorkerClient(
    private val launch: () -> WorkerChannel,
    private val requestTimeoutMs: Long = 90_000,
    private val startupTimeoutMs: Long = 60_000,
    private val hexagonMarker: String = "HtpProvider",
) {
    private val lock = Mutex()
    private val reader = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var channel: WorkerChannel? = null
    private var nextId = 0

    /** How many workers were started, which is how many times the models were loaded. */
    @Volatile
    var launches = 0
        private set

    /** Starts the worker now (loading the models) if it is not running, so the first real request does not pay for it. */
    suspend fun warmUp() {
        lock.withLock { if (channel == null) start() }
    }

    suspend fun transcribe(wavPath: String, language: String): WorkerReply = lock.withLock {
        val ch = channel ?: start()
        try {
            exchange(ch, wavPath, language)
        } catch (e: kotlinx.coroutines.CancellationException) {
            drop(ch) // a request is outstanding; its reply must not be read by the next call
            throw e
        }
    }

    private suspend fun exchange(ch: WorkerChannel, wavPath: String, language: String): WorkerReply {
        val id = ++nextId
        try {
            ch.send(
                JsonObject(
                    mapOf(
                        "v" to JsonPrimitive(1), "id" to JsonPrimitive(id), "cmd" to JsonPrimitive("transcribe"),
                        "wav" to JsonPrimitive(wavPath), "language" to JsonPrimitive(language),
                    ),
                ).toString(),
            )
        } catch (e: Exception) {
            drop(ch)
            throw WorkerException("could not send to the speech worker: ${e.message}", e)
        }
        val line = readProtocol(ch, requestTimeoutMs) ?: throw fail(ch, "the speech worker timed out after $requestTimeoutMs ms")
        val reply = parse(line) ?: throw fail(ch, "malformed reply from the speech worker: ${line.take(120)}")
        val replyId = reply["id"]?.jsonPrimitive?.long
        if (replyId != id.toLong()) throw fail(ch, "the speech worker answered request $replyId, but request $id was waiting")
        reply["error"]?.let { throw WorkerException("speech worker: ${it.jsonPrimitive.content}") } // the worker is healthy; keep it
        val text = reply["text"]?.jsonPrimitive?.content ?: throw fail(ch, "reply without text: ${line.take(120)}")
        return WorkerReply(text.trim(), reply["decode_ms"]?.jsonPrimitive?.long ?: 0, ch.sawLog(hexagonMarker))
    }

    /** Asks the worker to quit and releases it. Safe to call when none is running; the next call would start a new one. */
    suspend fun close() = lock.withLock {
        val ch = channel ?: return
        runCatching { ch.send("""{"v":1,"id":${++nextId},"cmd":"quit"}""") }
        drop(ch)
    }

    private suspend fun start(): WorkerChannel {
        val ch = try {
            launch()
        } catch (e: Exception) {
            throw WorkerException("could not start the speech worker: ${e.message}", e)
        }
        launches++
        try {
            val line = readProtocol(ch, startupTimeoutMs) ?: throw fail(ch, "the speech worker did not start within $startupTimeoutMs ms")
            val hello = parse(line) ?: throw fail(ch, "malformed start line from the speech worker: ${line.take(120)}")
            val ready = runCatching { hello["ready"]?.jsonPrimitive?.boolean }.getOrNull()
            if (ready != true) {
                throw fail(ch, "the speech worker could not start: ${runCatching { hello["error"]?.jsonPrimitive?.content }.getOrNull() ?: "no reason given"}")
            }
            channel = ch
            return ch
        } catch (e: Throwable) {
            drop(ch) // any way out of start-up (including the caller being cancelled) must not leave a model-loading process behind
            throw e
        }
    }

    /**
     * The next protocol line (one that starts with `{`), or null when [timeoutMs] runs out. Anything else on stdout is a library
     * log line that escaped to the wrong stream: it is skipped, but a worker that only chatters is dropped.
     */
    private suspend fun readProtocol(ch: WorkerChannel, timeoutMs: Long): String? {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        var skipped = 0
        while (true) {
            val left = (deadline - System.nanoTime()) / 1_000_000
            if (left <= 0) return null
            val line = read(ch, left) ?: return null
            if (line.trimStart().startsWith("{")) return line
            if (++skipped > MAX_SKIPPED_LINES) throw fail(ch, "the speech worker wrote too much non-protocol output on stdout")
        }
    }

    /** One line, or null after [timeoutMs]; the worker's end of stream is a failure that names itself. */
    private suspend fun read(ch: WorkerChannel, timeoutMs: Long): String? {
        val pending = reader.async { runCatching { ch.readLine() } }
        val result = withTimeoutOrNull(timeoutMs) { pending.await() }
        if (result == null) {
            pending.cancel()
            return null
        }
        return result.getOrNull() ?: throw fail(ch, "the speech worker process ended unexpectedly")
    }

    private fun parse(line: String): JsonObject? =
        runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()

    private fun fail(ch: WorkerChannel, message: String): WorkerException {
        drop(ch)
        return WorkerException(message)
    }

    private companion object {
        const val MAX_SKIPPED_LINES = 200
    }

    private fun drop(ch: WorkerChannel) {
        if (channel === ch) channel = null
        runCatching { ch.destroy() }
    }
}
