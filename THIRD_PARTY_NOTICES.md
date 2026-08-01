# Third-party notices

Nebula includes third-party software. Each component remains copyrighted by
its respective authors and is distributed under its own license. Complete
license texts are stored under `nebulaApp/src/main/assets/licenses` and can be
viewed offline in the app.

## Runtime source and reproducibility

The bundled `BepInEx-arm64.zip` has SHA-256
`e104c31226e6048addcfb29f7dd4df6e8030de5c1cd631f4d362641f41ed658f`.
Its BepInEx assemblies identify themselves as
`6.0.0-fusion.dev+19539ed46393ee78f6d0181ae614b4d546526685`.
The complete corresponding source and build files are available at:

<https://github.com/All-Of-Us-Mods/BepInExFusion/tree/19539ed46393ee78f6d0181ae614b4d546526685>

Nebula's FusionCore-derived Android integration source is included directly
in this repository under `nebulaApp/src/main/java/dev/allofus/fusioncore` and
`nebulaApp/src/main/jni/fusion`.

## Native and primary runtime components

| Component | Version/source identity | License | Upstream |
| --- | --- | --- | --- |
| FusionCore integration | Repository source | GPL-3.0-only | <https://github.com/All-Of-Us-Mods/FusionCore> |
| BepInEx Fusion | `6.0.0-fusion.dev+19539ed` | LGPL-2.1-only | <https://github.com/All-Of-Us-Mods/BepInExFusion/tree/19539ed46393ee78f6d0181ae614b4d546526685> |
| Il2CppInterop | `1.5.2-ci.star.12+c514879` | LGPL-3.0-only | <https://github.com/BepInEx/Il2CppInterop/tree/c5148797017bb32906f89a7131b71fb5acbe18da> |
| .NET/CoreCLR Android runtime | `10.0.5-dev` | MIT | <https://github.com/dotnet/runtime> |
| OpenSSL | `3.3.1` | Apache-2.0 | <https://github.com/openssl/openssl/tree/openssl-3.3.1> |
| Dobby | `b0176de` | Apache-2.0 | <https://github.com/jmpews/Dobby/tree/b0176de> |
| LLVM libc++ | Android NDK `29.0.14206865` | Apache-2.0 WITH LLVM-exception | <https://github.com/llvm/llvm-project/tree/main/libcxx> |

## Managed dependencies inside BepInEx-arm64.zip

The version and commit values below come from the assemblies' own product
metadata. Families cover all same-prefix DLLs in the archive.

| Shipped assembly family | Product version/source identity | License | Copyright / upstream |
| --- | --- | --- | --- |
| `0Harmony.dll` | `2.16.0+5939772` | MIT | BepInEx; Andreas Pardeike — <https://github.com/BepInEx/HarmonyX/tree/5939772c9fbef2554147c68f7ca81949b1ffade8> |
| `AsmResolver*.dll` | `6.0.0-beta.5+124e161` | MIT | Copyright © Washi 2016-2025 — <https://github.com/Washi1337/AsmResolver/tree/124e161a9bc0ebb83b4d97276ae3da3c9cfe2fc5> |
| `AssetRipper.CIL.dll` | `1.2.2+06216e6` | MIT | Copyright © 2025 ds5678 — <https://github.com/AssetRipper/AssetRipper.CIL/tree/06216e69825ccd6536dc17e9ca81c4d539ce9321> |
| `AssetRipper.Primitives.dll` | `3.2.0+d759b3d` | MIT | Copyright © 2022-2025 ds5678 — <https://github.com/AssetRipper/AssetRipper.Primitives/tree/d759b3db98bf2a1cb8c2181faf5d405610aac585> |
| `Cpp2IL.Core.dll`, `LibCpp2IL.dll`, `StableNameDotNet.dll`, `WasmDisassembler.dll` | `2022.1-development.1452+558ddd9` | MIT | Copyright © Samboy063 2019-2023 — <https://github.com/SamboyCoding/Cpp2IL/tree/558ddd98642010897d54316b51fbaa7889fda093> |
| `Disarm.dll` | `2022.1-master.99+8574010` | MIT | Copyright © Samboy063 2022 — <https://github.com/SamboyCoding/Disarm/tree/8574010015c8c56bb7faad7e407c50b0abe9cc72> |
| `Gee.External.Capstone.dll` | `2.3.2+b90e380` | MIT | Copyright © Ahmed Garhy — <https://github.com/ds5678/Capstone.NET/tree/b90e380c14e857865c94a355954236e678bd557c> |
| `Iced.dll` | `1.21.0+601c405` | MIT | Copyright © 2018-present iced contributors — <https://github.com/icedland/iced/tree/601c40570d6c986c8e1a6a2e483dd2fdf5ade552> |
| `Microsoft.*.dll` | `8.0.6` | MIT | © Microsoft Corporation — <https://github.com/dotnet/runtime> |
| `Mono.Cecil*.dll` | `0.11.6` | MIT | Copyright © 2008-2018 Jb Evain — <https://github.com/jbevain/cecil/tree/0.11.6> |
| `MonoMod*.dll` | Embedded versions `1.1.0` through `25.3.4-star+9da670d` | MIT | Copyright © 0x0ade, DaNike — <https://github.com/MonoMod/MonoMod> |
| `SemanticVersioning.dll` | `2.0.2` | MIT | Copyright © 2016 Adam Reeve — <https://github.com/adamreeve/semver.net> |

