# Contributing to Fugueti

Thanks for helping! Fugueti is a small project, so a few conventions keep it easy to work on.

## Reporting bugs and ideas

- Use the issue forms: **[BUG]** for problems, **[FEATURE]** for ideas. In the app,
  *Settings → About → Support* opens them with your app version, device and Android version
  already filled in.
- Search the existing issues first.
- For a crash, attach the log: *Settings → About* → long press the version shows the commit, and
  `adb logcat -d -b crash` gives the stack trace.

## Development workflow

| | Trigger | What happens |
|---|---|---|
| **Changes** | branch + pull request to `main` | CI builds the app and runs the unit tests |
| **Nightly** | every push to `main` | CI builds the signed nightly app and replaces the `nightly` release |
| **Release** | a `v*` tag (e.g. `v0.0.2`) | CI runs the tests, builds the signed app and publishes the release |

- Branch names: `feat/…`, `fix/…`, `chore/…`, `docs/…`.
- Pull requests are squash merged, so each change lands as one commit on `main`.
- Commit messages in English, imperative mood (`Fix double tap on empty workspace`).

### Releasing

1. Update `fuguetiVersion` in `build.gradle` and the top entry of `CHANGELOG.md`, merge to `main`.
2. Create the tag from `main` (`git tag v0.0.2 && git push origin v0.0.2`) or run the **Release**
   workflow by hand with the tag name.

Versions follow `MAJOR.MINOR.PATCH`; `versionCode = MAJOR*1000000 + MINOR*10000 + PATCH*100`.
Nightlies show `<next version>-nightly.<build>` and install next to the release app
(`app.fugueti.launcher.nightly`).

## Code

- Requirements and build commands: see the [README](README.md#building).
- New Fugueti code goes in `fugueti/` (package `app.fugueti.*`). When you change code under
  `lawnchair/`, `src/` or `quickstep/`, mark the file with
  `// Modified by Fugueti Launcher contributors (2026): …` and comment the change with `Fugueti:`.
- User-facing strings: English in `fugueti/res/values`, Portuguese in `values-pt-rBR` and
  `values-pt` (`LocaleStringsTest` checks it).
- Run the unit tests before opening a pull request:

  ```bash
  ./gradlew testLawnWithQuickstepGithubDebugUnitTest --tests 'app.fugueti.*'
  ```

- Clean room: never copy code, assets or text from proprietary apps.

## Code of conduct

Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md).
