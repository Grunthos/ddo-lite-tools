// The Character Builder's screens and their state (desktop and Android). Grew out of the plan's F1
// Compose test drive; the rules are in builder-core.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    jvm()

    android {
        namespace = "ddolite.builder.ui"
        compileSdk = libs.versions.android.composeCompileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":builder-core"))
            api(project(":common"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
