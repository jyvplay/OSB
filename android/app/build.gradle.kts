plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.fusion.shell"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.fusion.shell"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "0.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        // Non-debuggable but installable build used by :benchmark. Debuggable
        // builds produce misleading performance numbers.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isProfileable = true
            isMinifyEnabled = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    testOptions {
        managedDevices {
            devices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34").apply {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    // core
    implementation(project(":core:windowing"))
    implementation(project(":core:files"))
    implementation(project(":core:capabilities"))
    implementation(project(":core:model"))
    implementation(project(":core:commands"))
    implementation(project(":core:settings"))
    implementation(project(":core:security"))
    implementation(project(":core:perf"))
    implementation(project(":core:hw"))
    implementation(project(":core:index"))
    implementation(project(":core:storage"))
    implementation(project(":core:designsystem"))
    // features
    implementation(project(":feature:editor"))
    implementation(project(":feature:launcher"))
    implementation(project(":feature:find"))
    implementation(project(":feature:files"))
    implementation(project(":feature:browser"))
    implementation(project(":feature:terminal"))
    implementation(project(":feature:vault"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:calc"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:media"))
    implementation(project(":feature:beam"))
    implementation(project(":feature:alerts"))
    implementation(project(":feature:capture"))
    implementation(project(":feature:backup"))
    implementation(project(":feature:continuity"))
    implementation(project(":feature:accessibility"))
    implementation(project(":feature:together"))
    implementation(project(":feature:lens"))
    implementation(project(":feature:messages"))
    implementation(project(":feature:automation"))
    // formats
    implementation(project(":format:native"))
    implementation(project(":format:archive"))
    implementation(project(":format:ooxml"))
    implementation(project(":format:pdf"))
    implementation(project(":format:image"))
    implementation(project(":format:psd"))
    implementation(project(":format:lok"))
    // cast
    implementation(project(":cast:dlna"))
    implementation(project(":cast:airplay"))
    implementation(project(":cast:castv2"))
    // Ships the (currently empty) baseline profile via ProfileInstaller.
    implementation(project(":baselineprofile"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // ViewTree owners for the external-display Presentation's ComposeView.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
