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
package com.android.quickstep.util;

import static android.os.Trace.TRACE_TAG_APP;

<<<<<<< HEAD
import static com.android.launcher3.Flags.enableOverviewBackgroundWallpaperBlur;
import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;

import android.app.WallpaperManager;
import android.graphics.RenderEffect;
import android.graphics.Shader;
=======
import android.app.WallpaperManager;
import android.content.Context;
>>>>>>> aosp/android17-release
import android.gui.EarlyWakeupInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Trace;
import android.util.FloatProperty;
import android.util.Log;
import android.view.AttachedSurfaceControl;
import android.view.CrossWindowBlurListeners;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.statehandlers.BaseDepthController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.util.ListenableRef;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.systemui.shared.system.BlurUtils;

/**
<<<<<<< HEAD
 * Utility class for applying depth effect
 */
public class BaseDepthControllerImpl implements BaseDepthController {
    public static final float DEPTH_0_PERCENT = 0f;
    public static final float DEPTH_60_PERCENT = 0.6f;
    public static final float DEPTH_70_PERCENT = 0.7f;

    // Includes both Wallpaper Zoom and Blur
    private static final FloatProperty<BaseDepthControllerImpl> DEPTH =
            new FloatProperty<BaseDepthControllerImpl>("depth") {
=======
 * Utility class for applying depth effect.
 * @param <STATE> state associated with the container.
 * @param <CONTAINER> the StatefulContainer.
 */
public class BaseDepthControllerImpl<
        STATE extends BaseState<STATE>,
        CONTAINER extends Context & StatefulContainer<STATE>> implements BaseDepthController {

    // Includes both Wallpaper Zoom and Blur
    private static final FloatProperty<BaseDepthControllerImpl<?, ?>> DEPTH =
            new FloatProperty<>("depth") {
>>>>>>> aosp/android17-release
                @Override
                public void setValue(BaseDepthControllerImpl depthController, float depth) {
                    depthController.setDepth(depth);
                }

                @Override
                public Float get(BaseDepthControllerImpl depthController) {
                    return depthController.mDepth;
                }
            };

    private static final int DEPTH_INDEX_STATE_TRANSITION = 0;
    private static final int DEPTH_INDEX_WIDGET = 1;
    private static final int DEPTH_INDEX_COUNT = 2;

    private static final int ZOOM_INDEX_FOLDER = 0;
    private static final int ZOOM_INDEX_COUNT = 1;

    // b/291401432
<<<<<<< HEAD
    private static final String TAG = "BaseDepthController";

    protected final QuickstepLauncher mLauncher;
=======
    protected static final String TAG = "BaseDepthController";
    private static final boolean DEBUG = false;

    protected final CONTAINER mContainer;
>>>>>>> aosp/android17-release
    /** Property to set the depth for state transition. */
    public final MultiProperty stateDepth;
    /** Property to set the depth for widget picker. */
    public final MultiProperty widgetDepth;
    /** Property to set just the wallpaper zoom for folder expansion. */
    public final MultiProperty folderZoom;

    /**
     * Blur radius when completely zoomed out, in pixels.
     */
    protected final int mMaxBlurRadius;
    protected final WallpaperManager mWallpaperManager;
    protected boolean mCrossWindowBlursEnabled;

    /**
     * Ratio from 0 to 1, where 0 is fully zoomed out, and 1 is zoomed in.
     *
     * @see android.service.wallpaper.WallpaperService.Engine#onZoomChanged(float)
     */
    private float mDepth;
    // Used just for animating wallpaper zoom without affecting blur.
    private final AnimatedFloat mWallpaperZoomOnly = new AnimatedFloat(this::applyDepthAndBlur);

    protected SurfaceControl mBaseSurface;
<<<<<<< HEAD

    protected SurfaceControl mBaseSurfaceOverride;

    // Hints that there is potentially content behind Launcher and that we shouldn't optimize by
    // marking the launcher surface as opaque.  Only used in certain Launcher states.
    private boolean mHasContentBehindLauncher;

    /** Pause blur but allow transparent, can be used when launch something behind the Launcher. */
=======
    protected SurfaceControl mBaseSurfaceOverride;
    // May be temporarily null while the container is being created, in which case all blur
    // requests will be applied immediately rather than synced to the RenderThread. This shouldn't
    // really happen in practice since we won't apply blur until the container is interactive.
    @Nullable protected SurfaceTransactionApplier mSurfaceTransactionApplier;

    // Hints that there is potentially content behind container and that we shouldn't optimize by
    // marking the container surface as opaque.  Only used in certain container states.
    private boolean mHasContentBehindContainer;

    /** Pause blur but allow transparent, can be used when launch something behind the container. */
>>>>>>> aosp/android17-release
    protected boolean mPauseBlurs;

    /**
     * Last blur value, in pixels, that was applied.
     */
    protected int mCurrentBlur;
    /**
     * If we requested early wake-up offsets to SurfaceFlinger.
     */
    protected boolean mInEarlyWakeUp;

    protected boolean mWaitingOnSurfaceValidity;

    private SurfaceControl mBlurSurface = null;
    /**
     * Info for early wakeup requests to SurfaceFlinger.
     */
<<<<<<< HEAD
//    private EarlyWakeupInfo mEarlyWakeupInfo = new EarlyWakeupInfo();

    public BaseDepthControllerImpl(QuickstepLauncher activity) {
        mLauncher = activity;
        if (Flags.allAppsBlur() || enableOverviewBackgroundWallpaperBlur()) {
            if (Utilities.ATLEAST_S) {
                mCrossWindowBlursEnabled =
                        CrossWindowBlurListeners.getInstance().isCrossWindowBlurEnabled();
            } else {
                mCrossWindowBlursEnabled = false;
            }
            mMaxBlurRadius = activity.getResources().getDimensionPixelSize(
                    R.dimen.max_depth_blur_radius_enhanced);
        } else {
            mMaxBlurRadius = activity.getResources().getInteger(R.integer.max_depth_blur_radius);
        }
        mWallpaperManager = activity.getSystemService(WallpaperManager.class);

        MultiPropertyFactory<BaseDepthControllerImpl> depthProperty =
=======
    private final EarlyWakeupInfo mEarlyWakeupInfo = new EarlyWakeupInfo();

    public BaseDepthControllerImpl(CONTAINER container, ListenableRef<Boolean> blurState) {
        mContainer = container;
        mCrossWindowBlursEnabled = blurState.getValue();
        mMaxBlurRadius = container.getResources().getDimensionPixelSize(
                R.dimen.max_depth_blur_radius_enhanced);
        mWallpaperManager = container.getSystemService(WallpaperManager.class);

        MultiPropertyFactory<BaseDepthControllerImpl<?, ?>> depthProperty =
>>>>>>> aosp/android17-release
                new MultiPropertyFactory<>(this, DEPTH, DEPTH_INDEX_COUNT, Float::max);
        stateDepth = depthProperty.get(DEPTH_INDEX_STATE_TRANSITION);
        widgetDepth = depthProperty.get(DEPTH_INDEX_WIDGET);

        if (Flags.enableExpressiveFolderExpansion()) {
            MultiPropertyFactory<AnimatedFloat> folderZoomFactory =
                    new MultiPropertyFactory<>(mWallpaperZoomOnly, AnimatedFloat.VALUE,
                            ZOOM_INDEX_COUNT, Float::max);
            folderZoom = folderZoomFactory.get(ZOOM_INDEX_FOLDER);
        } else {
            folderZoom = null;
        }

<<<<<<< HEAD
//        mEarlyWakeupInfo.token = new Binder();
//        mEarlyWakeupInfo.trace = BaseDepthControllerImpl.class.getName();
=======
        mEarlyWakeupInfo.token = new Binder();
        mEarlyWakeupInfo.trace = BaseDepthControllerImpl.class.getName();

        mContainer.closeOnDestroy(blurState.forEach(mContainer.getUiExecutor(), blurEnabled -> {
            if (mCrossWindowBlursEnabled == blurEnabled) {
                return null;
            }
            Log.d(TAG, "onBlurStateChanged - blurEnabled: " + blurEnabled);
            mCrossWindowBlursEnabled = blurEnabled;
            applyDepthAndBlur();
            return null;
        }));
>>>>>>> aosp/android17-release
    }

    @Override
    public MultiProperty getStateDepth() {
        return stateDepth;
    }

    @Override
    public MultiProperty getWidgetDepth() {
        return widgetDepth;
    }

    @Override
    public MultiProperty getFolderZoom() {
        return folderZoom;
    }

    /**
<<<<<<< HEAD
     * Returns if cross window blurs are enabled. In other words, whether launcher should use blurs
     * style UI or fallback style UI.
     */
    public boolean isCrossWindowBlursEnabled() {
        return mCrossWindowBlursEnabled;
    }

    protected void setCrossWindowBlursEnabled(boolean isEnabled) {
        if (mCrossWindowBlursEnabled == isEnabled) {
            return;
        }
        mCrossWindowBlursEnabled = isEnabled;
        mLauncher.updateBlurStyle();
        applyDepthAndBlur();
    }

    public void setHasContentBehindLauncher(boolean hasContentBehindLauncher) {
        mHasContentBehindLauncher = hasContentBehindLauncher;
=======
     * Sets the applier to use for syncing surface transactions to the RenderThread.
     *
     * @param surfaceTransactionApplier created in container to be in sync with the other Surface
     *                                  transactions e.g. in RecentsView.
     */
    public void setSurfaceTransactionApplier(
            @Nullable SurfaceTransactionApplier surfaceTransactionApplier) {
        mSurfaceTransactionApplier = surfaceTransactionApplier;
    }

    public void setHasContentBehindContainer(boolean hasContentBehindContainer) {
        mHasContentBehindContainer = hasContentBehindContainer;
>>>>>>> aosp/android17-release
    }

    public void pauseBlursOnWindows(boolean pause) {
        if (mPauseBlurs == pause) {
            return;
        }
        mPauseBlurs = pause;
        applyDepthAndBlur();
    }

<<<<<<< HEAD
=======
    protected boolean shouldBlur() {
        boolean hasOpaqueBg = mContainer.getScrimView().isFullyOpaque();
        return mCrossWindowBlursEnabled && !hasOpaqueBg && !mPauseBlurs;
    }

>>>>>>> aosp/android17-release
    protected void onInvalidSurface() { }

    protected void applyDepthAndBlur() {
        applyDepthAndBlur(null, /* applyImmediately */ false, /* skipSimilarBlur */ true);
    }

    /**
<<<<<<< HEAD
     * Applies depth and blur to the launcher.
     *
     * @param transaction      optional Surface to apply to the blur to.
     * @param applyImmediately whether to apply the blur immediately or defer to the next frame.
     */
    protected void applyDepthAndBlur(SurfaceControl.Transaction transaction,
            boolean applyImmediately, boolean skipSimilarBlur) {
        try (transaction) {
            applyDepthAndBlurInternal(transaction, applyImmediately, skipSimilarBlur);
        }
    }

    private void applyDepthAndBlurInternal(SurfaceControl.Transaction transaction,
            boolean applyImmediately, boolean skipSimilarBlur) {
        float depth = mDepth;
        float wallpaperZoom = depth;
        if (Flags.enableExpressiveFolderExpansion()) {
            wallpaperZoom = Math.max(wallpaperZoom, mWallpaperZoomOnly.value);
        }
        IBinder windowToken = mLauncher.getRootView().getWindowToken();
        if (windowToken != null && Utilities.ATLEAST_R) {
            if (enableScalingRevealHomeAnimation()) {
                mWallpaperManager.setWallpaperZoomOut(windowToken, wallpaperZoom);
            } else {
                // The API's full zoom-out is three times larger than the zoom-out we apply
                // to the
                // icons. To keep the two consistent throughout the animation while keeping
                // Launcher's concept of full depth unchanged, we divide the depth by 3 here.
                mWallpaperManager.setWallpaperZoomOut(windowToken, wallpaperZoom / 3);
            }
        }

        boolean hasOpaqueBg = mLauncher.getScrimView().isFullyOpaque();
        boolean isSurfaceOpaque = !mHasContentBehindLauncher && hasOpaqueBg && !mPauseBlurs;

        float blurAmount;
        if (enableScalingRevealHomeAnimation()) {
            blurAmount = mapDepthToBlur(depth);
        } else {
            blurAmount = depth;
        }

        int previousBlur = mCurrentBlur;
        int newBlur = BlurUtils.supportsBlursOnWindows() && mCrossWindowBlursEnabled
                && !hasOpaqueBg && !mPauseBlurs
                ? (int) (blurAmount * mMaxBlurRadius) : 0;
        int delta = Math.abs(newBlur - previousBlur);
        boolean skipUpdate = skipSimilarBlur && delta < Utilities.dpToPx(1) && newBlur != 0
                && previousBlur != 0 && blurAmount != 1f;

        // Always keep mCurrentBlur and workspace RenderEffects in sync with depth, even when the
        // SurfaceControl is temporarily unavailable. Otherwise setDepth(0) can early-return later
        // (same depth) and leave workspace/hotseat stuck blurred until the activity restarts.
        if (!skipUpdate) {
            mCurrentBlur = newBlur;
            blurWorkspaceDepthTargets();
=======
     * Applies depth and blur to the container.
     *
     * @param surfaceTransaction optional SurfaceTransaction to apply the blur to.
     * @param applyImmediately whether to apply the blur immediately or defer to the next frame.
     * @param skipSimilarBlur whether to skip applying blur if the change is minimal.
     */
    private void applyDepthAndBlur(@Nullable SurfaceTransaction surfaceTransaction,
            boolean applyImmediately, boolean skipSimilarBlur) {

        IBinder windowToken = mContainer.getRootView().getWindowToken();
        float wallpaperZoom = mDepth;
        if (Flags.enableExpressiveFolderExpansion()) {
            wallpaperZoom = Math.max(wallpaperZoom, mWallpaperZoomOnly.value);
        }

        if (windowToken != null) {
            mWallpaperManager.setWallpaperZoomOut(windowToken, wallpaperZoom);
>>>>>>> aosp/android17-release
        }

        if (!BlurUtils.supportsBlursOnWindows()) {
            return;
        }
        if (mBaseSurface == null) {
            Log.d(TAG, "mSurface is null and mCurrentBlur is: " + mCurrentBlur);
            return;
        }
        if (!mBaseSurface.isValid()) {
            Log.d(TAG, "mSurface is not valid");
            mWaitingOnSurfaceValidity = true;
            onInvalidSurface();
            return;
        }
        mWaitingOnSurfaceValidity = false;
<<<<<<< HEAD
        SurfaceControl blurSurface =
                enableOverviewBackgroundWallpaperBlur() && mBlurSurface != null ? mBlurSurface
                        : mBaseSurface;

        if (skipUpdate) {
            Log.d(TAG, "Skipping small blur delta. newBlur: " + newBlur + " previousBlur: "
                    + previousBlur + " delta: " + delta + " surface: " + blurSurface);
            return;
        }

        Log.v(TAG, "Applying blur: " + mCurrentBlur + " to " + blurSurface);

        final SurfaceControl.Transaction finalTransaction =
                transaction == null ? createTransaction() : transaction;

        // LC: Fix blur effect on Android 12.1/12.0
        try (finalTransaction) {
            if (blurSurface == null || !blurSurface.isValid()) {
                return;
            }
            finalTransaction.setBackgroundBlurRadius(blurSurface, mCurrentBlur)
                    .setOpaque(blurSurface, isSurfaceOpaque);

            boolean wantsEarlyWakeUp = blurAmount > 0 && blurAmount < 1;
            if (wantsEarlyWakeUp && !mInEarlyWakeUp) {
                try {
                    setEarlyWakeup(finalTransaction, true);
                } catch (NoSuchMethodError e) {
                    // LC-Ignored: wtf?
                }
            } else if (!wantsEarlyWakeUp && mInEarlyWakeUp) {
                try {
                    setEarlyWakeup(finalTransaction, false);
                } catch (NoSuchMethodError e) {
                    // LC-Ignored: wtf?
                }
            }

            // LC: Always apply immediately.
            finalTransaction.apply();
        }
    }

=======
        boolean hasOpaqueBg = mContainer.getScrimView().isFullyOpaque();
        boolean isSurfaceOpaque = !mHasContentBehindContainer && hasOpaqueBg && !mPauseBlurs;
        // Only use depth for blur, not wallpaper zoom amount in case only zoom is applied.
        float blurAmount = mapDepthToBlur(mDepth);
        SurfaceControl blurSurface = mBlurSurface != null ? mBlurSurface : mBaseSurface;

        int previousBlur = mCurrentBlur;
        int newBlur = shouldBlur() ? (int) (blurAmount * mMaxBlurRadius) : 0;
        int delta = Math.abs(newBlur - previousBlur);
        if (skipSimilarBlur && delta < Utilities.dpToPx(1) && newBlur != 0 && previousBlur != 0
                && blurAmount != 1f) {
            if (DEBUG) {
                Log.v(TAG, "Skipping small blur delta. newBlur: " + newBlur + " previousBlur: "
                        + previousBlur + " delta: " + delta + " surface: " + blurSurface);
            }
            return;
        }
        mCurrentBlur = newBlur;
        Log.v(TAG, "Applying blur: " + mCurrentBlur + " to " + blurSurface + " applyImmediately: "
                + applyImmediately);

        if (surfaceTransaction == null) {
            surfaceTransaction = new SurfaceTransaction();
        }

        surfaceTransaction.forSurface(blurSurface)
                .setBackgroundBlurRadius(mCurrentBlur)
                .setOpaque(isSurfaceOpaque);
        // Set early wake-up flags when we know we're executing an expensive operation, this way
        // SurfaceFlinger will adjust its internal offsets to avoid jank.
        boolean wantsEarlyWakeUp = blurAmount > 0 && blurAmount < 1;
        if (wantsEarlyWakeUp && !mInEarlyWakeUp) {
            setEarlyWakeup(surfaceTransaction.getTransaction(), true);
        } else if (!wantsEarlyWakeUp && mInEarlyWakeUp) {
            setEarlyWakeup(surfaceTransaction.getTransaction(), false);
        }

        if (applyImmediately || mSurfaceTransactionApplier == null) {
            Log.d(TAG, "Applying blur immediately, mSurfaceTransactionApplier is null? "
                    + (mSurfaceTransactionApplier == null));
            surfaceTransaction.getTransaction().apply();
        } else {
            mSurfaceTransactionApplier.scheduleApply(surfaceTransaction);
        }

        onDepthAndBlurApplied();
    }

    protected void onDepthAndBlurApplied() {}

>>>>>>> aosp/android17-release
    /**
     * Sets the early wakeup state.
     *
     * @param inEarlyWakeUp whether SurfaceFlinger's early wakeup timing should be active.
     */
    public void setEarlyWakeup(boolean inEarlyWakeUp) {
        if (mInEarlyWakeUp == inEarlyWakeUp) {
            return;
        }
<<<<<<< HEAD
        try (SurfaceControl.Transaction transaction = createTransaction()) {
=======
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
>>>>>>> aosp/android17-release
            setEarlyWakeup(transaction, inEarlyWakeUp);
            transaction.apply();
        }
    }

    /**
     * Sets the early wakeup state.
     *
     * @param transaction transaction to apply to.
     * @param start whether to start or end the early wakeup.
     */
    protected void setEarlyWakeup(@NonNull SurfaceControl.Transaction transaction, boolean start) {
        if (mInEarlyWakeUp == start) {
            return;
        }
        Log.d(TAG, "setEarlyWakeup: " + start);
        if (start) {
<<<<<<< HEAD
//            Trace.instantForTrack(TRACE_TAG_APP, TAG, "notifyRendererForGpuLoadUp");
//            mLauncher.getRootView().getViewRootImpl().notifyRendererForGpuLoadUp("applyBlur");
//            transaction.setEarlyWakeupStart();
        } else {
//            transaction.setEarlyWakeupEnd();
=======
            Trace.instantForTrack(TRACE_TAG_APP, TAG, "notifyRendererForGpuLoadUp");
            mContainer.getRootView().getViewRootImpl().notifyRendererForGpuLoadUp("applyBlur");
            mContainer.getRootView().getViewRootImpl().notifyRendererOfExpensiveFrame("applyBlur");
            transaction.setEarlyWakeupStart(mEarlyWakeupInfo);
        } else {
            transaction.setEarlyWakeupEnd(mEarlyWakeupInfo);
>>>>>>> aosp/android17-release
        }
        mInEarlyWakeUp = start;
    }

<<<<<<< HEAD
    /** @return {@code true} if the workspace should be blurred. */
    @VisibleForTesting
    public boolean blurWorkspaceDepthTargets() {
        // RenderEffect / createBlurEffect require API 31+.
        if (!Utilities.ATLEAST_S) {
            return false;
        }
        StateManager<LauncherState, Launcher> stateManager = mLauncher.getStateManager();
        LauncherState settledState = stateManager.getState();

        // Stuck-blur bug: AllAppsState.shouldBlurWorkspace(NORMAL) is true during close, and a
        // skipped/interrupted apply can leave RenderEffect on workspace/hotseat icons after home
        // is settled. Always clear once we are on NORMAL with no active transition, or depth is 0.
        if (!Flags.allAppsBlur()
                || mDepth <= 0f
                || mCurrentBlur <= 0
                || (settledState == LauncherState.NORMAL && !stateManager.isInTransition())) {
            clearWorkspaceRenderEffects();
            return false;
        }

        LauncherState targetState = stateManager.getTargetState() != null
                ? stateManager.getTargetState() : settledState;
        // Only blur workspace if the current state wants to blur based on the target state.
        if (!stateManager.getCurrentStableState().shouldBlurWorkspace(targetState)) {
            clearWorkspaceRenderEffects();
            return false;
        }

        RenderEffect blurEffect = RenderEffect.createBlurEffect(
                mCurrentBlur, mCurrentBlur, Shader.TileMode.DECAL);
        mLauncher.getDepthBlurTargets().forEach(target -> target.setRenderEffect(blurEffect));
        return true;
    }

    /**
     * Clears {@link RenderEffect} from workspace/hotseat. Public so callers can drop a stuck
     * icon blur without going through SurfaceControl apply.
     */
    public void clearWorkspaceRenderEffects() {
        if (!Utilities.ATLEAST_S) {
            return;
        }
        mLauncher.getDepthBlurTargets().forEach(target -> {
            target.setRenderEffect(null);
            target.invalidate();
        });
    }

    private void setDepth(float depth) {
        depth = Utilities.boundToRange(depth, 0, 1);
        // Depth of the Launcher state we are in or transitioning to.
        float targetStateDepth = mLauncher.getStateManager().getState().getDepth(mLauncher);
=======
    private void setDepth(float depth) {
        depth = Utilities.boundToRange(depth, 0, 1);
        // Depth of the container state we are in or transitioning to.
        float targetStateDepth = mContainer.getStateManager().getState().getDepth(mContainer);
>>>>>>> aosp/android17-release

        float depthF;
        if (depth == targetStateDepth) {
            // Always apply the target state depth.
            depthF = depth;
        } else {
            // Round out the depth to dedupe frequent, non-perceptable updates
            int depthI = (int) (depth * 256);
            depthF = depthI / 256f;
        }
        if (Float.compare(mDepth, depthF) == 0) {
<<<<<<< HEAD
            // Depth is unchanged, but a previous apply may have returned early (invalid surface)
            // leaving blur stuck. Force a re-apply when clearing.
            if (depthF == 0f && mCurrentBlur != 0) {
                applyDepthAndBlur();
            }
=======
>>>>>>> aosp/android17-release
            return;
        }
        mDepth = depthF;
        applyDepthAndBlur();
    }

    /**
     * Sets the lowest surface that should not be blurred.
     * <p>
     * Blur is applied to below {@link #mBaseSurfaceOverride}. When set to {@code null}, blur is
     * applied to below {@link #mBaseSurface}.
     * </p>
     */
    public void setBaseSurfaceOverride(@Nullable SurfaceControl baseSurfaceOverride,
            boolean applyOnDraw) {
        if (mBaseSurfaceOverride != baseSurfaceOverride) {
            boolean applyImmediately = mBaseSurfaceOverride != null && baseSurfaceOverride == null
                    && !applyOnDraw;
            mBaseSurfaceOverride = baseSurfaceOverride;
            Log.d(TAG, "setBaseSurfaceOverride: applying blur behind leash " + baseSurfaceOverride);
<<<<<<< HEAD
            SurfaceControl.Transaction transaction = setupBlurSurface();
=======
            SurfaceTransaction transaction = setupBlurSurface();
>>>>>>> aosp/android17-release
            applyDepthAndBlur(transaction, applyImmediately, /* skipSimilarBlur */ false);
        }
    }

<<<<<<< HEAD
    private @Nullable SurfaceControl.Transaction setupBlurSurface() {
        SurfaceControl.Transaction transaction = null;
        if (mBaseSurface != null && mBaseSurfaceOverride != null) {
            transaction = createTransaction().setBackgroundBlurRadius(mBaseSurface, 0)
                    .setOpaque(mBaseSurface, false);
=======
    private @Nullable SurfaceTransaction setupBlurSurface() {
        SurfaceTransaction surfaceTransaction = null;

        if (mBaseSurface != null && mBaseSurfaceOverride != null) {
            surfaceTransaction = new SurfaceTransaction();
            surfaceTransaction.forSurface(mBaseSurface).setBackgroundBlurRadius(0).setOpaque(false);
>>>>>>> aosp/android17-release
            if (mBlurSurface == null) {
                mBlurSurface = new SurfaceControl.Builder()
                        .setName("Overview Blur")
                        .setHidden(false)
                        .build();
                Log.d(TAG,
                        "setupBlurSurface: creating Overview Blur surface " + mBlurSurface);
<<<<<<< HEAD
                transaction.reparent(mBlurSurface, mBaseSurface);
                Log.d(TAG,
                        "setupBlurSurface: reparenting " + mBlurSurface + " to " + mBaseSurface);
            }
            transaction.setRelativeLayer(mBlurSurface, mBaseSurfaceOverride, -1);
            Log.d(TAG, "setupBlurSurface: relayering to leash " + mBaseSurfaceOverride);
        } else if (mBlurSurface != null) {
            Log.d(TAG, "setupBlurSurface: removing blur surface " + mBlurSurface);
            transaction = createTransaction().remove(mBlurSurface);
            mBlurSurface = null;
        }
        return transaction;
=======
                surfaceTransaction.forSurface(mBlurSurface).reparent(mBaseSurface);
                Log.d(TAG, "setupBlurSurface: reparenting " + mBlurSurface + " to " + mBaseSurface);
            }
            surfaceTransaction.forSurface(mBlurSurface).setRelativeLayer(mBaseSurfaceOverride, -1);
            Log.d(TAG, "setupBlurSurface: relayering to leash " + mBaseSurfaceOverride);
        } else if (mBlurSurface != null) {
            Log.d(TAG, "setupBlurSurface: removing blur surface " + mBlurSurface);
            surfaceTransaction = new SurfaceTransaction();
            surfaceTransaction.forSurface(mBlurSurface).setRemove();
            mBlurSurface = null;
        }
        return surfaceTransaction;
>>>>>>> aosp/android17-release
    }

    /**
     * Sets the specified app target surface to apply the blur to.
     */
    protected void setBaseSurface(SurfaceControl baseSurface) {
        if (mBaseSurface != baseSurface || mWaitingOnSurfaceValidity) {
            mBaseSurface = baseSurface;
            Log.d(TAG, "setSurface:\n\tmWaitingOnSurfaceValidity: " + mWaitingOnSurfaceValidity
                    + "\n\tmBaseSurface: " + mBaseSurface);
<<<<<<< HEAD
            SurfaceControl.Transaction transaction = null;
            if (enableOverviewBackgroundWallpaperBlur()) {
                transaction = setupBlurSurface();
            }
=======
            SurfaceTransaction transaction = setupBlurSurface();
>>>>>>> aosp/android17-release
            applyDepthAndBlur(transaction, /* applyImmediately */ false,
                    /* skipSimilarBlur */ false);
        }
    }

    /**
     * Maps depth values to blur amounts as a percentage of the max blur.
     * The blur percentage grows linearly with depth, and maxes out at 30% depth.
     */
    private static float mapDepthToBlur(float depth) {
        return Interpolators.clampToProgress(depth, 0, 0.3f);
    }
<<<<<<< HEAD
    private SurfaceControl.Transaction createTransaction() {
        return new SurfaceControl.Transaction();
    }
}
=======
}
>>>>>>> aosp/android17-release
