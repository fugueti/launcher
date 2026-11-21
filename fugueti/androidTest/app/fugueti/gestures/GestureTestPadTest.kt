/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fugueti.gestures.engine.GestureKind
import app.fugueti.gestures.ui.GestureTestPadView
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.GesturesTestPad
import org.hamcrest.Matcher
import org.hamcrest.Matchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso: real multi-touch MotionEvents are injected into the "Test gestures" screen and the
 * gesture it recognized is checked, for all 13 gestures.
 */
@RunWith(AndroidJUnit4::class)
class GestureTestPadTest {

    private class PerformGesture(private val kind: GestureKind) : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()
        override fun getDescription() = "perform $kind"
        override fun perform(uiController: UiController, view: View) {
            // Let the screen enter animation finish so the pad does not move under the touch.
            uiController.loopMainThreadForAtLeast(1000)
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val cx = loc[0] + view.width / 2f
            val cy = loc[1] + view.height / 2f
            val unit = view.resources.displayMetrics.density * 32f
            GestureScripts.play(GestureScripts.script(kind, cx, cy, unit)) { ev ->
                uiController.injectMotionEvent(ev)
                ev.recycle()
            }
            uiController.loopMainThreadForAtLeast(350)
        }
    }

    private fun checkRecognized(kind: GestureKind) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = PreferenceActivity.createIntent(context, GesturesTestPad)
        ActivityScenario.launch<PreferenceActivity>(intent).use {
            val pad = onView(withTagValue(`is`(GestureTestPadView.TAG)))
            pad.perform(PerformGesture(kind))
            pad.check { view, noView ->
                if (noView != null) throw noView
                assertEquals(kind, (view as GestureTestPadView).lastGesture)
            }
            // Screenshot of the pad with the finger trail and the recognized gesture.
            val name = "pad-%02d-%s".format(kind.ordinal + 1, kind.name.lowercase().replace('_', '-'))
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("mkdir -p /sdcard/Download/fugueti").close()
            Thread.sleep(400)
            automation.executeShellCommand("screencap -p /sdcard/Download/fugueti/$name.png").use {
                java.io.FileInputStream(it.fileDescriptor).use { s -> s.readBytes() }
            }
        }
    }

    @Test fun swipeUp() = checkRecognized(GestureKind.SWIPE_UP)

    @Test fun swipeDown() = checkRecognized(GestureKind.SWIPE_DOWN)

    @Test fun swipeLeft() = checkRecognized(GestureKind.SWIPE_LEFT)

    @Test fun swipeRight() = checkRecognized(GestureKind.SWIPE_RIGHT)

    @Test fun doubleTap() = checkRecognized(GestureKind.DOUBLE_TAP)

    @Test fun doubleTapSwipeUp() = checkRecognized(GestureKind.DOUBLE_TAP_SWIPE_UP)

    @Test fun doubleTapSwipeDown() = checkRecognized(GestureKind.DOUBLE_TAP_SWIPE_DOWN)

    @Test fun twoFingerSwipeUp() = checkRecognized(GestureKind.TWO_FINGER_SWIPE_UP)

    @Test fun twoFingerSwipeDown() = checkRecognized(GestureKind.TWO_FINGER_SWIPE_DOWN)

    @Test fun pinchIn() = checkRecognized(GestureKind.PINCH_IN)

    @Test fun pinchOut() = checkRecognized(GestureKind.PINCH_OUT)

    @Test fun rotateClockwise() = checkRecognized(GestureKind.ROTATE_CW)

    @Test fun rotateCounterClockwise() = checkRecognized(GestureKind.ROTATE_CCW)
}
