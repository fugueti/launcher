/*
 * Copyright (C) 2026 Fugueti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.deviceprofile

import android.content.Context
import app.lawnchair.DeviceProfileOverrides
import app.lawnchair.LawnchairApp
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.R
import com.android.launcher3.Utilities

/**
 * Lawnchair's layout preferences, read where AOSP 17's profile factories compute the matching
 * dimension (on Android 16 they lived in DeviceProfile.java). The preferences are app-wide, so
 * they are read from the application context; defaults keep the AOSP layout.
 */
data class LawnchairLayoutFactors(
    /** Workspace side margin, applied at the source so cells fill the freed space (0..2). */
    val workspacePaddingHorizontal: Float = 1f,
    /** Workspace top/bottom padding, keeping the dock reservation (0..2). */
    val workspacePaddingVertical: Float = 1f,
    /** Widget padding (0 = none, 1 = AOSP). */
    val widgetPadding: Float = 1f,
    /** Page indicator height. */
    val pageIndicatorHeight: Float = 1f,
    /** Workspace icon label size (0 when labels are off). */
    val iconTextSize: Float = 1f,
    /** Folder icon label size (0 when labels are off). */
    val folderTextSize: Float = 1f,
    /** Workspace icon size, applied to responsive grids (IDP grids are scaled at the source). */
    val homeIconSize: Float = 1f,
    /** Space under the dock. */
    val hotseatBottom: Float = 1f,
    /** Additive drawer top padding, 1..2 (1 = no change). */
    val drawerPaddingTop: Float = 1f,
    /** Drawer side margin multiplier (preference value, see AllAppsProfile). */
    val drawerLeftRightMargin: Float = 0.01f,
    /** Labels under the dock icons. */
    val dockLabels: Boolean = false,
    /** Dock visible at all. */
    val hotseatEnabled: Boolean = true,
    /** Dock has a search bar (HotseatMode). */
    val hotseatQsb: Boolean = true,
    /** Dock rows (1..2). */
    val hotseatRows: Int = 1,
) {
    companion object {
        /**
         * Responsive cell specs' icon size scaled by Home screen > Icon size, like the IDP grids
         * (whose icon size DeviceProfileOverrides scales). The specs never see that setting.
         */
        @JvmStatic
        fun responsiveIconSize(specIconSizePx: Int): Int =
            kotlin.math.max(1, kotlin.math.round(specIconSizePx * get().homeIconSize).toInt())

        /** AOSP defaults, used when the application is not available (previews, tests). */
        @JvmField val DEFAULT = LawnchairLayoutFactors()

        @JvmStatic
        fun get(): LawnchairLayoutFactors {
            val context: Context =
                try {
                    LawnchairApp.instance
                } catch (e: UninitializedPropertyAccessException) {
                    return DEFAULT
                }
            return from(context)
        }

        @JvmStatic
        fun from(context: Context): LawnchairLayoutFactors {
            val prefs2 = PreferenceManager2.INSTANCE.get(context)
            val prefs = PreferenceManager.getInstance(context)
            val textFactors = DeviceProfileOverrides.INSTANCE.get(context).getTextFactors()
            fun clamp(value: Float, lo: Float, hi: Float) = Utilities.boundToRange(value, lo, hi)
            return LawnchairLayoutFactors(
                // Clamp to the slider ranges so a restored or hand-edited preference can't feed
                // out-of-range values into the layout math.
                workspacePaddingHorizontal =
                    clamp(prefs2.workspacePaddingHorizontalFactor.firstCached(), 0f, 2f),
                workspacePaddingVertical =
                    clamp(prefs2.workspacePaddingVerticalFactor.firstCached(), 0f, 2f),
                widgetPadding = clamp(prefs2.widgetPaddingFactor.firstCached(), 0f, 2f),
                pageIndicatorHeight = prefs2.pageIndicatorHeightFactor.firstCached(),
                iconTextSize = textFactors.iconTextSizeFactor,
                folderTextSize = textFactors.iconFolderTextSizeFactor,
                homeIconSize = prefs2.homeIconSizeFactor.firstCached(),
                hotseatBottom = prefs2.hotseatBottomFactor.firstCached(),
                drawerPaddingTop = clamp(prefs2.drawerPaddingTopFactor.firstCached(), 1f, 2f),
                drawerLeftRightMargin = prefs2.drawerLeftRightMarginFactor.firstCached(),
                dockLabels = prefs2.enableLabelInDock.firstCached(),
                hotseatEnabled = prefs2.isHotseatEnabled.firstCached(),
                hotseatQsb =
                    prefs2.hotseatMode.firstCached().layoutResourceId != R.layout.empty_view,
                hotseatRows = prefs.hotseatRows.get().coerceIn(1, 2),
            )
        }
    }
}

