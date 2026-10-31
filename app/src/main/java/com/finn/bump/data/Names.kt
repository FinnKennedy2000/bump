package com.finn.bump.data

import com.finn.bump.domain.Culture
import com.finn.bump.domain.NameGroup
import com.finn.bump.domain.NameIdea
import com.finn.bump.domain.Verdict
import com.finn.bump.domain.cultureOf

/** A name on the shared list, with each of your verdicts. [theirs] is null until they vote. */
data class ShortlistEntry(val name: String, val group: NameGroup, val mine: Verdict?, val theirs: Verdict?) {
    /** Both said yes — the only thing the app calls a match. */
    val isMatch: Boolean get() = mine == Verdict.YES && theirs == Verdict.YES
}

/**
 * The names left for [me] to judge in [group], in a stable shuffled order.
 *
 * Ordered by a hash of voter and name, so each name has a fixed place for this phone: the
 * order is the same every time the deck opens, judging a name only removes it (the card
 * waiting underneath is the one that comes up — a seeded shuffle of the remaining names
 * would reorder everything), and it differs between the two of you so you are not both
 * looking at the same names in the same order.
 */
fun nameQueue(ideas: List<NameIdea>, votes: List<NameVote>, me: String, group: NameGroup, culture: Culture? = null): List<NameIdea> {
    val live = votes.filterNot { it.deleted }
    val judged = live.filter { it.voter == me }.mapTo(HashSet()) { it.name.lowercase() }
    val known = ideas.mapTo(HashSet()) { it.name.lowercase() }
    // Names your partner typed in themselves are not in the bundled list; they join your
    // deck so you meet them the same way, blind, rather than only on the shared list.
    val theirOwn = live
        .filter { it.voter != me && it.group == group && it.name.lowercase() !in known && it.verdict != Verdict.NO }
        .distinctBy { it.name.lowercase() }
        .map { NameIdea(it.name, it.group, PARTNER_SUGGESTED, "") }
    // Partner-typed names have no known origin, so a culture filter leaves them out; they
    // come back under All.
    val pool = ideas.filter { it.group == group && (culture == null || cultureOf(it.origin) == culture) }
    return (pool + if (culture == null) theirOwn else emptyList())
        .filter { it.name.lowercase() !in judged }
        .sortedBy { "$me:${it.name.lowercase()}".hashCode() }
}

/** The origin line on a card for a name your partner added rather than one from the list. */
const val PARTNER_SUGGESTED = "Suggested by your partner"

/**
 * The names either of you is keen on — a yes or a maybe from someone. Your partner's
 * answer shows from the moment it syncs, whether or not you have swiped that name yet;
 * [ShortlistEntry.mine] is null until you do. Names you both turned down drop off; a name
 * only one of you passed on stays.
 *
 * Matches first, then names with any yes, then maybes; alphabetical within each. Votes
 * from any voter other than [me] count as the partner's, latest first — a reinstall
 * gives a phone a new voter id, and its old votes should still read as that person's.
 *
 * ponytail: the flip side is that if *this* phone loses its voter id (data cleared with
 * no backup to restore — the id is in `datastore/`, which is backed up), its own old
 * votes read as the partner's and can make false matches. The fix is a "these were mine"
 * claim on first launch; worth it only if a phone is ever wiped mid-search.
 */
fun shortlist(votes: List<NameVote>, me: String): List<ShortlistEntry> =
    votes.filterNot { it.deleted }
        .groupBy { it.name.lowercase() }
        .map { (_, vs) ->
            val mine = vs.filter { it.voter == me }.maxByOrNull { it.updatedAt }
            val theirs = vs.filter { it.voter != me }.maxByOrNull { it.updatedAt }
            val any = mine ?: theirs!!
            ShortlistEntry(any.name, any.group, mine?.verdict, theirs?.verdict)
        }
        .filter { it.mine in KEEN || it.theirs in KEEN }
        .sortedWith(
            compareBy<ShortlistEntry>(
                { !it.isMatch },
                { it.mine != Verdict.YES && it.theirs != Verdict.YES },
                { it.name.lowercase() },
            )
        )

private val KEEN = setOf(Verdict.YES, Verdict.MAYBE)

/**
 * What the partner has done with names since [seenAt], worded for a notification that
 * keeps their answers blind: a match or a name they typed in may be named, but their
 * likes on list names are only counted — naming them would spoil the swipe.
 */
data class NameNews(
    val matches: List<String>,
    val suggested: List<String>,
    val liked: Int,
    /** The newest partner stamp seen; store it as the next [seenAt]. */
    val newest: Long,
) {
    val isEmpty: Boolean get() = matches.isEmpty() && suggested.isEmpty() && liked == 0
}

fun nameNews(votes: List<NameVote>, me: String, known: Set<String>, seenAt: Long): NameNews {
    val live = votes.filterNot { it.deleted }
    val mine = live.filter { it.voter == me }.associateBy { it.name.lowercase() }
    val fresh = live.filter { it.voter != me && it.updatedAt > seenAt && it.verdict in KEEN }
    val matches = fresh.filter { it.verdict == Verdict.YES && mine[it.name.lowercase()]?.verdict == Verdict.YES }
    val unjudged = fresh.filter { it.name.lowercase() !in mine }
    val (suggested, liked) = unjudged.partition { it.name.lowercase() !in known }
    return NameNews(
        matches = matches.map { it.name }.distinct(),
        suggested = suggested.map { it.name }.distinct(),
        liked = liked.distinctBy { it.name.lowercase() }.size,
        newest = (live.filter { it.voter != me }.maxOfOrNull { it.updatedAt } ?: seenAt).coerceAtLeast(seenAt),
    )
}

/**
 * Names at least two different voters said yes to. For the keepsake, which belongs to
 * both of you and so has no "me" to measure from.
 */
fun sharedYeses(votes: List<NameVote>): List<String> =
    votes.filter { !it.deleted && it.verdict == Verdict.YES }
        .groupBy { it.name.lowercase() }
        .filterValues { vs -> vs.map { it.voter }.distinct().size >= 2 }
        .map { (_, vs) -> vs.first().name }
        .sortedBy { it.lowercase() }
