# Supporting Nebula on Android ARM64

This guide is for Among Us mod developers who want their BepInEx IL2CPP mod to run through Nebula Launcher on 64-bit ARM Android devices.

Nebula does not convert a Windows mod into an Android mod automatically. It supplies the Android FusionCore, BepInEx, IL2CPP interop, Reactor compatibility, profile, packaging, and update environment. Your managed plugin and every dependency still need to be portable to Android.

## Runtime target

A Nebula-compatible mod must support all of the following:

- Android on `arm64-v8a`
- The Android package `com.innersloth.spacemafia`
- The exact Among Us version advertised by the Nebula catalog
- BepInEx IL2CPP through FusionCore
- The interop assemblies generated for that exact game build

Do not assume that a plugin works on Android because its DLL is managed. A managed assembly can still contain Windows-only APIs, x64 ReadyToRun code, native dependencies, desktop Unity assumptions, or calls to Unity internals that are stripped from the Android player.

## Compile for the correct Among Us version

Among Us IL2CPP types and method signatures change between releases. Compile against the Android game libraries or generated interop assemblies for the exact version you intend to list in the Nebula catalog. Do not compile against the newest desktop game and change only the version text in the manifest.

For example, a catalog release targeting:

```text
Package:      com.innersloth.spacemafia
Version name: 2026.6.5
Version code: 7045
ABI:          arm64-v8a
```

must be compiled and tested against the Android IL2CPP API for that same build. The version name, Android version code, and ABI are separate values; matching only one is insufficient.

### 1. Pin a game-library source

Use one of these approaches:

1. Reference an `AmongUs.GameLibs.Android` package explicitly documented for the target Android game build.
2. Generate interop assemblies from your own authorized copy of that exact Android build using the same FusionCore/BepInEx IL2CPP toolchain used for testing.

Nebula/FusionCore-generated assemblies are placed under the target runtime's `BepInEx/interop/` directory. Important references normally include:

```text
BepInEx/interop/Assembly-CSharp.dll
BepInEx/interop/Assembly-CSharp-firstpass.dll
BepInEx/interop/UnityEngine.dll
BepInEx/interop/UnityEngine.CoreModule.dll
BepInEx/interop/UnityEngine.UI.dll
BepInEx/interop/Unity.TextMeshPro.dll
BepInEx/interop/Hazel.dll
```

Use game libraries only as compile-time references. Do not put `Assembly-CSharp.dll`, generated Unity interop assemblies, or other proprietary game files in the `.npkg` payload or your public repository.

The game-library package version is not guaranteed to use the same number as the visible Among Us version. Confirm the package's documented source build instead of assuming the numbers correspond.

### 2. Pin every framework dependency

Do not use floating versions such as `*`, prerelease wildcards, or an unpinned local `latest` folder. Pin BepInEx IL2CPP, game libraries, Reactor, MiraAPI, and other APIs to the exact files used by the tested release.

A project using a matching Android game-library package can be structured like this:

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net6.0</TargetFramework>
    <LangVersion>latest</LangVersion>
    <Nullable>enable</Nullable>
    <PlatformTarget>AnyCPU</PlatformTarget>
    <Prefer32Bit>false</Prefer32Bit>
    <PublishReadyToRun>false</PublishReadyToRun>
    <SelfContained>false</SelfContained>

    <!-- Project metadata: keep these synchronized with the package manifest. -->
    <ModVersion>1.0.0</ModVersion>
    <AmongUsVersionName>2026.6.5</AmongUsVersionName>
    <AmongUsVersionCode>7045</AmongUsVersionCode>

    <!-- Use the package release documented for the target Android build. -->
    <GameLibPackageVersion>REPLACE_WITH_MATCHING_PACKAGE_VERSION</GameLibPackageVersion>
  </PropertyGroup>

  <ItemGroup>
    <PackageReference Include="BepInEx.Unity.IL2CPP"
                      Version="6.0.0-be.735"
                      ExcludeAssets="runtime;native" />
    <PackageReference Include="BepInEx.AutoPlugin"
                      Version="1.1.0"
                      PrivateAssets="all" />
    <PackageReference Include="AmongUs.GameLibs.Android"
                      Version="$(GameLibPackageVersion)"
                      PrivateAssets="all" />
  </ItemGroup>

  <ItemGroup>
    <!-- Reference the exact tested dependency binaries without copying them
         into your plugin output. Package them separately only when licensing
         and the Nebula manifest explicitly account for redistribution. -->
    <Reference Include="Reactor">
      <HintPath>lib/Reactor.dll</HintPath>
      <Private>false</Private>
    </Reference>
    <Reference Include="MiraAPI">
      <HintPath>lib/MiraAPI.dll</HintPath>
      <Private>false</Private>
    </Reference>
  </ItemGroup>
