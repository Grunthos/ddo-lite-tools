plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// The VB6 data folder at the repository root, shared by both implementations.
val dataDir: File = rootDir.resolve("../Data").canonicalFile

// Sample builds saved by VB6, used by the .build round-trip tests.
val saveDir: File = rootDir.resolve("../Save").canonicalFile

// More VB6 builds kept for the tests only: TEST.build (level 36) was removed from Save/.
val testBuildsDir: File = rootDir.resolve("test-builds").canonicalFile

subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("ddolite.dataDir", dataDir.absolutePath)
        systemProperty("ddolite.saveDir", saveDir.absolutePath)
        systemProperty("ddolite.testBuildsDir", testBuildsDir.absolutePath)
        inputs.dir(saveDir)
        inputs.dir(testBuildsDir)
    }
}

// Test coverage of the shared modules' JVM tests: ./gradlew jvmCoverage writes
// build/reports/jacoco/jvmCoverage in each module (HTML, XML and CSV).
subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        apply(plugin = "jacoco")
        afterEvaluate {
            val jvmTest = tasks.findByName("jvmTest") as? Test
            if (jvmTest != null) {
                tasks.register<JacocoReport>("jvmCoverage") {
                    dependsOn(jvmTest)
                    executionData(jvmTest)
                    classDirectories.setFrom(layout.buildDirectory.dir("classes/kotlin/jvm/main"))
                    sourceDirectories.setFrom("src/commonMain/kotlin", "src/jvmMain/kotlin")
                    reports {
                        xml.required.set(true)
                        csv.required.set(true)
                        html.required.set(true)
                    }
                }
            }
        }
    }
}
