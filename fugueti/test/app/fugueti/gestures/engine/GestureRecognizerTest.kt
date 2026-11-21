/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures.engine

import app.fugueti.gestures.engine.TouchAction.CANCEL
import app.fugueti.gestures.engine.TouchAction.DOWN
import app.fugueti.gestures.engine.TouchAction.MOVE
import app.fugueti.gestures.engine.TouchAction.POINTER_DOWN
import app.fugueti.gestures.engine.TouchAction.POINTER_UP
import app.fugueti.gestures.engine.TouchAction.UP
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pure JVM tests of the recognizer state machine (density 2.75, like a Pixel 8). */
class GestureRecognizerTest {

    private val density = 2.75f
    private lateinit var r: GestureRecognizer
    private var t = 0L

    @Before
    fun setUp() {
        r = GestureRecognizer(RecognizerConfig.forDensity(density))
        t = 1_000L
    }

    private fun dp(v: Float) = v * density

    /** Feeds a one finger drag from (x0,y0) to (x1,y1) in [steps] moves of 10ms. Returns every result. */
    private fun drag(x0: Float, y0: Float, x1: Float, y1: Float, steps: Int = 10, up: Boolean = true): List<GestureKind?> {
        val out = mutableListOf<GestureKind?>()
        out += r.onTouch(TouchSample.single(DOWN, t, x0, y0))
        for (i in 1..steps) {
            t += 10
            val f = i / steps.toFloat()
            out += r.onTouch(TouchSample.single(MOVE, t, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f))
        }
        if (up) {
            t += 5
            out += r.onTouch(TouchSample.single(UP, t, x1, y1))
        }
        return out
    }

    private fun tap(x: Float, y: Float, duration: Long = 60): GestureKind? {
        r.onTouch(TouchSample.single(DOWN, t, x, y))
        t += duration
        return r.onTouch(TouchSample.single(UP, t, x, y))
    }

    private fun two(action: TouchAction, ax: Float, ay: Float, bx: Float, by: Float) = r.onTouch(TouchSample(action, t, listOf(TouchPointer(0, ax, ay), TouchPointer(1, bx, by))))

    /** Runs a full two finger gesture where finger positions are given by [pos] for f in 0..1. */
    private fun twoFinger(steps: Int = 12, pos: (Float) -> FloatArray): List<GestureKind?> {
        val out = mutableListOf<GestureKind?>()
        val p0 = pos(0f)
        out += r.onTouch(TouchSample.single(DOWN, t, p0[0], p0[1]))
        t += 8
        out += two(POINTER_DOWN, p0[0], p0[1], p0[2], p0[3])
        for (i in 1..steps) {
            t += 12
            val p = pos(i / steps.toFloat())
            out += two(MOVE, p[0], p[1], p[2], p[3])
        }
        val pe = pos(1f)
        t += 5
        out += two(POINTER_UP, pe[0], pe[1], pe[2], pe[3])
        t += 5
        out += r.onTouch(TouchSample.single(UP, t, pe[0], pe[1]))
        return out
    }

    private fun List<GestureKind?>.only(): GestureKind? = filterNotNull().also { assertTrue("more than one gesture: $it", it.size <= 1) }.firstOrNull()

    // region one finger swipes

    @Test fun swipeUp() = assertEquals(GestureKind.SWIPE_UP, drag(500f, 1500f, 500f, 900f).only())

    @Test fun swipeDown() = assertEquals(GestureKind.SWIPE_DOWN, drag(500f, 600f, 500f, 1200f).only())

    @Test fun swipeLeft() = assertEquals(GestureKind.SWIPE_LEFT, drag(900f, 1000f, 300f, 1000f).only())

    @Test fun swipeRight() = assertEquals(GestureKind.SWIPE_RIGHT, drag(200f, 1000f, 800f, 1000f).only())

    @Test fun diagonalPicksDominantAxis() = assertEquals(GestureKind.SWIPE_UP, drag(500f, 1500f, 700f, 900f).only())

    @Test fun shortDragIsNotASwipe() = assertNull(drag(500f, 1000f, 500f, 1000f - dp(30f)).only())

    @Test
    fun slowDragIsNotASwipe() {
        // 600px over 3 seconds then a pause: the release velocity is ~0.
        val res = drag(500f, 1500f, 500f, 900f, steps = 300, up = false).toMutableList()
        t += 200
        res += r.onTouch(TouchSample.single(MOVE, t, 500f, 900f))
        t += 5
        res += r.onTouch(TouchSample.single(UP, t, 500f, 900f))
        assertNull(res.only())
    }

    @Test
    fun quickStrokeWithShortPauseBeforeLiftIsStillASwipe() {
        // 600px in 140ms, then the finger rests 300ms before lifting.
        val res = drag(500f, 1500f, 500f, 900f, up = false).toMutableList()
        t += 300
        res += r.onTouch(TouchSample.single(UP, t, 500f, 900f))
        assertEquals(GestureKind.SWIPE_UP, res.only())
    }