</Project>
```

Remove references the mod does not use. If your mod does not depend on Reactor or MiraAPI, do not include them merely because the example shows them.

If no matching game-library package exists, use explicit local references to the generated interop directory instead:

```xml
<PropertyGroup>
  <AmongUsInteropDir>../game-libs/2026.6.5-7045-android-arm64</AmongUsInteropDir>
</PropertyGroup>

<ItemGroup>
  <Reference Include="Assembly-CSharp">
    <HintPath>$(AmongUsInteropDir)/Assembly-CSharp.dll</HintPath>
    <Private>false</Private>
  </Reference>
  <Reference Include="UnityEngine.CoreModule">
    <HintPath>$(AmongUsInteropDir)/UnityEngine.CoreModule.dll</HintPath>
    <Private>false</Private>
  </Reference>
  <Reference Include="Unity.TextMeshPro">
    <HintPath>$(AmongUsInteropDir)/Unity.TextMeshPro.dll</HintPath>
    <Private>false</Private>
  </Reference>
  <Reference Include="Hazel">
    <HintPath>$(AmongUsInteropDir)/Hazel.dll</HintPath>
    <Private>false</Private>
  </Reference>
</ItemGroup>
```

Keep this local directory out of Git and document how contributors obtain their own lawful copy.

### 3. Compile the plugin, not a published application

Use `dotnet build`, not a self-contained `dotnet publish` command:

```sh
dotnet restore --locked-mode
dotnet build MyMod.csproj -c Release --no-restore
```

Commit `packages.lock.json` when using NuGet package references. Generate or update it deliberately with:

```sh
dotnet restore --use-lock-file
```

The distributable plugin is normally the DLL under a path similar to:

```text
bin/Release/net6.0/MyMod.dll
```

Do not distribute the complete build directory. In particular, do not package game libraries from `bin/`, `.deps.json`, a desktop host executable, or a copied .NET runtime.

### 4. Detect accidental platform-specific output

Check the project file and build command for these problematic settings:

```text
RuntimeIdentifier=win-x64
RuntimeIdentifier=linux-x64
PlatformTarget=x64
PublishReadyToRun=true
SelfContained=true
```

For the managed plugin, omit `RuntimeIdentifier` and use `AnyCPU`. If you must ship native code, build the managed DLL portably and build the native Android `arm64-v8a` `.so` as a separate artifact.

An error like this means the plugin was not produced as portable managed IL:

```text
The assembly architecture is not compatible with the current process architecture.
```

Rebuild the affected assembly and every bundled managed dependency. Changing the file extension or package manifest cannot repair an x64 or ReadyToRun DLL.

### 5. Treat every game update as a new target

When Nebula adds another Among Us version:

1. Obtain or generate that version's Android interop assemblies.
2. Create a new dependency lock or version-specific build configuration.
3. Compile against the new assemblies.
4. Resolve compile errors instead of suppressing them with reflection unless reflection is genuinely required.
5. Test all Harmony patch targets against the new method signatures.
6. Rebuild Android AssetBundles if the Unity version or rendering behavior changed.
7. Test on a physical ARM64 device.
8. Create a new `.npkg` with new per-file hashes.
9. Add a distinct catalog version entry with its own package size, hash, game version name, and game version code.

Never overwrite an older package while retaining its old metadata. Keeping separate version entries lets Nebula select, verify, update, and diagnose each supported build correctly.

## Build a portable managed plugin

Build the plugin as a framework-dependent managed assembly. Do not publish it as a self-contained Windows or desktop executable.

Recommended project properties include:

```xml
<PropertyGroup>
  <TargetFramework>net6.0</TargetFramework>
  <PlatformTarget>AnyCPU</PlatformTarget>
  <Prefer32Bit>false</Prefer32Bit>
  <PublishReadyToRun>false</PublishReadyToRun>
  <SelfContained>false</SelfContained>
  <Nullable>enable</Nullable>
</PropertyGroup>
```

Use the target framework required by the BepInEx and game-library versions in your project. `net6.0` is a common plugin target; a different compatible managed target is acceptable. The important requirements are portable IL, no desktop ReadyToRun image, and no bundled desktop runtime.

A typical plugin declaration remains the normal BepInEx IL2CPP form:

```csharp
using BepInEx;
using BepInEx.Unity.IL2CPP;

