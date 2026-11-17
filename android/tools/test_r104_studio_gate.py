#!/usr/bin/env python3
"""Controls for `check_r104_studio.py`.

A gate that never fails is not a gate. Each test builds a minimal, valid project tree in a temporary directory,
asserts the gate accepts it, then breaks exactly one thing and asserts the gate names that thing. The fixture is a
few hundred bytes of text; it is not the real project, so these tests prove the *rules* hold, and the real project
is checked by running the gate itself.
"""
from __future__ import annotations

import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_r104_studio import check  # noqa: E402

CATALOG = """\
[versions]
agp = "9.1.1"
kotlin = "2.2.21"
compileSdk = "37"
targetSdk = "37"
minSdk = "34"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
"""

WRAPPER = """\
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-9.3.1-bin.zip
distributionSha256Sum=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06
"""

SETTINGS = """\
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
include(":app")
"""

PROPERTIES = """\
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.builtInKotlin=false
android.newDsl=false
"""

APP_BUILD = """\
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.fusion.shell"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    productFlavors {
        create("play") { }
        create("pro") { }
    }
}
"""

MANIFEST = """\
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""


def build(root: Path, **overrides) -> Path:
    files = {
        'gradle/libs.versions.toml': overrides.get('catalog', CATALOG),
        'gradle/wrapper/gradle-wrapper.properties': overrides.get('wrapper', WRAPPER),
        'settings.gradle.kts': overrides.get('settings', SETTINGS),
        'gradle.properties': overrides.get('properties', PROPERTIES),
        'app/build.gradle.kts': overrides.get('app', APP_BUILD),
        'app/src/main/AndroidManifest.xml': overrides.get('manifest', MANIFEST),
        'gradlew': '#!/bin/sh\n',
    }
    for name, content in files.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
    return root


class StudioGateTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def problems(self, **overrides) -> list[str]:
        return check(build(self.root, **overrides))

    def assertProblem(self, fragment: str, **overrides) -> None:
        found = self.problems(**overrides)
        self.assertTrue(any(fragment in problem for problem in found), f'expected {fragment!r} in {found}')

    def test_the_valid_fixture_passes(self):
        self.assertEqual([], self.problems())

    def test_agp_outside_the_rabbit_1_range(self):
        catalog = CATALOG.replace('agp = "9.1.1"', 'agp = "9.5.0"')
        self.assertProblem('outside Rabbit 1', catalog=catalog)

    def test_gradle_below_the_plugin_minimum(self):
        wrapper = WRAPPER.replace('gradle-9.3.1-bin.zip', 'gradle-8.13-bin.zip')
        self.assertProblem('needs Gradle 9.3', wrapper=wrapper)

    def test_a_wrapper_without_description_checksum(self):
        wrapper = '\n'.join(line for line in WRAPPER.splitlines() if 'Sha256' not in line) + '\n'
        self.assertProblem('checksum', wrapper=wrapper)

    def test_a_kotlin_target_below_jdk_17(self):
        app = APP_BUILD.replace('jvmTarget = "17"', 'jvmTarget = "11"')
        self.assertProblem('below JDK 17', app=app)

    def test_a_java_target_below_jdk_17(self):
        app = APP_BUILD.replace('VERSION_17', 'VERSION_11', 1)
        self.assertProblem('below JDK 17', app=app)

    def test_a_heap_too_small_for_the_module_count(self):
        properties = PROPERTIES.replace('-Xmx3072m', '-Xmx1024m')
        self.assertProblem('below 2048m', properties=properties)

    def test_compile_sdk_above_the_plugin_maximum(self):
        catalog = CATALOG.replace('compileSdk = "37"', 'compileSdk = "38"')
        self.assertProblem('above the newest API', catalog=catalog)

    def test_a_module_without_a_namespace(self):
        app = APP_BUILD.replace('    namespace = "dev.fusion.shell"\n', '')
        self.assertProblem('no namespace', app=app)

    def test_missing_androidx_flag(self):
        properties = PROPERTIES.replace('android.useAndroidX=true\n', '')
        self.assertProblem('android.useAndroidX', properties=properties)

    def test_missing_kotlin_bridge_declaration(self):
        properties = PROPERTIES.replace('android.builtInKotlin=false\n', '')
        self.assertProblem('Kotlin bridge', properties=properties)

    def test_a_launcher_that_is_not_exported(self):
        manifest = MANIFEST.replace('android:exported="true"', 'android:exported="false"')
        self.assertProblem('not exported', manifest=manifest)

    def test_a_missing_launcher(self):
        manifest = MANIFEST.replace('android.intent.category.LAUNCHER', 'android.intent.category.DEFAULT')
        self.assertProblem('no MAIN/LAUNCHER activity', manifest=manifest)

    def test_a_missing_flavor(self):
        app = APP_BUILD.replace('        create("pro") { }\n', '')
        self.assertProblem('flavor', app=app)

    def test_project_level_repositories(self):
        settings = SETTINGS.replace('    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n', '')
        self.assertProblem('project-level repositories', settings=settings)

    def test_a_missing_repository(self):
        settings = SETTINGS.replace('        gradlePluginPortal()\n', '')
        self.assertProblem('gradlePluginPortal', settings=settings)


if __name__ == '__main__':
    unittest.main()
