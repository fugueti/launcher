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