[BepInPlugin("com.example.my-mod", "My Mod", "1.0.0")]
public sealed class MyModPlugin : BasePlugin
{
    public override void Load()
    {
        Log.LogInfo("My Mod loaded on Nebula");
    }
}
```

Reference game and IL2CPP interop assemblies for the supported Android Among Us build. Do not package those generated game assemblies inside your mod.

### Native code

If the mod uses native code, provide an Android ARM64 `.so` built for `arm64-v8a` with the Android NDK. A Windows `.dll`, Linux desktop `.so`, macOS `.dylib`, or `x86_64` Android library cannot load in Nebula.

Avoid hard-coded Windows DLL names and paths. Select the native library by platform, and keep the managed P/Invoke signature compatible with Android.

## APIs and patterns to avoid

Review every dependency as well as your own code. Common Android failures include:

- `System.Windows.Forms`, WPF, Windows Registry, COM, or Win32 P/Invoke
- Windows path separators, drive letters, or case-insensitive filename assumptions
- Writing beside the original game APK
- Loading an x64 ReadyToRun or mixed-mode assembly
- Desktop-only Steam, Discord, or platform SDK integrations
- Synchronous Unity APIs stripped from the Android IL2CPP player
- AssetBundles built only for Windows
- Shaders compiled only for desktop graphics APIs
- Reflection against members that changed in the supported Among Us build
- Starting background work that assumes unrestricted desktop filesystem access

Use BepInEx paths such as `Paths.PluginPath`, `Paths.ConfigPath`, and `Paths.BepInExRootPath` instead of constructing desktop paths. Treat filenames as case-sensitive and keep writable data inside the paths supplied by the runtime.

### Unity assets

Build AssetBundles for Android, not Windows. Test their shaders and materials on an actual Android device. Unsupported shaders often appear bright pink, black, invisible, or heavily blurred.

Some Android Unity players strip synchronous internal calls while retaining asynchronous alternatives. Prefer supported public asynchronous APIs such as `AssetBundle.LoadAssetAsync` when practical. Never depend directly on private Unity `*_Internal` calls.

### Optional integrations

Guard optional desktop features by runtime and type availability. If a Discord, Steam, keyboard, controller, or platform class is absent, the mod should disable only that feature instead of failing plugin startup.

## Test the raw DLL first

Nebula debug builds expose **Upload DLL** in the Mod Store. Use it for early testing:

1. Build the portable plugin DLL.
2. Open a Nebula debug build.
3. Select the profile you use for development.
4. Open **Mod Store** and choose **Upload DLL**.
5. Select the DLL and launch Among Us through Nebula.
6. Inspect Android Logcat and the BepInEx log.

A successful test should show both plugin discovery and completed initialization, for example:

```text
[Info : BepInEx] Loading [My Mod 1.0.0]
[Info : My Mod] My Mod loaded on Nebula
[Message: BepInEx] Chainloader startup complete
```

`Loading [My Mod ...]` alone is not proof that the plugin initialized successfully. Read the following lines for exceptions.

Useful failure signatures include:

| Log message | Likely cause |
| --- | --- |
| `assembly architecture is not compatible with the current process architecture` | ReadyToRun, mixed-mode, x64, or other non-portable assembly |
| `DllNotFoundException` or `BadImageFormatException` | Missing or incorrectly built native dependency |
| `ICall ... was not resolved` | Unity internal call is stripped or changed on Android |
| Pink graphics | Unsupported shader or desktop AssetBundle |
| Black or missing graphics | Incomplete UI initialization, unsupported material, sorting, mask, or asset-load path |
| `TypeLoadException` or `MissingMethodException` | Wrong game, BepInEx, Reactor, MiraAPI, or interop version |

Test startup, menus, freeplay, hosting, joining, meetings, end-game flow, reconnects, orientation changes, and a second launch. A mod can reach the main menu and still fail during gameplay.

## Create a Nebula package

Mod Store packages use the `.npkg` extension and are ZIP archives with this shape:

```text
my-mod-1.0.0.npkg
├── manifest.json
└── payload/
    ├── MyMod.dll
    └── optional-android-assets.bundle
