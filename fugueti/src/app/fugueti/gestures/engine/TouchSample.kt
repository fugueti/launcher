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

/** Platform independent touch action, mirrors the masked MotionEvent actions we care about. */
enum class TouchAction { DOWN, POINTER_DOWN, MOVE, POINTER_UP, UP, CANCEL }

/** One finger on the screen, in pixels. */
data class TouchPointer(val id: Int, val x: Float, val y: Float)

/**
 * A snapshot of the screen at one instant: what happened ([action]), when ([timeMs]) and where
 * every finger currently on the screen is ([pointers]). For POINTER_UP and UP the lifted finger
 * is still listed, exactly like Android does.
 */
data class TouchSample(
    val action: TouchAction,
    val timeMs: Long,
    val pointers: List<TouchPointer>,
) {
    val pointerCount: Int get() = pointers.size

    fun pointer(id: Int): TouchPointer? = pointers.firstOrNull { it.id == id }

    companion object {
        /** Convenience for one finger samples, mostly used by tests. */
        fun single(action: TouchAction, timeMs: Long, x: Float, y: Float) = TouchSample(action, timeMs, listOf(TouchPointer(0, x, y)))
    }
}
