# Follow-ups found while resolving conflicts

Check these when the tree compiles.

- `DeviceProfile` fields moved into profiles (`getWorkspaceProfile()`, `getFolderProfile()`,
  `getHotseatProfile()`, `getAllAppsProfile()`): Lawnchair/Fugueti Kotlin code still uses the
  old fields (`iconSizePx`, `folderIconSizePx`, `cellYPaddingPx`…).
- `PreviewBackground` no longer owns the dot color; `FolderIcon` sets it and now reads the
  notification dot color preference. Check `BubbleTextView` does the same.
- `ItemInfoWithIcon.newIcon`: shape now comes from `ThemeManager.getIconShapeData()` (applied
  whether or not icons are themed). Check icon shapes still follow the Lawnchair preference.
- `ApiWrapper.queryAllUsers` / `getPreInstalledSystemPackages` moved into `UserCache`; remove the
  overrides in `SystemApiWrapper.kt`. Lawnchair wrapped `getLauncherUserInfo` in a try/catch
  (some ROMs throw); consider the same in `UserCache.buildCachedUserInfo`.
- `ShortcutConfigActivityInfo.getFullResIcon` uses `LauncherActivityInfo.getActivityInfo()`
  (API 31); Lawnchair had a pre-S fallback by package name.
- `com.android.launcher3.BuildConfig` (AOSP) vs Lawnchair's `BuildConfigs`: decide which fields
  the generated `BuildConfig` provides (`WIDGETS_ENABLED`, `IS_DEBUG_DEVICE`, `IS_STUDIO_BUILD`,
  `QSB_ON_FIRST_SCREEN`…).
- `LocalColorExtractor` is injected now (`@Inject`); Lawnchair callers of `newInstance()` and the
  `local_colors_extraction_class` override need updating.
- `WidgetSizes` static helpers moved to `WidgetSizeHandler`; update Lawnchair callers.
- `AsyncClockEventDelegate` keeps Lawnchair's composition (no `ClockEventDelegate` superclass,
  API 35 only) with AOSP's `getListenableRef` settings observer.
- Hotseat QSB height: `HotseatProfileInitialValues` now uses the `qsbHeight` passed by
  `DeviceProfile`; when resolving `DeviceProfile`, pass 0 when `HotseatMode` has no search bar
  (Lawnchair used `hotseatMode.layoutResourceId != R.layout.empty_view`).
- `Utilities.getFullDrawable` moved to `graphics/IconLoader.kt`; Lawnchair's icon-pack /
  wrap-adaptive check and the pre-S fallback were ported there. `loadFullDrawableWithoutTheme`
  (Lawnchair, used by `LawnchairShortcut`) stays in `Utilities` and uses the old
  `ShortcutConfigActivityInfo.getFullResIcon(IconCache)` signature.
- `FeatureFlags.ENABLE_TASKBAR_NAVBAR_UNIFICATION` is gone; Lawnchair guarded it against a crash
  on Android 15 QPR1 and older. Check where AOSP 17 reads the navbar unification flag.
- `AbstractFloatingView.TYPE_COMPOSE_VIEW` moved from bit 25 to bit 27 (AOSP took bit 25).
- `WorkspaceItemSpaceFinder.findSpaceForItem` has a new signature (returns `WorkspaceItemCoordinates`, takes excluded screens); update `app.lawnchair.deck.AddFoldersWithItemsTask`. The smartspace first-screen rule now lives in `AddWorkspaceItemsTask`.
- `NotificationListener` lost its static listener API upstream; Lawnchair hooks were reapplied and `getInstanceIfConnected()` kept (static connected instance). Check Lawnchair callers of the removed `addNotificationsChangedListener`.
- `QuickstepKeyGestureEventsManager` implements hidden Android 16 APIs; `AllAppsActionManager` now uses it lazily (Android 16+ only). `TISBinder` still injects it directly: check it is only created when Recents is enabled.
- Lawnchair removed `testing/TestInformationHandler`; AOSP 17 code calls `TestInformationHandler.trackUiSurface(...)` (dropped in BaseActivity, check other callers).