## Android application dependencies

Exact declared versions are maintained in `nebulaApp/build.gradle.kts`.

| Component family | Declared version | License | Upstream |
| --- | --- | --- | --- |
| Firebase Android Authentication | BoM `34.16.0` | Apache-2.0 | <https://github.com/firebase/firebase-android-sdk> |
| AndroidX Credentials, AppCompat, Preference, Annotation | `1.3.0`, `1.4.0`, `1.2.1`, `1.9.1` | Apache-2.0 | <https://github.com/androidx/androidx> |
| Google Identity / Google ID | `1.1.1` | Apache-2.0 | <https://developers.google.com/identity> |
| Material Components for Android | `1.4.0` | Apache-2.0 | <https://github.com/material-components/material-components-android> |
| Kotlin standard library | `2.3.20` | Apache-2.0 | <https://github.com/JetBrains/kotlin> |
| LSPlant | `84256d4` (vendored source) | LGPL-3.0-only | <https://github.com/LSPosed/LSPlant/tree/84256d4cb51abd79280da5c29437fb7004391667> |
| DexBuilder | `ac7fb22` (vendored with LSPlant) | LGPL-3.0-only | <https://github.com/LSPosed/DexBuilder/tree/ac7fb2230954ee311808bad469b0db501f31bfb8> |
| parallel-hashmap | `0cd57d2` (vendored with DexBuilder) | Apache-2.0 | <https://github.com/greg7mdp/parallel-hashmap/tree/0cd57d29a959256ed66b2afdd1009928fc625d09> |
| LLVM libc++ | Android NDK `29.0.14206865` (statically incorporated into LSPlant) | Apache-2.0 WITH LLVM-exception | <https://github.com/llvm/llvm-project/tree/main/libcxx> |
| AndroidHiddenApiBypass | `6.1` | Apache-2.0 | <https://github.com/LSPosed/AndroidHiddenApiBypass/tree/v6.1> |
| xDL | `2.3.0` | MIT | <https://github.com/hexhacking/xDL> |
| zstd-jni | `1.5.2-3` | BSD-2-Clause | <https://github.com/luben/zstd-jni> |
| Apache Commons Compress | `1.20` | Apache-2.0 | <https://github.com/apache/commons-compress> |
| XZ for Java | `1.7` | Public domain declaration | <https://tukaani.org/xz/java.html> |
| Orbitron font | Bundled TTF | OFL-1.1 | <https://github.com/theleagueof/orbitron> |

Nebula's `NebulaHook` Java/JNI adapter is part of this repository under
GPL-3.0-only. LSPlant supplies the ART hook engine under LGPL-3.0-only; Pine is
not linked, packaged, or required.

The archive digests and version identities in this notice describe the runtime
files distributed by this repository revision.
