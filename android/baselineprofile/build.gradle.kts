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

// Placeholder for the generated startup profile.
// Generate on device via :benchmark:BaselineProfileGenerator, then replace
// src/main/baseline-prof.txt with the real rules and upgrade this module to
// the androidx.baselineprofile plugin once the Studio toolchain is verified.
// Until then this module builds cleanly and makes no performance claims.

dependencies {
    implementation(libs.androidx.profileinstaller.profileinstaller)
}
