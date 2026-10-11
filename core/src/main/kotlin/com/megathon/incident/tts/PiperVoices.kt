package com.megathon.incident.tts

/**
 * One Piper voice as shipped in the sherpa-onnx `tts-models` release. [folder] is the extracted release folder
 * (the same name is used on the kit under `files/tts/`). [modelBytes] and [modelSha256] describe the .onnx file and
 * are what "verified" means for a release build.
 */
data class PiperVoice(
    val language: String,
    val folder: String,
    val modelFile: String,
    /** The espeak-ng voice the model was trained with (from the model's .onnx.json). */
    val espeakVoice: String,
    val label: String,
    val licence: String,
    val modelBytes: Long,
    val modelSha256: String,
)

/** The voices the app knows about, by session language. Telugu has no Piper voice in the sherpa release: it is not here on purpose. */
object PiperVoices {
    val english = PiperVoice(
        language = "en",
        folder = "vits-piper-en_US-lessac-medium",
        modelFile = "en_US-lessac-medium.onnx",
        espeakVoice = "en-us",
        label = "Piper TTS (sherpa-onnx, en_US lessac medium)",
        licence = "Blizzard 2013 (Lessac) dataset licence; check before any non-research use",
        modelBytes = 63_149_198L,
        modelSha256 = "4ba07d8549906668ee855fd9abf9faf66c5db74742712ff026a159f7277fca9f",
    )

    val hindi = PiperVoice(
        language = "hi",
        folder = "vits-piper-hi_IN-rohan-medium-int8",
        modelFile = "hi_IN-rohan-medium.onnx",
        espeakVoice = "hi",
        label = "Piper TTS (sherpa-onnx, hi_IN rohan medium int8)",
        licence = "IITM IndicTTS dataset licence; check before any non-research use",
        modelBytes = 18_301_724L,
        modelSha256 = "4f2dc5324c762fc676eb65be30b79b65c91848f47c3697ff86de69f3315c5da6",
    )

    val all: List<PiperVoice> = listOf(english, hindi)

    /**
     * The voice for a session language. Blank or missing means English (the app's default). The region is ignored
     * (`en-IN`, `EN_us`, `hi-IN`). A language with no voice (Telugu, anything unknown) gives null: the caller falls
     * back to the Android voice and says so.
     */
    fun forLanguage(language: String?): PiperVoice? {
        val base = language?.trim()?.lowercase()?.split('-', '_')?.firstOrNull().orEmpty()
        if (base.isEmpty()) return english
        return all.firstOrNull { it.language == base }
    }
}
