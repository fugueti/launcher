/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by Fugueti Launcher contributors (2026): one generic trigger for the 13 home screen
 * gestures, synchronous config snapshot for the touch controller, haptics preference and
 * accessibility actions. Modifications are distributed under GPL-3.0-or-later.
 */

package app.lawnchair.gestures

import android.view.View
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import app.fugueti.gestures.GestureLabels
import app.fugueti.gestures.engine.GestureKind
import app.fugueti.gestures.engine.Sensitivity
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.GestureHandler
import app.lawnchair.gestures.handlers.NoOpGestureHandler
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.firstBlocking
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class GestureController(private val launcher: LawnchairLauncher) {
    private val prefs = PreferenceManager2.getInstance(launcher)
    private val scope = launcher.lifecycleScope
    private val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(launcher)

    private val homePressHandler = handler(prefs.homePressGestureHandler)
    private val backPressHandler = handler(prefs.backPressGestureHandler)

    // Fugueti: one handler flow per home screen gesture.
    private val gestureHandlers: Map<GestureKind, Flow<GestureHandler>> =
        GestureKind.entries.associateWith { handler(prefs.gestureHandler(it)) }

    private val _configs = MutableStateFlow(
        GestureKind.entries.associateWith { prefs.gestureHandler(it).get().firstBlocking() },
    )

    /** Current action of every gesture, readable synchronously from touch code. */
    val configs: StateFlow<Map<GestureKind, GestureHandlerConfig>> = _configs.asStateFlow()

    @Volatile
    var hapticsEnabled: Boolean = prefs.gestureHaptics.get().firstBlocking()
        private set

    @Volatile
    var sensitivity: Sensitivity = Sensitivity.fromName(prefs.gestureSensitivity.get().firstBlocking())
        private set

    /**
     * Event times of the last ACTION_DOWNs that reached the empty workspace (not an icon or a
     * widget). Tap based gestures only fire when both taps landed on empty space.
     */
    private val emptySpaceDownTimes = ArrayDeque<Long>()

    /** Called by WorkspaceTouchListener for every ACTION_DOWN on empty workspace. */
    fun noteEmptySpaceDown(eventTime: Long) {
        emptySpaceDownTimes.addLast(eventTime)
        while (emptySpaceDownTimes.size > 4) emptySpaceDownTimes.removeFirst()
    }

    fun wasEmptySpaceDown(eventTime: Long): Boolean = eventTime in emptySpaceDownTimes

    private val accessibilityActionIds = mutableListOf<Int>()

    init {
        GestureKind.entries.forEach { kind ->
            prefs.gestureHandler(kind).get()
                .distinctUntilChanged()
                .onEach { config ->
                    _configs.value = _configs.value + (kind to config)
                    refreshAccessibilityActions()
                }
                .launchIn(launcher.lifecycleScope)
        }
        prefs.gestureHaptics.get().onEach { hapticsEnabled = it }.launchIn(launcher.lifecycleScope)
        prefs.gestureSensitivity.get().onEach { sensitivity = Sensitivity.fromName(it) }.launchIn(launcher.lifecycleScope)
    }

    fun configOf(kind: GestureKind): GestureHandlerConfig = _configs.value[kind] ?: GestureHandlerConfig.NoOp

    /** True when [kind] has an action other than "do nothing". */
    fun isConfigured(kind: GestureKind): Boolean = configOf(kind) !is GestureHandlerConfig.NoOp

    /** Last gesture whose action was started, with its uptime. Read by instrumentation tests. */
    @Volatile
    var lastTriggered: Pair<GestureKind, Long>? = null
        private set

    /** Runs the action configured for [kind]. */
    fun trigger(kind: GestureKind) {
        lastTriggered = kind to android.os.SystemClock.uptimeMillis()
        triggerHandler(gestureHandlers.getValue(kind), hapticsEnabled)
    }

    fun onDoubleTap() = trigger(GestureKind.DOUBLE_TAP)

    fun onSwipeUp() = trigger(GestureKind.SWIPE_UP)

    fun onSwipeDown() = trigger(GestureKind.SWIPE_DOWN)

    fun onTwoFingerSwipeUp() = trigger(GestureKind.TWO_FINGER_SWIPE_UP)

    fun onTwoFingerSwipeDown() = trigger(GestureKind.TWO_FINGER_SWIPE_DOWN)

    fun onHomePressed() {
        triggerHandler(homePressHandler, LawnchairApp.isRecentsEnabled)
    }

    fun onBackPressed() {
        triggerHandler(backPressHandler, false)
    }

    fun handle(handler: GestureHandlerConfig) {
        launcher.lifecycleScope.launch {
            val handler = handler.createHandler(launcher)
            handler.onTrigger(launcher)
        }
    }

    /**
     * Publishes every configured gesture as an accessibility action of the workspace, so people
     * using TalkBack (which changes how touches are delivered) can still run them from its menu.
     */
    fun refreshAccessibilityActions() {
        val workspace: View = launcher.workspace ?: return
        accessibilityActionIds.forEach { ViewCompat.removeAccessibilityAction(workspace, it) }
        accessibilityActionIds.clear()
        GestureKind.entries.filter(::isConfigured).forEach { kind ->
            val label = launcher.getString(
                R.string.fg_a11y_run_gesture,
                launcher.getString(GestureLabels.labelRes(kind)),
                configOf(kind).getLabel(launcher),
            )
            accessibilityActionIds += ViewCompat.addAccessibilityAction(workspace, label) { _, _ ->
                trigger(kind)
                true
            }
        }
    }

    private fun triggerHandler(handlerFlow: Flow<GestureHandler>, withHaptic: Boolean = true) {
        launcher.lifecycleScope.launch {
            val handler = handlerFlow.first()
            if (handler is NoOpGestureHandler) {
                return@launch
            }
            handler.onTrigger(launcher)
            if (withHaptic) {
                mMSDLPlayerWrapper.playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
            }
        }
    }

    private fun handler(pref: Preference<GestureHandlerConfig, String, *>) = pref.get()
        .distinctUntilChanged()
        .map { it.createHandler(launcher) }
        .shareIn(
            scope,
            SharingStarted.Lazily,
            replay = 1,
        )
}
