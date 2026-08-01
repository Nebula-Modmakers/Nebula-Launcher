# Nebula Launcher

Nebula is a native Android launcher for authenticated accounts, mod profiles,
the Nebula mod catalog, and modded Among Us.

FusionCore hosts the installed Android version of Among Us inside Nebula's
process. The BepInEx runtime and active mods are stored in Nebula's private app
data directory. NebulaCompat is not distributed in this repository or packaged
in the APK; an activated account downloads it from the Nebula API after login.

## Requirements

- Android Studio with Android SDK 36
- Android NDK `28.2.13676358` and `29.0.14206865`
- CMake `3.22.1` and `3.31.6`
- JDK 17 or newer for the included Gradle/Android Gradle Plugin versions
- An ARM64 Android device running Android 8 or newer

For command-line builds, set `ANDROID_HOME` to your Android SDK directory or
create an untracked `local.properties` containing `sdk.dir=/path/to/sdk`.

The Firebase Android client configuration in `nebulaApp/google-services.json`
contains public client identifiers required to build the existing app. It does
not contain Firebase Admin or service-account credentials.

## Build

On macOS or Linux:

```sh
./gradlew :nebulaApp:assembleDebug
```

On Windows:

```powershell
.\gradlew.bat :nebulaApp:assembleDebug
```

Run the verification suite with:

```sh
./gradlew :nebulaApp:testDebugUnitTest :nebulaApp:lintDebug
```

The debug APK is written to
`nebulaApp/build/outputs/apk/debug/nebulaApp-debug.apk`.

The current LSPlant source is pinned and vendored under `third_party/lsplant`
so Android 16 support does not depend on an unpublished binary artifact.

## Repository scope

This public repository contains the Android launcher and the runtime files it
packages. It intentionally excludes the production API, deployment
credentials, hosted mod packages, NebulaCompat binary, local build caches, and
release-signing keys.

## Privacy

User data is processed only through Nebula's proprietary server and Nebula's
Firebase project. It is never sold or used for advertising or purposes
unrelated to Nebula. See the complete [Privacy Policy](PRIVACY.md) for the data
Nebula processes, how it is used, retention periods, and user controls.

Account deletion, authorized-device retention, permitted data use, local
credential protection, and seven-day operational-log retention are described
in [PRIVACY.md](PRIVACY.md).

## Licensing

Nebula Launcher is distributed under GPL-3.0-only. See [LICENSE](LICENSE).
Bundled third-party components retain their own licenses and copyrights. See
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for license texts,
corresponding-source locations, exact versions, and upstream provenance.
