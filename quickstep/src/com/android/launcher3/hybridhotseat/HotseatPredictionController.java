/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.hybridhotseat;

<<<<<<< HEAD
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;
import static com.android.launcher3.hybridhotseat.HotseatEduController.getLawnchairSettingsIntent;
import static com.android.launcher3.hybridhotseat.HotseatEduController.getSettingsIntent;
=======
import static com.android.launcher3.LauncherAnimUtils.getScaleProperty;
>>>>>>> aosp/android17-release
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_PREDICTION_PINNED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_RANKED;
import static com.android.launcher3.util.OnboardingPrefs.HOTSEAT_LONGPRESS_TIP_SEEN;

import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Intent;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import app.lawnchair.preferences2.PreferenceManager2;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Flags;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.PredictedHotseatContainer;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PredictedContainerInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.HybridHotseatOrganizer;
import com.android.launcher3.views.PredictedAppIcon;
import com.android.launcher3.views.Snackbar;

import app.lawnchair.preferences2.PreferenceCacheExtensionsKt;
import java.io.PrintWriter;

/**
 * Provides prediction ability for the hotseat. Fills gaps in hotseat with predicted items, allows
 * pinning of predicted apps and manages replacement of predicted apps with user drag.
 */
public class HotseatPredictionController implements
        SystemShortcut.Factory<QuickstepLauncher> {

    private final Launcher mLauncher;
    private final HybridHotseatOrganizer mHotseatOrganizer;
    private final Hotseat mHotseat;
<<<<<<< HEAD
    private final Runnable mUpdateFillIfNotLoading = this::updateFillIfNotLoading;

    private List<ItemInfo> mPredictedItems = Collections.emptyList();

    private AnimatorSet mIconRemoveAnimators;
    private int mPauseFlags = 0;

    private final List<OutlineOnPage> mOutlineDrawings = new ArrayList<>();
=======
>>>>>>> aosp/android17-release

    private boolean mEnableHotseatLongPressTipForTesting = true;

    private static final String SETTINGS_ACTION =
            "android.settings.ACTION_CONTENT_SUGGESTIONS_SETTINGS";

    public HotseatPredictionController(QuickstepLauncher launcher) {
        mLauncher = launcher;
        mHotseatOrganizer = new HybridHotseatOrganizer(
                launcher,
                launcher.getWorkspace(),
                launcher.getItemInflater(),
                this::onPredictedItemLongClicked,
                mLauncher::isWorkspaceLoading);
        mHotseat = launcher.getHotseat();
        mLauncher.getDragController().addDragListener(mHotseatOrganizer);
        mLauncher.modelCallbacks.setHybridHotseatOrganizer(mHotseatOrganizer);
    }

    private boolean onPredictedItemLongClicked(View v) {
        if (!ItemLongClickListener.canStartDrag(mLauncher)) return false;
        if (mLauncher.getWorkspace().isSwitchingState()) return false;

        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onWorkspaceItemLongClick");
        if (mEnableHotseatLongPressTipForTesting && !HOTSEAT_LONGPRESS_TIP_SEEN.get(mLauncher)) {
            Snackbar.show(mLauncher, R.string.hotseat_tip_gaps_filled,
                    R.string.hotseat_prediction_settings, null,
                    // LC-Note: Start Lawnchair prediction settings instead of AOSP
                    () -> mLauncher.startActivity(getLawnchairSettingsIntent(mLauncher)));
            LauncherPrefs.get(mLauncher).put(HOTSEAT_LONGPRESS_TIP_SEEN, true);
            mLauncher.getDragLayer().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        }

        // Start the drag
        v.setVisibility(View.INVISIBLE);
        mLauncher.getWorkspace().beginDragShared(v, mHotseatOrganizer, new DragOptions());
        return true;
<<<<<<< HEAD
    };

    public HotseatPredictionController(QuickstepLauncher launcher) {
        mLauncher = launcher;
        mHotseat = launcher.getHotseat();
        mHotSeatItemsCount = mLauncher.getDeviceProfile().numShownHotseatIcons
                * mLauncher.getDeviceProfile().numHotseatRows;
        mLauncher.getDragController().addDragListener(this);

        launcher.addOnDeviceProfileChangeListener(this);
        attachPageListeners();
        mHotseat.getPagedView().setOnDockPageChangeListener(page -> {
            if (mPauseFlags == 0 && !mLauncher.isWorkspaceLoading()) {
                fillGapsWithPrediction(true);
            }
        });
    }

    private void attachPageListeners() {
        for (CellLayout page : mHotseat.getPageLayouts()) {
            page.getShortcutsAndWidgets().setOnHierarchyChangeListener(this);
        }
    }

    @Nullable
    private CellLayout getPageForRank(int rank) {
        return mHotseat.getPageAt(mHotseat.getPageFromOrder(rank));
    }

    @Nullable
    private View getChildAtRank(int rank) {
        CellLayout page = getPageForRank(rank);
        if (page == null) {
            return null;
        }
        return page.getChildAt(
                mHotseat.getCellXFromOrder(rank),
                mHotseat.getCellYFromOrder(rank));
    }

    /** Visible-page slot count (columns × rows). Predictions only fill the current page. */
    private int getVisiblePageStartRank() {
        int page = mHotseat.getPagedView().getNextPage();
        return Math.max(0, page) * mHotSeatItemsCount;
=======
>>>>>>> aosp/android17-release
    }


    /** Enables/disabled the hotseat prediction icon long press edu for testing. */
    @VisibleForTesting
    public void enableHotseatEdu(boolean enable) {
        mEnableHotseatLongPressTipForTesting = enable;
    }

<<<<<<< HEAD
    private void onHotseatHierarchyChanged() {
        if (mPauseFlags == 0 && !mLauncher.isWorkspaceLoading()) {
            // Post update after a single frame to avoid layout within layout
            MAIN_EXECUTOR.getHandler().removeCallbacks(mUpdateFillIfNotLoading);
            MAIN_EXECUTOR.getHandler().post(mUpdateFillIfNotLoading);
        }
    }

    private void updateFillIfNotLoading() {
        if (mPauseFlags == 0 && !mLauncher.isWorkspaceLoading()) {
            fillGapsWithPrediction(true);
        }
    }

    /**
     * Shows appropriate hotseat education based on prediction enabled and migration states.
     */
    public void showEdu() {
        mLauncher.getStateManager().goToState(NORMAL, true, forSuccessCallback(() -> {
            HotseatEduController eduController = new HotseatEduController(mLauncher);
            eduController.setPredictedApps(mPredictedItems.stream()
                    .map(i -> (WorkspaceItemInfo) i)
                    .collect(Collectors.toList()));
            eduController.showEdu();
        }));
    }

    /**
     * Returns if hotseat client has predictions
     */
    public boolean hasPredictions() {
        return !mPredictedItems.isEmpty();
    }

    private void fillGapsWithPrediction() {
        fillGapsWithPrediction(false);
    }

    private void fillGapsWithPrediction(boolean animate) {
        if (mPauseFlags != 0) {
            return;
        }

        int predictionIndex = 0;
        int numViewsAnimated = 0;
        ArrayList<WorkspaceItemInfo> newItems = new ArrayList<>();
        // make sure predicted icon removal and filling predictions don't step on each other
        if (mIconRemoveAnimators != null && mIconRemoveAnimators.isRunning()) {
            mIconRemoveAnimators.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    fillGapsWithPrediction(animate);
                    mIconRemoveAnimators.removeListener(this);
                }
            });
            return;
        }

        mPauseFlags |= FLAG_FILL_IN_PROGRESS;
        int startRank = getVisiblePageStartRank();
        for (int localRank = 0; localRank < mHotSeatItemsCount; localRank++) {
            int rank = startRank + localRank;
            View child = getChildAtRank(rank);

            if (child != null && !isPredictedIcon(child)) {
                continue;
            }
            if (mPredictedItems.size() <= predictionIndex) {
                // Remove predicted apps from the past
                if (isPredictedIcon(child)) {
                    CellLayout page = getPageForRank(rank);
                    if (page != null) {
                        page.removeView(child);
                    }
                }
                continue;
            }
            WorkspaceItemInfo predictedItem =
                    (WorkspaceItemInfo) mPredictedItems.get(predictionIndex++);
            if (isPredictedIcon(child) && child.isEnabled()) {
                PredictedAppIcon icon = (PredictedAppIcon) child;
                if (icon.applyFromWorkspaceItemWithAnimation(predictedItem, numViewsAnimated)) {
                    numViewsAnimated++;
                }
                finishBinding(icon);
            } else {
                newItems.add(predictedItem);
            }
            preparePredictionInfo(predictedItem, rank);
        }
        bindItems(newItems, animate);

        mPauseFlags &= ~FLAG_FILL_IN_PROGRESS;
    }

    private void bindItems(List<WorkspaceItemInfo> itemsToAdd, boolean animate) {
        AnimatorSet animationSet = new AnimatorSet();
        for (WorkspaceItemInfo item : itemsToAdd) {
            View icon = mLauncher.getItemInflater().inflateItem(item, mHotseat);
            mLauncher.getWorkspace().addInScreenFromBind(icon, item);
            finishBinding(icon);
            if (animate) {
                animationSet.play(ObjectAnimator.ofFloat(icon, SCALE_PROPERTY, 0.2f, 1));
            }
        }
        if (animate) {
            animationSet.addListener(
                    forSuccessCallback(this::removeOutlineDrawings));
            animationSet.start();
        } else {
            removeOutlineDrawings();
        }
    }

    private void finishBinding(View view) {
        view.setOnLongClickListener(mPredictionLongClickListener);
        ((CellLayoutLayoutParams) view.getLayoutParams()).canReorder = false;
    }

    private void removeOutlineDrawings() {
        if (mOutlineDrawings.isEmpty()) return;
        for (OutlineOnPage outline : mOutlineDrawings) {
            outline.page.removeDelegatedCellDrawing(outline.drawing);
            outline.page.invalidate();
        }
        mOutlineDrawings.clear();
    }


    /**
     * Unregisters callbacks and frees resources
     */
    public void destroy() {
        mLauncher.removeOnDeviceProfileChangeListener(this);
    }

