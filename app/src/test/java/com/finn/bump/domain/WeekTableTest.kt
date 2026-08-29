package com.finn.bump.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekTableTest {

    private val table = WeekTable(
        (0..40).map { WeekInfo(
            week = it,
            size = "size-$it",
            lengthCm = it.toDouble(),
            weightG = it * 10,
            blurb = "blurb-$it",
        ) }
    )

    @Test
    fun `looks up a week in range`() {
        assertEquals("size-22", table[22].size)
    }

    @Test
    fun `clamps above the last defined week so overdue still renders`() {
        assertEquals("size-40", table[41].size)
        assertEquals("size-40", table[52].size)
    }

    @Test
    fun `clamps below zero`() {
        assertEquals("size-0", table[-3].size)
    }

    @Test
    fun `length is crown to heel only from week 21`() {
        assertEquals(false, table[20].measuresCrownToHeel)
        assertEquals(true, table[21].measuresCrownToHeel)
    }
}
