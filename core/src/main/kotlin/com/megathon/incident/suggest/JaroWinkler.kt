package com.megathon.incident.suggest

import kotlin.math.max
import kotlin.math.min

/**
 * Jaro-Winkler string similarity (Winkler 1990): 1.0 for identical strings, 0.0 for nothing in common.
 * Rewards a shared prefix, which suits names where speech recognition gets the start right
 * ("maple hostle" vs "maple hostel"). Plain arithmetic on the CPU; no model, no dependency.
 */
object JaroWinkler {
    private const val PREFIX_SCALE = 0.1
    private const val MAX_PREFIX = 4

    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0

        val window = max(0, max(a.length, b.length) / 2 - 1)
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            for (j in max(0, i - window)..min(b.length - 1, i + window)) {
                if (!bMatched[j] && a[i] == b[j]) {
                    aMatched[i] = true; bMatched[j] = true; matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0

        var prefix = 0
        while (prefix < min(MAX_PREFIX, min(a.length, b.length)) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * PREFIX_SCALE * (1 - jaro)
    }
}
