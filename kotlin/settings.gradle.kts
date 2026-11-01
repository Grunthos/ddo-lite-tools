rootProject.name = "ddo-lite-tools"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":data-format",
    ":common",
    ":builder-core",
    ":tools:validate",
    ":prototypes:skills-ui",
    ":prototypes:skills-desktop",
    ":prototypes:skills-android",
)
