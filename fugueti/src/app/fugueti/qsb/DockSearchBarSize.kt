/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.qsb

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Size of Google's search bar widget on phones (Search bar > Size, shown for the "Google Search
 * bar" mode), wherever it sits: in the dock below or above the icons, or at the top of the
 * screen. At 100% it is Lawnchair's bar: as wide as the dock icons reach and `qsb_widget_height`
 * minus its vertical padding tall. Bigger sizes grow taller and up to the full slot width (120% is
 * the widget filling its whole slot, as it did before).
 */
object DockSearchBarSize {
    const val MIN = 0.8f
    const val MAX = 1.2f

    data class Size(val width: Int, val height: Int)

    fun factor(context: Context): Float =
        PreferenceManager.getInstance(context).hotseatQsbSizeFactor.get().coerceIn(MIN, MAX)

    /**
     * @param requestedWidth the width the dock gives the bar
     * @param slotHeight the height of the bar's slot (`qsb_widget_height`)
     */
    fun measure(context: Context, dp: DeviceProfile, requestedWidth: Int, slotHeight: Int): Size {
        val factor = factor(context)
        val res = context.resources
        val baseHeight = res.getDimensionPixelSize(R.dimen.qsb_widget_height) -
            2 * res.getDimensionPixelSize(R.dimen.qsb_widget_vertical_padding)
        val height = min(slotHeight, (baseHeight * factor).roundToInt())
        val cellWidth = DeviceProfile.calculateCellWidth(
            requestedWidth,
            dp.cellLayoutBorderSpacePx.x,
            dp.numShownHotseatIcons,
        )
        val iconSize = (dp.iconSizePx * 0.92f).toInt()
        val lawnchairWidth = requestedWidth - (cellWidth - iconSize)
        val width = if (factor <= 1f) {
            lawnchairWidth
        } else {
            lawnchairWidth + ((requestedWidth - lawnchairWidth) * (factor - 1f) / (MAX - 1f)).roundToInt()
        }
        return Size(width.coerceIn(0, requestedWidth), height)
    }
}
