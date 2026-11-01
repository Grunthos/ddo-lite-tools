plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    implementation(project(":builder-core"))
    testImplementation(kotlin("test"))
}

application {
    mainClass = "ddolite.tools.validate.MainKt"
}

// `gradlew :tools:validate:run` checks the repository's Data folder by default.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
    args(rootDir.resolve("../Data").canonicalPath)
}
