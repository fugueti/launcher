/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.nova

import android.content.Context
import app.lawnchair.hotseat.DisabledHotseat
import app.lawnchair.hotseat.LawnchairHotseat
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.InvariantDeviceProfile
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/**
 * Reads the settings of a Nova Launcher backup (`nova.xml`) that Fugueti has too.
 *
 * - Icon size and labels: `desktop_cellspecs` and `drawer_cellspecs` are colon-separated strings
 *   such as `1.0:false:13.0:262914:true:sans-serif-condensed:true:false`: icon scale, whether
 *   labels are shown, label size in sp. Nova's default label size, 13sp, is taken as Fugueti's
 *   default label size.
 * - App drawer background: `color_scheme_night` (and `color_scheme` for the light theme) is a
 *   colon-separated list of `{…}` groups, one per Nova surface; the third is the app drawer, and
 *   its second field is the background color. Colors are packed longs whose low 32 bits are an
 *   ARGB color (the upper bits say which kind of color it is), so bits 24-31 are the
 *   alpha (Nova shows it as transparency: alpha 0 is "100%"). The old `drawer_transparency` key
 *   no longer follows Nova's setting and is ignored.
 * - `drawer_grid` (`rows`x`columns`), `show_notification_bar`, `drawer_show_keyboard_by_default`,
 *   `drawer_searchbar_position` (`NONE` hides it, `BOTTOM` puts it at the bottom),
 *   `drawer_pull_to_search`, `searchbar_placement` (home screen search bar: `DOCK_BELOW_ICONS`,
 *   `DOCK_ABOVE_ICONS` or `PERSISTENT` at the top of the screen; `NONE` turns the dock search
 *   bar off and the others turn it back on if it was off; Nova's other places are left alone),
 *   `drawer_predictive_apps` and `screen_orientation`.
 */
object NovaSettingsImporter {
    private const val DESKTOP_CELLSPECS = "desktop_cellspecs"
    private const val DRAWER_CELLSPECS = "drawer_cellspecs"
    private const val COLOR_SCHEME_NIGHT = "color_scheme_night"
    private const val COLOR_SCHEME_DAY = "color_scheme"
    private const val DRAWER_GRID = "drawer_grid"
    private const val SHOW_NOTIFICATION_BAR = "show_notification_bar"
    private const val DRAWER_SHOW_KEYBOARD = "drawer_show_keyboard_by_default"
    private const val DRAWER_SEARCH_BAR_POSITION = "drawer_searchbar_position"
    private const val DRAWER_PULL_TO_SEARCH = "drawer_pull_to_search"
    private const val SEARCHBAR_PLACEMENT = "searchbar_placement"
    private const val DRAWER_PREDICTIVE_APPS = "drawer_predictive_apps"
    private const val SCREEN_ORIENTATION = "screen_orientation"

    private const val ICON_SCALE_INDEX = 0
    private const val SHOW_LABELS_INDEX = 1
    private const val LABEL_SIZE_INDEX = 2
    private const val NOVA_DEFAULT_LABEL_SIZE = 13f
    private const val DRAWER_GROUP_INDEX = 2
    private const val BACKGROUND_FIELD_INDEX = 1
    private const val ALPHA_SHIFT = 24
    private const val TENTHS = 10f

    // Ranges of Fugueti's sliders.
    private val SIZE_FACTOR_RANGE = 0.5f..1.5f
    private val DRAWER_COLUMNS_RANGE = 3..10

    private val gridRegex = Regex("(\\d+)x(\\d+)")
    private val fixedOrientations = setOf("NOSENSOR", "PORTRAIT")
    private val rotatingOrientations = setOf("SENSOR", "FULL_SENSOR", "AUTO")

