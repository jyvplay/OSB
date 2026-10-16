plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.fusion.shell"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.fusion.shell"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.7.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            applicationIdSuffix = ".play"
        }
        create("pro") {
            dimension = "distribution"
            applicationIdSuffix = ".pro"
        }
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
                    // R12: exercise the unchanged framebuffer assertions on a regular image.
                    // ATD returned transparent screenshot pixels despite working remote input.
                    systemImageSource = "google"
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
    // Ships measured first-party startup/Files rules; no physical speedup claim.
    implementation(project(":baselineprofile"))

    implementation(platform(libs.androidx.compose.compose.bom))
    implementation(libs.androidx.activity.activity.compose)
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.compose.foundation.foundation)
    implementation(libs.androidx.compose.material3.material3)
    implementation(libs.androidx.lifecycle.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.lifecycle.runtime.ktx)
    // ViewTree owners for the external-display Presentation's ComposeView.
    implementation(libs.androidx.lifecycle.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate.savedstate.ktx)
    implementation(libs.androidx.documentfile.documentfile)
    implementation(libs.kotlinx.kotlinx.coroutines.android)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator.uiautomator)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(platform(libs.androidx.compose.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.ui.test.manifest)
}

// Unknown licenses and unpassed device gates cannot silently become a release APK.
// Debug/benchmark source evaluation remains available; no release entitlement is fabricated.
val verifyReleaseReadiness by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine(providers.gradleProperty("fusionPython").getOrElse("python3"),
        "tools/requirements.py", "--release")
}
tasks.configureEach {
    if (name == "prePlayReleaseBuild" || name == "preProReleaseBuild") dependsOn(verifyReleaseReadiness)
}
