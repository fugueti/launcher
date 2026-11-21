/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.nova

import app.fugueti.gestures.engine.GestureKind
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.OpenAppTarget
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class NovaGestureImporterTest {

    private fun novaXml(vararg entries: Pair<String, String>): File {
        val body = entries.joinToString("\n") { (k, v) ->
            "<string name=\"$k\">${v.replace("&", "&amp;")}</string>"
        }
        return File.createTempFile("nova", ".xml").apply {
            writeText("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n$body\n<boolean name=\"home_button_action_default_page_only\" value=\"false\" />\n</map>")
            deleteOnExit()
        }
    }

    private val drawer = "App drawer\$#Intent;action=com.teslacoilsw.launcher.ACTION;launchFlags=0x10000000;component=com.teslacoilsw.launcher/.NovaShortcutHandler;S.LAUNCHER_ACTION=APP_DRAWER;end"
    private val lock = "Screen lock\$#Intent;action=com.teslacoilsw.launcher.ACTION;launchFlags=0x10000000;extendedLaunchFlags=0x4;component=com.teslacoilsw.launcher/.NovaShortcutHandler;S.LAUNCHER_ACTION=SCREEN_OFF;end"
    private val notifications = "Expand notifications\$#Intent;action=com.teslacoilsw.launcher.ACTION;launchFlags=0x10000000;component=com.teslacoilsw.launcher/.NovaShortcutHandler;S.LAUNCHER_ACTION=EXPAND_STATUS_BAR;end"
    private val app = "Example\$#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10200000;component=com.example.app/.MainActivity;sourceBounds=1%202%203%204;end"
    private val unknown = "Something\$#Intent;action=com.teslacoilsw.launcher.ACTION;component=com.teslacoilsw.launcher/.NovaShortcutHandler;S.LAUNCHER_ACTION=SOMETHING_NEW;end"

    @Test
    fun mapsGesturesAndNovaActions() {
        val result = NovaGestureImporter.parse(
            novaXml(
                "gesture_swipe_up" to drawer,
                "gesture_double_tap" to lock,
                "gesture_swipe_down" to notifications,
                "gesture_pinch" to app,
                "gesture_rotate_ccw" to unknown,
                "not_a_gesture" to app,
            ),
        )
        assertEquals(GestureHandlerConfig.OpenAppDrawer, result[GestureKind.SWIPE_UP])
        assertEquals(GestureHandlerConfig.Sleep, result[GestureKind.DOUBLE_TAP])
        assertEquals(GestureHandlerConfig.OpenNotifications, result[GestureKind.SWIPE_DOWN])
        // unknown Nova action and non-gesture keys are skipped
        assertFalse(GestureKind.ROTATE_CCW in result)
        assertEquals(4, result.size)
    }

    @Test
    fun appLaunchBecomesOpenApp() {
        val config = NovaGestureImporter.toConfig(app) as GestureHandlerConfig.OpenApp
        assertEquals("Example", config.appName)
        val target = config.target as OpenAppTarget.App
        assertEquals("com.example.app", target.key.componentName.packageName)
        assertEquals("com.example.app.MainActivity", target.key.componentName.className)
    }

    @Test
    fun otherIntentsBecomeOpenIntent() {
        val value = "Wi-Fi\$#Intent;action=android.settings.WIFI_SETTINGS;launchFlags=0x10000000;end"
        val config = NovaGestureImporter.toConfig(value) as GestureHandlerConfig.OpenIntent
        assertEquals("Wi-Fi", config.label)
        assertTrue(config.uri.contains("android.settings.WIFI_SETTINGS"))
    }

    @Test
    fun allGestureKeysMap() {
        val keys = listOf(
            "gesture_swipe_up", "gesture_swipe_down", "gesture_double_tap", "gesture_double_tap_swipe_up",
            "gesture_double_tap_swipe_down", "gesture_two_finger_scroll_up", "gesture_two_finger_scroll_down",
            "gesture_pinch", "gesture_pinch_out", "gesture_rotate_cw", "gesture_rotate_ccw",
        )
        val result = NovaGestureImporter.parse(novaXml(*keys.map { it to app }.toTypedArray()))
        assertEquals(11, result.size)
    }
}
