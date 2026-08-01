plugins {
    id("com.android.library")
}

dependencies {
    compileOnly("org.lsposed.libcxx:libcxx:29.0.14206865")
}

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "org.lsposed.lsplant"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    buildFeatures {
        androidResources = false
        buildConfig = false
        prefab = true
        prefabPublishing = true
    }

    prefab {
        register("lsplant") {
            headers = "src/main/jni/include"
        }
    }

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters.add("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf(
                    "-Wno-gnu-string-literal-operator-template",
                    "-fno-exceptions",
                    "-fno-rtti",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
                arguments += listOf(
                    "-DANDROID_STL=none",
                    "-DLSPLANT_STANDALONE=ON",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DDEBUG_SYMBOLS_PATH=${layout.buildDirectory.dir("symbols").get().asFile.absolutePath}"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "*/arm64-v8a/*.so"
        }
    }
}