=======
>>>>>>> aosp/android17-release
    /**
     * Sets or updates the predicted items
     */
    public void setPredictedItems(PredictedContainerInfo items) {
        mHotseatOrganizer.setPredictedItems(items.getContents());
    }

    /**
     * Pins a predicted app icon into place.
     */
    public void pinPrediction(ItemInfo info) {
        PredictedAppIcon icon = (PredictedAppIcon) getChildAtRank(info.rank);
        if (icon == null) {
            return;
        }
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo((WorkspaceItemInfo) info);
        mLauncher.getModelWriter().addItemToDatabase(workspaceItemInfo,
                LauncherSettings.Favorites.CONTAINER_HOTSEAT, workspaceItemInfo.screenId,
                workspaceItemInfo.cellX, workspaceItemInfo.cellY);
        ObjectAnimator.ofFloat(icon, getScaleProperty(), 1, 0.8f, 1).start();
        icon.pin(workspaceItemInfo);
        mLauncher.getStatsLogManager().logger()
                .withItemInfo(workspaceItemInfo)
                .log(LAUNCHER_HOTSEAT_PREDICTION_PINNED);
    }

<<<<<<< HEAD
    private List<PredictedIconOnPage> getPredictedIcons() {
        List<PredictedIconOnPage> icons = new ArrayList<>();
        for (CellLayout page : mHotseat.getPageLayouts()) {
            ViewGroup vg = page.getShortcutsAndWidgets();
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (isPredictedIcon(child)) {
                    icons.add(new PredictedIconOnPage(page, (PredictedAppIcon) child));
                }
            }
        }
        return icons;
    }

    private void removePredictedApps(DropTarget.DragObject dragObject) {
        if (mIconRemoveAnimators != null) {
            mIconRemoveAnimators.end();
        }
        mIconRemoveAnimators = new AnimatorSet();
        removeOutlineDrawings();
        for (PredictedIconOnPage entry : getPredictedIcons()) {
            PredictedAppIcon icon = entry.icon;
            if (!icon.isEnabled()) {
                continue;
            }
            if (dragObject.dragSource == this && icon.equals(dragObject.originalView)) {
                removeIconWithoutNotify(icon);
                continue;
            }
            int rank = ((WorkspaceItemInfo) icon.getTag()).rank;
            mOutlineDrawings.add(new OutlineOnPage(entry.page,
                    new PredictedAppIcon.PredictedIconOutlineDrawing(
                            mHotseat.getCellXFromOrder(rank),
                            mHotseat.getCellYFromOrder(rank), icon)));
            icon.setEnabled(false);
            ObjectAnimator animator = ObjectAnimator.ofFloat(icon, SCALE_PROPERTY, 0);
            animator.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (icon.getParent() != null) {
                        removeIconWithoutNotify(icon);
                    }
                }
            });
            mIconRemoveAnimators.play(animator);
        }
        mIconRemoveAnimators.start();
    }

