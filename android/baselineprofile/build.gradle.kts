plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.fusion.baselineprofile"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Actual measured first-party profile: provenance and limitations in PROVENANCE.md.
// Android library profile merge is used; no baselineprofile Gradle plugin is required here.

dependencies {
    implementation(libs.androidx.profileinstaller.profileinstaller)
}
