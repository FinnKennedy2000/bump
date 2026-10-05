package com.finn.bump.domain

import kotlinx.serialization.Serializable

/** Which list a name sits in. A name can be in two — Rowan is both a boy's and a neutral name. */
@Serializable
enum class NameGroup { GIRL, BOY, NEUTRAL }

/** How one of you feels about a name. */
@Serializable
enum class Verdict { YES, MAYBE, NO }

/**
 * A name from the bundled list, with where it comes from and its traditional meaning.
 *
 * Meanings are the usual etymologies given for each name — folk history as much as
 * scholarship, and many names have more than one. They are there to talk about, not to
 * settle anything.
 */
@Serializable
data class NameIdea(val name: String, val group: NameGroup, val origin: String, val meaning: String)

/** The bundled name list, looked up by group and by name. */
class NameTable(val all: List<NameIdea>) {
    init {
        require(all.isNotEmpty()) { "name list is empty" }
    }

    fun inGroup(group: NameGroup): List<NameIdea> = all.filter { it.group == group }

    private val byName = all.groupBy { it.name.lowercase() }

    /** The first entry for a name, whatever its group — for showing a meaning on the list. */
    fun find(name: String): NameIdea? = byName[name.trim().lowercase()]?.firstOrNull()
}
