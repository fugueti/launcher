# Fugueti Launcher: gesture architecture

It describes how Fugueti Launcher extends the gesture system of Lawnchair (based on AOSP
Launcher3) to get configurable home screen gestures.

## 1. Origin rule (clean room)

All recognition logic was written from scratch from three sources:

1. the open source code of Lawnchair and Launcher3 (Apache 2.0 / GPL-3.0), the base of the fork;
2. the public Android documentation (`MotionEvent`, `GestureDetector`,
   `WindowInsets.getSystemGestureInsets`, `LauncherApps`);
3. the public description of which gestures a launcher may offer (the scope list).

Nothing was decompiled, inspected or copied from any proprietary launcher. The thresholds
(distances, times, angles) were chosen from the public Android `ViewConfiguration` constants and
tuned by testing on the emulator.

## 2. What Lawnchair already had

| Piece | File | What it does |
|---|---|---|
| `GestureController` | `lawnchair/src/app/lawnchair/gestures/GestureController.kt` | Binds 5 triggers (double tap, swipe up, swipe down, home button, back) to a `GestureHandler` read from the preferences. |
| `VerticalSwipeTouchController` | same | One finger, vertical only `TouchController`, only when the action is not the default one. |
| `WorkspaceTouchListener.onDoubleTap` | `src/com/android/launcher3/touch/` | Double tap through Android's `GestureDetector`. |
| `GestureHandlerConfig` | `gestures/config/` | Serializable action (kotlinx.serialization) stored in DataStore. |
| `GestureHandlerPreference` | `ui/preferences/components/` | Compose bottom sheet to choose the action. |

Limitations: no two finger support, no horizontal swipes on the home screen, no double tap followed
by a swipe, and the "shortcut" target of `OpenAppGestureHandler` existed but did nothing.

## 3. How a touch reaches the launcher

`BaseDragLayer` keeps a list of `TouchController`s. For each event, while there is no active
controller, it asks each one, in order, `onControllerInterceptTouchEvent`. The first one to answer
`true` gets the rest of the gesture through `onControllerTouchEvent`. In practice, **the first
controller in the list sees every event before the others** (including the second finger's
`ACTION_POINTER_DOWN`). Fugueti installs itself first, watches everything without intercepting
and only intercepts when it recognizes a gesture that has an action configured.

## 4. The gestures

The scope speaks of 12 gestures, but its list has 13 items; all of them were implemented.

| # | Gesture | Preference key | Recognition |
|---|---|---|---|
| 1 | Swipe up | `swipe_up_gesture_handler` (inherited) | 1 finger, dominant vertical movement, minimum distance and speed |
| 2 | Swipe down | `swipe_down_gesture_handler` (inherited) | same |
| 3 | Swipe left | `fg_swipe_left` | 1 finger, horizontal; only fires on the last page (otherwise it turns the page) |
| 4 | Swipe right | `fg_swipe_right` | same, only on the first page and with no feed on the left |
| 5 | Double tap | `double_tap_gesture_handler` (inherited) | two short taps within `doubleTapTimeout` and `doubleTapSlop` |
| 6 | Double tap + swipe up | `fg_double_tap_swipe_up` | the second tap stays down and slides up |
| 7 | Double tap + swipe down | `fg_double_tap_swipe_down` | same, down |
| 8 | Two finger swipe up | `fg_two_finger_swipe_up` | the centroid of both fingers moves up, distance between them roughly constant |
| 9 | Two finger swipe down | `fg_two_finger_swipe_down` | same, down |
| 10 | Pinch in | `fg_pinch_in` | distance between the fingers drops by ≥ 25% |
| 11 | Pinch out | `fg_pinch_out` | distance between the fingers grows by ≥ 25% |
| 12 | Rotate clockwise | `fg_rotate_cw` | the angle of the vector between the fingers turns ≥ 30° (clockwise on screen) |
| 13 | Rotate counterclockwise | `fg_rotate_ccw` | same, counterclockwise |

### 4.1 Recognition engine (`GestureRecognizer`)

A pure Kotlin class, **with no Android dependency**, so it can be tested with plain JUnit. It takes
samples (`TouchSample`: action, id and position of each finger, time) and returns at most one
`GestureKind` per touch. State machine:

```
IDLE ──down──▶ ONE_FINGER ──pointer down──▶ TWO_FINGERS ──▶ (classify) ──▶ DONE
                 │   │
                 │   └─quick still up──▶ WAIT_SECOND_TAP ──down in time──▶ SECOND_TAP
                 │                                                │         │
                 └─up after moving──▶ SWIPE_*               still up│         │vertical move
                                                            DOUBLE_TAP  DOUBLE_TAP_SWIPE_*
```

Two finger classification: each candidate gets a "score" = measure / threshold.

* translation: vertical travel of the centroid / `minSwipeDistance`;
* pinch: |current span / initial span − 1| / 0.25;
* rotation: |Δangle| / 30°.