    /** Nova's settings, in Fugueti's terms; null where the backup does not say. */
    data class Settings(
        val homeScreenLabels: Boolean? = null,
        val drawerLabels: Boolean? = null,
        /** 0 (transparent) to 1 (solid), in the drawer opacity slider's 10% steps. */
        val drawerOpacity: Float? = null,
        val homeIconSize: Float? = null,
        val drawerIconSize: Float? = null,
        val homeLabelSize: Float? = null,
        val drawerLabelSize: Float? = null,
        val drawerColumns: Int? = null,
        val showStatusBar: Boolean? = null,
        val showKeyboardInDrawer: Boolean? = null,
        val hideDrawerSearchBar: Boolean? = null,
        val drawerSearchBarAtBottom: Boolean? = null,
        val pullToSearch: Boolean? = null,
        /** One of PreferenceManager2's HOME_SEARCH_BAR_* values. */
        val homeSearchBarPosition: String? = null,
        /** Whether Nova has no home screen search bar (`searchbar_placement` = `NONE`). */
        val homeSearchBarOff: Boolean? = null,
        val showSuggestedApps: Boolean? = null,
        val allowRotation: Boolean? = null,
    )

    fun parse(xmlFile: File): Settings {
        val root = runCatching {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile).documentElement
        }.getOrNull() ?: return Settings()
        val values = mutableMapOf<String, String>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
            values[name] = node.attributes.getNamedItem("value")?.nodeValue ?: node.textContent ?: continue
        }
        val desktopSpecs = values[DESKTOP_CELLSPECS]?.split(":")
        val drawerSpecs = values[DRAWER_CELLSPECS]?.split(":")
        return Settings(
            homeScreenLabels = desktopSpecs?.getOrNull(SHOW_LABELS_INDEX)?.toBooleanStrictOrNull(),
            drawerLabels = drawerSpecs?.getOrNull(SHOW_LABELS_INDEX)?.toBooleanStrictOrNull(),
            // Fugueti has one drawer opacity: prefer the dark scheme, Nova's usual one.
            drawerOpacity = drawerOpacity(values[COLOR_SCHEME_NIGHT])
                ?: drawerOpacity(values[COLOR_SCHEME_DAY]),
            homeIconSize = sizeFactor(desktopSpecs?.getOrNull(ICON_SCALE_INDEX)),
            drawerIconSize = sizeFactor(drawerSpecs?.getOrNull(ICON_SCALE_INDEX)),
            homeLabelSize = labelSizeFactor(desktopSpecs?.getOrNull(LABEL_SIZE_INDEX)),
            drawerLabelSize = labelSizeFactor(drawerSpecs?.getOrNull(LABEL_SIZE_INDEX)),
            drawerColumns = values[DRAWER_GRID]?.let { gridRegex.find(it) }
                ?.groupValues?.get(2)?.toIntOrNull()?.coerceIn(DRAWER_COLUMNS_RANGE),
            showStatusBar = values[SHOW_NOTIFICATION_BAR]?.toBooleanStrictOrNull(),
            showKeyboardInDrawer = values[DRAWER_SHOW_KEYBOARD]?.toBooleanStrictOrNull(),
            hideDrawerSearchBar = values[DRAWER_SEARCH_BAR_POSITION]?.let { it == "NONE" },
            drawerSearchBarAtBottom = values[DRAWER_SEARCH_BAR_POSITION]
                ?.takeIf { it != "NONE" }?.let { it == "BOTTOM" },
            pullToSearch = values[DRAWER_PULL_TO_SEARCH]?.toBooleanStrictOrNull(),
            homeSearchBarPosition = when (values[SEARCHBAR_PLACEMENT]) {
                "DOCK_BELOW_ICONS" -> PreferenceManager2.HOME_SEARCH_BAR_DOCK_BELOW
                "DOCK_ABOVE_ICONS" -> PreferenceManager2.HOME_SEARCH_BAR_DOCK_ABOVE
                "PERSISTENT" -> PreferenceManager2.HOME_SEARCH_BAR_TOP
                else -> null
            },
            homeSearchBarOff = values[SEARCHBAR_PLACEMENT]?.let { it == "NONE" },
            showSuggestedApps = values[DRAWER_PREDICTIVE_APPS]?.toBooleanStrictOrNull(),
            allowRotation = when (values[SCREEN_ORIENTATION]) {
                in fixedOrientations -> false
                in rotatingOrientations -> true
                else -> null
            },
        )
    }

    private fun tenths(value: Float) = (value * TENTHS).roundToInt() / TENTHS

    private fun sizeFactor(scale: String?): Float? =
        scale?.toFloatOrNull()?.takeIf { it > 0f }?.let { tenths(it.coerceIn(SIZE_FACTOR_RANGE)) }

    private fun labelSizeFactor(size: String?): Float? =
        size?.toFloatOrNull()?.takeIf { it > 0f }
            ?.let { tenths((it / NOVA_DEFAULT_LABEL_SIZE).coerceIn(SIZE_FACTOR_RANGE)) }

    private fun drawerOpacity(colorScheme: String?): Float? {
        val group = topLevelFields(colorScheme ?: return null).getOrNull(DRAWER_GROUP_INDEX)
            ?.removeSurrounding("{", "}") ?: return null
        val color = group.split(":").getOrNull(BACKGROUND_FIELD_INDEX)?.toLongOrNull() ?: return null
        if (color == 0L) return null
        val alpha = ((color shr ALPHA_SHIFT) and 0xff).toInt()
        return tenths(alpha / 255f)
    }

    /** Splits on the colons that are not inside `{…}`, keeping empty fields. */
    private fun topLevelFields(text: String): List<String> {
        val fields = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { i, c ->
            when (c) {
                '{' -> depth++
                '}' -> depth--
                ':' -> if (depth == 0) {
                    fields += text.substring(start, i)
                    start = i + 1
                }
            }
        }
        fields += text.substring(start)
        return fields
    }

    suspend fun apply(context: Context, settings: Settings) {
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)
        settings.homeScreenLabels?.let { prefs2.showIconLabelsOnHomeScreen.set(it) }
        settings.drawerLabels?.let { prefs2.showIconLabelsInDrawer.set(it) }
        settings.drawerOpacity?.let { prefs.drawerOpacity.set(it) }
        settings.homeIconSize?.let { prefs2.homeIconSizeFactor.set(it) }
        settings.drawerIconSize?.let { prefs2.drawerIconSizeFactor.set(it) }
        settings.homeLabelSize?.let { prefs2.homeIconLabelSizeFactor.set(it) }
        settings.drawerLabelSize?.let { prefs2.drawerIconLabelSizeFactor.set(it) }
        settings.drawerColumns?.let {
            prefs2.drawerColumns.set(it, InvariantDeviceProfile.INSTANCE.get(context).closestProfile)
        }
        settings.showStatusBar?.let { prefs2.showStatusBar.set(it) }
        settings.showKeyboardInDrawer?.let { prefs2.autoShowKeyboardInDrawer.set(it) }
        settings.hideDrawerSearchBar?.let { prefs2.hideAppDrawerSearchBar.set(it) }
        settings.drawerSearchBarAtBottom?.let { prefs2.drawerSearchBarAtBottom.set(it) }
        settings.homeSearchBarPosition?.let { prefs2.homeSearchBarPosition.set(it) }
        when (settings.homeSearchBarOff) {
            true -> prefs2.hotseatMode.set(DisabledHotseat)
            // Nova has one: bring the dock bar back if it is off (keeps Google's bar if chosen).
            false -> if (prefs2.hotseatMode.firstCached() == DisabledHotseat) prefs2.hotseatMode.set(LawnchairHotseat)
            null -> Unit
        }
        settings.pullToSearch?.let { prefs2.drawerPullToSearch.set(it) }
        settings.showSuggestedApps?.let { prefs2.showSuggestedAppsInDrawer.set(it) }
        settings.allowRotation?.let { prefs.allowRotation.set(it) }
    }
}