/**
 * Lawnchair's dock sizing (Android 16 `DeviceProfile.updateHotseatSizes`): label-aware cell
 * height, bottom space factor, extra rows, QSB-on-top layouts and a hidden dock. [AOSP] keeps
 * AOSP 17's sizing.
 */
data class LawnchairDock(
    val active: Boolean = false,
    /** Height of a workspace icon label. */
    val labelHeightPx: Int = 0,
    val labels: Boolean = false,
    val rows: Int = 1,
    val bottomFactor: Float = 1f,
    val enabled: Boolean = true,
    val qsb: Boolean = true,
    /** Taskbar present or two panels: the QSB sits above the dock icons. */
    val qsbOnTop: Boolean = false,
    /** Minimum touch target when labels are hidden. */
    val minTouchPx: Int = 0,
    /** Fugueti: room a search bar at the top of the screen takes above the workspace. */
    val screenTopQsbSpacePx: Int = 0,
    /**
     * Fugueti: search bar above the dock icons on a phone: its slot plus the gap above the icons
     * (0 otherwise). The dock is then that, the dock rows and the bottom space, as on Android 16.
     */
    val qsbAboveIconsSpacePx: Int = 0,
) {
    /** Dock cell height: room for the label when shown, else the icon/folder footprint. */
    fun cellHeightPx(iconSizePx: Int): Int =
        if (active) {
            app.lawnchair.util.DockCellSize.calculate(iconSizePx, labelHeightPx, labels, minTouchPx)
        } else {
            kotlin.math.ceil(
                    iconSizePx * com.android.launcher3.folder.ClippedFolderIconLayoutRule
                        .ICON_OVERLAP_FACTOR
                )
                .toInt()
        }

    /** Extra QSB space so the labels don't touch the search bar. */
    fun qsbSpace(aospQsbSpace: Int): Int =
        aospQsbSpace + if (active && qsb && labels) labelHeightPx / 2 else 0

    fun barBottomSpacePx(aospBarBottomSpacePx: Int): Int =
        if (active) (aospBarBottomSpacePx * bottomFactor).toInt() else aospBarBottomSpacePx

    /** Dock bar height (hotseatBarSizePx). */
    fun barSizePx(
        iconSizePx: Int,
        barEdgePaddingPx: Int,
        isVerticalBarLayout: Boolean,
        barWorkspaceSpacePx: Int,
        qsbVisualHeight: Int,
        barBottomSpacePx: Int,
        qsbSpace: Int,
        isQsbInline: Boolean,
    ): Int {
        if (!active) {
            return when {
                isVerticalBarLayout -> iconSizePx + barEdgePaddingPx + barWorkspaceSpacePx
                isQsbInline -> kotlin.math.max(iconSizePx, qsbVisualHeight) + barBottomSpacePx
                else -> iconSizePx + qsbSpace + qsbVisualHeight + barBottomSpacePx
            }
        }
        if (!enabled) return 0
        val cellHeight = cellHeightPx(iconSizePx)
        val space = kotlin.math.abs(cellHeight / 2) - 16
        val extraRowsHeight = (rows - 1) * cellHeight
        return when {
            isVerticalBarLayout -> iconSizePx + barEdgePaddingPx + barWorkspaceSpacePx + space
            isQsbInline ->
                kotlin.math.max(iconSizePx, qsbVisualHeight) + barBottomSpacePx + extraRowsHeight
            qsbOnTop && qsbAboveIconsSpacePx > 0 ->
                qsbAboveIconsSpacePx + cellHeight + extraRowsHeight +
                    barBottomSpacePx - kotlin.math.abs(cellHeight - iconSizePx) / 2
            qsbOnTop ->
                iconSizePx + qsbSpace + qsbVisualHeight + barBottomSpacePx + extraRowsHeight
            else ->
                iconSizePx + qsbSpace + qsbVisualHeight + barBottomSpacePx + space + extraRowsHeight
        }
    }

    companion object {
        @JvmField val AOSP = LawnchairDock()

        @JvmStatic
        fun create(
            factors: LawnchairLayoutFactors,
            labelHeightPx: Int,
            qsbOnTop: Boolean,
            minTouchPx: Int,
            screenTopQsbSpacePx: Int,
            qsbAboveIconsSpacePx: Int,
        ) =
            LawnchairDock(
                active = true,
                labelHeightPx = labelHeightPx,
                labels = factors.dockLabels,
                rows = factors.hotseatRows,
                bottomFactor = factors.hotseatBottom,
                enabled = factors.hotseatEnabled,
                // Fugueti: a search bar at the top of the screen leaves the dock.
                qsb = factors.hotseatQsb && screenTopQsbSpacePx == 0,
                qsbOnTop = qsbOnTop,
                minTouchPx = minTouchPx,
                screenTopQsbSpacePx = screenTopQsbSpacePx,
                qsbAboveIconsSpacePx = qsbAboveIconsSpacePx,
            )
    }
}
