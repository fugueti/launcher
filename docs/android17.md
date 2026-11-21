# Moving to Launcher3 from Android 17 (`17-dev`)

Working notes for the upgrade. `main` stays on the Android 16 base (16.x releases and nightlies);
this branch takes the Android 17 work and is merged into `main` when it is stable (17.0.0).
Merge `main` into `17-dev` regularly so Fugueti changes keep flowing in.

## Status (2026-11-21)

| Step | State |
|---|---|
| Branch and CI (unit tests on `*-dev`, nightlies only from `main`) | done |
| `framework-17.jar` | extracted, not committed yet (see below) |
| `SystemUI-core-17`, `SystemUI-statsd-17`, `WindowManager-Shell-17` | open |
| Merge AOSP Launcher3 `android17-release` | measured, not started |
| Vendored libs (`platform_frameworks_libs_systemui`, `systemUI/`, `wmshell/`) to Android 17 | open |
| Build, test on an Android 17 device, merge into `main` | open |

## Prebuilt JARs without an AOSP build

The Android 17 emulator system image (`system-images;android-37.0;…;x86_64`, build
`CE2A.260420.051.A2`) carries the platform classes:

1. `7z e system.img 1.super.img`, then `lpunpack -p system -p system_ext 1.super.img parts/`.
2. The partitions are EROFS: `fsck.erofs --extract=<dir> parts/system.img` (package `erofs-utils`).
3. `system/framework/framework.jar` is dex: `d2j-dex2jar.sh -o framework-17.jar framework.jar`
   ([dex2jar](https://github.com/pxb1988/dex2jar) 2.4). Result: 37,803 classes (the Android 16
   JAR has 27,040), hidden APIs included (`ActivityTaskManager`, `TransitionInfo`,
   `RemoteTransition`…).

The SystemUI in that image is Google's (`SystemUIGoogle.apk`, proprietary parts), so it may be
used locally to try a build but **must not** be committed or used in CI. For the three SystemUI
JARs the options are, in order: keep the Android 16 JARs if the Android 17 code still compiles
against them; the AOSP-only emulator image (no Google apps); or building the modules from the
Android 17 sources (`prebuilts/libs/README.md`).

## Merge measurement

Trial merge of `aosp/android17-release` (Launcher3, `c612e6e`, 2026-04-16) into `17-dev`.
Common ancestor `f8d94de` (2025-06-09), 5,951 AOSP commits since.

- 844 conflicted paths; 373 are tests, AOSP build files or folders removed in the cleanup
  (resolve by keeping them deleted).
- 471 real ones:
  - 281 both-modified + 84 both-added, mostly `src/` (97), `quickstep/res` (92, mostly
    translations), `modules/widgetpicker` (84, added on both sides), `quickstep/src` (80);
  - ~106 delete/add mismatches, mostly `wmshell/shared` (46) and `src/` (32).
- Launcher3 is only one of the sources: `frameworks/libs/systemui` and the SystemUI / WM Shell
  pieces under `systemUI/` and `wmshell/` also need their Android 17 versions.

Local setup used: `git remote add aosp https://android.googlesource.com/platform/packages/apps/Launcher3`
with `remote.aosp.promisor=true` (the clone is partial, so blobs only AOSP has are fetched from
there), ref `aosp/android17-release`.

## Plan

1. Settle the SystemUI JARs (try the Android 16 ones first).
2. Merge `aosp/android17-release` in pieces: resolve the deleted-files conflicts mechanically,
   take AOSP translations, then `src/` and `quickstep/` by hand, keeping every `Fugueti:` /
   Lawnchair change.
3. Update the vendored libs from `frameworks/libs/systemui` and `frameworks/base` at the
   Android 17 tags.
4. Point `build.gradle` and `compatLib` at the `-17` JARs, set Quickstep's SDK range, build.
5. Test on an Android 17 device (gestures, feed, Recents, backup), then merge into `main` as 17.0.0.
