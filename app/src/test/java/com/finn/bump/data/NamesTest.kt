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
    fun `the partner's answers stay hidden until I have voted on the name`() {
        val list = shortlist(listOf(vote("them", "Ada", YES)), "me")
        assertTrue(list.isEmpty())
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
}
