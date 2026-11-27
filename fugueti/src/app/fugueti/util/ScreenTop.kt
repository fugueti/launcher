/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.util

import android.graphics.RectF
import android.view.View
import android.view.WindowInsets
import com.android.launcher3.R
import com.android.launcher3.Utilities
import kotlin.math.ceil
import kotlin.math.min

/** Where a search bar at the top of the screen goes, like Nova. */
object ScreenTop {

    /**
     * Screen y of the top of a search bar at the top of the screen: just below the status bar,
     * or just below the camera when the status bar is hidden.
     *
     * @param fallbackStatusBarTop the status bar height to use before the window insets are known
     */
    @JvmStatic
    fun searchBarTop(view: View, statusBarShown: Boolean, fallbackStatusBarTop: Int): Int {
        val gap = view.resources.getDimensionPixelSize(R.dimen.fugueti_drawer_search_bar_gap)
        val windowInsets = view.rootWindowInsets
        var statusBarTop = fallbackStatusBarTop
        if (windowInsets != null && Utilities.ATLEAST_R) {
            statusBarTop = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        }
        if (statusBarShown) return statusBarTop + gap
        // Status bar hidden: below the camera cutout itself, not its whole safe area.
        var cutoutBottom = statusBarTop
        if (windowInsets != null) {
            val cutout = if (Utilities.ATLEAST_P) windowInsets.displayCutout else null
            cutoutBottom = cutout?.safeInsetTop ?: 0
            val path = if (cutout != null && Utilities.ATLEAST_S) cutout.cutoutPath else null
            if (path != null) {
                val bounds = RectF()
                path.computeBounds(bounds, true)
                if (!bounds.isEmpty) cutoutBottom = ceil(bounds.bottom).toInt()
            }
        }
        return min(statusBarTop, cutoutBottom) + gap / 2
    }
}
