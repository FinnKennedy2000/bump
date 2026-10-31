package com.finn.bump.domain

/** A broad family a name's free-text origin belongs to, for filtering the Discover deck. */
enum class Culture(val label: String) {
    IRISH("Irish"), SCOTTISH("Scottish"), WELSH("Welsh"), ENGLISH("English"),
    FRENCH("French"), SPANISH("Spanish"), PORTUGUESE("Portuguese"), ITALIAN("Italian"),
    GERMAN("German"), DUTCH("Dutch"), NORDIC("Nordic"), SLAVIC("Slavic"),
    GREEK("Greek"), LATIN("Latin"), HEBREW("Hebrew"), ARABIC("Arabic"),
    INDIAN("Indian"), TURKISH("Turkish"), PERSIAN("Persian"), OTHER("Other"),
}

// First match wins, so more specific keywords come before broader ones. Lowercase substrings.
private val KEYWORDS: List<Pair<Culture, List<String>>> = listOf(
    Culture.IRISH to listOf("irish", "gaelic", "celtic", "breton"),
    Culture.SCOTTISH to listOf("scot"),
    Culture.WELSH to listOf("welsh"),
    Culture.ENGLISH to listOf("english", "american", "british", "australian", "canadian"),
    Culture.FRENCH to listOf("french"),
    Culture.SPANISH to listOf("spanish", "mexican", "basque"),
    Culture.PORTUGUESE to listOf("portuguese", "brazil"),
    Culture.ITALIAN to listOf("italian"),
    Culture.GERMAN to listOf("german", "swiss", "austrian"),
    Culture.DUTCH to listOf("dutch"),
    Culture.NORDIC to listOf("norse", "scandinavian", "nordic", "danish", "norwegian", "finnish", "swedish", "icelandic"),
    Culture.SLAVIC to listOf("slavic", "russian", "polish", "czech", "ukrain", "serb", "bulgarian", "croatian"),
    Culture.GREEK to listOf("greek"),
    Culture.LATIN to listOf("latin"),
    Culture.HEBREW to listOf("hebrew", "aramaic", "biblical"),
    Culture.ARABIC to listOf("arabic"),
    Culture.INDIAN to listOf("indian", "sanskrit", "hindi"),
    Culture.TURKISH to listOf("turkish"),
    Culture.PERSIAN to listOf("persian", "iranian"),
)

/** The culture a free-text origin ("Irish", "Old English", "Latin/Greek") belongs to; unknown is [Culture.OTHER]. */
fun cultureOf(origin: String): Culture {
    val o = origin.lowercase()
    // ponytail: first keyword match wins, so a compound origin like "Greek/Latin" takes the earlier
    // culture in the table; split origins into a list if that ever matters.
    return KEYWORDS.firstOrNull { (_, words) -> words.any { it in o } }?.first ?: Culture.OTHER
}