=======
>>>>>>> aosp/android17-release
    /**
     * Called when an item is pinned using the context menu.
     * This action directly refreshes all predicted apps without animations to ensure the
     * predictions are updated with the newly pinned item.
     */
<<<<<<< HEAD
    private void removeIconWithoutNotify(PredictedAppIcon icon) {
        mPauseFlags |= FLAG_REMOVING_PREDICTED_ICON;
        ViewGroup parent = (ViewGroup) icon.getParent();
        if (parent != null) {
            // Parent is ShortcutAndWidgetContainer; remove via CellLayout.
            ViewGroup cellLayout = (ViewGroup) parent.getParent();
            if (cellLayout instanceof CellLayout) {
                cellLayout.removeView(icon);
            } else {
                parent.removeView(icon);
            }
        }
        mPauseFlags &= ~FLAG_REMOVING_PREDICTED_ICON;
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        removePredictedApps(dragObject);
        if (mOutlineDrawings.isEmpty()) return;
        for (OutlineOnPage outline : mOutlineDrawings) {
            outline.page.addDelegatedCellDrawing(outline.drawing);
            outline.page.invalidate();
        }
        mPauseFlags |= FLAG_DRAG_IN_PROGRESS;
    }

    @Override
    public void onDragEnd() {
        mPauseFlags &= ~FLAG_DRAG_IN_PROGRESS;
        fillGapsWithPrediction(true);
=======
    public void onItemPinnedFromContextMenu() {
        for (PredictedAppIcon icon : mHotseatOrganizer.getPredictedIcons()) {
            mHotseatOrganizer.removeIconWithoutNotify(icon);
        }
        mHotseatOrganizer.fillGapsWithPrediction();
>>>>>>> aosp/android17-release
    }

    @Nullable
    @Override
    public SystemShortcut<QuickstepLauncher> getShortcut(QuickstepLauncher activity,
            ItemInfo itemInfo, View originalView) {
        PreferenceManager2 prefs = PreferenceManager2.getInstance(activity);
        if (PreferenceCacheExtensionsKt.firstCached(prefs.getLockHomeScreen())) {
            return null;
        }
        if (itemInfo.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            return null;
        }
        if (Flags.enablePrivateSpace() && UserCache.getInstance(
                activity.getApplicationContext()).getUserInfo(itemInfo.user).isPrivate()) {
            return null;
        }
        return new PinPrediction(activity, itemInfo, originalView);
    }

<<<<<<< HEAD
    private void preparePredictionInfo(WorkspaceItemInfo itemInfo, int rank) {
        itemInfo.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
        itemInfo.rank = rank;
        itemInfo.cellX = mHotseat.getCellXFromOrder(rank);
        itemInfo.cellY = mHotseat.getCellYFromOrder(rank);
        itemInfo.screenId = rank;
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile profile) {
        this.mHotSeatItemsCount = profile.numShownHotseatIcons * profile.numHotseatRows;
        attachPageListeners();
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean success) {
        //Does nothing
    }

=======
>>>>>>> aosp/android17-release
    /**
     * Logs rank info based on current list of predicted items
     */
    public void logLaunchedAppRankingInfo(@NonNull ItemInfo itemInfo, InstanceId instanceId) {
        ComponentName targetCN = itemInfo.getTargetComponent();
        if (targetCN == null) {
            return;
        }
        int rank = mHotseatOrganizer.getPredictedRank(targetCN, itemInfo.user);
        if (rank < 0) {
            return;
        }

        int cardinality = 0;
<<<<<<< HEAD
        for (PredictedIconOnPage entry : getPredictedIcons()) {
            ItemInfo info = (ItemInfo) entry.icon.getTag();
=======
        for (PredictedAppIcon icon : mHotseatOrganizer.getPredictedIcons()) {
            ItemInfo info = (ItemInfo) icon.getTag();
>>>>>>> aosp/android17-release
            cardinality |= 1 << info.screenId;
        }

        PredictedHotseatContainer.Builder containerBuilder = PredictedHotseatContainer.newBuilder();
        containerBuilder.setCardinality(cardinality);
        if (itemInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            containerBuilder.setIndex(rank);
        }
        mLauncher.getStatsLogManager().logger()
                .withInstanceId(instanceId)
                .withRank(rank)
                .withContainerInfo(ContainerInfo.newBuilder()
                        .setPredictedHotseatContainer(containerBuilder)
                        .build())
                .log(LAUNCHER_HOTSEAT_RANKED);
    }

    /**
     * Called when user completes adding item requiring a config activity to the hotseat
     */
    public void onDeferredDrop(int cellX, int cellY) {
<<<<<<< HEAD
        CellLayout page = mHotseat.getCurrentPageLayout();
        if (page == null) {
            return;
        }
        View child = page.getChildAt(cellX, cellY);
        if (child instanceof PredictedAppIcon) {
            removeIconWithoutNotify((PredictedAppIcon) child);
=======
        View child = mHotseat.getChildAt(cellX, cellY);
        if (child instanceof PredictedAppIcon predictedAppIcon) {
            mHotseatOrganizer.removeIconWithoutNotify(predictedAppIcon);
>>>>>>> aosp/android17-release
        }
    }

    private class PinPrediction extends SystemShortcut<QuickstepLauncher> {

        private PinPrediction(QuickstepLauncher target, ItemInfo itemInfo, View originalView) {
            super(getDrawableId(), R.string.pin_prediction, target,
                    itemInfo, originalView);
        }

        public static int getDrawableId() {
            return R.drawable.keep_24px;
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView();
            pinPrediction(mItemInfo);
        }
    }

    public void dump(String prefix, PrintWriter writer) {
        mHotseatOrganizer.dump(prefix, writer);
    }

    static Intent getSettingsIntent() {
        return new Intent(SETTINGS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static final class PredictedIconOnPage {
        final CellLayout page;
        final PredictedAppIcon icon;

        PredictedIconOnPage(CellLayout page, PredictedAppIcon icon) {
            this.page = page;
            this.icon = icon;
        }
    }

    private static final class OutlineOnPage {
        final CellLayout page;
        final PredictedAppIcon.PredictedIconOutlineDrawing drawing;

        OutlineOnPage(CellLayout page, PredictedAppIcon.PredictedIconOutlineDrawing drawing) {
            this.page = page;
            this.drawing = drawing;
        }
    }
}
