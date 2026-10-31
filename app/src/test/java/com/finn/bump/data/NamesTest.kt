package com.finn.bump.data

import com.finn.bump.domain.NameGroup.BOY
import com.finn.bump.domain.NameGroup.GIRL
import com.finn.bump.domain.NameIdea
import com.finn.bump.domain.Verdict.MAYBE
import com.finn.bump.domain.Verdict.NO
import com.finn.bump.domain.Verdict.YES
import com.finn.bump.domain.Verdict
import com.finn.bump.domain.NameGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesTest {

    private fun vote(voter: String, name: String, verdict: Verdict, at: Long = 1, group: NameGroup = GIRL) =
        NameVote(NameVote.idFor(voter, name), voter, name, group, verdict, updatedAt = at)

    private val ideas = listOf("Isla", "Ada", "Ruby", "Iris").map { NameIdea(it, GIRL, "", "") } +
        NameIdea("Leo", BOY, "", "")

    @Test
    fun `the queue holds only my unjudged names in the group, in a stable order`() {
        val votes = listOf(vote("me", "Ada", NO), vote("them", "Ruby", YES))
        val q = nameQueue(ideas, votes, "me", GIRL)
        assertEquals(setOf("Isla", "Ruby", "Iris"), q.map { it.name }.toSet())
        assertEquals(q, nameQueue(ideas, votes, "me", GIRL))
    }

    @Test
    fun `judging the top name leaves the rest of the deck in the same order`() {
        val many = (1..60).map { NameIdea("Name$it", GIRL, "", "") }
        for (me in listOf("me", "voter-a", "voter-b")) {
            val before = nameQueue(many, emptyList(), me, GIRL)
            val after = nameQueue(many, listOf(vote(me, before.first().name, YES)), me, GIRL)
            assertEquals(me, before.drop(1), after)
        }
    }

    @Test
    fun `both keen means yes or maybe from each of us`() {
        val list = shortlist(
            listOf(
                vote("me", "Ada", MAYBE), vote("them", "Ada", YES),
                vote("me", "Ruby", MAYBE), vote("them", "Ruby", MAYBE),
                vote("me", "Isla", YES), vote("them", "Isla", NO),
                vote("them", "Iris", YES),
            ),
            "me",
        )
        assertEquals(setOf("Ada", "Ruby"), list.filter { it.isBothKeen }.map { it.name }.toSet())
    }

    @Test
    fun `both saying yes is a match and sorts first`() {
        val list = shortlist(
            listOf(
                vote("me", "Ruby", MAYBE), vote("them", "Ruby", MAYBE),
                vote("me", "Iris", YES),
                vote("me", "Isla", YES), vote("them", "Isla", YES),
            ),
            "me",
        )
        assertEquals(listOf("Isla", "Iris", "Ruby"), list.map { it.name })
        assertTrue(list[0].isMatch)
        assertFalse(list[1].isMatch)
    }

    @Test
    fun `a name only the partner likes shows on my list with my answer still empty`() {
        val entry = shortlist(listOf(vote("them", "Ada", YES)), "me").single()
        assertEquals("Ada", entry.name)
        assertNull(entry.mine)
        assertEquals(YES, entry.theirs)
        assertFalse(entry.isMatch)
    }

    @Test
    fun `a name the partner typed in joins my deck`() {
        val q = nameQueue(ideas, listOf(vote("them", "Marigold", YES)), "me", GIRL)
        assertEquals(PARTNER_SUGGESTED, q.single { it.name == "Marigold" }.origin)
        assertTrue(nameQueue(ideas, listOf(vote("them", "Marigold", YES), vote("me", "Marigold", NO)), "me", GIRL)
            .none { it.name == "Marigold" })
    }

    @Test
    fun `names neither of you is keen on drop off, one-sided noes stay`() {
        val list = shortlist(
            listOf(vote("me", "Ada", NO), vote("them", "Ada", NO), vote("me", "Iris", NO), vote("them", "Iris", YES)),
            "me",
        )
        assertEquals(listOf("Iris"), list.map { it.name })
    }

    @Test
    fun `a changed mind replaces the old verdict, and votes merge per person`() {
        val mine = AppState(nameVotes = listOf(vote("me", "Isla", MAYBE, at = 1)))
        val theirs = AppState(nameVotes = listOf(vote("me", "Isla", YES, at = 2), vote("them", "Isla", YES, at = 2)))
        val merged = merge(mine, theirs)
        assertEquals(2, merged.nameVotes.size)
        assertTrue(shortlist(merged.nameVotes, "me").single().isMatch)
    }

    @Test
    fun `a culture filter keeps only that culture and leaves partner-typed names out`() {
        val ideas = listOf(
            NameIdea("Aoife", GIRL, "Irish", "x"), NameIdea("Olga", GIRL, "Russian", "x"), NameIdea("Rhian", GIRL, "Welsh", "x"),
        )
        val votes = listOf(vote("them", "Marigold", YES))
        assertEquals(listOf("Aoife"), nameQueue(ideas, votes, "me", GIRL, com.finn.bump.domain.Culture.IRISH).map { it.name })
        assertEquals(setOf("Aoife", "Olga", "Rhian", "Marigold"), nameQueue(ideas, votes, "me", GIRL).map { it.name }.toSet())
    }

    @Test
    fun `awaiting me is the partner's current keen votes on names I have not answered`() {
        val votes = listOf(
            vote("them", "Ada", YES), vote("them", "Ruby", MAYBE), vote("them", "Isla", YES),
            vote("them", "Iris", YES, at = 1), vote("them2", "Iris", NO, at = 2),
            vote("them", "Leo", NO),
            vote("me", "Isla", NO), vote("me", "Ruby", MAYBE),
        )
        assertEquals(listOf("Ada"), awaitingMe(votes, "me").map { it.name })
    }

    @Test
    fun `the queue can be narrowed to the names awaiting me`() {
        val votes = listOf(vote("them", "Ruby", YES))
        val only = awaitingMe(votes, "me").mapTo(HashSet()) { it.name.lowercase() }
        assertEquals(listOf("Ruby"), nameQueue(ideas, votes, "me", GIRL, only = only).map { it.name })
        assertEquals(emptyList<NameIdea>(), nameQueue(ideas, votes, "me", GIRL, only = emptySet()))
    }

    @Test
    fun `list sections put both keen apart, matches first, and respect the group filter`() {
        val list = shortlist(
            listOf(
                vote("me", "Ada", MAYBE), vote("them", "Ada", YES),
                vote("me", "Ruby", YES), vote("them", "Ruby", YES),
                vote("them", "Iris", YES),
                vote("me", "Leo", YES, group = BOY), vote("them", "Leo", YES, group = BOY),
            ),
            "me",
        )
        val all = listSections(list, null)
        assertEquals(listOf("Leo", "Ruby", "Ada"), all.bothKeen.map { it.name })
        assertEquals(listOf("Iris"), all.onlyOne.map { it.name })
        val boys = listSections(list, BOY)
        assertEquals(listOf("Leo"), boys.bothKeen.map { it.name })
        assertTrue(boys.onlyOne.isEmpty())
    }
}
