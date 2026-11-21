/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures

import androidx.annotation.StringRes
import app.fugueti.gestures.engine.GestureKind
import com.android.launcher3.R

object GestureLabels {
    @StringRes
    fun labelRes(kind: GestureKind): Int = when (kind) {
        GestureKind.SWIPE_UP -> R.string.gesture_swipe_up
        GestureKind.SWIPE_DOWN -> R.string.gesture_swipe_down
        GestureKind.SWIPE_LEFT -> R.string.gesture_swipe_left
        GestureKind.SWIPE_RIGHT -> R.string.gesture_swipe_right
        GestureKind.DOUBLE_TAP -> R.string.gesture_double_tap
        GestureKind.DOUBLE_TAP_SWIPE_UP -> R.string.fg_gesture_double_tap_swipe_up
        GestureKind.DOUBLE_TAP_SWIPE_DOWN -> R.string.fg_gesture_double_tap_swipe_down
        GestureKind.TWO_FINGER_SWIPE_UP -> R.string.fg_gesture_two_finger_swipe_up
        GestureKind.TWO_FINGER_SWIPE_DOWN -> R.string.fg_gesture_two_finger_swipe_down
        GestureKind.PINCH_IN -> R.string.fg_gesture_pinch_in
        GestureKind.PINCH_OUT -> R.string.fg_gesture_pinch_out
        GestureKind.ROTATE_CW -> R.string.fg_gesture_rotate_cw
        GestureKind.ROTATE_CCW -> R.string.fg_gesture_rotate_ccw
    }

    @StringRes
    fun groupRes(group: GestureKind.Group): Int = when (group) {
        GestureKind.Group.ONE_FINGER -> R.string.fg_group_one_finger
        GestureKind.Group.DOUBLE_TAP -> R.string.fg_group_double_tap
        GestureKind.Group.TWO_FINGERS -> R.string.fg_group_two_fingers
    }

    /** Extra hint shown under some gestures in the settings screen, 0 when none. */
    @StringRes
    fun hintRes(kind: GestureKind): Int = when (kind) {
        GestureKind.SWIPE_LEFT -> R.string.fg_hint_swipe_left
        GestureKind.SWIPE_RIGHT -> R.string.fg_hint_swipe_right
        else -> 0
    }
}
