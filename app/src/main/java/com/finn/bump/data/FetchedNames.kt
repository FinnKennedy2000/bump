package com.finn.bump.data

import com.finn.bump.domain.Culture
import com.finn.bump.domain.NameGroup
import com.finn.bump.domain.NameIdea
import com.finn.bump.domain.cultureOf
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

/** The origin on names cached before fetched names carried a nationality; still loads, files under Other. */
const val FETCHED_NAME = "Fetched name"

/**
 * randomuser.me's `nat` codes (it supports only a fixed list) and the origin each gives a
 * fetched name. The culture a code belongs to is [cultureOf] its label, so there is one table.
 */
private val NATS = mapOf(
    "GB" to "English", "US" to "English", "AU" to "English", "CA" to "English", "NZ" to "English",
    "IE" to "Irish", "FR" to "French", "ES" to "Spanish", "MX" to "Mexican", "BR" to "Brazilian",
    "DE" to "German", "CH" to "Swiss", "NL" to "Dutch",
    "DK" to "Danish", "NO" to "Norwegian", "FI" to "Finnish",
    "UA" to "Ukrainian", "RS" to "Serbian", "IN" to "Indian", "IR" to "Persian", "TR" to "Turkish",
)

/** Whether randomuser.me has any names for [culture]; Greek, Latin, Hebrew and the like do not. */
fun hasOnlineNames(culture: Culture): Boolean = NATS.values.any { cultureOf(it) == culture }

private fun api(culture: Culture?): String {
    // The broad list stays what it was; the newer codes are only asked for by their culture.
    val codes = if (culture == null) NATS.filterKeys { it !in setOf("MX", "IN", "IR", "TR", "UA", "RS") }.keys
    else NATS.filter { cultureOf(it.value) == culture }.keys
    return "https://randomuser.me/api/?results=100&inc=name,gender,nat" +
        "&nat=${codes.joinToString(",") { it.lowercase() }}&noinfo"
}

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
    val origins = HashMap<String, String>()
    val order = ArrayList<String>()
    for (r in results) {
        val o = r as JsonObject
        val first = (o.getValue("name") as JsonObject).getValue("first").jsonPrimitive.content.trim()
        if (!PLAIN_NAME.matches(first)) continue
        val key = first.lowercase()
        if (key in known) continue
        val origin = NATS[o["nat"]?.jsonPrimitive?.content?.uppercase()] ?: FETCHED_NAME
        val group = when (o["gender"]?.jsonPrimitive?.content) {
            "male" -> NameGroup.BOY
            "female" -> NameGroup.GIRL
            else -> continue
        }
        val before = seen[key]
        if (before == null) {
            order += first.lowercase().replaceFirstChar { it.uppercase() }
            origins[key] = origin
        }
        seen[key] = if (before != null && before != group) NameGroup.NEUTRAL else group
    }
    return order.map { NameIdea(it, seen.getValue(it.lowercase()), origins.getValue(it.lowercase()), "") }
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

    /**
     * Fetch one batch, limited to [culture] when given, add the new names to the cache and
     * return the whole cache. A culture with no online names makes no request.
     */
    suspend fun fetchMore(known: Set<String>, culture: Culture? = null): List<NameIdea> = withContext(Dispatchers.IO) {
        val have = load()
        if (culture != null && !hasOnlineNames(culture)) return@withContext have
        val fresh = parseFetchedNames(download(api(culture)), known + have.map { it.name.lowercase() })
        val all = have + fresh
        val tmp = File(file.path + ".tmp")
        tmp.writeText(Json.encodeToString(serializer, all))
        if (!tmp.renameTo(file)) throw IOException("Could not save fetched names")
        all
    }

    private fun download(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
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
