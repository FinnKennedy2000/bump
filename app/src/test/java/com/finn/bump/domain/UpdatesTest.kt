package com.finn.bump.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun parsesTag() {
        assertEquals(12, buildNumber("build-12"))
        assertNull(buildNumber("v1.2"))
        assertNull(buildNumber("build-"))
        assertNull(buildNumber("build-12-rc"))
    }

    @Test fun comparesToInstalled() {
        assertFalse(isNewer(5, "garbage"))
        assertFalse(isNewer(5, "build-5"))
        assertFalse(isNewer(5, "build-4"))
        assertTrue(isNewer(5, "build-6"))
    }
}
