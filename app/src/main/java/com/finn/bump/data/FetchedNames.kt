package com.finn.bump.data

import com.finn.bump.domain.NameGroup
import com.finn.bump.domain.NameIdea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The origin line on a card for a name fetched from the public API: it has no meaning to show. */
const val FETCHED_NAME = "Fetched name"

private const val API = "https://randomuser.me/api/?results=100&inc=name,gender,nat" +
    "&nat=gb,us,au,ie,ca,nz,fr,es,de,dk,fi,nl,no,ch,br&noinfo"

// ponytail: plain letters only, so hyphenated and apostrophe names ("Jean-Pierre") are dropped;
// widen the regex if the supply of names ever feels too narrow.
private val PLAIN_NAME = Regex("""\p{L}{2,15}""")

/**
 * The names in a randomuser.me response that are new: [known] is every name already in
 * the bundled list, fetched before, or voted on (lowercase). Gender maps to the boy and
 * girl lists; a name that came back as both is neutral. Odd names are dropped.
 * Throws on a response that is not the expected shape, so the caller can say so.
 */
fun parseFetchedNames(body: String, known: Set<String>): List<NameIdea> {
    val results = (Json.parseToJsonElement(body) as JsonObject).getValue("results") as JsonArray
    val seen = HashMap<String, NameGroup>()
    val order = ArrayList<String>()
    for (r in results) {
        val o = r as JsonObject
        val first = (o.getValue("name") as JsonObject).getValue("first").jsonPrimitive.content.trim()
        if (!PLAIN_NAME.matches(first)) continue
        val key = first.lowercase()
        if (key in known) continue
        val group = when (o["gender"]?.jsonPrimitive?.content) {
            "male" -> NameGroup.BOY
            "female" -> NameGroup.GIRL
            else -> continue
        }
        val before = seen[key]
        if (before == null) order += first.lowercase().replaceFirstChar { it.uppercase() }
        seen[key] = if (before != null && before != group) NameGroup.NEUTRAL else group
    }
    return order.map { NameIdea(it, seen.getValue(it.lowercase()), FETCHED_NAME, "") }
}

/**
 * Names fetched on request and kept in `filesDir/fetched_names.json`, so the deck works
 * offline after the first fetch. Not in AppState and not synced: a vote carries its own
 * name and group, so the partner's phone needs no copy of this list.
 */
class FetchedNames(private val file: File) {
    private val serializer = ListSerializer(NameIdea.serializer())

    /** The cache; a missing file is simply empty, an unreadable one throws. */
    fun load(): List<NameIdea> =
        if (file.exists()) Json.decodeFromString(serializer, file.readText()) else emptyList()

    /** Fetch one batch, add the new names to the cache and return the whole cache. */
    suspend fun fetchMore(known: Set<String>): List<NameIdea> = withContext(Dispatchers.IO) {
        val have = load()
        val fresh = parseFetchedNames(download(), known + have.map { it.name.lowercase() })
        val all = have + fresh
        val tmp = File(file.path + ".tmp")
        tmp.writeText(Json.encodeToString(serializer, all))
        if (!tmp.renameTo(file)) throw IOException("Could not save fetched names")
        all
    }

    private fun download(): String {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }.decodeToString()
        } finally {
            conn.disconnect()
        }
    }
}
