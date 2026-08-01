pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":nebulaApp")
include(":lsplant")
project(":lsplant").projectDir = file("third_party/lsplant")
