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

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Turns a stream of [TouchSample]s into at most one [GestureKind] per touch.
 *
 * Pure Kotlin on purpose: no Android type is used so the whole state machine can be unit tested
 * on the JVM. See docs/gestures/ARCHITECTURE.md for the state diagram.
 */
class GestureRecognizer(var config: RecognizerConfig) {

    enum class Phase { IDLE, ONE_FINGER, WAIT_SECOND_TAP, SECOND_TAP, TWO_FINGERS, DONE }

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    var phase: Phase = Phase.IDLE
        private set

    // Current finger (one finger phases).
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var movedBeyondSlop = false
    private val history = ArrayDeque<Triple<Long, Float, Float>>()

    // First tap of a potential double tap.
    private var firstTapX = 0f
    private var firstTapY = 0f
    private var firstTapUpTime = 0L

    // Two finger tracking.
    private var idA = -1
    private var idB = -1
    private var startSpan = 0f
    private var startCx = 0f
    private var startCy = 0f
    private var lastAngle = 0f
    private var accumulatedAngle = 0f
    private var lastScores = TwoFingerScores.ZERO

    private var lastSample: TouchSample? = null

    /** Scores of the three two finger candidates, each normalized so that 1.0 is the threshold. */
    data class TwoFingerScores(
        val swipe: Float,
        val pinch: Float,
        val rotate: Float,
        val translationY: Float,
        val spanRatio: Float,
        val rotation: Float,
    ) {
        companion object {
            val ZERO = TwoFingerScores(0f, 0f, 0f, 0f, 1f, 0f)
        }
    }

    /** Clears every state, including a pending first tap. */
    fun reset() {
        phase = Phase.IDLE
        history.clear()
        movedBeyondSlop = false
        idA = -1
        idB = -1
        accumulatedAngle = 0f
        lastScores = TwoFingerScores.ZERO
        lastSample = null
    }

    /**
     * Feeds one sample. Returns the recognized gesture when this sample completes one, otherwise
     * null. Feeding the very same sample twice is harmless (the duplicate is ignored), which lets
     * the touch controller forward events from both the intercept and the touch callbacks.
     */
    fun onTouch(sample: TouchSample): GestureKind? {
        if (sample == lastSample) return null
        lastSample = sample
        return when (sample.action) {
            TouchAction.DOWN -> onDown(sample)

            TouchAction.POINTER_DOWN -> onPointerDown(sample)

            TouchAction.MOVE -> onMove(sample)

            TouchAction.POINTER_UP -> onPointerUp(sample)

            TouchAction.UP -> onUp(sample)

            TouchAction.CANCEL -> {
                reset()
                null
            }
        }
    }

    /**
     * Direction of the current one finger drag once it left the touch slop, null otherwise.
     * Used by the touch controller to decide when to steal the touch from the workspace.
     */
    fun oneFingerDirection(): Direction? {
        if (phase != Phase.ONE_FINGER || !movedBeyondSlop) return null
        return directionOf(lastX - downX, lastY - downY)
    }

    /** True while the second tap of a double tap is being dragged vertically past the slop. */
    fun isSecondTapDragging(): Boolean {
        if (phase != Phase.SECOND_TAP) return false
        val dx = lastX - downX
        val dy = lastY - downY
        return abs(dy) > config.touchSlop && abs(dy) > abs(dx)
    }

    /** True when a second tap arriving at [timeMs] would still complete a double tap. */
    fun isWaitingForSecondTap(timeMs: Long): Boolean = phase == Phase.WAIT_SECOND_TAP && timeMs - firstTapUpTime <= config.doubleTapTimeoutMs

    /** Last computed two finger scores, exposed for the gesture test screen and for tests. */
    fun twoFingerScores(): TwoFingerScores = lastScores

    // region one finger

    private fun onDown(s: TouchSample): GestureKind? {
        val p = s.pointers.first()
        val isSecondTap = phase == Phase.WAIT_SECOND_TAP &&
            s.timeMs - firstTapUpTime <= config.doubleTapTimeoutMs &&
            hypot(p.x - firstTapX, p.y - firstTapY) <= config.doubleTapSlop
        startFinger(p, s.timeMs)
        phase = if (isSecondTap) Phase.SECOND_TAP else Phase.ONE_FINGER
        if (s.pointerCount >= 2) startTwoFingers(s)
        return null
    }

    private fun startFinger(p: TouchPointer, time: Long) {
        downX = p.x
        downY = p.y
        lastX = p.x
        lastY = p.y
        downTime = time
        movedBeyondSlop = false
        history.clear()
        history.addLast(Triple(time, p.x, p.y))
        idA = -1
        idB = -1
        lastScores = TwoFingerScores.ZERO
    }

