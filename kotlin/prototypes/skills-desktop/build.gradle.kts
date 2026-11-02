// Plan step F1: desktop window for the skills screen prototype.
// Run with: ./gradlew :prototypes:skills-desktop:run
// Portable app (folder with its own Java runtime, Data/ and Settings/ beside the .exe):
//   ./gradlew :prototypes:skills-desktop:portableZip   → build/portable/
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

// The VB Builder's resource file (class icons and other bitmaps), which VB builds into its .exe;
// the app reads it from the classpath (Main.kt loadClassIcons).
val vbResources = tasks.register<Sync>("vbResources") {
    from(rootDir.resolve("../Source/CharacterBuilderLite.RES"))
    into(layout.buildDirectory.dir("generated/vb-resources"))
}
sourceSets["main"].resources.srcDir(vbResources)

compose.desktop {
    application {
        mainClass = "ddolite.prototype.skills.desktop.MainKt"
        nativeDistributions {
            packageName = "CharacterBuilderPrototype"
            packageVersion = "0.1.0"
            description = "DDO Character Builder Lite - Kotlin prototype"
            vendor = "DDO Lite Tools"
        }
    }
}

// `gradlew run` reads Data/ and Settings/ straight from the repository.
// (Compose creates the run task after this script, so configure it when it appears.)
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    jvmArgs("-Dddolite.repoRoot=" + rootDir.resolve("..").canonicalPath)
}

// The portable app: createDistributable's folder (launcher + bundled runtime) plus the repository's
// Data/ and Settings/ beside the launcher, as in the VB release. The app finds them by looking
// next to its .exe (Main.kt findAppRoot). Built for the OS Gradle runs on.
val portableDir = layout.buildDirectory.dir("portable/CharacterBuilderPrototype")

val portableApp = tasks.register<Sync>("portableApp") {
    group = "distribution"
    description = "Portable app folder in build/portable (launcher, runtime, Data, Settings)"
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app/CharacterBuilderPrototype"))
    from(rootDir.resolve("../Data")) { into("Data") }
    from(rootDir.resolve("../Settings")) { into("Settings") }
    into(portableDir)
}

tasks.register<Zip>("portableZip") {
    group = "distribution"
    description = "Zips the portable app folder"
    dependsOn(portableApp)
    from(portableDir) { into("CharacterBuilderPrototype") }
    archiveFileName = "CharacterBuilderPrototype-" + System.getProperty("os.name").split(" ")[0].lowercase() + ".zip"
    destinationDirectory = layout.buildDirectory.dir("portable")
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
