plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

android {
    namespace = "com.megathon.incident"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.megathon.incident"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters.add("arm64-v8a") } // the kit is arm64 only
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // GenieX and the QNN runtime ship .so files that must be extracted to disk to be loaded/executed.
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.geniex.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
