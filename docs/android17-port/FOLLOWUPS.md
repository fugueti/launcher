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
- `QuickstepTransitionManager`: rebuilt from AOSP 17 with Lawnchair's compat calls
  (`LawnchairQuickstepCompat` remote transitions / `makeRemoteAnimation`, try/catch around
  `registerRemoteAnimations`, `mCustomAnimation` only on 16+). AOSP moved `SpringAnimRunner` and
  `RemoteAnimationCoordinateTransfer` to `launcher3/remoteanimations/` (Kotlin): update
  `LawnchairFloatingSurfaceView`. Rotation now comes from `AnimatedSurface` (wmshell shared):
  re-add Lawnchair's `NoSuchFieldError` guard for `RemoteAnimationTarget.rotationChange` there
  when wmshell is updated. Check `getRemoteTransition` accepts an `IRemoteTransition`.
- `AllAppsProfile.createAllAppsProfile(..., cellHeightMultiplier)`: pass Lawnchair's drawer row height from `DeviceProfile`.
- `lawnchair/src/com/android/app/tracing/TracingShim.kt`: stand-in for the non-vendored tracinglib (`traceSection`, `TraceUtils.trace`, `TraceStateLogger`); replace with the real lib if it gets vendored.
- Depth/blur: `BaseDepthControllerImpl` rebuilt from AOSP 17 (lazy `EarlyWakeupInfo`, guarded hidden APIs, zoom only on R+). The cross-window blur state now comes from a `ListenableRef` passed by the caller: check the caller guards `CrossWindowBlurListeners` (S+) like Lawnchair did. Lawnchair's stuck-blur fix uses `DepthController.clearWorkspaceRenderEffects()`, implemented in `LauncherDepthController`.
- Taskbar: `TaskbarView` is AOSP 17 (pinned apps overflow replaces Lawnchair's ignored icons; `getIgnoreTaskbarIconCount()` returns 0). The taskbar QSB now comes from `QsbWidgetFactory` (DI): make it honor Lawnchair's `HotseatMode` (empty view when the mode has no search bar), as Lawnchair did in `TaskbarView`.
- Hotseat predictions: AOSP moved the logic into `util/HybridHotseatOrganizer.kt`; Lawnchair's paged/multi-row dock handling was ported there (fills the visible page, per-page outlines/listeners). Test with predictions on and several dock pages.
- App drawer: AOSP 17 always presents All Apps as a bottom sheet (no `shouldShowAllAppsOnSheet`).
  Lawnchair's drawer colour/opacity cache now feeds AOSP's `getBackgroundColor()`. The
  non-sheet header opacity path (`getAppDrawerSearchBarBackground` + drawer opacity) was dropped
  with AOSP's branch: check the search header still looks right with the drawer opacity setting.
  `LawnchairAlphabeticalAppsList` must drop its `<T>` (AOSP `AlphabeticalAppsList` is no longer
  generic).
