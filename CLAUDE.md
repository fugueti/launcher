# Fugueti Launcher: notes for Claude

Fugueti is an Android launcher based on Lawnchair (Launcher3). `main` is the Android 16 line;
`17-dev` is the Android 17 port (see `docs/android17.md` there). Fugueti's own code lives in
`fugueti/src` and `fugueti/res`; changes to Lawnchair/Launcher3 code are marked `Fugueti:`.

## Building

- Run Gradle only through `~/.cache/fugueti-android17/bin/gw <tasks>` (never `./gradlew`): it runs
  in a memory-capped slice, one build at a time; plain Gradle freezes the laptop.
- After editing preference classes (`PreferenceManager*.kt`, `BasePreferenceManager.kt`), delete
  `build/kotlin` and `build/intermediates/built_in_kotlinc` and build with `--no-build-cache`.
- Release test builds: `:assembleLawnWithQuickstepGithubRelease` (16),
  `:assembleLawnWithQuickstepNightlyRelease` (17).

## Before changing an area, read its doc

- Backup, restore, Nova import, launcher databases and grids: `docs/backup-restore.md`
  (pitfalls that crashed or wiped the home screen, and the test matrix).
- Gestures: `docs/gestures/`.

## Conventions

- Every user-visible change gets a `CHANGELOG.md` entry; commit messages explain why.
- New settings text goes into `fugueti/res/values*/fugueti_*_strings.xml`, translated into every
  language under `fugueti/res` (Lawnchair's own `strings.xml` stays as upstream).
- New settings are backed up as they are (the whole SharedPreferences and DataStore are), but check
  that a Nova import or a restore that sets them works off the main thread.
- Test on the device for both 16 and 17 before committing; capture the log from before the action
  (`adb logcat -G 16M` first).
