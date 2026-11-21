/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures.engine

import android.view.MotionEvent

/** Thin bridge between Android [MotionEvent]s and the platform independent [TouchSample]. */
object MotionEventAdapter {

    /** Returns null for actions the recognizer does not care about (hover, outside, scroll...). */
    fun toSample(ev: MotionEvent): TouchSample? {
        val action = when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchAction.DOWN
            MotionEvent.ACTION_POINTER_DOWN -> TouchAction.POINTER_DOWN
            MotionEvent.ACTION_MOVE -> TouchAction.MOVE
            MotionEvent.ACTION_POINTER_UP -> TouchAction.POINTER_UP
            MotionEvent.ACTION_UP -> TouchAction.UP
            MotionEvent.ACTION_CANCEL -> TouchAction.CANCEL
            else -> return null
        }
        val pointers = List(ev.pointerCount) { i ->
            TouchPointer(ev.getPointerId(i), ev.getX(i), ev.getY(i))
        }
        return TouchSample(action, ev.eventTime, pointers)
    }
}
