plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.fusion.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        missingDimensionStrategy("distribution", "pro")
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Macrobenchmark drives the real app process rather than instrumenting it.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            devices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34").apply {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "google"
                }
            }
        }
    }

    buildTypes {
        // Must mirror the app's non-debuggable, profileable variant.
        create("benchmark") {
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.espresso.core)
    implementation(libs.androidx.test.uiautomator.uiautomator)
    implementation(libs.androidx.benchmark.benchmark.macro.junit4)
}
