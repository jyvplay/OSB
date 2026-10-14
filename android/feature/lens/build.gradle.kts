plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "dev.fusion.lens"
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
    implementation(platform(libs.androidx.compose.compose.bom))
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.compose.foundation.foundation)
    implementation(libs.androidx.compose.material3.material3)
    implementation(libs.androidx.activity.activity.compose)
    implementation(libs.androidx.core.core.ktx)
    // Lifecycle-aware LocalLifecycleOwner used for CameraX binding.
    implementation(libs.androidx.lifecycle.lifecycle.runtime.compose)
    // CameraX: camera2 backend is required for ProcessCameraProvider to resolve a camera.
    implementation(libs.androidx.camera.camera.core)
    implementation(libs.androidx.camera.camera.camera2)
    implementation(libs.androidx.camera.camera.lifecycle)
    implementation(libs.androidx.camera.camera.view)
    implementation(libs.kotlinx.kotlinx.coroutines.android)
}
