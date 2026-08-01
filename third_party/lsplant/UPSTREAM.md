# LSPlant upstream provenance

This directory vendors the LSPlant Android library source from
https://github.com/LSPosed/LSPlant at commit
`84256d4cb51abd79280da5c29437fb7004391667`.

Its `external/dex_builder` directory comes from
https://github.com/LSPosed/DexBuilder at commit
`ac7fb2230954ee311808bad469b0db501f31bfb8`.

DexBuilder's vendored `external/parallel_hashmap` dependency comes from
https://github.com/greg7mdp/parallel-hashmap at commit
`0cd57d29a959256ed66b2afdd1009928fc625d09`.

The local Gradle build file replaces upstream publishing and test build
configuration. The library source is otherwise kept at those revisions. The
standalone build incorporates LLVM libc++ from Android NDK `29.0.14206865`,
licensed under Apache-2.0 WITH LLVM-exception.