    @Test
    fun directionIsReportedOncePastSlop() {
        r.onTouch(TouchSample.single(DOWN, t, 500f, 1000f))
        t += 10
        r.onTouch(TouchSample.single(MOVE, t, 500f, 1000f - dp(4f)))
        assertNull(r.oneFingerDirection())
        t += 10
        r.onTouch(TouchSample.single(MOVE, t, 500f, 1000f - dp(20f)))
        assertEquals(GestureRecognizer.Direction.UP, r.oneFingerDirection())
    }

    // endregion

    // region double tap

    @Test
    fun doubleTap() {
        assertNull(tap(500f, 1000f))
        t += 120
        assertEquals(GestureKind.DOUBLE_TAP, tap(510f, 1005f))
    }

    @Test
    fun singleTapIsNothing() {
        assertNull(tap(500f, 1000f))
        assertTrue(r.isWaitingForSecondTap(t + 100))
        assertFalse(r.isWaitingForSecondTap(t + 400))
    }

    @Test
    fun tapsTooFarApartInTimeAreNotADoubleTap() {
        tap(500f, 1000f)
        t += 450
        assertNull(tap(500f, 1000f))
    }

    @Test
    fun tapsTooFarApartInSpaceAreNotADoubleTap() {
        tap(200f, 1000f)
        t += 100
        assertNull(tap(200f + dp(150f), 1000f))
    }

    @Test
    fun longPressIsNotATap() {
        tap(500f, 1000f, duration = 600)
        t += 100
        assertNull(tap(500f, 1000f))
    }

    @Test
    fun tripleTapFiresOnlyOnce() {
        tap(500f, 1000f)
        t += 100
        assertEquals(GestureKind.DOUBLE_TAP, tap(500f, 1000f))
        t += 100
        assertNull(tap(500f, 1000f))
    }

    @Test
    fun doubleTapSwipeUp() {
        tap(500f, 1200f)
        t += 100
        val res = drag(500f, 1200f, 500f, 700f)
        assertEquals(GestureKind.DOUBLE_TAP_SWIPE_UP, res.only())
    }

    @Test
    fun doubleTapSwipeDown() {
        tap(500f, 800f)
        t += 100
        val res = drag(500f, 800f, 500f, 1300f)
        assertEquals(GestureKind.DOUBLE_TAP_SWIPE_DOWN, res.only())
    }

    @Test
    fun doubleTapSwipeFiresWhileFingerIsStillDown() {
        tap(500f, 1200f)
        t += 100
        val res = drag(500f, 1200f, 500f, 700f, up = false)
        assertEquals(GestureKind.DOUBLE_TAP_SWIPE_UP, res.only())
        assertEquals(GestureRecognizer.Phase.DONE, r.phase)
    }

    @Test
    fun secondTapDraggingIsReportedForInterception() {
        tap(500f, 1200f)
        t += 100
        r.onTouch(TouchSample.single(DOWN, t, 500f, 1200f))
        t += 10
        r.onTouch(TouchSample.single(MOVE, t, 500f, 1200f - dp(20f)))
        assertTrue(r.isSecondTapDragging())
    }

    @Test
    fun doubleTapThenSidewaysIsNothing() {
        tap(500f, 1000f)
        t += 100
        assertNull(drag(500f, 1000f, 1000f, 1000f).only())
    }

    // endregion

    // region two fingers

    @Test
    fun twoFingerSwipeUp() {
        val res = twoFinger { f -> floatArrayOf(400f, 1400f - 500f * f, 700f, 1400f - 500f * f) }
        assertEquals(GestureKind.TWO_FINGER_SWIPE_UP, res.only())
    }

    @Test
    fun twoFingerSwipeDown() {
        val res = twoFinger { f -> floatArrayOf(400f, 700f + 500f * f, 700f, 700f + 500f * f) }
        assertEquals(GestureKind.TWO_FINGER_SWIPE_DOWN, res.only())
    }

    @Test
    fun pinchIn() {
        val res = twoFinger { f -> floatArrayOf(200f + 250f * f, 700f + 250f * f, 900f - 250f * f, 1400f - 250f * f) }
        assertEquals(GestureKind.PINCH_IN, res.only())
    }

    @Test
    fun pinchOut() {
        val res = twoFinger { f -> floatArrayOf(450f - 250f * f, 950f - 250f * f, 650f + 250f * f, 1150f + 250f * f) }
        assertEquals(GestureKind.PINCH_OUT, res.only())
    }