    private fun trackFinger(s: TouchSample) {
        val p = s.pointers.first()
        lastX = p.x
        lastY = p.y
        history.addLast(Triple(s.timeMs, p.x, p.y))
        while (history.size > 2 && s.timeMs - history.first().first > config.velocityWindowMs * 2) {
            history.removeFirst()
        }
        if (hypot(lastX - downX, lastY - downY) > config.touchSlop) movedBeyondSlop = true
    }

    private fun onMove(s: TouchSample): GestureKind? = when (phase) {
        Phase.ONE_FINGER -> {
            trackFinger(s)
            null
        }

        Phase.SECOND_TAP -> {
            trackFinger(s)
            val dx = lastX - downX
            val dy = lastY - downY
            when {
                abs(dy) >= config.minSwipeDistance && abs(dy) > abs(dx) -> {
                    phase = Phase.DONE
                    if (dy < 0) GestureKind.DOUBLE_TAP_SWIPE_UP else GestureKind.DOUBLE_TAP_SWIPE_DOWN
                }

                abs(dx) >= config.minSwipeDistance -> {
                    // Sideways drag after a tap: not one of our gestures.
                    phase = Phase.DONE
                    null
                }

                else -> null
            }
        }

        Phase.TWO_FINGERS -> evaluateTwoFingers(s, final = false)

        else -> null
    }

    private fun onUp(s: TouchSample): GestureKind? {
        val result = when (phase) {
            Phase.ONE_FINGER -> {
                trackFinger(s)
                finishOneFinger(s)
            }

            Phase.SECOND_TAP -> {
                trackFinger(s)
                val dx = lastX - downX
                val dy = lastY - downY
                when {
                    !movedBeyondSlop && s.timeMs - downTime <= config.maxTapDurationMs -> GestureKind.DOUBLE_TAP

                    abs(dy) >= config.minSwipeDistance && abs(dy) > abs(dx) ->
                        if (dy < 0) GestureKind.DOUBLE_TAP_SWIPE_UP else GestureKind.DOUBLE_TAP_SWIPE_DOWN

                    else -> null
                }
            }

            Phase.TWO_FINGERS -> evaluateTwoFingers(s, final = true)

            else -> null
        }
        // A tap moves us to WAIT_SECOND_TAP inside finishOneFinger, keep that.
        if (phase != Phase.WAIT_SECOND_TAP) {
            phase = Phase.IDLE
            history.clear()
        }
        return result
    }

    private fun finishOneFinger(s: TouchSample): GestureKind? {
        val dx = lastX - downX
        val dy = lastY - downY
        if (!movedBeyondSlop) {
            if (s.timeMs - downTime <= config.maxTapDurationMs) {
                phase = Phase.WAIT_SECOND_TAP
                firstTapX = downX
                firstTapY = downY
                firstTapUpTime = s.timeMs
            }
            return null
        }
        val direction = directionOf(dx, dy)
        val distance = if (direction == Direction.UP || direction == Direction.DOWN) abs(dy) else abs(dx)
        if (distance < config.minSwipeDistance) return null
        val (vx, vy) = releaseVelocity()
        val speed = when (direction) {
            Direction.UP -> -vy
            Direction.DOWN -> vy
            Direction.LEFT -> -vx
            Direction.RIGHT -> vx
        }
        // A deliberate stroke that pauses a moment before lifting (or whose last events arrive
        // late) has ~0 release speed; its average speed over the whole stroke still shows intent.
        // A truly slow drag fails both checks.
        val averageSpeed = distance / (s.timeMs - downTime).coerceAtLeast(1L) * 1000f
        if (speed < config.minSwipeVelocity && averageSpeed < config.minSwipeVelocity) return null
        return when (direction) {
            Direction.UP -> GestureKind.SWIPE_UP
            Direction.DOWN -> GestureKind.SWIPE_DOWN
            Direction.LEFT -> GestureKind.SWIPE_LEFT
            Direction.RIGHT -> GestureKind.SWIPE_RIGHT
        }
    }

