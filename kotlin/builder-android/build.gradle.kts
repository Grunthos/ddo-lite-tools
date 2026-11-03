// The Character Builder on Android (an early demo of the same screens; phase G).
// Build with: ./gradlew :builder-android:assembleDebug
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ddolite.builder.android"
    compileSdk = libs.versions.android.composeCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "ddolite.builder"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1-demo"
    }

    // Ship the repository's Data and Settings folders inside the app, as plan §2a.3 describes
    // for mobile (no Data folder next to the program on a phone).
    sourceSets.getByName("main") {
        assets.directories.add(rootDir.resolve("../Data").canonicalPath)
        assets.directories.add(rootDir.resolve("../Settings").canonicalPath)
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    implementation(project(":builder-ui"))
    implementation(libs.androidx.activity.compose)
}
