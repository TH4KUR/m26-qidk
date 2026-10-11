package com.megathon.incident.speech

/**
 * Builds one transcript out of successive ASR windows (Whisper decodes whole windows; it is not a streaming recognizer).
 *
 * A window that shares audio with the one before it ([addWindow] `overlapped`) is authoritative for that shared region: the
 * words it re-heard replace the earlier ones, found by matching the end of the transcript against the start of the window.
 * Words are only dropped on such a match, so a phrase the speaker really repeated is never collapsed. When a shared region
 * has no match, every word is kept and [ambiguous] is set so the final pass can fall back to a whole-recording decode.
 */
class IncrementalTranscript {
    private val words = mutableListOf<String>()
    private var tailCount = 0

    /** Changes only when [text] changes, so a caller can tell a stale extraction from a current one. */
    var revision = 0
        private set

    /** True once a shared region could not be matched; the text may then hold a repeated word or two. */
    var ambiguous = false
        private set

    val text: String get() = words.joinToString(" ")
    val stableText: String get() = words.dropLast(tailCount).joinToString(" ")
    val tailText: String get() = words.takeLast(tailCount).joinToString(" ")

    /** Returns true when the transcript changed. */
    fun addWindow(window: String, overlapped: Boolean, maxOverlapWords: Int = 0, tailWords: Int = 0): Boolean {
        val incoming = window.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (incoming.isEmpty()) return false
        val before = text
        var keep = words.size
        if (overlapped && words.isNotEmpty()) {
            val k = overlap(incoming, maxOverlapWords)
            if (k > 0) keep -= k else ambiguous = true
        }
        while (words.size > keep) words.removeAt(words.lastIndex)
        words += incoming
        tailCount = tailWords.coerceIn(0, words.size)
        return (text != before).also { if (it) revision++ }
    }

    /** The recording ended: nothing is unsettled any more. */
    fun finish() {
        tailCount = 0
    }

    /** The most words at the end of the transcript that the start of [incoming] repeats, at most [limit]; 0 when none. */
    private fun overlap(incoming: List<String>, limit: Int): Int {
        for (k in minOf(limit, words.size, incoming.size) downTo 1) {
            val ours = words.subList(words.size - k, words.size)
            if ((0 until k).all { same(ours[it], incoming[it], edge = it == k - 1 || it == 0) }) return k
        }
        return 0
    }

    /** Equal words, or at a window edge a word cut short by the window ("thr" for "three"). */
    private fun same(a: String, b: String, edge: Boolean): Boolean {
        val x = norm(a)
        val y = norm(b)
        if (x == y) return true
        return edge && minOf(x.length, y.length) >= 2 && (x.startsWith(y) || y.startsWith(x))
    }

    private fun norm(w: String) = w.lowercase().filter { it.isLetterOrDigit() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
