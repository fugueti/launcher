/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3.statehandlers;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_DEPTH_CONTROLLER;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;

import android.content.Context;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;

import androidx.annotation.VisibleForTesting;

<<<<<<< HEAD
import com.android.launcher3.BaseActivity;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
=======
>>>>>>> aosp/android17-release
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.states.StateAnimationConfig;
<<<<<<< HEAD
import com.android.launcher3.uioverrides.QuickstepLauncher;
=======
import com.android.launcher3.util.ListenableRef;
>>>>>>> aosp/android17-release
import com.android.quickstep.util.BaseDepthControllerImpl;

import java.io.PrintWriter;

import app.lawnchair.preferences2.PreferenceCacheExtensionsKt;
import app.lawnchair.compat.LawnchairQuickstepCompat;
import app.lawnchair.preferences2.PreferenceManager2;

/**
 * Controls blur and wallpaper zoom.
 * @param <STATE> state associated with the container.
 * @param <CONTAINER> the StatefulContainer.
 */
<<<<<<< HEAD
public class DepthController extends BaseDepthControllerImpl implements StateHandler<LauncherState>,
        BaseActivity.MultiWindowModeChangedListener {
    @VisibleForTesting
    final ViewTreeObserver.OnDrawListener mOnDrawListener = this::onLauncherDraw;
=======
public class DepthController<
        STATE extends BaseState<STATE>,
        CONTAINER extends Context & StatefulContainer<STATE>>
        extends BaseDepthControllerImpl<STATE, CONTAINER>
        implements StateHandler<STATE> {
    public static final float DEPTH_0_PERCENT = 0f;
    public static final float DEPTH_70_PERCENT = 0.7f;
>>>>>>> aosp/android17-release

    @VisibleForTesting
    final ViewTreeObserver.OnDrawListener mOnDrawListener = this::onContainerDraw;

    private final Runnable mOpaquenessListener = this::applyDepthAndBlur;

    private View.OnAttachStateChangeListener mOnAttachListener;

    // Ensure {@link mOnDrawListener} is added only once to avoid spamming DragLayer's mRunQueue
    // via {@link View#post(Runnable)}
    private boolean mIsOnDrawListenerAdded = false;
    private boolean mRemoveOnDrawListenerCancelled = false;

<<<<<<< HEAD
    private final boolean mEnableDepth;

    public DepthController(QuickstepLauncher launcher) {
        super(launcher);
        var prefs = PreferenceManager2.getInstance(launcher);
        var pref = prefs.getWallpaperDepthEffect();
        mEnableDepth = PreferenceCacheExtensionsKt.firstCached(pref);
=======
    public DepthController(CONTAINER container, ListenableRef<Boolean> blurState) {
        super(container, blurState);
>>>>>>> aosp/android17-release
    }

    private void onContainerDraw() {
        View view = mContainer.getDragLayer();
        ViewRootImpl viewRootImpl = view.getViewRootImpl();
        try {
            if (Utilities.ATLEAST_Q) {
                setBaseSurface(viewRootImpl != null ? viewRootImpl.getSurfaceControl() : null);
            }
        } catch (Throwable t) {
            // LC-Ignored
        }
        mRemoveOnDrawListenerCancelled = false;
        view.post(() -> {
            if (!mRemoveOnDrawListenerCancelled) {
                removeOnDrawListener();
            }
        });
    }

    private void ensureDependencies() {
        View rootView = mContainer.getRootView();
        if (rootView == null) {
            return;
        }
        if (mOnAttachListener != null) {
            return;
        }
        mOnAttachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
<<<<<<< HEAD
                if (Utilities.ATLEAST_S) {
                    try {
                            UI_HELPER_EXECUTOR.execute(() ->
                                    CrossWindowBlurListeners.getInstance().addListener(
                                            mLauncher.getMainExecutor(), mCrossWindowBlurListener));
                    } catch (Throwable t) {
                        // LC-Ignored
                    }
                }
                mLauncher.getScrimView().addOpaquenessListener(mOpaquenessListener);
=======
                mContainer.getScrimView().addOpaquenessListener(mOpaquenessListener);
>>>>>>> aosp/android17-release

                // To handle the case where window token is invalid during last setDepth call.
                applyDepthAndBlur();
            }

            @Override
            public void onViewDetachedFromWindow(View view) { }
        };
        rootView.addOnAttachStateChangeListener(mOnAttachListener);
        if (rootView.isAttachedToWindow()) {
            mOnAttachListener.onViewAttachedToWindow(rootView);
        }
    }

    /**
<<<<<<< HEAD
     * Cleans up after this controller so it can be garbage collected without leaving traces.
     */
    public void dispose() {
        removeSecondaryListeners();

        if (mLauncher.getRootView() != null && mOnAttachListener != null) {
            mLauncher.getRootView().removeOnAttachStateChangeListener(mOnAttachListener);
            mOnAttachListener = null;
        }
    }

    private void removeSecondaryListeners() {
        if (Utilities.ATLEAST_S) {
            try {
                UI_HELPER_EXECUTOR.execute(() ->
                    CrossWindowBlurListeners.getInstance()
                        .removeListener(mCrossWindowBlurListener));
            } catch (Throwable t) {
                // LC-Ignored
            }
        }
        if (mOpaquenessListener != null) {
            mLauncher.getScrimView().removeOpaquenessListener(mOpaquenessListener);
        }
    }

    /**
=======
>>>>>>> aosp/android17-release
     * Sets if the underlying activity is started or not
     */
    public void setActivityStarted(boolean isStarted) {
        if (isStarted) {
            addOnDrawListener();
        } else {
            removeOnDrawListener();
            setBaseSurface(null);
            setEarlyWakeup(false);
        }
    }

    @Override
    public void setState(STATE toState) {
        stateDepth.setValue(toState.getDepth(mContainer));
        if (toState == mContainer.getBackgroundAppState()) {
            addOnDrawListener();
        }
        // Re-apply even when depth is already 0 so an interrupted All Apps / depth transition
        // cannot leave workspace RenderEffect or surface blur stuck until the next resume.
        if (toState == LauncherState.NORMAL) {
            clearWorkspaceRenderEffects();
            applyDepthAndBlur();
        }
    }

    @Override
    public void setStateWithAnimation(STATE toState, StateAnimationConfig config,
            PendingAnimation animation) {
<<<<<<< HEAD
        if (config.hasAnimationFlag(SKIP_DEPTH_CONTROLLER)
                || mIgnoreStateChangesDuringMultiWindowAnimation) {
            if (toState == LauncherState.NORMAL) {
                clearWorkspaceRenderEffects();
            }
=======
        if (config.hasAnimationFlag(SKIP_DEPTH_CONTROLLER)) {
>>>>>>> aosp/android17-release
            return;
        }

        float toDepth = toState.getDepth(mContainer);
        animation.setFloat(stateDepth, MULTI_PROPERTY_VALUE, toDepth,
                config.getInterpolator(ANIM_DEPTH, LINEAR));
        if (toState == LauncherState.NORMAL) {
            animation.addEndListener(success -> {
                if (mLauncher.getStateManager().getState() == LauncherState.NORMAL) {
                    clearWorkspaceRenderEffects();
                    applyDepthAndBlur();
                }
            });
        }
    }

    /**
     * Clears a stuck workspace icon blur after resume when settled on home.
     */
    public void clearStuckBlurOnResumeIfHome() {
        if (mLauncher.getStateManager().getState() == LauncherState.NORMAL
                && !mLauncher.getStateManager().isInTransition()) {
            clearWorkspaceRenderEffects();
            applyDepthAndBlur();
        }
    }

    @Override
    protected void applyDepthAndBlur() {
        try {
            if (LawnchairQuickstepCompat.ATLEAST_R && mEnableDepth) {
                ensureDependencies();
                super.applyDepthAndBlur();
            } else if (mLauncher.getStateManager().getState() == LauncherState.NORMAL
                    && !mLauncher.getStateManager().isInTransition()) {
                clearWorkspaceRenderEffects();
            }
        } catch (Throwable t) {
            // LC-Ignored
            if (mLauncher.getStateManager().getState() == LauncherState.NORMAL) {
                try {
                    clearWorkspaceRenderEffects();
                } catch (Throwable ignored) {
                    // LC-Ignored
                }
            }
        }
    }

    @Override
    protected void onInvalidSurface() {
        // Lets wait for surface to become valid again
        addOnDrawListener();
    }

    private void addOnDrawListener() {
        mRemoveOnDrawListenerCancelled = true;
        if (mIsOnDrawListenerAdded) {
            return;
        }
        mContainer.getDragLayer().getViewTreeObserver().addOnDrawListener(mOnDrawListener);
        mIsOnDrawListenerAdded = true;
    }

    private void removeOnDrawListener() {
        mRemoveOnDrawListenerCancelled = true;
        if (!mIsOnDrawListenerAdded) {
            return;
        }
        mContainer.getDragLayer().getViewTreeObserver().removeOnDrawListener(mOnDrawListener);
        mIsOnDrawListenerAdded = false;
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "DepthController");
        writer.println(prefix + "\tmMaxBlurRadius=" + mMaxBlurRadius);
        writer.println(prefix + "\tmCrossWindowBlursEnabled=" + mCrossWindowBlursEnabled);
        writer.println(prefix + "\tmBaseSurface=" + mBaseSurface);
        writer.println(prefix + "\tmBaseSurfaceOverride=" + mBaseSurfaceOverride);
        writer.println(prefix + "\tmStateDepth=" + stateDepth.getValue());
        writer.println(prefix + "\tmWidgetDepth=" + widgetDepth.getValue());
        writer.println(prefix + "\tmCurrentBlur=" + mCurrentBlur);
        writer.println(prefix + "\tmInEarlyWakeUp=" + mInEarlyWakeUp);
        writer.println(prefix + "\tmPauseBlurs=" + mPauseBlurs);
        writer.println(prefix + "\tmWaitingOnSurfaceValidity=" + mWaitingOnSurfaceValidity);
    }
}
