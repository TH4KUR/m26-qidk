pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "incident-assistant"

// :core is plain Kotlin/JVM: it builds with JDK 17 and Gradle only (no Android SDK, no Android Studio).
include(":core")

// :app needs the Android SDK. Include it only when one is found, so laptops without the SDK can still
// run `./gradlew :core:test`. Override with -PincludeApp=true (force on) or -PincludeApp=false (force off).
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").takeIf { it.exists() }?.readText()?.contains("sdk.dir") == true
val includeApp = providers.gradleProperty("includeApp").map { it.toBoolean() }.getOrElse(hasAndroidSdk)
if (includeApp) include(":app")
