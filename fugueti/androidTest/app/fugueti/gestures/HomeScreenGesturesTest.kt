/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fugueti.gestures.engine.GestureKind
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.util.ComponentKey
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * End to end: the debug build must be the default home app. Each test maps one gesture to an
 * action, injects the gesture on the real home screen through UiAutomation and checks the
 * Fugueti engine fired it (or, for the native app drawer swipe, that the drawer opened).
 * A screenshot of the result is written to /sdcard/Download/fugueti/.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class HomeScreenGesturesTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val prefs = PreferenceManager2.getInstance(context)

    private fun shell(cmd: String): String {
        val pfd = automation.executeShellCommand(cmd)
        return FileInputStream(pfd.fileDescriptor).use { it.readBytes().decodeToString() }.also { pfd.close() }
    }

    private fun goHome() {
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        SystemClock.sleep(1200)
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        SystemClock.sleep(900)
    }

    private fun launcher(): LawnchairLauncher {
        var l: Launcher? = null
        val deadline = SystemClock.uptimeMillis() + 15_000
        var started = false
        while (l == null && SystemClock.uptimeMillis() < deadline) {
            l = Launcher.ACTIVITY_TRACKER.getCreatedActivity()
            if (l == null && !started) {
                // After the instrumentation restarts the process the home activity must be recreated.
                context.startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                started = true
            }
            if (l == null) SystemClock.sleep(250)
        }
        assertNotNull("Fugueti debug build must be the default home app", l)
        return l as LawnchairLauncher
    }

    /**
     * Waits until the home screen is resumed, loaded and idle in its normal state. Gestures
     * injected while the launcher is still being (re)created are partly delivered to the window
     * transition instead, which made the end-to-end test flaky on a loaded emulator.
     */
    private fun waitUntilHomeIsReady() {
        val deadline = SystemClock.uptimeMillis() + 20_000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            val l = launcher()
            instrumentation.runOnMainSync {
                ready = l.hasBeenResumed() && !l.isWorkspaceLoading && l.isInState(LauncherState.NORMAL)
            }
            if (!ready) SystemClock.sleep(250)
        }
        assertTrue("home screen never became ready", ready)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(1500)
    }

    private fun app(pkg: String, cls: String, label: String) = GestureHandlerConfig.OpenApp(
        appName = label,
        target = OpenAppTarget.App(ComponentKey(ComponentName(pkg, cls), Process.myUserHandle())),
    )

    /** The action each gesture gets during the test (chosen so every screenshot looks different). */
    private val mapping: Map<GestureKind, GestureHandlerConfig> = mapOf(
        GestureKind.SWIPE_UP to GestureHandlerConfig.OpenAppDrawer,
        GestureKind.SWIPE_DOWN to GestureHandlerConfig.OpenAppSearch,
        GestureKind.SWIPE_LEFT to app("com.android.settings", "com.android.settings.Settings", "Settings"),
        GestureKind.SWIPE_RIGHT to GestureHandlerConfig.OpenIntent("Wi-Fi", "intent:#Intent;action=android.settings.WIFI_SETTINGS;end"),
        GestureKind.DOUBLE_TAP to GestureHandlerConfig.OpenNotifications,
        GestureKind.DOUBLE_TAP_SWIPE_UP to GestureHandlerConfig.OpenIntent("Date & time", "intent:#Intent;action=android.settings.DATE_SETTINGS;end"),
        GestureKind.DOUBLE_TAP_SWIPE_DOWN to GestureHandlerConfig.OpenActivity("Display", "com.android.settings", "com.android.settings.Settings\$DisplaySettingsActivity"),
        GestureKind.TWO_FINGER_SWIPE_UP to GestureHandlerConfig.OpenIntent("Battery", "intent:#Intent;action=android.intent.action.POWER_USAGE_SUMMARY;end"),
        GestureKind.TWO_FINGER_SWIPE_DOWN to GestureHandlerConfig.OpenNotifications,
        GestureKind.PINCH_IN to GestureHandlerConfig.OpenIntent("Sound", "intent:#Intent;action=android.settings.SOUND_SETTINGS;end"),
        GestureKind.PINCH_OUT to GestureHandlerConfig.OpenIntent("Apps", "intent:#Intent;action=android.settings.APPLICATION_SETTINGS;end"),
        GestureKind.ROTATE_CW to GestureHandlerConfig.OpenIntent("Dial", "tel:5511999999999"),
        GestureKind.ROTATE_CCW to GestureHandlerConfig.OpenIntent("Location", "intent:#Intent;action=android.settings.LOCATION_SOURCE_SETTINGS;end"),
    )

    @Before
    fun setUp() {
        runBlocking {
            GestureKind.entries.forEach { prefs.gestureHandler(it).set(mapping.getValue(it)) }
            prefs.gestureHaptics.set(false)
        }
        shell("mkdir -p /sdcard/Download/fugueti")
        // Changing the default home recreates the launcher, so only do it when needed.
        val home = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        if (!home.contains(context.packageName + "/")) {
            shell("cmd package set-home-activity ${context.packageName}/app.lawnchair.LawnchairLauncher")
        }
        goHome()
        waitUntilHomeIsReady()
    }

    @After
    fun tearDown() {
        runBlocking {
            GestureKind.entries.forEach {
                prefs.gestureHandler(it).set(prefs.gestureHandler(it).defaultValue)
            }
            prefs.gestureHaptics.set(true)
        }
        goHome()
    }

    private fun perform(kind: GestureKind, index: Int) {
        val dm = context.resources.displayMetrics
        val cx = dm.widthPixels / 2f
        // Empty band of the default home screen: below the smartspace, above the first icon row.
        val cy = dm.heightPixels * 0.42f
        val unit = dm.density * 32f

        // On a loaded emulator an injected gesture is sometimes partly lost before reaching the
        // app (it even happens to upstream's native swipe). One retry keeps the check meaningful.
        var fired = false
        for (attempt in 1..2) {
            if (attempt == 2) {
                goHome()
                waitUntilHomeIsReady()
            }
            if (kind == GestureKind.SWIPE_LEFT) {
                // Swipe left belongs to Fugueti only on the last page (elsewhere it turns the page).
                instrumentation.runOnMainSync {
                    val ws = launcher().workspace
                    ws.snapToPageImmediately(ws.pageCount - 1)
                }
                SystemClock.sleep(800)
            }
            val before = SystemClock.uptimeMillis()
            GestureScripts.play(GestureScripts.script(kind, cx, cy, unit)) { ev ->
                automation.injectInputEvent(ev, true)
                ev.recycle()
            }
            fired = waitFor(3_000) { hasFired(kind, before) }
            if (fired) break
        }
        assertTrue("gesture $kind had no effect", fired)

        SystemClock.sleep(1200)
        val name = "%02d-%s".format(index, kind.name.lowercase().replace('_', '-'))
        shell("screencap -p /sdcard/Download/fugueti/$name.png")
    }

    private fun hasFired(kind: GestureKind, since: Long): Boolean {
        if (kind == GestureKind.SWIPE_UP) {
            // Native animated drawer, like upstream Lawnchair.
            var open = false
            instrumentation.runOnMainSync { open = launcher().isInState(LauncherState.ALL_APPS) }
            return open
        }
        val last = launcher().gestureController.lastTriggered ?: return false
        return last.first == kind && last.second >= since
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    @Test fun g01_swipeUp() = perform(GestureKind.SWIPE_UP, 1)

    @Test fun g02_swipeDown() = perform(GestureKind.SWIPE_DOWN, 2)

    @Test fun g03_swipeLeft() = perform(GestureKind.SWIPE_LEFT, 3)

    @Test fun g04_swipeRight() = perform(GestureKind.SWIPE_RIGHT, 4)

    @Test fun g05_doubleTap() = perform(GestureKind.DOUBLE_TAP, 5)

    @Test fun g06_doubleTapSwipeUp() = perform(GestureKind.DOUBLE_TAP_SWIPE_UP, 6)

    @Test fun g07_doubleTapSwipeDown() = perform(GestureKind.DOUBLE_TAP_SWIPE_DOWN, 7)

    @Test fun g08_twoFingerSwipeUp() = perform(GestureKind.TWO_FINGER_SWIPE_UP, 8)

    @Test fun g09_twoFingerSwipeDown() = perform(GestureKind.TWO_FINGER_SWIPE_DOWN, 9)

    @Test fun g10_pinchIn() = perform(GestureKind.PINCH_IN, 10)

    @Test fun g11_pinchOut() = perform(GestureKind.PINCH_OUT, 11)

    @Test fun g12_rotateClockwise() = perform(GestureKind.ROTATE_CW, 12)

    @Test fun g13_rotateCounterClockwise() = perform(GestureKind.ROTATE_CCW, 13)
}
