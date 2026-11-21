# Prebuilt AOSP libraries

Launcher3 and Quickstep use hidden platform APIs, so the build compiles against JARs produced by an
AOSP build instead of the public SDK. Only the JARs below are used (see `build.gradle` and
`compatLib/`); anything else was removed.

| File | Used by | AOSP module (`m …`) | Source tag |
|---|---|---|---|
| `framework-16.jar` | app (Quickstep), all modules (`addFrameworkJar`) | `framework` | android-16.0.0_r3 |
| `SystemUI-core-16.jar` | `compatLib`, modules with `compileOnlyCommonJars()` | `SystemUI-core` | android-16.0.0_r3 |
| `SystemUI-statsd-16.jar` | app, common jars | `SystemUI-statsd` | android-16.0.0_r3 |
| `WindowManager-Shell-16.jar` | app, `compatLib`, common jars | `WindowManager-Shell` | android-16.0.0_r3 |
| `framework-10.jar` … `framework-15.jar` | `compatLib/compatLibVQ` … `compatLibVV` (per Android version compatibility layers) | `framework` | Android 10 … 15 |

## Producing them (needed for Launcher3 from Android 17)

1. Check out AOSP at the target tag and build for
   `aosp_cf_x86_64_only_phone-aosp_current-userdebug`:

   ```bash
   source build/envsetup.sh
   lunch aosp_cf_x86_64_only_phone-aosp_current-userdebug
   m framework SystemUI-core SystemUI-statsd WindowManager-Shell
   ```

2. Copy the outputs from `out/soong/.intermediates/`:

   | Module | Path |
   |---|---|
   | framework | `frameworks/base/framework/android_common/turbine-combined/framework.jar` |
   | SystemUI-statsd | `frameworks/base/packages/SystemUI/shared/SystemUI-statsd/android_common/javac/SystemUI-statsd.jar` |
   | WindowManager-Shell | `frameworks/base/libs/WindowManager/Shell/WindowManager-Shell/android_common/javac/WindowManager-Shell.jar` |
   | SystemUI-core | `frameworks/base/packages/SystemUI/SystemUI-core/android_common/javac/SystemUI-core.jar` |

3. Name them with the Android version (`framework-17.jar`, …) and update the references in
   `build.gradle` and `compatLib/build.gradle`.

A full AOSP checkout and build needs several hundred GB of disk and a powerful machine.
