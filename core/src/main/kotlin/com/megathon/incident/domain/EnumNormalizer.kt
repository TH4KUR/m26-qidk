package com.megathon.incident.domain

import java.text.Normalizer

/**
 * Maps free text from the LLM or the user to contract enums. Unknown text returns null and is
 * rejected upstream; it is never coerced to `other` (codex rule, design Q6).
 * Rules are ordered: the first matching rule wins, so specific phrases come before general ones.
 */
object EnumNormalizer {

    private val TYPE_RULES: List<Pair<IncidentType, List<String>>> = listOf(
        IncidentType.SPILL_CHEMICAL to listOf("gas leak", "gas smell", "chemical", "spill", "spilled", "acid", "fumes", "gas ki smell", "gas leak ho", "गैस"),
        IncidentType.FIRE_SMOKE to listOf("fire", "smoke", "burning", "flames", "on fire", "aag", "dhuan", "dhuaan", "jal raha", "आग", "धुआं", "धुआँ"),
        IncidentType.ELECTRICAL to listOf("electrical", "spark", "sparks", "short circuit", "shock", "exposed wire", "power outage", "power cut", "bijli", "करंट", "बिजली", "current laga"),
        IncidentType.WATER_LEAK_FLOOD to listOf("leak", "leaking", "flood", "flooding", "burst pipe", "water", "seepage", "paani", "pani", "पानी", "रिसाव"),
        IncidentType.INJURY to listOf("injury", "injured", "hurt", "cut", "bleeding", "fell", "fall", "slipped", "fracture", "chot", "chot lagi", "gir gaya", "gir gayi", "चोट", "घायल"),
        IncidentType.ILLNESS to listOf("illness", "sick", "fainted", "unconscious", "vomiting", "chest pain", "behosh", "bimar", "बेहोश", "बीमार"),
        IncidentType.SECURITY to listOf("security", "theft", "stolen", "robbery", "robber", "thief", "burglary", "intruder", "break-in", "assault", "chor", "chori", "चोर", "चोरी", "लूट"),
        IncidentType.NEAR_MISS to listOf("near miss", "near_miss", "almost", "nearly", "bal bal bacha", "बाल बाल"),
        IncidentType.EQUIPMENT_FAILURE to listOf("equipment failure", "breakdown", "broke down", "not working", "malfunction", "stopped working", "kharab", "खराब"),
        IncidentType.PROPERTY_DAMAGE to listOf("property damage", "damage", "damaged", "broken", "pothole", "crack", "toota", "tuta", "टूटा", "गड्ढा", "gaddha"),
        IncidentType.ENVIRONMENTAL to listOf("environmental", "pollution", "waste", "garbage", "sewage", "kachra", "कचरा"),
        IncidentType.OTHER to listOf("other"),
    )

    private val UNKNOWN_PHRASES = listOf("don't know", "dont know", "do not know", "no idea", "not sure", "pata nahi", "pata nahin", "पता नहीं", "maloom nahi")

    private val SEVERITY_RULES: List<Pair<Severity, List<String>>> = listOf(
        Severity.CRITICAL to listOf("critical", "life threatening", "life-threatening", "fatal", "dying", "jaan ka khatra", "जान का खतरा"),
        Severity.SEVERE to listOf("severe", "serious", "major", "badly", "very bad", "gambhir", "गंभीर", "bahut bura"),
        Severity.MODERATE to listOf("moderate", "medium", "somewhat", "theek thaak", "madhyam", "मध्यम"),
        Severity.MINOR to listOf("minor", "small", "slight", "little", "not serious", "chhota", "chota", "halka", "mamuli", "छोटा", "मामूली", "हल्का"),
        Severity.NONE to listOf("none", "no harm", "no damage", "nothing serious", "kuch nahi", "कुछ नहीं"),
    )

    fun incidentType(raw: String?): IncidentType? {
        val text = normalize(raw) ?: return null
        IncidentType.entries.firstOrNull { it.wire == text.replace(' ', '_') }?.let { return it }
        return TYPE_RULES.firstOrNull { (_, cues) -> cues.any { containsPhrase(text, it) } }?.first
    }

    fun severity(raw: String?): Severity? {
        val text = normalize(raw) ?: return null
        Severity.entries.firstOrNull { it.wire == text }?.let { return it }
        if (UNKNOWN_PHRASES.any { containsPhrase(text, it) }) return null
        return SEVERITY_RULES.firstOrNull { (_, cues) -> cues.any { containsPhrase(text, it) } }?.first
    }

    /** NFC, lower case, collapsed whitespace; null when blank. */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val t = Normalizer.normalize(raw, Normalizer.Form.NFC).lowercase().replace(Regex("\\s+"), " ").trim()
        return t.ifEmpty { null }
    }

    /** Whole-word match for Latin text; plain substring for Devanagari (no reliable word boundaries). */
    fun containsPhrase(text: String, phrase: String): Boolean {
        val p = phrase.lowercase()
        if (p.any { it.code in 0x0900..0x097F }) return text.contains(p)
        return Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(p) + "(?![\\p{L}\\p{N}])").containsMatchIn(text)
    }
}
