/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.model;

import static com.android.launcher3.AbstractFloatingView.TYPE_SNACKBAR;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.os.UserHandle;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.UndoDeleteController;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.tasks.AddWorkspaceItemsTask;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.PersistedItemArray;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.views.ActivityContext;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Class to maintain a queue of pending items to be added to the workspace.
 */
@LauncherAppSingleton
public class ItemInstallQueue {

    public static final int FLAG_ACTIVITY_PAUSED = 1;
    public static final int FLAG_LOADER_RUNNING = 2;
    public static final int FLAG_DRAG_AND_DROP = 4;

    // The set of shortcuts that are pending install
    private static final String APPS_PENDING_INSTALL = "apps_to_install";

    public static final int NEW_SHORTCUT_BOUNCE_DURATION = 450;
    public static final int NEW_SHORTCUT_STAGGER_DELAY = 85;

    public static DaggerSingletonObject<ItemInstallQueue> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getItemInstallQueue);
    private final PersistedItemArray<SerializedItemItem> mStorage =
            new PersistedItemArray<>(APPS_PENDING_INSTALL);

    private final Context mContext;
    private final WorkspaceItemSerializer mPendingItemParser;
    private final Provider<WorkspaceItemSpaceFinder> mSpaceFinderProvider;
    private final Provider<LauncherModel> mModelProvider;

    // Determines whether to defer installing shortcuts immediately until
    // processAllPendingInstalls() is called.
    private int mInstallQueueDisabledFlags = 0;

    // Only accessed on worker thread
    private List<SerializedItemItem> mItems;

    private WeakReference<ActivityContext> mIconUISurface = new WeakReference<>(null);

    @Inject
    public ItemInstallQueue(@ApplicationContext Context context,
            Provider<WorkspaceItemSpaceFinder> spaceFinderProvider,
            WorkspaceItemSerializer pendingItemParser,
            Provider<LauncherModel> modelProvider) {
        mContext = context;
        mSpaceFinderProvider = spaceFinderProvider;
        mPendingItemParser = pendingItemParser;
        mModelProvider = modelProvider;
    }

    /**
     * Sets the UI surface responsible for drawing the icons. The queue will only be pushed if
     * there is a valid UI surface available.
     */
    public void setIconUISurface(ActivityContext context) {
        mIconUISurface = new WeakReference<>(context);
    }

    /** Queues a pending item to ths install queue */
    @AnyThread
    public void queueItem(SerializedItemItem info) {
        // Queue the item up for adding if launcher has not loaded properly yet
        MODEL_EXECUTOR.post(() -> {
            ensureQueueLoaded();
            if (!mItems.contains(info)) {
                mItems.add(info);
                mStorage.write(mContext, mItems);
            }
        });
        flushInstallQueue();
    }

    @WorkerThread
    private void ensureQueueLoaded() {
        Preconditions.assertWorkerThread();
        if (mItems == null) {
            mItems = mStorage.read(mContext, SerializedItemItem::new);
        }
    }

    @WorkerThread
    private void flushQueueInBackground() {
        ensureQueueLoaded();
        if (mItems.isEmpty()) return;

        List<Supplier<ItemInfo>> itemSuppliers = new ArrayList<>();
        mItems.forEach(it -> itemSuppliers.add(() -> mPendingItemParser.decode(it)));
        MAIN_EXECUTOR.execute(() -> {
            ActivityContext uiSurface = mIconUISurface.get();
            // Launcher not loaded
            if (uiSurface == null) return;

            // If there's an undo snack bar, force it to complete to ensure empty screens are
            // removed before trying to add new items.
            UndoDeleteController undoDeleteController = uiSurface.getUndoDeleteController();
            if (undoDeleteController != null) {
                undoDeleteController.commit();
            }
            AbstractFloatingView.closeOpenViews(uiSurface, true, TYPE_SNACKBAR);

            MODEL_EXECUTOR.execute(() -> {
                mItems.clear();
                mStorage.getFile(mContext).delete();
                mModelProvider.get().enqueueModelUpdateTask(
                        new AddWorkspaceItemsTask(itemSuppliers, mSpaceFinderProvider.get()));
            });
        });
    }

    /**
     * Removes previously added items from the queue.
     */
    @WorkerThread
    public void removeFromInstallQueue(Set<String> packageNames, UserHandle user) {
        if (packageNames.isEmpty()) {
            return;
        }
        ensureQueueLoaded();
        if (mItems.removeIf(item ->
                item.user.equals(user) && packageNames.contains(item.getTargetPackage()))) {
            mStorage.write(mContext, mItems);
        }
    }

    /**
     * Returns a stream of all pending shortcuts in the queue
     */
    @WorkerThread
    public Stream<ShortcutKey> getPendingShortcuts(UserHandle user) {
        ensureQueueLoaded();
        return mItems.stream()
                .filter(item -> item.itemType == ITEM_TYPE_DEEP_SHORTCUT && user.equals(item.user))
                .map(item -> ShortcutKey.fromIntent(item.getIntent(), user));
    }

    /**
     * Pauses the push-to-model flow until unpaused. All items are held in the queue and
     * not added to the model.
     */
    public void pauseModelPush(int flag) {
        mInstallQueueDisabledFlags |= flag;
    }

    /**
     * Adds all the queue items to the model if the use is completely resumed.
     */
    public void resumeModelPush(int flag) {
        mInstallQueueDisabledFlags &= ~flag;
        flushInstallQueue();
    }

    private void flushInstallQueue() {
        if (mInstallQueueDisabledFlags != 0) {
            return;
        }
        MODEL_EXECUTOR.post(this::flushQueueInBackground);
    }
