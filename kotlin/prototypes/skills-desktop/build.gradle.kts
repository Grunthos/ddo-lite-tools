// Plan step F1: desktop window for the skills screen prototype.
// Run with: ./gradlew :prototypes:skills-desktop:run
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    implementation(project(":prototypes:skills-ui"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "ddolite.prototype.skills.desktop.MainKt"
        // The repository root, so the prototype can find Data/ and Settings/.
        jvmArgs("-Dddolite.repoRoot=" + rootDir.resolve("..").canonicalPath)
    }
}

// Renders the prototype to PNG files in build/screenshots, without opening a window.
tasks.register<JavaExec>("screenshots") {
    group = "verification"
    description = "Render the skills prototype off-screen to build/screenshots"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "ddolite.prototype.skills.desktop.ScreenshotsKt"
    jvmArgs("-Dddolite.repoRoot=" + rootDir.resolve("..").canonicalPath)
    args(layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}
