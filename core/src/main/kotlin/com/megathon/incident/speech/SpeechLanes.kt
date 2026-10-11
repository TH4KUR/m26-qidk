package com.megathon.incident.speech

/** Which speech recogniser a language is sent to. */
enum class RecognitionEngine {
    /** Whisper-Small through sherpa-onnx's offline CLI: the shipped English path. */
    WHISPER_SMALL,

    /** Nemotron-3.5 ASR streaming through sherpa-onnx's online CLI: the experimental Hindi lane. */
    NEMOTRON_HINDI,
}

/**
 * One language lane: what the recognition call is given, and what runs it.
 *
 * [language] is the code handed to the engine (`en` or `hi`, never a region form). [notice] is set only when the
 * request could not be honoured as asked (Telugu, an unknown code) and says why in words; it is null for the
 * plain English default and for Hindi.
 */
data class SpeechLane(
    val language: String,
    val engine: RecognitionEngine,
    val experimental: Boolean = false,
    val notice: String? = null,
) {
    companion object {
        /** The lane the app always used before the Hindi lane existed: Whisper-Small, language `en`, nothing to say. */
        val ENGLISH = SpeechLane(language = "en", engine = RecognitionEngine.WHISPER_SMALL)
    }
}

/**
 * Picks the lane for a language code. Pure and total: any input gives a lane.
 *
 *  - missing, blank, `en`, `en-IN`, ... -> English, no notice
 *  - `hi`, `hi-IN`, ...                 -> Hindi (Nemotron-3.5), experimental
 *  - `te` / `tel` (Telugu)              -> English with a notice: Telugu is not supported and is never sent to the Hindi model
 *  - anything else                      -> English with a notice naming the code
 */
object SpeechLanes {
    fun select(code: String?): SpeechLane {
        val raw = code?.trim().orEmpty()
        if (raw.isEmpty()) return SpeechLane.ENGLISH
        return when (val base = raw.lowercase().split('-', '_').first()) {
            "en" -> SpeechLane.ENGLISH
            "hi" -> SpeechLane(language = "hi", engine = RecognitionEngine.NEMOTRON_HINDI, experimental = true)
            "te", "tel" -> SpeechLane.ENGLISH.copy(
                notice = "Telugu is not supported in this build, so English is used. The Hindi model is never used for Telugu.",
            )
            else -> SpeechLane.ENGLISH.copy(
                notice = "Language \"$raw\" is not supported (only en and hi are known; base \"$base\"), so English is used.",
            )
        }
    }
}

/** The honest wording for the experimental lane, shown on the proof sheet. It states no accuracy figure because there is none. */
object HindiLaneNotice {
    const val SWITCH_LABEL = "हिन्दी (experimental)"

    const val TEXT =
        "Hindi is an experimental lane. Speech is recognised by a separate Hindi model (Nemotron-3.5 ASR, run by sherpa-onnx). " +
            "Hindi extraction accuracy is UNMEASURED. The report labels, questions and spoken replies stay in English."
}

/**
 * The one short line shown where a Devanagari transcript sits next to English labels, questions and replies.
 * Null when no transcript contains Devanagari, so an English run shows nothing extra.
 */
object TranscriptScriptNote {
    const val LINE = "This transcript is in Hindi (Devanagari). Labels, questions and spoken replies are in English."

    fun forTexts(texts: List<String>): String? = if (texts.any { containsDevanagari(it) }) LINE else null

    fun containsDevanagari(text: String): Boolean = text.any { it.code in 0x0900..0x097F }
}