    /** Velocity in px/s over the last [RecognizerConfig.velocityWindowMs] of the touch. */
    private fun releaseVelocity(): Pair<Float, Float> {
        val last = history.lastOrNull() ?: return 0f to 0f
        val first = history.firstOrNull { last.first - it.first <= config.velocityWindowMs }
            ?.takeIf { it !== last }
            ?: history.first()
        val dt = (last.first - first.first).toFloat()
        if (dt <= 0f) {
            // Every sample has the same timestamp: treat it as an instant flick.
            val total = (last.first - downTime).toFloat().coerceAtLeast(1f)
            return (last.second - downX) / total * 1000f to (last.third - downY) / total * 1000f
        }
        return (last.second - first.second) / dt * 1000f to (last.third - first.third) / dt * 1000f
    }

    private fun directionOf(dx: Float, dy: Float): Direction = if (abs(dy) >= abs(dx)) {
        if (dy < 0) Direction.UP else Direction.DOWN
    } else {
        if (dx < 0) Direction.LEFT else Direction.RIGHT
    }

    // endregion

    // region two fingers

    private fun onPointerDown(s: TouchSample): GestureKind? {
        if (s.pointerCount < 2) return null
        when (phase) {
            Phase.ONE_FINGER, Phase.SECOND_TAP, Phase.IDLE, Phase.WAIT_SECOND_TAP -> startTwoFingers(s)
            else -> Unit // a third finger keeps tracking the first two
        }
        return null
    }

    private fun startTwoFingers(s: TouchSample) {
        val a = s.pointers[0]
        val b = s.pointers[1]
        idA = a.id
        idB = b.id
        startSpan = hypot(b.x - a.x, b.y - a.y)
        startCx = (a.x + b.x) / 2f
        startCy = (a.y + b.y) / 2f
        lastAngle = angleOf(a, b)
        accumulatedAngle = 0f
        lastScores = TwoFingerScores.ZERO
        phase = Phase.TWO_FINGERS
    }

    private fun onPointerUp(s: TouchSample): GestureKind? {
        if (phase != Phase.TWO_FINGERS) return null
        val result = evaluateTwoFingers(s, final = true)
        // Whatever happens next (the other finger moving or lifting) is not a new gesture.
        phase = Phase.DONE
        return result
    }

    private fun evaluateTwoFingers(s: TouchSample, final: Boolean): GestureKind? {
        val a = s.pointer(idA)
        val b = s.pointer(idB)
        if (a == null || b == null) {
            if (final) phase = Phase.DONE
            return null
        }
        val span = hypot(b.x - a.x, b.y - a.y)
        if (span > config.touchSlop * 2 && startSpan > config.touchSlop * 2) {
            val angle = angleOf(a, b)
            accumulatedAngle += normalizeDegrees(angle - lastAngle)
            lastAngle = angle
        }
        val cx = (a.x + b.x) / 2f
        val cy = (a.y + b.y) / 2f
        val tx = cx - startCx
        val ty = cy - startCy
        val spanRatio = if (startSpan > 0f) span / startSpan else 1f

        val scores = TwoFingerScores(
            swipe = if (abs(ty) > abs(tx)) abs(ty) / config.minSwipeDistance else 0f,
            pinch = if (startSpan > config.touchSlop) abs(spanRatio - 1f) / config.pinchRatio else 0f,
            rotate = abs(accumulatedAngle) / config.rotationDegrees,
            translationY = ty,
            spanRatio = spanRatio,
            rotation = accumulatedAngle,
        )
        lastScores = scores

        val ranked = listOf(
            Candidate.SWIPE to scores.swipe,
            Candidate.PINCH to scores.pinch,
            Candidate.ROTATE to scores.rotate,
        ).sortedByDescending { it.second }
        val (best, bestScore) = ranked[0]
        val runnerUp = ranked[1].second
        val accept = bestScore >= 1f && (final || bestScore >= runnerUp * config.dominance)
        if (!accept) return null

        phase = Phase.DONE
        return when (best) {
            Candidate.SWIPE -> if (ty < 0) GestureKind.TWO_FINGER_SWIPE_UP else GestureKind.TWO_FINGER_SWIPE_DOWN
            Candidate.PINCH -> if (spanRatio < 1f) GestureKind.PINCH_IN else GestureKind.PINCH_OUT
            Candidate.ROTATE -> if (accumulatedAngle > 0) GestureKind.ROTATE_CW else GestureKind.ROTATE_CCW
        }
    }

    private enum class Candidate { SWIPE, PINCH, ROTATE }

    /** Angle of the line from [a] to [b], in degrees. Screen y grows downwards, so a growing angle is clockwise. */
    private fun angleOf(a: TouchPointer, b: TouchPointer): Float = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()

    private fun normalizeDegrees(d: Float): Float {
        var r = d % 360f
        if (r > 180f) r -= 360f
        if (r <= -180f) r += 360f
        return r
    }

    // endregion
}
