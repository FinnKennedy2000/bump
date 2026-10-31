package com.finn.bump

import com.finn.bump.ui.components.fetusPicture
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FetusPictureTest {
    private val dir = File("src/main/res/drawable-nodpi")

    @Test fun everyWeekFrom5To40HasAPictureFile() {
        for (week in 5..40) {
            assertNotNull("week $week", fetusPicture(week))
            val file = File(dir, "fetus_w%02d.webp".format(week))
            assertTrue("missing ${file.name}", file.isFile && file.length() > 10_000)
        }
    }

    @Test fun earlyAndOutOfRangeWeeksFallBackToTheDrawnRender() {
        assertNull(fetusPicture(4))
        assertNull(fetusPicture(41))
    }

    @Test fun picturesAreDistinctPerWeek() {
        assertEquals(36, (5..40).map { fetusPicture(it) }.toSet().size)
    }
}
