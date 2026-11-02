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
    ":prototypes:skills-ui",
    ":prototypes:skills-desktop",
    ":prototypes:skills-android",
)
