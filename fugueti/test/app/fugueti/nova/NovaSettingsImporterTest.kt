/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.nova

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class NovaSettingsImporterTest {

    private fun novaXml(vararg entries: String): File =
        File.createTempFile("nova", ".xml").apply {
            writeText(
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" +
                    entries.joinToString("\n") +
                    "\n<int name=\"drawer_transparency\" value=\"128\" />\n</map>",
            )
            deleteOnExit()
        }

    private fun string(name: String, value: String) = "<string name=\"$name\">$value</string>"
    private fun boolean(name: String, value: Boolean) = "<boolean name=\"$name\" value=\"$value\" />"

    // Nova 9.0.1 color schemes, with the app drawer background (third group) at 0% and 100%
    // transparency.
    private fun colorScheme(drawerBackground: Long) =
        "{0:72057598316118038:72057598316118016:72057598316118021:72057598316118021:40:20}:" +
            "{0:0:288230376151711744:72057598316118023:72057598316118023:40:20}:" +
            "{0:$drawerBackground:288230376151711744:72057598316118021:72057598316118021:40:20}:" +
            "{0:72057598316118024:288230376151711744:72057598316118020:72057598316118020:40:20}:::" +
            "{0:72057598316118028:288230376151711744:72057598316118023:72057598316118023:40:20}"

    @Test
    fun readsANovaBackup() {
        val settings = NovaSettingsImporter.parse(
            novaXml(
                string("desktop_cellspecs", "1.0:false:13.0:262914:true:sans-serif-condensed:true:false"),
                string("drawer_cellspecs", "0.9:true:13.0:262914:false:sans-serif-condensed:true:true"),
                string("color_scheme_night", colorScheme(72057598316118038)),
                string("drawer_grid", "10x7"),
                boolean("show_notification_bar", false),
                boolean("drawer_show_keyboard_by_default", true),
                string("drawer_searchbar_position", "TOP"),
                boolean("drawer_predictive_apps", false),
                string("screen_orientation", "NOSENSOR"),
            ),
        )
        assertEquals(
            NovaSettingsImporter.Settings(
                homeScreenLabels = false,
                drawerLabels = true,
                drawerOpacity = 1f,
                homeIconSize = 1f,
                drawerIconSize = 0.9f,
                homeLabelSize = 1f,
                drawerLabelSize = 1f,
                drawerColumns = 7,
                showStatusBar = false,
                showKeyboardInDrawer = true,
                hideDrawerSearchBar = false,
                showSuggestedApps = false,
                allowRotation = false,
            ),
            settings,
        )
    }

    @Test
    fun fullyTransparentDrawer() {
        val settings = NovaSettingsImporter.parse(
            novaXml(string("color_scheme_night", colorScheme(72057594037927958))),
        )
        assertEquals(0f, settings.drawerOpacity)
    }

    @Test
    fun halfTransparentDrawer() {
        // alpha 0x7f
        val settings = NovaSettingsImporter.parse(
            novaXml(string("color_scheme_night", colorScheme(72057596168634390))),
        )
        assertEquals(0.5f, settings.drawerOpacity)
    }

    @Test
    fun valuesOutsideFuguetiRangesAreClamped() {
        val settings = NovaSettingsImporter.parse(
            novaXml(
                string("desktop_cellspecs", "2.0:true:26.0"),
                string("drawer_grid", "10x12"),
                string("drawer_searchbar_position", "NONE"),
            ),
        )
        assertEquals(1.5f, settings.homeIconSize)
        assertEquals(1.5f, settings.homeLabelSize)
        assertEquals(10, settings.drawerColumns)
        assertEquals(true, settings.hideDrawerSearchBar)
    }

    @Test
    fun missingOrMalformedValuesLeaveSettingsAlone() {
        val settings = NovaSettingsImporter.parse(
            novaXml(
                string("desktop_cellspecs", "x"),
                string("color_scheme_night", "{0:1}"),
                string("screen_orientation", "SOMETHING_NEW"),
            ),
        )
        assertEquals(NovaSettingsImporter.Settings(), settings)
    }
}
