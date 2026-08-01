# Third-party licenses

Nebula distributes native and managed runtime components under several
licenses. The complete license texts are packaged for offline viewing and are
available here:

- [GPL-3.0-only](nebulaApp/src/main/assets/licenses/GPL-3.0-only.txt)
- [LGPL-2.1-only](nebulaApp/src/main/assets/licenses/LGPL-2.1-only.txt)
- [LGPL-3.0-only](nebulaApp/src/main/assets/licenses/LGPL-3.0-only.txt)
- [MIT](nebulaApp/src/main/assets/licenses/MIT.txt)
- [Apache-2.0](nebulaApp/src/main/assets/licenses/Apache-2.0.txt)
- [LLVM exception to Apache-2.0](nebulaApp/src/main/assets/licenses/LLVM-exception.txt)
- [BSD-2-Clause](nebulaApp/src/main/assets/licenses/BSD-2-Clause.txt)
- [OFL-1.1](nebulaApp/src/main/assets/licenses/OFL-1.1.txt)
- [XZ for Java public-domain notice](nebulaApp/src/main/assets/licenses/LicenseRef-Public-Domain.txt)

## Copyleft components and corresponding source

- Nebula Launcher and its FusionCore-derived integration are GPL-3.0-only.
  The corresponding source is this repository.
- BepInEx Fusion is LGPL-2.1-only. The bundled binary identifies exact source
  commit `19539ed46393ee78f6d0181ae614b4d546526685`, available from
  <https://github.com/All-Of-Us-Mods/BepInExFusion/tree/19539ed46393ee78f6d0181ae614b4d546526685>.
- Il2CppInterop is LGPL-3.0-only. The bundled binary identifies exact source
  commit `c5148797017bb32906f89a7131b71fb5acbe18da`, available from
  <https://github.com/BepInEx/Il2CppInterop/tree/c5148797017bb32906f89a7131b71fb5acbe18da>.
- LSPlant is LGPL-3.0-only. Its corresponding source is vendored under
  `third_party/lsplant` from exact commit
  `84256d4cb51abd79280da5c29437fb7004391667`.
- DexBuilder is LGPL-3.0-only. Its source is vendored with LSPlant from exact
  commit `ac7fb2230954ee311808bad469b0db501f31bfb8`.

## Permissive runtime components

- MIT: .NET/CoreCLR, HarmonyX, AsmResolver, AssetRipper.CIL,
  AssetRipper.Primitives, Cpp2IL, Disarm, Capstone.NET, Iced, Mono.Cecil,
  MonoMod, SemanticVersioning, StableNameDotNet, and WasmDisassembler.
- Apache-2.0: OpenSSL 3.3.1, Dobby `b0176de`, AndroidHiddenApiBypass 6.1,
  and parallel-hashmap `0cd57d2`.
- Apache-2.0 WITH LLVM-exception: LLVM libc++ from Android NDK
  `29.0.14206865`, statically incorporated into LSPlant.
- BSD-2-Clause: zstd-jni `1.5.2-3`.
- OFL-1.1: Orbitron font.
- Public-domain declaration: XZ for Java 1.7.

Nebula uses its own GPL-3.0 Java/JNI adapter over LSPlant. Pine is not a
dependency and is not included in the APK.

Exact versions, embedded commit identifiers, copyright notices, archive
digests, and upstream links are recorded in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
