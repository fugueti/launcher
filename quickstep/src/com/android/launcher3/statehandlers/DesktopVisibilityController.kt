/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.statehandlers

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Debug
import android.util.Log
import android.util.SparseArray
import android.view.Display.DEFAULT_DISPLAY
import androidx.core.util.forEach
import com.android.internal.util.LatencyTracker
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
//import com.android.launcher3.R
import android.util.SparseBooleanArray
import androidx.annotation.AnyThread
import androidx.core.util.set
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.Preconditions
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.fallback.RecentsState
import com.android.wm.shell.desktopmode.DisplayDeskState
import com.android.wm.shell.desktopmode.IDesktopTaskListener.Stub
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus.useRoundedCorners
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

import com.android.wm.shell.R as SharedR

/**
 * Controls the visibility of the workspace and the resumed / paused state when desktop mode is
 * enabled.
 */
@LauncherAppSingleton
class DesktopVisibilityController
@Inject
constructor(
    @ApplicationContext private val context: Context,
    systemUiProxy: SystemUiProxy,
    lifecycleTracker: DaggerSingletonTracker,
) {
    /**
     * Tracks the desks configurations on each display.
     *
     * (Used only when multiple desks are enabled).
     *
     * @property displayId The ID of the display this object represents.
     * @property activeDeskId The ID of the active desk on the associated display (if any). It has a
     *   value of `INACTIVE_DESK_ID` (-1) if there are no active desks. Note that there can only be
     *   at most one active desk on each display.
     * @property deskIds a set containing the IDs of the desks on the associated display.
     */
    private data class DisplayDeskConfig(
        val displayId: Int,
        var activeDeskId: Int = INACTIVE_DESK_ID,
        val deskIds: MutableSet<Int>,
    )

    private val _canCreateDesks = MutableListenableRef(false)

    /** True if it is possible to create new desks on current setup. */
    val canCreateDesks = _canCreateDesks.asListenable()

    /** Maps each display by its ID to its desks configuration. */
    private val displaysDesksConfigsMap = SparseArray<DisplayDeskConfig>()

    private val desktopVisibilityListeners: MutableSet<DesktopVisibilityListener> =
        ConcurrentHashMap.newKeySet()

    // to let launcher hold off on notifying desktop visibility listeners.
    var launcherAnimationRunning = false

<<<<<<< HEAD
    // TODO: b/394387739 - Deprecate this and replace it with something that tracks the count per
    //  desk.
    /**
     * Number of visible desktop windows in desktop mode. This can be > 0 when user goes to overview
     * from desktop window mode.
     */
    @Deprecated("Does not work with multi-desks")
    var visibleDesktopTasksCountDeprecated: Int = 0
        /**
         * Sets the number of desktop windows that are visible and updates launcher visibility based
         * on it.
         */
        set(visibleTasksCount) {
            if (enableMultipleDesktops(context)) {
                return
            }
            if (DEBUG) {
                Log.d(
                    TAG,
                    ("setVisibleDesktopTasksCount: visibleTasksCount=" +
                        visibleTasksCount +
                        " currentValue=" +
                        field),
                )
            }

            if (visibleTasksCount != field) {
                if (visibleDesktopTasksCountDeprecated == 0 && visibleTasksCount == 1) {
                    isInDesktopModeDeprecated = true
                }
                if (visibleDesktopTasksCountDeprecated == 1 && visibleTasksCount == 0) {
                    isInDesktopModeDeprecated = false
                }
                val wasVisible = field > 0
                val isVisible = visibleTasksCount > 0
                val wereDesktopTasksVisibleBefore = areDesktopTasksVisibleAndNotInOverview()
                field = visibleTasksCount
                val areDesktopTasksVisibleNow = areDesktopTasksVisibleAndNotInOverview()

                if (
                    wereDesktopTasksVisibleBefore != areDesktopTasksVisibleNow ||
                        wasVisible != isVisible
                ) {
                    if (!launcherAnimationRunning) {
                        notifyIsInDesktopModeChanged(DEFAULT_DISPLAY, areDesktopTasksVisibleNow)
                    } else {
                        isNotifyingDesktopVisibilityPending = true
                    }
                }

                if (
                    !false && wasVisible != isVisible
                ) {
                    // TODO: b/333533253 - Remove after flag rollout
                    if (field > 0) {
                        if (!inOverviewState) {
                            // When desktop tasks are visible & we're not in overview, we want
                            // launcher
                            // to appear paused, this ensures that taskbar displays.
                            markLauncherPaused()
                        }
                    } else {
                        // If desktop tasks aren't visible, ensure that launcher appears resumed to
                        // behave normally.
                        markLauncherResumed()
                    }
                }
            }
        }

    private var inOverviewState = false
    private var backgroundStateEnabled = false
    private var gestureInProgress = false

    private var desktopTaskListener: DesktopTaskListenerImpl?
=======
    private var inOverviewStateMap = SparseBooleanArray()
>>>>>>> aosp/android17-release

    init {
        lifecycleTracker.addCloseable(
            systemUiProxy.desktopTaskListeners.register(DesktopTaskListenerImpl(this))
        )
    }

    /**
     * Returns the ID of the active desk (if any) on the display whose ID is [displayId], or
     * [INACTIVE_DESK_ID] if no desk is currently active or the multiple desks feature is disabled.
     */
    fun getActiveDeskId(displayId: Int): Int {
        return getDisplayDeskConfig(displayId)?.activeDeskId ?: INACTIVE_DESK_ID
    }

    /** Returns whether a desk is currently active on the display with the given [displayId]. */
    @AnyThread
    fun isInDesktopMode(displayId: Int): Boolean {
        val activeDeskId = getDisplayDeskConfig(displayId)?.activeDeskId ?: INACTIVE_DESK_ID
        val isInDesktopMode = activeDeskId != INACTIVE_DESK_ID
        if (DEBUG) {
            Log.d(TAG, "isInDesktopMode ($displayId): $isInDesktopMode")
        }
        return isInDesktopMode
    }

    /**
     * Returns whether a desk is currently active on the display with the given [displayId] and
     * Overview is not active.
     */
    fun isInDesktopModeAndNotInOverview(displayId: Int): Boolean {
        val inOverviewState = inOverviewStateMap[displayId]
        if (DEBUG) {
            Log.d(
                TAG,
                "isInDesktopModeAndNotInOverview: displayId=$displayId overview=$inOverviewState",
            )
        }
        return isInDesktopMode(displayId) && !inOverviewState
    }

    fun onLauncherStateChanged(displayId: Int, state: LauncherState) {
        onLauncherStateChanged(
            state,
            state === LauncherState.BACKGROUND_APP,
            state.isRecentsViewVisible,
            displayId,
        )
    }

    /**
     * Launcher Driven Desktop Mode changes. For example, swipe to home and quick switch from
     * Desktop Windowing Mode. if there is any pending notification please notify desktop visibility
     * listeners.
     */
    fun onLauncherAnimationFromDesktopEnd() {
        launcherAnimationRunning = false
    }

    fun onLauncherStateChanged(displayId: Int, state: RecentsState) {
        onLauncherStateChanged(
            state,
            state === RecentsState.BACKGROUND_APP,
            state.isRecentsViewVisible(),
            displayId,
        )
    }

    /** Process launcher state change and update launcher view visibility based on desktop state */
    private fun onLauncherStateChanged(
        state: BaseState<*>,
        isBackgroundAppState: Boolean,
        isRecentsViewVisible: Boolean,
        displayId: Int,
    ) {
        if (DEBUG) {
            Log.d(TAG, "onLauncherStateChanged: newState=$state")
        }
        // Desktop visibility tracks overview and background state separately
        setOverviewStateEnabled(displayId, !isBackgroundAppState && isRecentsViewVisible)
    }

    private fun setOverviewStateEnabled(displayId: Int, overviewStateEnabled: Boolean) {
        val inOverviewState = inOverviewStateMap[displayId]
        if (DEBUG) {
            Log.d(
                TAG,
                ("setOverviewStateEnabled: enabled=" +
                    overviewStateEnabled +
                    " currentValue=" +
                    inOverviewState),
            )
        }
        if (overviewStateEnabled != inOverviewState) {
<<<<<<< HEAD
            val wereDesktopTasksVisibleBefore = areDesktopTasksVisibleAndNotInOverview()
            inOverviewState = overviewStateEnabled
            val areDesktopTasksVisibleNow = areDesktopTasksVisibleAndNotInOverview()

            if (!enableMultipleDesktops(context)) {
                if (wereDesktopTasksVisibleBefore != areDesktopTasksVisibleNow) {
                    notifyIsInDesktopModeChanged(DEFAULT_DISPLAY, areDesktopTasksVisibleNow)
                }
            } else {
                // When overview state changes, it changes together on all displays.
                displaysDesksConfigsMap.forEach { displayId, deskConfig ->
                    // Overview affects the state of desks only if desktop mode is active on this
                    // display.
                    if (isInDesktopMode(displayId)) {
                        notifyIsInDesktopModeChanged(
                            displayId,
                            isInDesktopModeAndNotInOverview(displayId),
                        )
                    }
                }
            }

            if (false) {
                return
            }

            // TODO: b/333533253 - Clean up after flag rollout
            if (inOverviewState) {
                markLauncherResumed()
            } else if (areDesktopTasksVisibleNow && !gestureInProgress) {
                // Switching out of overview state and gesture finished.
                // If desktop tasks are still visible, hide launcher again.
                markLauncherPaused()
            }
=======
            inOverviewStateMap[displayId] = overviewStateEnabled
>>>>>>> aosp/android17-release
        }
    }

    /** Registers a listener for Taskbar changes in Desktop Mode. */
    @AnyThread
    fun registerDesktopVisibilityListener(listener: DesktopVisibilityListener) {
        desktopVisibilityListeners.add(listener)
    }

    /** Removes a previously registered listener for Taskbar changes in Desktop Mode. */
    @AnyThread
    fun unregisterDesktopVisibilityListener(listener: DesktopVisibilityListener) {
        desktopVisibilityListeners.remove(listener)
    }

    private fun notifyTaskbarDesktopModeListeners(
        doesAnyTaskRequireTaskbarRounding: Boolean,
        displayId: Int,
    ) {
        Preconditions.assertTaskbarUiThread()
        if (DEBUG) {
            Log.d(
                TAG,
                "notifyTaskbarDesktopModeListeners: doesAnyTaskRequireTaskbarRounding=" +
                    doesAnyTaskRequireTaskbarRounding +
                    " displayId=" +
                    displayId,
            )
        }
        for (listener in desktopVisibilityListeners) {
            listener.onTaskbarCornerRoundingUpdate(doesAnyTaskRequireTaskbarRounding, displayId)
        }
    }

    private fun notifyOnDeskAdded(displayId: Int, deskId: Int) {
        if (DEBUG) {
            Log.d(TAG, "notifyOnDeskAdded: displayId=$displayId, deskId=$deskId")
        }

        for (listener in desktopVisibilityListeners) {
            listener.onDeskAdded(displayId, deskId)
        }
    }

    private fun notifyOnDeskRemoved(displayId: Int, deskId: Int) {
        if (DEBUG) {
            Log.d(TAG, "notifyOnDeskRemoved: displayId=$displayId, deskId=$deskId")
        }

        for (listener in desktopVisibilityListeners) {
            listener.onDeskRemoved(displayId, deskId)
        }
    }

    private fun notifyOnActiveDeskChanged(displayId: Int, newActiveDesk: Int, oldActiveDesk: Int) {
        if (DEBUG) {
            Log.d(
                TAG,
                "notifyOnActiveDeskChanged: displayId=$displayId, newActiveDesk=$newActiveDesk, oldActiveDesk=$oldActiveDesk",
            )
        }

        for (listener in desktopVisibilityListeners) {
            listener.onActiveDeskChanged(displayId, newActiveDesk, oldActiveDesk)
        }
    }

    private fun notifyOnTaskAppearingInDeskWithOverviewShowing(
        taskId: Int,
        displayId: Int,
        deskId: Int,
    ) {
        if (DEBUG) {
            Log.d(
                TAG,
                "notifyOnTaskAppearingInDeskWithOverviewShowing: " +
                    "taskId=$taskId displayId=$displayId deskId=$deskId",
            )
        }

        for (listener in desktopVisibilityListeners) {
            listener.onTaskAppearingInDeskWithOverviewShowing(taskId, displayId, deskId)
        }
    }

    // Called when the DesktopTaskListener is first connected to WM.
    private fun onListenerConnected(
        displayDeskStates: Array<DisplayDeskState>,
        canCreateDesks: Boolean,
    ) {
        clearDisplaysDesksConfigsMap()

        displayDeskStates.forEach { displayDeskState ->
            if (DEBUG) {
                Log.d(TAG, "onListenerConnected displayId=${displayDeskState.displayId}")
            }
            putDisplaysDeskConfig(
                displayDeskState.displayId,
                DisplayDeskConfig(
                    displayId = displayDeskState.displayId,
                    activeDeskId = displayDeskState.activeDeskId,
                    deskIds = displayDeskState.deskIds.toMutableSet(),
                ),
            )
        }

        onCanCreateDesksChanged(canCreateDesks)
        notifyOnListenerInitializedFromShell()
    }

    private fun notifyOnListenerInitializedFromShell() {
        if (DEBUG) {
            Log.d(TAG, "notifyOnListenerInitializedFromShell")
        }

        for (listener in desktopVisibilityListeners) {
            listener.onListenerInitializedFromShell()
        }
    }

    @AnyThread
    private fun getDisplayDeskConfig(displayId: Int): DisplayDeskConfig? {
        return if (enableTaskbarUiThread()) {
            synchronized(displaysDesksConfigsMap) { displaysDesksConfigsMap[displayId] }
        } else {
            displaysDesksConfigsMap[displayId]
        }
    }

    @AnyThread
    private fun putDisplaysDeskConfig(displayId: Int, value: DisplayDeskConfig) {
        if (enableTaskbarUiThread()) {
            synchronized(displaysDesksConfigsMap) { displaysDesksConfigsMap[displayId] = value }
        } else {
            displaysDesksConfigsMap[displayId] = value
        }
    }

    private fun onCanCreateDesksChanged(canCreateDesks: Boolean) {
        this._canCreateDesks.dispatchValue(canCreateDesks)
    }

    private fun onDeskAdded(displayId: Int, deskId: Int) {
        // Add the config for the desk if there is nothing yet, as the display can start without any
        // desks.
        if (getDisplayDeskConfig(displayId) == null) {
            putDisplaysDeskConfig(
                displayId,
                DisplayDeskConfig(displayId, INACTIVE_DESK_ID, mutableSetOf(deskId)),
            )
        } else {
            getDisplayDeskConfig(displayId)!!.also {
                if (!it.deskIds.add(deskId)) {
                    Log.e(TAG, "Found a duplicate desk Id: $deskId on display: $displayId")
                }
            }
        }

        notifyOnDeskAdded(displayId, deskId)
    }

    private fun onDeskRemoved(displayId: Int, deskId: Int) {
        getDisplayDeskConfig(displayId)?.also {
            if (!it.deskIds.remove(deskId)) {
                Log.e(TAG, "Removing non-existing desk Id: $deskId on display: $displayId")
            }
            if (it.activeDeskId == deskId) {
                it.activeDeskId = INACTIVE_DESK_ID
            }
        }

        notifyOnDeskRemoved(displayId, deskId)
    }

    private fun onActiveDeskChanged(displayId: Int, newActiveDesk: Int, oldActiveDesk: Int) {
        getDisplayDeskConfig(displayId)?.also {
            if (oldActiveDesk != it.activeDeskId) {
                Log.e(
                    TAG,
                    "Mismatch between the Shell's oldActiveDesk: $oldActiveDesk, " +
                        "and Launcher's: ${it.activeDeskId}",
                )
            }
            if (newActiveDesk != INACTIVE_DESK_ID && !it.deskIds.contains(newActiveDesk)) {
                Log.e(TAG, "newActiveDesk: $newActiveDesk was never added to display: $displayId")
            }
            it.activeDeskId = newActiveDesk
        }

        if (newActiveDesk != oldActiveDesk) {
            notifyOnActiveDeskChanged(displayId, newActiveDesk, oldActiveDesk)
        }
<<<<<<< HEAD

        if (
            (newActiveDesk == INACTIVE_DESK_ID || oldActiveDesk == INACTIVE_DESK_ID) &&
                !launcherAnimationRunning
        ) {
            val duration = context.resources.getInteger(SharedR.integer.to_desktop_animation_duration_ms)
            if (oldActiveDesk == INACTIVE_DESK_ID && newActiveDesk != INACTIVE_DESK_ID) {
                notifyTaskbarDesktopModeListenersForEntry(duration)
            } else if (newActiveDesk == INACTIVE_DESK_ID && oldActiveDesk != INACTIVE_DESK_ID) {
                notifyTaskbarDesktopModeListenersForExit(duration)
            } else {
                // do nothing because user switch between two desktop.
            }
        } else {
            isNotifyingDesktopVisibilityPending = true
        }
    }

    /** TODO: b/333533253 - Remove after flag rollout */
    private fun markLauncherPaused() {
        if (false) {
            return
        }
        if (DEBUG) {
            Log.d(TAG, "markLauncherPaused " + Debug.getCaller())
        }
        val activity: StatefulActivity<LauncherState>? =
            QuickstepLauncher.ACTIVITY_TRACKER.getCreatedContext()
        activity?.setPaused()
    }

    /** TODO: b/333533253 - Remove after flag rollout */
    private fun markLauncherResumed() {
        if (false) {
            return
        }
        if (DEBUG) {
            Log.d(TAG, "markLauncherResumed " + Debug.getCaller())
        }
        val activity: StatefulActivity<LauncherState>? =
            QuickstepLauncher.ACTIVITY_TRACKER.getCreatedContext()
        // Check activity state before calling setResumed(). Launcher may have been actually
        // paused (eg fullscreen task moved to front).
        // In this case we should not mark the activity as resumed.
        if (activity != null && activity.isResumed) {
            activity.setResumed()
        }
=======
    }

    private fun onTaskAppearingInDeskWithOverviewShowing(taskId: Int, displayId: Int, deskId: Int) {
        notifyOnTaskAppearingInDeskWithOverviewShowing(taskId, displayId, deskId)
>>>>>>> aosp/android17-release
    }

    fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "DesktopVisibilityController:")

        pw.println("$prefix\tdesktopVisibilityListeners=$desktopVisibilityListeners")
        pw.println("$prefix\tinOverviewState=$inOverviewStateMap")
        pw.println("$prefix\tcontext=$context")
    }

    private fun clearDisplaysDesksConfigsMap() {
        if (enableTaskbarUiThread()) {
            synchronized(displaysDesksConfigsMap) { displaysDesksConfigsMap.clear() }
        } else {
            displaysDesksConfigsMap.clear()
        }
    }

    /**
     * Wrapper for the IDesktopTaskListener stub to prevent lingering references to the launcher
     * activity via the controller.
     */
