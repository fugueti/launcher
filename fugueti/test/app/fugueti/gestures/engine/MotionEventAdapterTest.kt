/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures.engine

import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Robolectric tests: real [MotionEvent] objects go through the adapter into the recognizer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
// No TLS is needed here, so the Conscrypt security provider stays off.
@ConscryptMode(ConscryptMode.Mode.OFF)
class MotionEventAdapterTest {

    private fun event(action: Int, time: Long, vararg points: Pair<Float, Float>): MotionEvent {
        val props = Array(points.size) { i ->
            PointerProperties().apply {
                id = i
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(points.size) { i ->
            PointerCoords().apply {
                x = points[i].first
                y = points[i].second
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(0L, time, action, points.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointerAction(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    @Test
    fun convertsSingleFingerDown() {
        val s = MotionEventAdapter.toSample(event(MotionEvent.ACTION_DOWN, 10, 100f to 200f))!!
        assertEquals(TouchAction.DOWN, s.action)
        assertEquals(10L, s.timeMs)
        assertEquals(listOf(TouchPointer(0, 100f, 200f)), s.pointers)
    }

    @Test
    fun convertsSecondPointerDownWithBothPointers() {
        val s = MotionEventAdapter.toSample(
            event(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), 20, 100f to 200f, 300f to 400f),
        )!!
        assertEquals(TouchAction.POINTER_DOWN, s.action)
        assertEquals(2, s.pointerCount)
        assertEquals(TouchPointer(1, 300f, 400f), s.pointer(1))
    }

    @Test
    fun ignoresHover() {
        assertNull(MotionEventAdapter.toSample(event(MotionEvent.ACTION_HOVER_MOVE, 5, 1f to 1f)))
    }

    @Test
    fun realMotionEventsProduceAPinchOut() {
        val r = GestureRecognizer(RecognizerConfig.forDensity(2.75f))
        var t = 100L
        val results = mutableListOf<GestureKind?>()
        fun feed(ev: MotionEvent) {
            results += r.onTouch(MotionEventAdapter.toSample(ev)!!)
            ev.recycle()
        }
        feed(event(MotionEvent.ACTION_DOWN, t, 450f to 1000f))
        t += 10
        feed(event(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), t, 450f to 1000f, 650f to 1000f))
        for (i in 1..10) {
            t += 12
            val d = 30f * i
            feed(event(MotionEvent.ACTION_MOVE, t, 450f - d to 1000f, 650f + d to 1000f))
        }
        t += 5
        feed(event(pointerAction(MotionEvent.ACTION_POINTER_UP, 1), t, 150f to 1000f, 950f to 1000f))
        t += 5
        feed(event(MotionEvent.ACTION_UP, t, 150f to 1000f))
        assertEquals(listOf(GestureKind.PINCH_OUT), results.filterNotNull())
    }
}
