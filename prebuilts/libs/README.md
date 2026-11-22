# Prebuilt AOSP libraries

Launcher3 and Quickstep use hidden platform APIs, so the build compiles against JARs produced by an
AOSP build instead of the public SDK. Only the JARs below are used (see `build.gradle` and
`compatLib/`); anything else was removed.

| File | Used by | AOSP module (`m …`) | Source tag |
|---|---|---|---|
| `framework-17.jar` | Android 17 line (`17-dev`): app and modules (`addFrameworkJar`) | `framework` (stubs, see below) | `CE2A.260420.051.A3` |
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

## framework-17.jar (from the AOSP emulator image)

`framework-17.jar` is a compile-only stub JAR: every method body is
`throw new RuntimeException("Stub!")` (like the SDK's `android.jar`), private methods and static
initializers are removed, and only signatures and constants remain. It is made from the AOSP-only
emulator image, without an AOSP checkout:

| | |
|---|---|
| SDK package | `system-images;android-37.0;default;x86_64`, revision 6 (no Google apps) |
| Archive | `https://dl.google.com/android/repository/sys-img/android/x86_64-37.0_r06.zip` |
| Archive SHA-1 | `8842a57a6b146423debb46c1496a9c0986aa1b9e` (from the SDK repository index) |
| Build | `Android/generic_system/generic:17/CE2A.260420.051.A3/16571990:userdebug/test-keys` |
| `system/framework/framework.jar` SHA-256 | `794ee9ee8f81db1ab1c14e3fe54d478f04a072a19c8336feee653a3297359653` |
| `framework-17.jar` SHA-256 | `78a621fb1f6f51c78f43087398e2e2288a8595f57992c61dc62f0647470136a4` |

Steps:

```bash
curl -fLO https://dl.google.com/android/repository/sys-img/android/x86_64-37.0_r06.zip
sha1sum x86_64-37.0_r06.zip                                 # 8842a57a...
7z x x86_64-37.0_r06.zip                                    # x86_64/system.img
7z e x86_64/system.img 1.super.img                          # the dynamic partitions
lpunpack -p system 1.super.img parts/                       # parts/system.img (ext4)
7z e parts/system.img system/framework/framework.jar
d2j-dex2jar.sh -o framework-d2j.jar framework.jar           # dex2jar 2.4
prebuilts/tools/make-framework-jar.sh framework-d2j.jar 17  # writes prebuilts/libs/framework-17.jar
```

`prebuilts/tools/StubJar.java` (ASM 9.7.1, downloaded and checksummed by the script) also raises
the class files to version 52 and adds the InnerClasses entries that dex2jar leaves out (Kotlin
needs them to resolve nested types such as `Drawable.Callback`). Its output is reproducible
(sorted entries, fixed timestamps), so the SHA-256 above can be checked.
