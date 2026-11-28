/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.drawer

import android.view.MotionEvent
import android.view.ViewConfiguration
import app.lawnchair.LawnchairLauncher
import app.lawnchair.allapps.AllAppsSearchInput
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.util.TouchController
import kotlin.math.abs

/**
 * Nova's "pull to search": with the drawer search bar hidden, a swipe down that starts in the
 * lower third of the app drawer, while the apps are scrolled to the top, opens the search bar
 * and the keyboard instead of closing the drawer. A swipe down higher up still closes it.
 *
 * Sits before the state touch controller, which would otherwise take the swipe to close the
 * drawer.
 */
class PullToSearchTouchController(private val launcher: LawnchairLauncher) : TouchController {

    private val prefs2 = PreferenceManager2.getInstance(launcher)
    private val touchSlop = ViewConfiguration.get(launcher).scaledTouchSlop

    private var armed = false
    private var intercepting = false
    private var downX = 0f
    private var downY = 0f

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                intercepting = false
                armed = canStart(ev)
                downX = ev.x
                downY = ev.y
            }

            MotionEvent.ACTION_MOVE -> if (armed && !intercepting) {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > touchSlop || dy < -touchSlop) {
                    armed = false
                } else if (dy > touchSlop && dy > abs(dx)) {
                    intercepting = true
                    armed = false
                    openSearch()
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> armed = false

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> armed = false
        }
        return intercepting
    }

    /** Takes the rest of the swipe so nothing else reacts to it. */
    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            intercepting = false
        }
        return true
    }

    private fun canStart(ev: MotionEvent): Boolean {
        if (!launcher.isInState(LauncherState.ALL_APPS)) return false
        if ((ev.edgeFlags and Utilities.EDGE_NAV_BAR) != 0) return false
        if (AbstractFloatingView.getTopOpenView(launcher) != null) return false
        if (!prefs2.drawerPullToSearch.firstCached()) return false
        if (!prefs2.hideAppDrawerSearchBar.firstCached()) return false
        val appsView = launcher.appsView ?: return false
        if (appsView.isSearching || appsView.isHiddenSearchBarRevealed) return false
        if (appsView.searchUiManager !is AllAppsSearchInput) return false
        val dragLayer = launcher.dragLayer ?: return false
        if (ev.y < dragLayer.height * 2f / 3f) return false
        // Only when the apps cannot scroll up any more (a swipe down would move the drawer).
        return appsView.shouldContainerScroll(ev)
    }

    private fun openSearch() {
        (launcher.appsView.searchUiManager as? AllAppsSearchInput)?.pullToSearch()
    }
}
