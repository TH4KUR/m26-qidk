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
        // Production default, unchanged: the kit is arm64 only and needs the Hexagon DSP runtime (see AndroidManifest.xml).
        // `-PlocalUi=true` builds a separate x86_64 app (`.localui` id, DSP library optional) so the Compose UI can run on a desktop
        // emulator with the fake engines; it is opt-in so `assembleDebug` / `installDebug` for the kit behave exactly as before.
        val localUi = providers.gradleProperty("localUi").orNull == "true"
        if (localUi) {
            applicationId = "com.megathon.incident.localui"
            ndk { abiFilters.add("x86_64") }
        } else {
            ndk { abiFilters.add("arm64-v8a") }
        }
        manifestPlaceholders["dspRuntimeRequired"] = (!localUi).toString()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // GenieX and the QNN runtime ship .so files that must be extracted to disk to be loaded/executed.
    packaging { jniLibs { useLegacyPackaging = true } }

    // The sherpa-onnx speech CLI and ONNX Runtime are staged locally by `app/scripts/speech_bundle.sh stage` and are not
    // in git. Packed only when present, so a clean checkout still builds.
    val speechLibs = file("src/speech/jniLibs")
    if (speechLibs.exists()) sourceSets.getByName("main").jniLibs.srcDir(speechLibs)
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
    implementation(libs.androidx.compose.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
