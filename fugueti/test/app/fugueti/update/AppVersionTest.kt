/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun parsesTagsAndVersions() {
        assertEquals(listOf(16, 0, 2), AppVersion.parse("v16.0.2"))
        assertEquals(listOf(17, 1, 10), AppVersion.parse("17.1.10"))
    }

    @Test
    fun rejectsOtherFormats() {
        assertNull(AppVersion.parse("nightly"))
        assertNull(AppVersion.parse("v16.1.0-beta.1"))
        assertNull(AppVersion.parse("16.0"))
        assertNull(AppVersion.parse("16.0.2-nightly.4"))
    }

    @Test
    fun comparesEachPartNumerically() {
        assertTrue(AppVersion.isNewer("v16.0.3", "16.0.2"))
        assertTrue(AppVersion.isNewer("v16.1.0", "16.0.9"))
        assertTrue(AppVersion.isNewer("v16.0.10", "16.0.9"))
        assertTrue(AppVersion.isNewer("v17.0.0", "16.9.9"))
    }

    @Test
    fun sameOrOlderIsNotNewer() {
        assertFalse(AppVersion.isNewer("v16.0.2", "16.0.2"))
        assertFalse(AppVersion.isNewer("v16.0.1", "16.0.2"))
        assertFalse(AppVersion.isNewer("v16.0.3", "garbage"))
    }
}
