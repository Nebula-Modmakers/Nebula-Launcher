plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven("${rootProject.projectDir}/local-repo")
    maven("https://jitpack.io")
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
    implementation(project(":lsplant"))
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("io.github.hexhacking:xdl:2.3.0")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.google.android.material:material:1.4.0")
    implementation("com.github.luben:zstd-jni:1.5.2-3@aar")
    implementation("org.apache.commons:commons-compress:1.20")
    implementation("org.tukaani:xz:1.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}

android {
    namespace = "dev.tates.nebula"
    compileSdk = 36

    buildFeatures {
        prefab = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        applicationId = "dev.tates.nebula"
        versionCode = 8
        versionName = "1.2.2"
        ndk {
            abiFilters.add("arm64-v8a")
        }
        proguardFile("proguard-unity.txt")
    }

    val releaseStorePath = providers.environmentVariable("NEBULA_RELEASE_STORE_FILE").orNull
    val releaseStorePassword = providers.environmentVariable("NEBULA_RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("NEBULA_RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("NEBULA_RELEASE_KEY_PASSWORD").orNull
    if (listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
            .all { !it.isNullOrBlank() }) {
        signingConfigs {
            create("production") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfigs.findByName("production")?.let { signingConfig = it }
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += listOf("*/arm64-v8a/*.so")
        }
    }

    lint {
        abortOnError = false
    }
    ndkVersion = "28.2.13676358"
}