<<<<<<< HEAD
    private class DesktopTaskListenerImpl(
        controller: DesktopVisibilityController,
        @ApplicationContext private val context: Context,
    ) : Stub() {
        private val controller = WeakReference(controller)
        private val displayManager: DisplayManager = context.getSystemService(DisplayManager::class.java)
        private val displayId = if (Utilities.ATLEAST_R) {
            context.displayId
        } else {
            displayManager.displays[DEFAULT_DISPLAY].displayId
        }
=======
    private class DesktopTaskListenerImpl(private val controller: DesktopVisibilityController) :
        Stub() {
>>>>>>> aosp/android17-release

        override fun onListenerConnected(
            displayDeskStates: Array<DisplayDeskState>,
            canCreateDesks: Boolean,
        ) {
            MAIN_EXECUTOR.execute {
                controller.onListenerConnected(displayDeskStates, canCreateDesks)
            }
        }

        override fun onTasksVisibilityChanged(displayId: Int, visibleTasksCount: Int) {}

        override fun onStashedChanged(displayId: Int, stashed: Boolean) {}

        override fun onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding: Boolean,
            displayId: Int,
        ) {
            if (!useRoundedCorners()) return
            getTaskbarUiThread().execute {
                controller.apply {
                    Log.d(
                        TAG,
                        "DesktopTaskListenerImpl: doesAnyTaskRequireTaskbarRounding= " +
                            doesAnyTaskRequireTaskbarRounding +
                            " displayId=" +
                            displayId,
                    )
                    notifyTaskbarDesktopModeListeners(doesAnyTaskRequireTaskbarRounding, displayId)
                }
            }
        }

        override fun onEnterDesktopModeTransitionStarted(transitionDuration: Int) {}

        override fun onExitDesktopModeTransitionStarted(
            transitionDuration: Int,
            shouldEndUpAtHome: Boolean,
        ) {}

        override fun onCanCreateDesksChanged(canCreateDesks: Boolean) {
            MAIN_EXECUTOR.execute { controller.onCanCreateDesksChanged(canCreateDesks) }
        }

        override fun onDeskAdded(displayId: Int, deskId: Int) {
            MAIN_EXECUTOR.execute { controller.onDeskAdded(displayId, deskId) }
        }

        override fun onDeskRemoved(displayId: Int, deskId: Int) {
            MAIN_EXECUTOR.execute { controller.onDeskRemoved(displayId, deskId) }
        }

        override fun onActiveDeskChanged(displayId: Int, newActiveDesk: Int, oldActiveDesk: Int) {
            MAIN_EXECUTOR.execute {
                controller.onActiveDeskChanged(displayId, newActiveDesk, oldActiveDesk)
            }
        }

        override fun onTaskAppearingInDeskWithOverviewShowing(
            taskId: Int,
            displayId: Int,
            deskId: Int,
        ) {
            MAIN_EXECUTOR.execute {
                controller.onTaskAppearingInDeskWithOverviewShowing(taskId, displayId, deskId)
            }
        }
    }

    /** A listener for when the user enters/exits Desktop Mode. */
    interface DesktopVisibilityListener {
        /**
         * Called when a new desk is added.
         *
         * @param displayId The ID of the display on which the desk was added.
         * @param deskId The ID of the newly added desk.
         */
        fun onDeskAdded(displayId: Int, deskId: Int) {}

        /**
         * Called when an existing desk is removed.
         *
         * @param displayId The ID of the display on which the desk was removed.
         * @param deskId The ID of the desk that was removed.
         */
        fun onDeskRemoved(displayId: Int, deskId: Int) {}

        /**
         * Called when the active desk changes.
         *
         * @param displayId The ID of the display on which the desk activation change is happening.
         * @param newActiveDesk The ID of the new active desk or -1 if no desk is active anymore
         *   (i.e. exit desktop mode).
         * @param oldActiveDesk The ID of the desk that was previously active, or -1 if no desk was
         *   active before.
         */
        fun onActiveDeskChanged(displayId: Int, newActiveDesk: Int, oldActiveDesk: Int) {}

        /**
         * Called when a task appears in a desk.
         *
         * @param taskId the ID of the task appearing.
         * @param displayId the ID of the display in which the task is appearing
         * @param deskId the ID of the desk in which the task is appearing
         */
        fun onTaskAppearingInDeskWithOverviewShowing(taskId: Int, displayId: Int, deskId: Int) {}

        /** Called when the listener is initialised from shell. */
        fun onListenerInitializedFromShell() {}

        /**
         * Callback for when task is resized in desktop mode. This callback is executed on taskbar
         * ui thread.
         *
         * @param doesAnyTaskRequireTaskbarRounding whether task requires taskbar corner roundness.
         */
        fun onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding: Boolean,
            displayId: Int,
        ) {}
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getDesktopVisibilityController)

        private const val TAG = "DesktopVisController"
        private const val DEBUG = false

        const val INACTIVE_DESK_ID = -1
    }
}