```

Every payload file is declared separately with its uncompressed size and SHA-256 digest. Destinations must stay under `plugins/`.

Example `manifest.json`:

```json
{
  "format": "nebula-package",
  "formatVersion": 1,
  "id": "com.example.my-mod",
  "name": "My Mod",
  "version": "1.0.0",
  "author": "Example Team",
  "description": "Android ARM64 support for My Mod.",
  "licenses": [
    "GPL-3.0-only",
    "MIT"
  ],
  "licenseComponents": {
    "GPL-3.0-only": ["My Mod"],
    "MIT": ["Example Library"]
  },
  "githubRepos": [
    {
      "name": "My Mod",
      "url": "https://github.com/example/my-mod"
    },
    {
      "name": "Example Library",
      "url": "https://github.com/example/example-library"
    }
  ],
  "platform": {
    "os": "android",
    "architecture": "arm64-v8a",
    "runtime": "fusioncore",
    "gamePackage": "com.innersloth.spacemafia",
    "gameVersion": "2026.6.5",
    "gameVersionCode": 7045
  },
  "install": {
    "targetRoot": "activeProfile",
    "restartRequired": true,
    "files": [
      {
        "source": "payload/MyMod.dll",
        "destination": "plugins/MyMod.dll",
        "size": 123456,
        "sha256": "replace-with-the-lowercase-sha256-of-MyMod.dll"
      }
    ]
  }
}
```

Set top-level `requires_reinstall` to `true` only when the update must regenerate BepInEx or IL2CPP support files:

```json
{
  "requires_reinstall": true
}
```

This is intentionally different from an ordinary plugin restart. Most DLL-only updates should not request a full reinstall.

### Manifest rules

- `id` is the stable package identifier and must not change between releases.
- `version` must match the version entry in the Nebula catalog.
- `licenses` contains one or more valid SPDX identifiers.
- `licenseComponents` maps every license to the components using it.
- `githubRepos` may contain multiple named HTTPS repository links.
- `platform` must describe Android, ARM64, FusionCore, the game package, and the exact supported game version.
- `targetRoot` must be `activeProfile`.
- Every destination must be inside `plugins/` and must not escape it with `..` or absolute paths.
- Every file needs the exact byte size and lowercase SHA-256 digest.

Nebula rejects a package if its identity, version, licenses, repositories, platform, file metadata, or catalog entry do not agree.

## Catalog metadata

Each mod release has its own version metadata. Do not reuse the size, hash, or supported game version from another release.

```json
{
  "version": "1.0.0",
  "gameVersion": "2026.6.5",
  "gameVersionCode": 7045,
  "file": "my-mod-1.0.0.npkg",
  "size": 234567,
  "sha256": "replace-with-the-lowercase-sha256-of-the-entire-npkg",
  "updatedAt": 1785754680000
}
```

The catalog entry also supplies the name, author, description, category, icon URL, licenses, component attribution, repository links, latest version, Android availability, and enabled state shown by the Mod Store.

The package manifest describes files inside the package. The catalog digest describes the complete `.npkg` download. They are different hashes.

## Licensing requirements

Declare the license for your mod and every redistributed dependency or asset. Use SPDX identifiers such as `GPL-3.0-only`, `LGPL-3.0-only`, `LGPL-2.1-only`, `MIT`, or `Apache-2.0` when those identifiers accurately describe the component.

Multiple licenses and repositories are supported. Nebula downloads needed license texts and shows which installed mods use them. Repository names in `githubRepos` are also the names users can tap from the license page.

Packaging a dependency can create source, notice, attribution, relinking, or license-text obligations. Meeting Nebula's manifest format does not by itself satisfy those obligations. Review every dependency's license before distributing it.

## Produce hashes and sizes

On macOS:

```sh
stat -f '%z' MyMod.dll
shasum -a 256 MyMod.dll
stat -f '%z' my-mod-1.0.0.npkg
shasum -a 256 my-mod-1.0.0.npkg
```

On Linux:

```sh
stat -c '%s' MyMod.dll
sha256sum MyMod.dll
stat -c '%s' my-mod-1.0.0.npkg
sha256sum my-mod-1.0.0.npkg
```

Create the archive with `manifest.json` and `payload/` at the archive root. After creating it, verify its contents:

```sh
unzip -l my-mod-1.0.0.npkg
unzip -p my-mod-1.0.0.npkg manifest.json
```

## Requesting Mod Store inclusion

Before asking the Nebula maintainers to add a release, provide:

- The `.npkg` file
- The complete catalog entry
- Source and release repository links
- License identifiers and component attribution
- Supported Among Us version name and Android version code
- Test device model and Android version
- A BepInEx log showing clean plugin initialization and chainloader completion
- Reproduction instructions for the mod's major features
- Any known Android limitations

The maintainers may request changes or a compatibility patch when a dependency assumes a desktop-only Unity or operating-system API. NebulaCompat can bridge narrow runtime differences, but mod-side portable code is preferred whenever the mod can implement the fix itself.

## Compatibility checklist

- [ ] Plugin is portable managed IL, not desktop ReadyToRun or mixed-mode code
- [ ] Every native dependency is Android `arm64-v8a`
- [ ] Built and tested against the exact supported Among Us Android build
- [ ] Uses BepInEx/FusionCore paths instead of Windows paths
- [ ] Optional desktop integrations fail gracefully
- [ ] AssetBundles, shaders, and materials are built and tested for Android
- [ ] Startup reaches `Chainloader startup complete` without plugin exceptions
- [ ] Gameplay, meetings, and end-game flow were tested on a physical device
- [ ] Manifest file sizes and hashes match every payload file
- [ ] Catalog metadata matches the complete `.npkg`
- [ ] All licenses, components, and repositories are declared
- [ ] `requires_reinstall` is enabled only when regeneration is genuinely required