<<<<<<< HEAD

    private static class PendingInstallShortcutInfo extends ItemInfo {

        final Intent intent;

        @Nullable ShortcutInfo shortcutInfo;
        @Nullable AppWidgetProviderInfo providerInfo;

        /**
         * Initializes a PendingInstallShortcutInfo to represent a pending launcher target.
         */
        public PendingInstallShortcutInfo(String packageName, UserHandle userHandle) {
            itemType = Favorites.ITEM_TYPE_APPLICATION;
            intent = new Intent().setPackage(packageName);
            user = userHandle;
        }

        /**
         * Initializes a PendingInstallShortcutInfo to represent a deep shortcut.
         */
        public PendingInstallShortcutInfo(ShortcutInfo info) {
            itemType = Favorites.ITEM_TYPE_DEEP_SHORTCUT;
            intent = ShortcutKey.makeIntent(info);
            user = info.getUserHandle();

            shortcutInfo = info;
        }

        /**
         * Initializes a PendingInstallShortcutInfo to represent an app widget.
         */
        public PendingInstallShortcutInfo(AppWidgetProviderInfo info, int widgetId) {
            itemType = Favorites.ITEM_TYPE_APPWIDGET;
            intent = new Intent()
                    .setComponent(info.provider)
                    .putExtra(EXTRA_APPWIDGET_ID, widgetId);
            user = info.getProfile();

            providerInfo = info;
        }

        @Override
        @Nullable
        public Intent getIntent() {
            return intent;
        }

        @SuppressWarnings("NewApi")
        public Pair<ItemInfo, Object> getItemInfo(Context context) {
            switch (itemType) {
                case ITEM_TYPE_APPLICATION: {
                    String packageName = intent.getPackage();
                    List<LauncherActivityInfo> laiList =
                            context.getSystemService(LauncherApps.class)
                                    .getActivityList(packageName, user);

                    final WorkspaceItemInfo si = new WorkspaceItemInfo();
                    si.user = user;

                    LauncherActivityInfo lai;
                    boolean usePackageIcon = laiList.isEmpty();
                    if (usePackageIcon) {
                        lai = null;
                        si.intent = makeLaunchIntent(new ComponentName(packageName, ""))
                                .setPackage(packageName);
                        si.status |= WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON;
                    } else {
                        lai = laiList.get(0);
                        si.intent = makeLaunchIntent(lai);
                        try {
                            if (Utilities.ATLEAST_V && Flags.enableSupportForArchiving()
                                    && lai.getActivityInfo().isArchived) {
                                si.runtimeStatusFlags |= FLAG_ARCHIVED;
                            }
                        } catch (Throwable t) {
                            // ignore
                        }
                    }
                    LauncherAppState.getInstance(context).getIconCache()
                            .getTitleAndIcon(si, () -> lai,
                                    DESKTOP_ICON_FLAG.withUsePackageIcon(usePackageIcon));
                    return Pair.create(si, null);
                }
                case ITEM_TYPE_DEEP_SHORTCUT: {
                    WorkspaceItemInfo itemInfo = new WorkspaceItemInfo(shortcutInfo, context);
                    LauncherAppState.getInstance(context).getIconCache()
                            .getShortcutIcon(itemInfo, shortcutInfo);
                    return Pair.create(itemInfo, shortcutInfo);
                }
                case ITEM_TYPE_APPWIDGET: {
                    LauncherAppWidgetProviderInfo info = LauncherAppWidgetProviderInfo
                            .fromProviderInfo(context, providerInfo);
                    LauncherAppWidgetInfo widgetInfo = new LauncherAppWidgetInfo(
                            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0),
                            info.provider);
                    InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
                    widgetInfo.minSpanX = info.minSpanX;
                    widgetInfo.minSpanY = info.minSpanY;
                    widgetInfo.spanX = Math.min(info.spanX, idp.numColumns);
                    widgetInfo.spanY = Math.min(info.spanY, idp.numRows);
                    widgetInfo.user = user;
                    return Pair.create(widgetInfo, providerInfo);
                }
            }
            return null;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PendingInstallShortcutInfo) {
                PendingInstallShortcutInfo other = (PendingInstallShortcutInfo) obj;

                boolean userMatches = user.equals(other.user);
                boolean itemTypeMatches = itemType == other.itemType;
                boolean intentMatches = intent.toUri(0).equals(other.intent.toUri(0));
                boolean shortcutInfoMatches = shortcutInfo == null
                        ? other.shortcutInfo == null
                        : other.shortcutInfo != null
                            && shortcutInfo.getId().equals(other.shortcutInfo.getId())
                            && shortcutInfo.getPackage().equals(other.shortcutInfo.getPackage());
                boolean providerInfoMatches = providerInfo == null
                        ? other.providerInfo == null
                        : other.providerInfo != null
                            && providerInfo.provider.equals(other.providerInfo.provider);

                return userMatches
                        && itemTypeMatches
                        && intentMatches
                        && shortcutInfoMatches
                        && providerInfoMatches;
            }
            return false;
        }
    }

    private static String getIntentPackage(Intent intent) {
        return intent.getComponent() == null
                ? intent.getPackage() : intent.getComponent().getPackageName();
    }

    private PendingInstallShortcutInfo decode(int itemType, UserHandle user, Intent intent) {
        switch (itemType) {
            case Favorites.ITEM_TYPE_APPLICATION:
                return new PendingInstallShortcutInfo(intent.getPackage(), user);
            case Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                List<ShortcutInfo> si = ShortcutKey.fromIntent(intent, user)
                        .buildRequest(mContext)
                        .query(ShortcutRequest.ALL);
                if (si.isEmpty()) {
                    return null;
                } else {
                    return new PendingInstallShortcutInfo(si.get(0));
                }
            }
            case Favorites.ITEM_TYPE_APPWIDGET: {
                int widgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, 0);
                AppWidgetProviderInfo info =
                        AppWidgetManager.getInstance(mContext).getAppWidgetInfo(widgetId);
                if (info == null || !info.provider.equals(intent.getComponent())
                        || !info.getProfile().equals(user)) {
                    return null;
                }
                return new PendingInstallShortcutInfo(info, widgetId);
            }
            default:
                Log.e(TAG, "Unknown item type");
        }
        return null;
    }
=======
>>>>>>> aosp/android17-release
}
