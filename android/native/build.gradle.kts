// Shelf Native: Shelf rebuilt as a native Android app. Kotlin for the screens, Google's Filament engine for every 3D
// object (the Cartridge Studio's beveled plastic, studio lighting and see-through shells), and the code the WebView app
// already shares (:shared) for finding games and launching emulators. It installs next to the WebView Shelf.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.razberryraccoon.shelfnative"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.razberryraccoon.shelfnative"
        minSdk = 30          // Android 11, the Retroid Pocket 3+
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.25." + (System.getenv("GITHUB_RUN_NUMBER") ?: "dev")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }   // handhelds; leaves out the emulator-only builds
    }

    // The same fixed key as the WebView app, so updates install over the last version and keep your data.
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
    androidResources { noCompress += listOf("glb", "hdr") }   // read straight from the APK
}

dependencies {
    implementation(project(":shared"))
    implementation("com.google.android.filament:filament-android:1.75.1")   // 1.76+ needs compileSdk 37
    implementation("com.google.android.filament:gltfio-android:1.75.1")
    implementation("com.google.android.filament:filament-utils-android:1.75.1")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
