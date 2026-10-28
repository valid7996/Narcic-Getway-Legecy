# Narcic Getway — Legacy (Android 6+)

Android-only copy of Narcic Getway for older phones. It installs next to the normal app.

| | Normal app | Legacy |
|---|---|---|
| applicationId | `com.narcic.getway` | `com.narcic.getway.legacy` |
| minSdk | 24 (Android 7) | 23 (Android 6) |
| Desktop module | yes | removed |

## Build

1. Put `nrgcore.aar` in `app/libs/` (it is git-ignored; build it with `tools/build-nrgcore.sh`
   or run the `android-natives.yml` workflow).
2. `./gradlew assembleRelease`

If the build stops with `minSdkVersion 23 cannot be smaller than 24 declared in library ...`,
that library needs a lower version (or this build cannot go below 24).

## Things to change in the new repo

- `GitHubReleases.REPO` in `shared/.../data/update/UpdateChecker.kt` — the update checker still
  points at `valid7996/Narcic-Getway`, so it would offer the normal APK to this app.
- The default Windscribe subscription URL in `ConfigRepository.kt` (search `windscribe.txt`)
  also points at the original repo.
- The README badges/links, and the `.github/workflows` (only the Android ones were kept).
- IKEv2 and default Windscribe IKEv2 subscription have been completely removed (IKEv2 requires Android 11+ and is unavailable on legacy devices).
