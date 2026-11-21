/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * The app follows the device language: English is the default, Portuguese is a full translation,
 * and no Fugueti screen mixes the two.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class LocaleStringsTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun inLocale(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }

    /** Every Fugueti string id, found through the generated R class. */
    private val ownStrings: Map<String, Int> = R.string::class.java.fields
        .filter { it.name.startsWith("fg_") }
        .associate { it.name to it.getInt(null) }

    /** Strings that are the same in every language (only placeholders and symbols). */
    private val languageNeutral = setOf("fg_action_open_intent_config", "fg_test_pad_result")

    /** Lawnchair strings shown on the gesture screens next to Fugueti ones. */
    private val upstreamOnGestureScreens = listOf(
        R.string.gestures_label,
        R.string.gesture_swipe_up,
        R.string.gesture_swipe_down,
        R.string.gesture_swipe_left,
        R.string.gesture_swipe_right,
        R.string.gesture_double_tap,
        R.string.gesture_home_tap,
        R.string.gesture_back_tap,
        R.string.gesture_handler_no_op,
        R.string.gesture_handler_sleep,
        R.string.gesture_handler_recents,
        R.string.gesture_handler_open_notifications,
        R.string.gesture_handler_open_app_option,
        R.string.gesture_handler_open_app_drawer,
        R.string.gesture_handler_open_app_search,
        R.string.gesture_handler_open_search,
        R.string.gesture_handler_open_assistant,
        R.string.d2ts_recents_a11y_hint_title,
        R.string.dt2s_admin_hint_title,
    )

    @Test
    fun englishIsTheDefault() {
        assertTrue(ownStrings.size > 50)
        assertEquals("Pinch in", inLocale("en-US").getString(R.string.fg_gesture_pinch_in))
        // A language without a Fugueti translation falls back to English.
        assertEquals("Pinch in", inLocale("de-DE").getString(R.string.fg_gesture_pinch_in))
    }

    @Test
    fun portugueseFollowsTheDevice() {
        assertEquals("Pinça para dentro", inLocale("pt-BR").getString(R.string.fg_gesture_pinch_in))
        assertEquals("Pinça para dentro", inLocale("pt-PT").getString(R.string.fg_gesture_pinch_in))
    }

    @Test
    fun everyGestureStringIsTranslated() {
        val en = inLocale("en-US")
        val pt = inLocale("pt-BR")
        for ((name, id) in ownStrings) {
            if (name in languageNeutral) continue
            assertNotEquals("$name has no Portuguese translation", en.getString(id), pt.getString(id))
        }
    }

    @Test
    fun lawnchairStringsOnGestureScreensAreTranslated() {
        val en = inLocale("en-US")
        val pt = inLocale("pt-BR")
        for (id in upstreamOnGestureScreens) {
            val name = app.resources.getResourceEntryName(id)
            assertNotEquals("$name has no Portuguese translation", en.getString(id), pt.getString(id))
        }
    }

    @Test
    fun warningsNameFuguetiNotLawnchair() {
        for (tag in listOf("en-US", "pt-BR")) {
            val ctx = inLocale(tag)
            for ((name, id) in ownStrings) {
                assertFalse("$name ($tag) names Lawnchair", ctx.getString(id).contains("Lawnchair"))
            }
        }
        assertTrue(inLocale("en-US").getString(R.string.fg_set_default_launcher_tip).contains("Fugueti"))
        assertTrue(inLocale("pt-BR").getString(R.string.fg_dt2s_a11y_hint).contains("Fugueti"))
    }
}
