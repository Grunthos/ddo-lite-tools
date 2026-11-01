plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

// The VB6 data folder at the repository root, shared by both implementations.
val dataDir: File = rootDir.resolve("../Data").canonicalFile

subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("ddolite.dataDir", dataDir.absolutePath)
    }
}
