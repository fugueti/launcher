/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import app.fugueti.gestures.engine.GestureKind
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds and plays real multi-touch MotionEvent sequences for each gesture, in screen pixels,
 * around a center point. Timing is real (the injector sleeps between frames) so the system and
 * the recognizer see the same speeds a finger would produce.
 */
object GestureScripts {

    /** One instant: positions of every finger currently down (index = pointer id). */
    data class Frame(val points: List<Pair<Float, Float>>, val delayMs: Long = 12)

    /** A touch = consecutive frames; the finger count may change between frames. */
    data class Touch(val frames: List<Frame>, val pauseAfterMs: Long = 0)

    fun script(kind: GestureKind, cx: Float, cy: Float, unit: Float): List<Touch> {
        val d = 5f * unit // travel used by swipes, comfortably above every threshold
        return when (kind) {
            GestureKind.SWIPE_UP -> listOf(line(cx, cy + d / 2, cx, cy - d / 2))

            GestureKind.SWIPE_DOWN -> listOf(line(cx, cy - d / 2, cx, cy + d / 2))

            GestureKind.SWIPE_LEFT -> listOf(line(cx + d / 2, cy, cx - d / 2, cy))

            GestureKind.SWIPE_RIGHT -> listOf(line(cx - d / 2, cy, cx + d / 2, cy))

            GestureKind.DOUBLE_TAP -> listOf(tap(cx, cy), tap(cx, cy))

            GestureKind.DOUBLE_TAP_SWIPE_UP -> listOf(tap(cx, cy + d / 2), line(cx, cy + d / 2, cx, cy - d / 2))

            GestureKind.DOUBLE_TAP_SWIPE_DOWN -> listOf(tap(cx, cy - d / 2), line(cx, cy - d / 2, cx, cy + d / 2))

            GestureKind.TWO_FINGER_SWIPE_UP -> listOf(twoFingers { f -> pair(cx - unit, cy + d / 2 - d * f, cx + unit, cy + d / 2 - d * f) })

            GestureKind.TWO_FINGER_SWIPE_DOWN -> listOf(twoFingers { f -> pair(cx - unit, cy - d / 2 + d * f, cx + unit, cy - d / 2 + d * f) })

            GestureKind.PINCH_IN -> listOf(
                twoFingers { f ->
                    val r = 2.5f * unit * (1f - 0.7f * f)
                    pair(cx - r, cy - r, cx + r, cy + r)
                },
            )

            GestureKind.PINCH_OUT -> listOf(
                twoFingers { f ->
                    val r = 0.8f * unit * (1f + 2.2f * f)
                    pair(cx - r, cy - r, cx + r, cy + r)
                },
            )

            GestureKind.ROTATE_CW -> listOf(twoFingers { f -> rotate(cx, cy, 1.6f * unit, 75.0 * f) })

            GestureKind.ROTATE_CCW -> listOf(twoFingers { f -> rotate(cx, cy, 1.6f * unit, -75.0 * f) })
        }
    }

    private fun pair(ax: Float, ay: Float, bx: Float, by: Float) = listOf(ax to ay, bx to by)

    private fun rotate(cx: Float, cy: Float, r: Float, degrees: Double): List<Pair<Float, Float>> {
        val a = Math.toRadians(-60.0 + degrees)
        val dx = (r * cos(a)).toFloat()
        val dy = (r * sin(a)).toFloat()
        return pair(cx - dx, cy - dy, cx + dx, cy + dy)
    }

    private fun tap(x: Float, y: Float) = Touch(
        listOf(Frame(listOf(x to y), 0), Frame(listOf(x to y), 50)),
        pauseAfterMs = 90,
    )

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, steps: Int = 14) = Touch(
        List(steps + 1) { i ->
            val f = i / steps.toFloat()
            Frame(listOf(x0 + (x1 - x0) * f to y0 + (y1 - y0) * f), if (i == 0) 0 else 12)
        },
    )

    /** First finger lands, second joins one frame later, both move, then they lift. */
    private fun twoFingers(steps: Int = 16, pos: (Float) -> List<Pair<Float, Float>>): Touch {
        val frames = mutableListOf<Frame>()
        val start = pos(0f)
        frames += Frame(listOf(start[0]), 0)
        for (i in 0..steps) frames += Frame(pos(i / steps.toFloat()), if (i == 0) 8 else 14)
        return Touch(frames)
    }

    /** Plays [touches] through [inject] (UiAutomation or Espresso), generating proper actions. */
    fun play(touches: List<Touch>, inject: (MotionEvent) -> Unit) {
        for (touch in touches) {
            val downTime = SystemClock.uptimeMillis()
            var previousCount = 0
            var last: List<Pair<Float, Float>> = emptyList()
            for (frame in touch.frames) {
                if (frame.delayMs > 0) SystemClock.sleep(frame.delayMs)
                val count = frame.points.size
                when {
                    previousCount == 0 -> inject(event(downTime, MotionEvent.ACTION_DOWN, frame.points))

                    count > previousCount -> {
                        // Pointer joins: emit POINTER_DOWN with the new pointer index.
                        inject(event(downTime, MotionEvent.ACTION_POINTER_DOWN or (previousCount shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), frame.points))
                    }

                    else -> inject(event(downTime, MotionEvent.ACTION_MOVE, frame.points))
                }
                previousCount = count
                last = frame.points
            }
            SystemClock.sleep(8)
            if (last.size > 1) {
                inject(event(downTime, MotionEvent.ACTION_POINTER_UP or ((last.size - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), last))
                SystemClock.sleep(8)
            }
            inject(event(downTime, MotionEvent.ACTION_UP, last.take(1)))
            if (touch.pauseAfterMs > 0) SystemClock.sleep(touch.pauseAfterMs)
        }
    }

    private fun event(downTime: Long, action: Int, points: List<Pair<Float, Float>>): MotionEvent {
        val props = Array(points.size) { i ->
            MotionEvent.PointerProperties().apply {
                id = i
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(points.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = points[i].first
                y = points[i].second
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, points.size, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
    }
}
