/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures

import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.WindowInsetsCompat
import app.fugueti.gestures.engine.GestureKind
import app.fugueti.gestures.engine.GestureRecognizer
import app.fugueti.gestures.engine.GestureRecognizer.Direction
import app.fugueti.gestures.engine.MotionEventAdapter
import app.fugueti.gestures.engine.RecognizerConfig
import app.fugueti.gestures.engine.Sensitivity
import app.fugueti.gestures.engine.TouchAction
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.GestureController
import app.lawnchair.gestures.config.GestureHandlerConfig
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.util.TouchController

/**
 * First touch controller of the drag layer. It watches every home screen touch without taking
 * it, feeds the [GestureRecognizer] and only intercepts the touch when a configured gesture
 * needs it (two fingers, double tap + swipe, or a one finger swipe with a custom action).
 *
 * Replaces upstream's VerticalSwipeTouchController.
 */
class GestureTouchController(
    private val launcher: LawnchairLauncher,
    private val gestures: GestureController,
) : TouchController {

    private var currentSensitivity: Sensitivity? = null
    private val recognizer = GestureRecognizer(buildConfig(Sensitivity.MEDIUM))

    /** The current touch started somewhere we must leave alone (system gesture area, folder open...). */
    private var ignoreTouch = false

    /** We took over the current touch. */
    private var intercepting = false

    /** Horizontal swipes are only ours when the workspace cannot scroll that way. */
    private var canSwipeLeft = false
    private var canSwipeRight = false

    /** Fugueti: the touch started on a paged dock, whose pager owns horizontal swipes. */
    private var downOnPagedDock = false

    /** Event time of the ACTION_DOWN that started the current touch. */
    private var touchDownTime = Long.MIN_VALUE

    /** Event time of the ACTION_DOWN of the previous touch (first tap of a double tap). */
    private var previousDownTime = Long.MIN_VALUE

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            startTouch(ev)
        }
        if (ignoreTouch) return false
        process(ev)
        return intercepting
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) startTouch(ev)
        if (!ignoreTouch) process(ev)
        return true
    }

    private fun startTouch(ev: MotionEvent) {
        intercepting = false
        previousDownTime = touchDownTime
        touchDownTime = ev.eventTime
        refreshConfig()
        ignoreTouch = !canHandle(ev)
        if (ignoreTouch) {
            recognizer.reset()
            return
        }
        computeHorizontalEdges()
        downOnPagedDock = isOnPagedDock(ev)
    }

    private fun isOnPagedDock(ev: MotionEvent): Boolean {
        val hotseat = launcher.hotseat ?: return false
        return hotseat.pagedView.isPagingEnabled && launcher.dragLayer.isEventOverView(hotseat, ev)
    }

    private fun process(ev: MotionEvent) {
        val sample = MotionEventAdapter.toSample(ev) ?: return
        val result = recognizer.onTouch(sample)
        if (!intercepting) intercepting = shouldIntercept()
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "${sample.action} n=${sample.pointerCount} phase=${recognizer.phase} dir=${recognizer.oneFingerDirection()} intercept=$intercepting result=$result")
        }
        if (result != null) dispatch(result)
        if (sample.action == TouchAction.UP || sample.action == TouchAction.CANCEL) intercepting = false
    }

    private fun shouldIntercept(): Boolean {
        val r = recognizer
        if (r.phase == GestureRecognizer.Phase.TWO_FINGERS) {
            return GestureKind.entries.any { it.isMultiTouch && gestures.isConfigured(it) }
        }
        if (r.isSecondTapDragging()) {
            return bothTapsOnEmptySpace() &&
                (gestures.isConfigured(GestureKind.DOUBLE_TAP_SWIPE_UP) || gestures.isConfigured(GestureKind.DOUBLE_TAP_SWIPE_DOWN))
        }
        return when (r.oneFingerDirection()) {
            Direction.UP -> isCustom(GestureKind.SWIPE_UP, GestureHandlerConfig.OpenAppDrawer)
            Direction.DOWN -> isCustom(GestureKind.SWIPE_DOWN, GestureHandlerConfig.OpenNotifications)
            Direction.LEFT -> !downOnPagedDock && canSwipeLeft && gestures.isConfigured(GestureKind.SWIPE_LEFT)
            Direction.RIGHT -> !downOnPagedDock && canSwipeRight && gestures.isConfigured(GestureKind.SWIPE_RIGHT)
            null -> false
        }
    }

    /**
     * Vertical swipes keep upstream behaviour: when the action is the launcher's native one (app
     * drawer for up, notifications for down) the native animated controller handles the touch.
     */
    private fun isCustom(kind: GestureKind, native: GestureHandlerConfig): Boolean {
        val config = gestures.configOf(kind)
        return config !is GestureHandlerConfig.NoOp && config != native
    }

    private fun dispatch(kind: GestureKind) {
        val allowed = when (kind) {
            // One finger swipes only fire when we owned the touch, otherwise the workspace (page
            // scroll) or a native controller already reacted to it.
            GestureKind.SWIPE_UP, GestureKind.SWIPE_DOWN,
            GestureKind.SWIPE_LEFT, GestureKind.SWIPE_RIGHT,
            -> intercepting

            GestureKind.DOUBLE_TAP -> bothTapsOnEmptySpace()

            GestureKind.DOUBLE_TAP_SWIPE_UP, GestureKind.DOUBLE_TAP_SWIPE_DOWN -> intercepting

            else -> intercepting
        }
        if (allowed && gestures.isConfigured(kind)) gestures.trigger(kind)
    }

    /**
     * The workspace listener records an ACTION_DOWN only when it lands on empty space. It runs
     * after us, but by the time a double tap is dispatched both downs have been recorded.
     */
    private fun bothTapsOnEmptySpace(): Boolean = gestures.wasEmptySpaceDown(touchDownTime) && gestures.wasEmptySpaceDown(previousDownTime)

    private fun canHandle(ev: MotionEvent): Boolean {
        if ((ev.edgeFlags and Utilities.EDGE_NAV_BAR) != 0) return false
        if (!launcher.isInState(LauncherState.NORMAL)) return false
        if (AbstractFloatingView.getTopOpenView(launcher) != null) return false
        return !isInSystemGestureArea(ev)
    }

    /** Android 10+ reserves the screen edges for back and home; never compete with them. */
    private fun isInSystemGestureArea(ev: MotionEvent): Boolean {
        val dragLayer = launcher.dragLayer ?: return false
        val rootInsets = dragLayer.rootWindowInsets ?: return false
        val insets = WindowInsetsCompat.toWindowInsetsCompat(rootInsets, dragLayer)
            .getInsets(WindowInsetsCompat.Type.systemGestures())
        val x = ev.x
        val y = ev.y
        return x < insets.left || x > dragLayer.width - insets.right ||
            y < insets.top || y > dragLayer.height - insets.bottom
    }

    private fun computeHorizontalEdges() {
        val workspace = launcher.workspace
        if (workspace == null) {
            canSwipeLeft = false
            canSwipeRight = false
            return
        }
        val page = workspace.nextPage
        val atFirst = page <= 0
        val atLast = page + workspace.panelCount >= workspace.pageCount
        // Finger moving right reveals the previous page in LTR and the next one in RTL.
        val rtl = Utilities.isRtl(launcher.resources)
        val previousBlocked = atFirst && !workspace.hasOverlay()
        canSwipeRight = if (rtl) atLast else previousBlocked
        canSwipeLeft = if (rtl) previousBlocked else atLast
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "edges page=$page panels=${workspace.panelCount} pages=${workspace.pageCount} overlay=${workspace.hasOverlay()} left=$canSwipeLeft right=$canSwipeRight")
        }
    }

    private fun refreshConfig() {
        val wanted = gestures.sensitivity
        if (wanted != currentSensitivity) {
            currentSensitivity = wanted
            recognizer.config = buildConfig(wanted)
        }
    }

    private fun buildConfig(sensitivity: Sensitivity): RecognizerConfig {
        val vc = ViewConfiguration.get(launcher)
        return RecognizerConfig.forDensity(
            density = launcher.resources.displayMetrics.density,
            sensitivity = sensitivity,
            touchSlopPx = vc.scaledTouchSlop.toFloat(),
            doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
            doubleTapSlopPx = vc.scaledDoubleTapSlop.toFloat(),
            maxTapDurationMs = ViewConfiguration.getLongPressTimeout().toLong(),
        )
    }

    private companion object {
        const val TAG = "FuguetiGestures"
    }
}
