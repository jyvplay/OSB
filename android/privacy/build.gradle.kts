plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "dev.fusion.privacy"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "dev.fusion.privacy"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions.managedDevices.devices {
        maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34").apply {
            device="Pixel 6";apiLevel=34;systemImageSource="google"
        }
    }
}
dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.uiautomator.uiautomator)
}
val verifyReleaseReadiness by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine(providers.gradleProperty("fusionPython").getOrElse("python3"),"tools/requirements.py","--release")
}
tasks.configureEach { if(name=="preReleaseBuild") dependsOn(verifyReleaseReadiness) }
