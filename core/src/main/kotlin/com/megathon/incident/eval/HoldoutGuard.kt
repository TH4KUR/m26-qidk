package com.megathon.incident.eval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The first 16 hex digits of SHA-256: how prompts, gold files and builds are named in the logs and the ledger. */
object ShortHash {
    fun of(bytes: ByteArray): String = hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))

    /** Streams, so a 120 MB APK is hashed without being held in memory. */
    fun of(input: java.io.InputStream): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 16)
        input.use { s -> while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return hex(md.digest())
    }

    private fun hex(digest: ByteArray) = digest.joinToString("") { "%02x".format(it) }.take(16)
}

/** The two splits of E's gold set. A run is one or the other, never both. */
enum class EvalSplit(val label: String) { DEV("dev"), HOLDOUT("holdout") }

object SplitFilter {
    /** Exactly the wanted label: a missing or unknown label is never run, so an unlabelled item cannot leak into either run. */
    fun selects(label: String?, wanted: EvalSplit): Boolean = label == wanted.label
}

/** One held-out run, recorded BEFORE its first model call so an interrupted run still counts. */
@Serializable
data class HoldoutRun(
    @SerialName("prompt_hash") val promptHash: String,
    val build: String,
    @SerialName("gold_hash") val goldHash: String,
    val ids: List<String>,
    @SerialName("at_ms") val atMs: Long,
) {
    fun toLine(): String = Json.encodeToString(serializer(), this)
}

/** The runs recorded so far. A line that cannot be read is counted, never skipped silently: it may hide an earlier run. */
data class HoldoutLedger(val runs: List<HoldoutRun>, val unreadable: Int) {
    companion object {
        fun parse(text: String): HoldoutLedger {
            var bad = 0
            val runs = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
                runCatching { Json.decodeFromString(HoldoutRun.serializer(), line) }.getOrElse { bad++; null }
            }.toList()
            return HoldoutLedger(runs, bad)
        }
    }
}

sealed interface HoldoutDecision {
    /** No held-out run was asked for: carry on with whatever else the launch is doing. */
    data object NotRequested : HoldoutDecision

    /** [repeat] is true when this prompt already ran on this build and the second explicit word was given. */
    data class Allowed(val repeat: Boolean) : HoldoutDecision

    data class Refused(val reason: String) : HoldoutDecision
}

/**
 * The held-out items are a one-shot measurement, so running them takes a deliberate act that cannot happen by accident:
 * the exact word [CONFIRM], never in the same invocation as a dev run, and the same prompt on the same build is refused a
 * second time unless [CONFIRM_AGAIN] is also given.
 */
object HoldoutGuard {
    const val CONFIRM = "CONFIRM"
    const val CONFIRM_AGAIN = "CONFIRM_AGAIN"

    fun decide(
        holdout: String?,
        again: String?,
        devRequested: Boolean,
        promptHash: String,
        build: String,
        ledger: HoldoutLedger,
    ): HoldoutDecision {
        if (holdout == null) return HoldoutDecision.NotRequested
        if (holdout != CONFIRM) return HoldoutDecision.Refused("a held-out run needs --es holdout $CONFIRM exactly; got '$holdout'")
        if (devRequested) return HoldoutDecision.Refused("a held-out run must not share an invocation with a dev run (--es eval)")
        val againGiven = again == CONFIRM_AGAIN
        if (ledger.unreadable > 0 && !againGiven) {
            return HoldoutDecision.Refused(
                "the held-out ledger has ${ledger.unreadable} unreadable line(s), so an earlier run may be hidden; " +
                    "check it, or pass --es holdout_again $CONFIRM_AGAIN",
            )
        }
        val repeat = ledger.runs.any { it.promptHash == promptHash && it.build == build }
        if (repeat && !againGiven) {
            return HoldoutDecision.Refused(
                "the held-out items already ran for this prompt on this build; to run them again pass --es holdout_again $CONFIRM_AGAIN",
            )
        }
        return HoldoutDecision.Allowed(repeat = repeat)
    }
}
