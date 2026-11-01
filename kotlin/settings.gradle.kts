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
    ":builder-core",
    ":tools:validate",
)
