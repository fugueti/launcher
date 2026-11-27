/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.home

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import app.fugueti.util.ScreenTop
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BaseActivity
import com.android.launcher3.Insettable
import com.android.launcher3.views.ActivityContext

/**
 * The home screen search bar at the top of the screen, on every page, like Nova's "persistent"
 * search bar (Home screen > Search bar > Position > Top of the screen). The dock is then laid out
 * without its search bar and the workspace starts below this one (see DeviceProfile).
 *
 * It shows the same bar as the dock would (Fugueti's or Google's) and fades with the home screen
 * (WorkspaceStateTransitionAnimation).
 */
class ScreenTopSearchBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), Insettable {

    private val activity: ActivityContext = ActivityContext.lookupContext<BaseActivity>(context)
    private val prefs2 = PreferenceManager2.getInstance(context)

    init {
        clipChildren = false
        clipToPadding = false
        val dp = activity.deviceProfile
        isVisible = dp.isQsbAtScreenTop
        if (dp.isQsbAtScreenTop) {
            val layoutId = prefs2.hotseatMode.firstCached().layoutResourceId
            val bar = LayoutInflater.from(context).inflate(layoutId, this, false)
            // Centred: Google's widget is narrower than its slot (Search bar > Size).
            (bar.layoutParams as? LayoutParams)?.gravity = Gravity.CENTER
            addView(bar)
        }
    }

    override fun setInsets(insets: Rect) {
        val dp = activity.deviceProfile
        isVisible = dp.isQsbAtScreenTop && childCount > 0
        if (!isVisible) return
        val statusBarShown = prefs2.showStatusBar.firstCached()
        (layoutParams as LayoutParams).apply {
            topMargin = ScreenTop.searchBarTop(this@ScreenTopSearchBar, statusBarShown, insets.top)
            height = dp.mScreenTopQsbHeight
            // Line the bar up with the dock, as the dock's own search bar is.
            val padding = dp.getHotseatLayoutPadding(context)
            leftMargin = padding.left
            rightMargin = padding.right
        }
        requestLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The window insets (status bar, camera) are known now.
        val insets = activity.deviceProfile.insets
        setInsets(Rect(insets))
    }
}
