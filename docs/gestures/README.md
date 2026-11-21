# Fugueti Launcher: home screen gestures

## Gestures

| One finger | Double tap | Two fingers |
|---|---|---|
| Swipe up | Double tap | Swipe up |
| Swipe down | Double tap + swipe up | Swipe down |
| Swipe left ¹ | Double tap + swipe down | Pinch in |
| Swipe right ¹ | | Pinch out |
| | | Rotate clockwise |
| | | Rotate counterclockwise |

¹ A horizontal swipe turns the page; the gesture only fires on the last page (left) or on the
first page (right, when the feed on the left is off).

Each gesture can:

* **Do nothing**
* **Open the app drawer** or **the app search**
* **Open the notification panel**
* **Open an app**
* **Open an app shortcut** (the shortcuts shown when you long press an icon)
* **Open an activity** (any exported screen of an installed app)
* **Launch an intent** written as a URI, for example `https://example.com`,
  `tel:5511999999999`, `geo:0,0?q=coffee` or `intent:#Intent;action=android.settings.WIFI_SETTINGS;end`
* Run the actions inherited from Lawnchair: lock the screen, recents, search, assistant

## Language

The app follows the device language. English is the default; a device set to Portuguese shows the
whole app in Portuguese (`values-pt-rBR` and `values-pt`). No Fugueti screen mixes the two.

## Setup

1. Install the APK and make Fugueti Launcher the home app
   (Android Settings > Apps > Default apps > Home app).
2. Long press an empty spot of the home screen > **Home settings** > **Gestures**.
3. Tap a gesture and choose its action. "Open activity", "Open app shortcut" and "Launch intent"
   open a picker.
4. Under **Options**: vibration on gesture, sensitivity (low, medium, high) and **Test gestures**,
   where you can practice each movement without running its action.

Tips:

* Gestures that start at the screen edges belong to the system (back and home) and Fugueti
  Launcher never competes with them. Start a little further in.
* Double tap and double tap + swipe only count on empty space (not on icons or widgets).
* With TalkBack on, the configured gestures show up in the TalkBack actions menu of the workspace
  ("Run gesture ...").

## Tests

```bash
# engine, adapter and languages (JVM + Robolectric)
./gradlew testLawnWithQuickstepGithubDebugUnitTest --tests 'app.fugueti.gestures.*'
# Espresso + end to end on the home screen (emulator or connected phone)
./gradlew connectedLawnWithQuickstepGithubDebugAndroidTest
```

The end to end test makes the debug build the home app, applies a map of actions, performs the 13
gestures for real and saves screenshots to `/sdcard/Download/fugueti/`.

## Where the code lives

| Folder | Contents |
|---|---|
| `fugueti/src/app/fugueti/gestures/engine/` | Recognition engine (pure Kotlin) |
| `fugueti/src/app/fugueti/gestures/GestureTouchController.kt` | Home screen integration |
| `fugueti/src/app/fugueti/gestures/ui/` | Pickers, intent editor and "Test gestures" |
| `fugueti/res/` | Strings (English default, Portuguese translation) |
| `fugueti/test/`, `fugueti/androidTest/` | Tests |
| `lawnchair/src/app/lawnchair/gestures/` | Controller and actions (from Lawnchair, modified) |

Detailed architecture: [ARCHITECTURE.md](ARCHITECTURE.md).
Changes: [CHANGELOG.md](../../CHANGELOG.md).
