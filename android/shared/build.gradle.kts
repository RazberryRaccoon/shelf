// What both Shelf apps share: finding ROMs and apps, launching emulators, handing ROM files over, and the art services.
// Plain Java with no Android libraries beyond the platform itself.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.razberryraccoon.shelf.shared"
    compileSdk = 35
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
