// Plugins are declared once here (apply false) so every subproject shares one classloader.
// Applying them is up to the subprojects; AGP is only applied by :app, which needs the Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
}
