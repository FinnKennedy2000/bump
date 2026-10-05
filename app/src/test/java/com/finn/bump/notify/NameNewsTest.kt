package com.finn.bump.notify

import com.finn.bump.data.NameVote
import com.finn.bump.data.nameNews
import com.finn.bump.data.sharedYeses
import com.finn.bump.domain.NameGroup.GIRL
import com.finn.bump.domain.Verdict
import com.finn.bump.domain.Verdict.MAYBE
import com.finn.bump.domain.Verdict.NO
import com.finn.bump.domain.Verdict.YES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameNewsTest {

    private fun vote(voter: String, name: String, verdict: Verdict, at: Long) =
        NameVote(NameVote.idFor(voter, name), voter, name, GIRL, verdict, updatedAt = at)

    private val known = setOf("isla", "ada", "ruby", "iris")

    @Test
    fun `only partner likes newer than the seen mark count, and the mark moves to the newest`() {
        val votes = listOf(
            vote("them", "Ada", YES, at = 5),       // already seen
            vote("them", "Ruby", YES, at = 20),
            vote("them", "Iris", MAYBE, at = 30),
            vote("them", "Isla", NO, at = 40),      // a no is never news
        )
        val news = nameNews(votes, "me", known, seenAt = 10)
        assertEquals(2, news.liked)
        assertEquals(40L, news.newest)
    }

    @Test
    fun `a partner yes on a name I already said yes to is a named match, not a count`() {
        val news = nameNews(listOf(vote("me", "Isla", YES, 1), vote("them", "Isla", YES, 20)), "me", known, 10)
        assertEquals(listOf("Isla"), news.matches)
        assertEquals(0, news.liked)
        assertEquals("You both like Isla", words(news).first)
    }

    @Test
    fun `a name the partner typed in is named, list names stay blind`() {
        val news = nameNews(listOf(vote("them", "Marigold", YES, 20), vote("them", "Ruby", YES, 21)), "me", known, 0)
        assertEquals(listOf("Marigold"), news.suggested)
        val (title, text) = words(news)
        assertEquals("A name from your partner", title)
        assertTrue(text.contains("Marigold"))
        assertFalse("a liked list name must not be named", text.contains("Ruby"))
        assertTrue(text.contains("liked a new name"))
    }

    @Test
    fun `nothing new is empty`() {
        assertTrue(nameNews(listOf(vote("me", "Ada", YES, 50)), "me", known, 0).isEmpty)
    }

    @Test
    fun `the keepsake's matches need two different voters`() {
        val votes = listOf(vote("a", "Isla", YES, 1), vote("b", "Isla", YES, 1), vote("a", "Ada", YES, 1))
        assertEquals(listOf("Isla"), sharedYeses(votes))
    }
}
