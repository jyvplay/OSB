plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.fusion.files"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":core:documents"))
    // DocumentFile is part of SafRepository/TrashRepository public parameter and return types.
    api(libs.androidx.documentfile.documentfile)
    implementation(libs.kotlinx.kotlinx.coroutines.android)
}
