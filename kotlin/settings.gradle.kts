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
    ":tools:form-layout",
    ":builder-ui",
    ":builder-desktop",
    ":builder-android",
)