    @Test
    fun pinchWithOneFingerStill() {
        val res = twoFinger { f -> floatArrayOf(300f, 800f, 900f - 400f * f, 1400f - 400f * f) }
        assertEquals(GestureKind.PINCH_IN, res.only())
    }

    private fun rotation(degrees: Float): (Float) -> FloatArray = { f ->
        val cx = 540f
        val cy = 1200f
        val radius = 250f
        val a = Math.toRadians((degrees * f).toDouble())
        val ax = cx - radius * cos(a).toFloat()
        val ay = cy - radius * sin(a).toFloat()
        val bx = cx + radius * cos(a).toFloat()
        val by = cy + radius * sin(a).toFloat()
        floatArrayOf(ax, ay, bx, by)
    }

    @Test fun rotateClockwise() = assertEquals(GestureKind.ROTATE_CW, twoFinger(pos = rotation(60f)).only())

    @Test fun rotateCounterClockwise() = assertEquals(GestureKind.ROTATE_CCW, twoFinger(pos = rotation(-60f)).only())

    @Test
    fun rotationAcrossTheAngleWrapAround() {
        // Fingers start almost vertical (angle near 180 after atan2) and keep turning.
        val res = twoFinger { f ->
            val a = Math.toRadians((170.0 + 60.0 * f))
            floatArrayOf(540f, 1200f, 540f + 300f * cos(a).toFloat(), 1200f + 300f * sin(a).toFloat())
        }
        assertEquals(GestureKind.ROTATE_CW, res.only())
    }

    @Test
    fun smallTwoFingerJitterIsNothing() {
        val res = twoFinger { f -> floatArrayOf(400f + 5f * f, 1000f + 5f * f, 700f - 5f * f, 1000f) }
        assertNull(res.only())
    }

    @Test
    fun twoFingersAreNeverReportedAsOneFingerSwipe() {
        val res = twoFinger { f -> floatArrayOf(400f, 1400f - 700f * f, 700f, 1400f - 700f * f) }
        assertEquals(GestureKind.TWO_FINGER_SWIPE_UP, res.only())
    }

    @Test
    fun thirdFingerDoesNotRestartTracking() {
        r.onTouch(TouchSample.single(DOWN, t, 400f, 1000f))
        t += 5
        two(POINTER_DOWN, 400f, 1000f, 700f, 1000f)
        t += 5
        r.onTouch(TouchSample(POINTER_DOWN, t, listOf(TouchPointer(0, 400f, 1000f), TouchPointer(1, 700f, 1000f), TouchPointer(2, 550f, 1300f))))
        var result: GestureKind? = null
        for (i in 1..10) {
            t += 12
            val d = 40f * i
            result = result ?: r.onTouch(
                TouchSample(MOVE, t, listOf(TouchPointer(0, 400f - d, 1000f), TouchPointer(1, 700f + d, 1000f), TouchPointer(2, 550f, 1300f))),
            )
        }
        assertEquals(GestureKind.PINCH_OUT, result)
    }

    // endregion

    // region robustness

    @Test
    fun cancelClearsEverything() {
        tap(500f, 1000f)
        r.onTouch(TouchSample.single(CANCEL, t + 10, 500f, 1000f))
        assertEquals(GestureRecognizer.Phase.IDLE, r.phase)
        t += 50
        assertNull(tap(500f, 1000f))
    }

    @Test
    fun duplicateSamplesAreIgnored() {
        val down = TouchSample.single(DOWN, t, 500f, 1000f)
        r.onTouch(down)
        r.onTouch(down)
        t += 60
        val up = TouchSample.single(UP, t, 500f, 1000f)
        r.onTouch(up)
        r.onTouch(up)
        assertEquals(GestureRecognizer.Phase.WAIT_SECOND_TAP, r.phase)
    }

    @Test
    fun recognizerIsReusableAfterAGesture() {
        assertEquals(GestureKind.SWIPE_UP, drag(500f, 1500f, 500f, 900f).only())
        t += 500
        assertEquals(GestureKind.SWIPE_DOWN, drag(500f, 600f, 500f, 1200f).only())
        t += 500
        assertEquals(GestureKind.PINCH_IN, twoFinger { f -> floatArrayOf(200f + 250f * f, 1000f, 900f - 250f * f, 1000f) }.only())
    }

    @Test
    fun lowSensitivityNeedsMoreTravel() {
        r = GestureRecognizer(RecognizerConfig.forDensity(density, Sensitivity.LOW))
        // 65dp is enough for MEDIUM (56dp) but not for LOW (78dp).
        assertNull(drag(500f, 1500f, 500f, 1500f - dp(65f)).only())
        r = GestureRecognizer(RecognizerConfig.forDensity(density, Sensitivity.MEDIUM))
        t += 500
        assertEquals(GestureKind.SWIPE_UP, drag(500f, 1500f, 500f, 1500f - dp(65f)).only())
    }

    // endregion
}
