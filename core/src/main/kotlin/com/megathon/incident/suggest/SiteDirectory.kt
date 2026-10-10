package com.megathon.incident.suggest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer

/** Which report field a directory entry can fill: LOCATION, EQUIPMENT_ASSET, or a person list. */
enum class EntryKind { PLACE, ASSET, PERSON }

data class SiteEntry(val id: String, val name: String, val kind: EntryKind, val aliases: List<String>)

/** Which site list is in use: [fromFile] is false for the bundled one; [problem] explains a rejected file. */
data class SiteLoad(val site: SiteDirectory, val fromFile: Boolean, val problem: String? = null)

/**
 * The site gazetteer (spec D16): known places, assets and people with their spoken aliases.
 * Pure data, no Android; the app and tests load the same `site/site.json` resource.
 *
 * [lookup] is exact after normalisation only. Fuzzy and phonetic matching belong to the
 * suggestion engine, which must be able to say *why* it proposed a name.
 */
class SiteDirectory(val entries: List<SiteEntry>) {

    private val byKey: Map<String, SiteEntry> = index(entries)

    /** The entry whose name or alias equals [spoken], ignoring case, spaces and punctuation. */
    fun lookup(spoken: String): SiteEntry? = byKey[key(spoken)]

    companion object {
        private const val DEFAULT_PATH = "/site/site.json"
        private val NOT_ALNUM = Regex("[^\\p{L}\\p{M}\\p{N}]+")
        private val json = Json { ignoreUnknownKeys = true }

        /** Maps every normalised name and alias to its entry; two entries sharing a key is a data error. */
        private fun index(entries: List<SiteEntry>): Map<String, SiteEntry> {
            val byKey = mutableMapOf<String, SiteEntry>()
            for (entry in entries) {
                for (spoken in listOf(entry.name) + entry.aliases) {
                    val key = key(spoken)
                    if (key.isEmpty()) continue
                    val other = byKey[key]
                    require(other == null || other.id == entry.id) {
                        "alias '$spoken' ($key) is used by both ${other!!.id} and ${entry.id}"
                    }
                    byKey[key] = entry
                }
            }
            return byKey
        }

        /** "N-105", "n 105" and "N105." all become "n105". */
        fun key(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFC).lowercase().replace(NOT_ALNUM, "")

        fun fromJson(text: String): SiteDirectory {
            val doc = json.decodeFromString<SiteDoc>(text)
            val placeIds = doc.places.map { it.id }.toSet()
            for (p in doc.places) {
                require(p.building == null || p.building in placeIds) { "place ${p.id} names unknown building '${p.building}'" }
            }
            for (a in doc.assets) {
                require(a.place == null || a.place in placeIds) { "asset ${a.id} names unknown place '${a.place}'" }
            }
            return SiteDirectory(
                doc.places.map { SiteEntry(it.id, it.name, EntryKind.PLACE, it.aliases) } +
                    doc.assets.map { SiteEntry(it.id, it.name, EntryKind.ASSET, it.aliases) } +
                    doc.people.map { SiteEntry(it.id, it.name, EntryKind.PERSON, it.aliases) },
            )
        }

        /**
         * The site list from [file] when it exists and is valid, else the bundled (fictional) one. The app
         * passes `filesDir/site.json`, so a real list pushed to the kit for the live demo replaces the
         * bundled one without being in the code. Never throws: a broken file falls back and [SiteLoad.problem] says why.
         */
        fun loadPreferring(file: File?): SiteLoad {
            if (file == null || !file.isFile) return SiteLoad(loadDefault(), fromFile = false)
            return try {
                SiteLoad(fromJson(file.readText(Charsets.UTF_8)), fromFile = true)
            } catch (e: Exception) {
                SiteLoad(loadDefault(), fromFile = false, problem = "${file.name} not used: ${e.message ?: e::class.simpleName}")
            }
        }

        fun loadDefault(): SiteDirectory {
            val stream = SiteDirectory::class.java.getResourceAsStream(DEFAULT_PATH)
                ?: error("missing site resource $DEFAULT_PATH")
            return fromJson(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }
    }
}

@Serializable
private data class SiteDoc(
    val places: List<PlaceDoc> = emptyList(),
    val assets: List<AssetDoc> = emptyList(),
    val people: List<PersonDoc> = emptyList(),
)

@Serializable
private data class PlaceDoc(val id: String, val name: String, val building: String? = null, val aliases: List<String> = emptyList())

@Serializable
private data class AssetDoc(val id: String, val name: String, val place: String? = null, val aliases: List<String> = emptyList())

@Serializable
private data class PersonDoc(val id: String, val name: String, val aliases: List<String> = emptyList())
