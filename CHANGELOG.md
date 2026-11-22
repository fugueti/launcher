# Fugueti Launcher changelog

## 16.0.2 (2026-11-22)

* **Updates:** the release app now updates itself like the nightly. Every day at 10:00 it looks
  for a newer GitHub release and posts a notification on its own **App updates** channel; the
  notification opens *Settings → About*, which lists the changes, downloads the APK, checks its
  SHA-256 and hands it to the system installer. The check also runs when About opens and can be
  turned off in *Settings → General → Updates*. The launcher asks once for the notification
  permission.
* **Hide** in the long-press menu of an app drawer icon hides the app from the drawer at once,
  with **Undo** in the snackbar. Hidden apps are still managed in *Settings → App drawer →
  Hidden apps*.
* App drawer: no empty band above the apps when the drawer search bar is hidden.
* **Support** in *Settings → About* opens a sheet to report a bug or suggest a feature on GitHub
  (issue forms titled `[BUG]` / `[FEATURE]`, with app version, device and Android version filled
  in) or to send an e-mail.
* Release channels: stable versions (Launcher3 from Android 16) come from `main` as tagged
  releases; the **nightly** (`app.fugueti.launcher.nightly`) now follows `17-dev`, Fugueti on
  Launcher3 from Android 17.

## 16.0.1 (2026-11-21)

First release of **Fugueti**, built on Lawnchair 16 (`16-dev`, Launcher3 from Android 16,
compiled against API 37).

### Identity

* App name **Fugueti**, package `app.fugueti.launcher` (nightly and play variants with their
  own ids), so it installs next to the official Lawnchair. Own versioning (16.0.1: the Android base, then minor and patch); Lawnchair's
  version is still shown in its part of the About screen.
* Icon: outline rocket with orange exhaust on a light sky (release) or a night sky (nightly),
  with a monochrome layer for themed icons (winner of the icon poll). Sources and the other icon
  options in `art/icon/`.

### Gestures

* 13 configurable home screen gestures with their own recognition engine
  (`fugueti/src/app/fugueti/gestures/`): swipe up/down/left/right, double tap,
  double tap + swipe up/down, two finger swipe up/down, pinch in/out and rotation.
* Haptics, sensitivity, a "Test gestures" pad and TalkBack actions for configured gestures.
* New actions: open app shortcut, open activity, launch intent.
* Two finger swipes reuse Lawnchair's settings; the paged dock keeps its horizontal swipes;
  native animations stay for the default actions.

### Search bar

* Microphone with the Google provider opens the default assistant (Gemini) or Google voice
  search instead of the voice command the Google app now ignores (also broken upstream).
* "Google" dock search bar mode hosts the regular Google search widget instead of the Pixel-only
  one that rendered empty, and its microphone and Lens/QR buttons work (also broken upstream).

### Backup

* Fugueti backups include the gesture settings (they live in the backed up preferences).
* **Restore fixes:** settings stored in Lawnchair's shared preferences (icon pack, grid, drawer,
  hidden apps…) were undone right after being restored, and DataStore settings and the icon
  override / drawer folder database could be lost too. Restores now apply each part through the
  running app instead of overwriting its open files; backups fold database journals in first.
* Backups also carry custom fonts and saved wallpapers (backup format 2; older backups still
  restore).
* Backup files are named `YYYY-MM-DD_hh-mm.lawnchairbackup`.
* The backup picker accepts any file, so Lawnchair and Fugueti backups that went through other
  apps (and lost their zip type) can be selected; invalid files are still rejected.
* Restoring a **Nova Launcher** backup also imports its home screen gestures (swipes, double tap,
  double tap + swipe, two finger swipes, pinch and rotation): Nova actions such as app drawer,
  screen lock and notifications, app launches and other intents. Nova actions without a Fugueti
  equivalent are skipped.

### Feed

* Google feed (Discover) through **Fugueti Feed**
  ([victorwalter/fugueti-feed](https://github.com/victorwalter/fugueti-feed)), a small companion
  app signed with the same key. The "Feed provider" setting is gone (the Google app refuses
  direct connections from release builds): with the feed on and the bridge missing, the home
  screen settings link to its download.
  The settings screen also shows a red warning with the download link while the feed is on and
  the bridge is missing (translated into every language Lawnchair has).

### About and settings

* About screen: Fugueti section (version, update check, support e-mail, GitHub, crypto donation
  with QR code, team) above Lawnchair's unchanged About.
* Settings show "Fugueti <version>" and the menu says "Restart Fugueti" and theme options say "Managed by Fugueti"; GitHub links, release checks and the nightly updater use
  this repository.

### Other

* Release workflow: tag `v*` runs the gesture tests, builds the signed APK and publishes a
  GitHub release.
* English and Portuguese strings; unit tests for the gesture engine.
* Defaults: 7 x 12 home screen grid and 7 dock icons on phones (the grid sliders now go up to 12
  without "increase max grid size"), icons at 80% on the home screen and in the app drawer, app
  drawer search bar hidden, At a Glance and Lawnchair's online announcements off.
* The app drawer has no drag handle pill at the top.
