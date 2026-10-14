plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "dev.fusion.beam"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":core:model"))
    // ThermalState is part of BeamScreen + QualityGovernor public signatures.
    implementation(project(":core:perf"))
    // Real cast clients used by CastSendPanel (CA-02, CA-08).
    implementation(project(":cast:castv2"))
    implementation(project(":cast:airplay"))
    implementation(platform(libs.androidx.compose.compose.bom))
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.compose.foundation.foundation)
    implementation(libs.androidx.compose.material3.material3)
    implementation(libs.androidx.activity.activity.compose)
    implementation(libs.kotlinx.kotlinx.coroutines.android)
    testImplementation(libs.junit.junit)
}
