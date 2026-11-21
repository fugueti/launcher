/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.fugueti.gestures.engine

/**
 * Every home screen gesture Fugueti can recognize.
 *
 * [fingers] is the number of fingers the gesture uses and [group] is used by the
 * settings screen to group the rows.
 */
enum class GestureKind(val fingers: Int, val group: Group) {
    SWIPE_UP(1, Group.ONE_FINGER),
    SWIPE_DOWN(1, Group.ONE_FINGER),
    SWIPE_LEFT(1, Group.ONE_FINGER),
    SWIPE_RIGHT(1, Group.ONE_FINGER),
    DOUBLE_TAP(1, Group.DOUBLE_TAP),
    DOUBLE_TAP_SWIPE_UP(1, Group.DOUBLE_TAP),
    DOUBLE_TAP_SWIPE_DOWN(1, Group.DOUBLE_TAP),
    TWO_FINGER_SWIPE_UP(2, Group.TWO_FINGERS),
    TWO_FINGER_SWIPE_DOWN(2, Group.TWO_FINGERS),
    PINCH_IN(2, Group.TWO_FINGERS),
    PINCH_OUT(2, Group.TWO_FINGERS),
    ROTATE_CW(2, Group.TWO_FINGERS),
    ROTATE_CCW(2, Group.TWO_FINGERS),
    ;

    enum class Group { ONE_FINGER, DOUBLE_TAP, TWO_FINGERS }

    val isMultiTouch: Boolean get() = fingers > 1
}
