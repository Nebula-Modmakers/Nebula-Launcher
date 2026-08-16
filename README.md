# Nebula Launcher

Nebula is a native Android launcher for playing modded **Among Us** with Android-compatible BepInEx and IL2CPP mods.

It gives you one place to sign in, organize separate mod profiles, install supported mods, manage your files, and launch the Android version of Among Us through FusionCore. 

**Join my community!**

[![](https://dcbadge.limes.pink/api/server/https://discord.gg/cWbGxNeX5f)](https://discord.gg/cWbGxNeX5f)

> [!IMPORTANT]
> Nebula is currently pre-release software. Expect rough edges, especially during the first modded launch. Back up any files you care about before testing new mods. Nebula operates within a sandboxed environment and should not affect other apps on your device.

## What Nebula offers

- A polished, phone-friendly launcher interface
- Native Android ARM64 mod loading through FusionCore
- Nebula accounts with automatic session refresh
- Optional Among Us account linking for the supported online flow
- Separate mod profiles that can be switched before launch
- An integrated mod store for supported packages
- Manual import for compatible DLL and ZIP-based mod packages
- A built-in browser for Nebula and BepInEx files
- Clearer launch, runtime, and IL2CPP-generation error messages
- An in-game utility menu opened by triple-tapping the bottom-right edge

## Requirements

Before installing Nebula, make sure your device meets these requirements:

- **Android 8.0 or newer**
- A **64-bit ARM device** (`arm64-v8a`)
- The Android version of **Among Us 17.4a**
- Enough free storage for Among Us, the FusionCore runtime, generated IL2CPP files, and your mods
- Internet access for Nebula account services, runtime preparation, and mod downloads
- Permission for Nebula to manage the shared FusionCore and mod folders

Nebula checks the installed Among Us version before launch. If your version is older or newer than the supported build, the launcher will tell you whether you need to upgrade or downgrade.

Nebula does not include the Among Us APK. Install the game from a source you are authorized to use.

## Installing Nebula

1. Download the latest Nebula APK from this repository's **Releases** page.
2. Open the APK on your Android device.
3. If Android blocks the installation, allow your browser or file manager to install unknown apps, then try again.
4. Open Nebula Launcher.
5. Create a Nebula account or sign in to an existing account.
6. Verify your email if Nebula asks you to do so.
7. When the storage-access prompt appears, choose **Grant Access** and enable all-files access for Nebula.
8. Confirm that Among Us 2026.3.31 is installed.
9. Select a profile and install any supported mods you want to use.
10. Press **Launch**.

Android may display warnings because Nebula is installed outside Google Play. Only install APKs obtained from a release you trust.

## The first modded launch

The first launch takes much longer than later launches. Nebula and FusionCore may need to:

- Copy Unity game data into a usable location
- Detect the exact Unity runtime version
- Download a compatible Unity library when required
- Extract the Android .NET and BepInEx runtime
- Generate IL2CPP interop assemblies for the installed game version

IL2CPP generation is memory-intensive. On some phones, Android may close the game, or the phone may become temporarily unstable during the first attempt. The app may crash, or the phone may become unresponsive. This is normal behavior. If the app or device becomes unresponsive, a device restart may be necessary. If the loading icon is still moving, the app is **not** unresponsive. Wait for it to complete. It may take up to 8 minutes. Do not close the app during this stage. If Nebula reports that IL2CPP generation was interrupted:

1. Reopen Nebula.
2. Press **Launch** again.
3. Leave the device alone while generation continues.
4. Do not repeatedly close the game during this stage.

The second attempt commonly succeeds because some preparation work is already complete. Later launches should be considerably faster.

## Nebula accounts

Your Nebula account is used for the launcher and Nebula services. It is separate from your Among Us account.

Nebula refreshes your saved session whenever the app launches. If the server cannot be reached temporarily, an existing account can receive a limited offline grace period. Deactivated accounts and expired or revoked device sessions must reconnect to the server.

Logging out deletes the device session and deauthorizes your device. Your installed game and mod files are not deleted when you log out. Up to 3 device sessions may be authorized per account.

## Linking your Among Us account

Nebula includes an account-linking flow for the supported Android online setup. Follow the instructions shown in **Settings** and paste the returned link into Nebula when requested.

Account linking and the Nebula launcher login serve different purposes:

- Your **Nebula account** signs you into the launcher.
- Your **Among Us account link** supplies game-side identity information for the supported online flow.

Treat account links and tokens as private information. Do not post them in issues, screenshots, Discord messages, or public logs.

## Mod profiles

Profiles let you keep different sets of mods without manually rearranging the BepInEx folder every time.

For example, you might create:

- `Default` for the basic Nebula runtime
- `Town of Us` for Town of Us: Mira

The selected profile is staged into Nebula's shared BepInEx plugin directory when you launch the game. Changing profiles affects the next launch; it does not modify the original Among Us APK.

The default profile cannot be deleted. Other profiles can be created, selected, and removed from the Profiles page.

## Installing mods

Mod developers who want to add Android ARM64 support should read the
[Nebula Mod Developer Guide](MOD_DEVELOPER_GUIDE.md).

### From the mod store

Open **Mod Store**, select a mod card, review its description and available versions, and choose **Install**. Nebula downloads the package, verifies its size and SHA-256 fingerprint, validates its manifest, and installs its files into the active profile.

### From a local file

Nebula can import supported DLL files and ZIP-based mod packages. Imported files are checked and copied into the active profile's plugin area.

Only use mods that explicitly support:

- Android
- ARM64
- IL2CPP
- FusionCore
- Your exact Among Us version

A Windows mod DLL is not compatible with Android. Mods can initialize successfully while still having broken menus, networking, native calls, asset loading, or gameplay behavior. Verify that the mod you are installing is compatible with Android before installing it. Mods within the mod store are verified to be compatible with Android.


## Files and storage

Shared FusionCore files are stored under a folder similar to:

```text
FusionCore/com.innersloth.spacemafia/
```

This area contains the shared BepInEx runtime, configuration, active plugins, generated interop assemblies, and logs.

Nebula also keeps private application data for accounts, profiles, prepared Unity data, and launch state. Uninstalling Nebula can remove its private data. Shared files may remain depending on your Android version and storage behavior.

The built-in file browser is intended for inspecting Nebula and mod-related files. It only edits supported text files and refuses files that are too large for the editor.

## In-game utility menu

While Among Us is running through Nebula, triple-tap the **bottom-right edge** of the screen to open the utility menu.

The menu provides quick actions such as quitting the game and viewing useful runtime information. The normal Android status bar remains hidden in game, and the game is forced into landscape orientation.

## Troubleshooting

### Among Us opens without mods

- Confirm that you launched the game from Nebula rather than the normal Among Us icon.
- Check that the intended profile is active.
- Open Nebula's file browser and confirm that the expected DLL files are present.
- Review the latest BepInEx log for plugin-loading errors.
- Ensure the mod is actually compatible with Android IL2CPP.
- Check the mod's documentation for any known issues or compatibility notes.
- Make sure you installed the mod correctly (see "Installing mods" section).
- Confirm the version the mod is compatible with Among Us version 17.4a.

### Nebula asks me to upgrade or downgrade

Nebula currently requires Among Us 17.4a. A matching version name with a different version code is not accepted.

### The first launch crashes

Reopen Nebula and launch again. If the launcher says IL2CPP generation was interrupted, allow the next attempt to run without closing it. Make sure the phone has sufficient free memory and storage.

### The loading animation never finishes during login

Nebula should return to the login screen after an authentication error. Check your connection, email verification, activation status, and password. If the server rejects the saved session, sign in again.

### The loading animation never finishes during game launch

If on first launch, this is expected. The first launch takes a while as Nebula is generating the IL2CPP files for Among Us. Subsequent launches should be faster. If it takes more than a few minutes, try restarting Nebula.

### Storage access keeps appearing

The prompt returns until Android reports that Nebula has all-files access. Choose **Grant Access**, enable the permission for Nebula in Android's settings, and return to the app. This prompt should no longer appear after granting access.

### A mod crashes during startup

Disable or remove the newest mod from the active profile, then try again. A plugin appearing in BepInEx logs does not guarantee that every part of it works on Android. If the mod was downloaded from the mod store, make sure it is the correct version for Among Us version 17.4a.

### Online lobbies or accounts do not work

- Confirm that Among Us is the supported version.
- Check whether your Among Us account needs to be linked again.
- Verify that the account link or token has not expired.
- Test without newly added gameplay or networking mods.
- Save the relevant BepInEx and Logcat output before reporting the problem.

## Reporting a problem

When opening an issue, include:

- Your phone model
- Android version
- Nebula version
- Among Us version name and version code
- Active profile and installed mod versions
- Whether the failure happens before or after the Among Us menu appears
- The relevant BepInEx log
- Clear steps that reproduce the problem

Remove email addresses, account links, bearer tokens, session tokens, device tokens, and other private credentials before attaching logs.

## Important limitations

- Nebula currently targets ARM64 Android devices only.
- Only Among Us 17.4a is supported by this build.
- Not every desktop Among Us mod can run on Android.
- Voice-chat and platform-integration mods may start correctly but require additional devices or players for complete testing.
- First-run IL2CPP generation can use a large amount of memory.
- The mod catalog is still small and will grow as compatible packages are tested.

## Credits

Nebula is built around the Android FusionCore ecosystem and related work from All Of Us Mods:

- [FusionCore](https://github.com/All-Of-Us-Mods/FusionCore)
- [FusionCore.UnityDependencies](https://github.com/All-Of-Us-Mods/FusionCore.UnityDependencies)
- [Il2CppInteropFusion](https://github.com/All-Of-Us-Mods/Il2CppInteropFusion)
- [BepInExFusion](https://github.com/All-Of-Us-Mods/BepInExFusion)
- [AndroidUtilities](https://github.com/All-Of-Us-Mods/AndroidUtilities)

Special thanks to the All Of Us Mods team for their foundational work on FusionCore and related projects.

Nebula Launcher is distributed under GPL-3.0-only. See [LICENSE](LICENSE).
Bundled third-party components retain their own licenses and copyrights. See
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for license texts,
corresponding-source locations, exact versions, and upstream provenance.


Among Us is developed by Innersloth. Nebula is an independent community project and is not affiliated with or endorsed by Innersloth, Epic Games, Google, All Of Us Mods, or the BepInEx project.
