// Plan step F1 (Compose test drive): throwaway prototype of the skills screen.
// Shared UI code for desktop and Android; see prototypes/README.md.
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
        namespace = "ddolite.prototype.skills"
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
