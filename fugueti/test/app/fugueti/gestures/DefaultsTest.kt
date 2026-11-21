/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.R
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Fugueti defaults that differ from Lawnchair's. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class DefaultsTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun lawnchairAnnouncementsAreOffByDefault() {
        // Lawnchair's remote announcements (and, in debug builds, its test announcements) would
        // show Lawnchair news, in English only, at the top of Fugueti's settings.
        assertFalse(app.resources.getBoolean(R.bool.config_default_live_information_enabled))
    }
}
