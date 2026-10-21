pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FusionShell"
include(
    ":app",
    ":privacy", // Separate no-INTERNET offline companion APK; not a Tails replacement.
    // core
    ":core:documents",
    ":core:fileops",
    ":core:windowing",
    ":core:files",
    ":core:capabilities",
    ":core:model",
    ":core:commands",
    ":core:settings",
    ":core:security",
    ":core:perf",
    ":core:hw",
    ":core:index",
    ":core:storage",
    ":core:designsystem",
    // features
    ":feature:editor",
    ":feature:launcher",
    ":feature:find",
    ":feature:files",
    ":feature:browser",
    ":feature:office",
    ":feature:quickpanel",
    ":feature:photos",
    ":feature:ime",
    ":feature:terminal",
    ":feature:vault",
    ":feature:notes",
    ":feature:calc",
    ":feature:settings",
    ":feature:media",
    ":feature:maps",
    ":feature:beam",
    ":feature:alerts",
    ":feature:capture",
    ":feature:backup",
    ":feature:continuity",
    ":feature:accessibility",
    ":feature:together",
    ":feature:lens",
    ":feature:messages",
    ":feature:automation",
    // formats
    ":format:native",
    ":format:archive",
    ":format:ooxml",
    ":format:poi",
    ":format:pdf",
    ":format:image",
    ":format:psd",
    ":format:lok",
    // cast
    ":cast:dlna",
    ":cast:airplay",
    ":cast:castv2",
    // performance
    ":benchmark",
    ":baselineprofile",
    // shared test fixtures
    ":testing"
)
