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

/** How much movement is needed before a gesture is accepted. */
enum class Sensitivity(val factor: Float) {
    LOW(1.4f),
    MEDIUM(1.0f),
    HIGH(0.7f),
    ;

    companion object {
        fun fromName(name: String?): Sensitivity = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/**
 * Thresholds used by [GestureRecognizer]. Distances are in pixels, speeds in pixels per second.
 *
 * The defaults come from the public Android `ViewConfiguration` values (touch slop of 8dp,
 * double tap timeout of 300ms, double tap slop of 100dp) plus our own tuning on an emulator.
 */
data class RecognizerConfig(
    /** Movement under this distance still counts as a tap. */
    val touchSlop: Float,
    /** Minimum travel of a swipe (one or two fingers). */
    val minSwipeDistance: Float,
    /** Minimum speed of a one finger swipe at the moment the finger is lifted. */
    val minSwipeVelocity: Float,
    /** Maximum time between the first finger up and the second finger down of a double tap. */
    val doubleTapTimeoutMs: Long = 300,
    /** Maximum distance between the two taps of a double tap. */
    val doubleTapSlop: Float,
    /** A press longer than this is not a tap (it is a long press). */
    val maxTapDurationMs: Long = 400,
    /** Relative change of the distance between two fingers that counts as a pinch (0.25 = 25%). */
    val pinchRatio: Float = 0.25f,
    /** Rotation, in degrees, of the line between two fingers that counts as a twist. */
    val rotationDegrees: Float = 30f,
    /** The winning two finger candidate must beat the runner up by this factor to fire early. */
    val dominance: Float = 1.2f,
    /** Window used to compute the release velocity of a one finger swipe. */
    val velocityWindowMs: Long = 100,
) {
    companion object {
        /** Builds a config from the screen density (px per dp) and the user sensitivity. */
        fun forDensity(
            density: Float,
            sensitivity: Sensitivity = Sensitivity.MEDIUM,
            touchSlopPx: Float = 8f * density,
            doubleTapTimeoutMs: Long = 300,
            doubleTapSlopPx: Float = 100f * density,
            maxTapDurationMs: Long = 400,
        ): RecognizerConfig {
            val f = sensitivity.factor
            return RecognizerConfig(
                touchSlop = touchSlopPx,
                minSwipeDistance = 56f * density * f,
                minSwipeVelocity = 100f * density * f,
                doubleTapTimeoutMs = doubleTapTimeoutMs,
                doubleTapSlop = doubleTapSlopPx,
                maxTapDurationMs = maxTapDurationMs,
                pinchRatio = 0.25f * f,
                rotationDegrees = 30f * f,
            )
        }
    }
}