It fires as soon as the best score passes 1 **and** beats the runner up by at least 20% (so a pinch
is not mistaken for a rotation). If the touch ends with no clear winner, the best score ≥ 1 at the
end wins. Rotation direction: in screen coordinates (y grows downwards), a positive `atan2` Δangle
is clockwise.

### 4.2 Integration (`GestureTouchController`)

* First in the list of `LawnchairLauncher.createTouchControllers()`, replacing Lawnchair's
  `VerticalSwipeTouchController`.
* Only acts in the `NORMAL` state, with no floating view open (folder, menu, widget picker).
* Converts `MotionEvent` to `TouchSample` (a thin adapter, tested with Robolectric).
* Intercepts the gesture when: (a) a second finger lands and some two finger gesture is configured;
  (b) it is the second tap of a double tap that started to slide; (c) it is a one finger swipe whose
  action is not the native one (same rule as Lawnchair: swipe up still opens the drawer with the
  original animation when that is the chosen action).
* Once a gesture is recognized it calls `GestureController.trigger(kind)`, which resolves the action
  and vibrates.

### 4.3 Conflicts with system gestures (Android 15)

* Touches that start inside `WindowInsets.getSystemGestureInsets()` (the "back" side edges and the
  "home" bottom strip) or with `EDGE_NAV_BAR` are ignored. The launcher never asks for exclusion
  areas (`setSystemGestureExclusionRects`), so the system back gesture stays intact.
* Two fingers do not conflict with the system: Android 15 only uses one finger gestures from the
  edges.
* A horizontal swipe on the middle pages is still a page change.

## 5. Actions

| Action | Class | Note |
|---|---|---|
| Nothing | `NoOp` (inherited) | |
| App drawer | `OpenAppDrawer` (inherited) | |
| Notifications | `OpenNotifications` (inherited) | |
| Open app | `OpenApp` with an `App` target (inherited) | |
| Shortcut | `OpenApp` with a `Shortcut` target | **new**: `LauncherApps.startShortcut`; Lawnchair left it empty |
| Activity | `OpenActivity` | **new**: picks any exported activity of an installed app |
| Intent | `OpenIntent` | **new**: intent URI (`Intent.parseUri`), e.g. `https://…`, `tel:…` or `intent:#Intent;action=…;end` |

Lawnchair's extra actions (lock screen, recents, search, assistant) were kept.

## 6. Settings UI

"Gestures" screen (Compose, Material 3 with Material You dynamic color) reorganized in groups: One
finger, Double tap, Two fingers, Buttons. Each row opens the action picker (Lawnchair's bottom
sheet, `GestureHandlerPreference`, now with the new actions). New items: vibration on gesture,
sensitivity (low, medium, high) and the test pad ("Test gestures"), a screen where the gesture you
make is recognized and shown without running the action.

## 7. Languages

English is the default (`fugueti/res/values`) and Portuguese is a full translation
(`values-pt-rBR`, `values-pt`); the app follows the device language. Lawnchair strings shown on the
gesture screens that had no Portuguese translation, or that told the user to look for "Lawnchair",
were replaced by Fugueti strings. `LocaleStringsTest` keeps this true.

## 8. Accessibility

* With TalkBack on, the system transforms touches (explore by touch), so two finger gestures do not
  arrive as on a regular screen. To leave nobody out, each configured gesture becomes an
  **accessibility action** of the workspace ("Run gesture: pinch in"), reachable from the TalkBack
  menu.
* Vibration follows the user's preference and the system setting.

## 9. Tests

* **Unit (JUnit + Robolectric)**: `GestureRecognizerTest` covers the 13 gestures, ambiguous cases
  and cancellations; `MotionEventAdapterTest` (Robolectric) checks the multi-touch `MotionEvent`
  conversion; `LocaleStringsTest` checks the languages.
* **Instrumented (Espresso)**: `GestureTestPadTest` injects real one and two finger `MotionEvent`s
  into the gesture test screen on the emulator and checks the recognized gesture.
* **End to end (UiAutomation)**: `HomeScreenGesturesTest` makes the debug build the home app,
  assigns one action per gesture, performs the 13 gestures on the real home screen and checks that
  the engine fired each one (or, for the native swipe, that the drawer opened).
* **Release**: the signed APK was checked on the emulator with touches injected straight into the
  multi-touch driver (`/dev/input`), with no test code inside the app.

## 10. Thresholds in use (medium sensitivity)

| Measure | Value |
|---|---|
| Minimum swipe distance (1 or 2 fingers) | 56 dp |
| Minimum speed (1 finger) | 100 dp/s, at release **or** averaged over the whole stroke |
| Maximum time between double tap taps | `ViewConfiguration.getDoubleTapTimeout()` (300 ms) |
| Maximum distance between taps | `scaledDoubleTapSlop` (100 dp) |
| Pinch | 25% change in the distance between the fingers |
| Rotation | 30° |
| Dominance to fire before release | 1.2 × the runner up |

Low sensitivity multiplies distances and angles by 1.4; high, by 0.7.
