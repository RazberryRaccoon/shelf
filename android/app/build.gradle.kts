// Shelf for Android: a small Java shell around the Shelf interface (the same WebGL page as the prototype),
// with native code for finding ROMs and apps, launching emulators and passing in controller buttons.
plugins {
    id("com.android.application")
}

android {
    namespace = "com.razberryraccoon.shelf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.razberryraccoon.shelf"
        minSdk = 30          // Android 11, the Retroid Pocket 3+
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "dev")
    }

    // One fixed key for every build, so a new APK installs over the old one and keeps your data.
    // It's a debug key, checked in on purpose: this app is only ever sideloaded.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("shelf-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
