// Minimal root build so `./gradlew :core:test` works before A's skeleton lands.
// A's skeleton adds `include(":app")` and the Android plugins.
pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral(); google() }
}
dependencyResolutionManagement {
    repositories { mavenCentral(); google() }
}
rootProject.name = "m26-qidk"
include(":core")
