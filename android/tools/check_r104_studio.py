#!/usr/bin/env python3
"""R104 Studio-readiness gate: can this project be opened and built in Android Studio Rabbit 1?

Rabbit 1 is Android Studio 2026.2.1. Google's compatibility table (developer.android.com/build/releases/about-agp)
gives it an Android Gradle Plugin range of **7.1 to 9.4**, and the same page gives the minimum Gradle for each
plugin: 9.1 needs Gradle 9.3.1, 9.4 needs 9.6.0. The IDE runs Gradle on whichever "Gradle JDK" the user picks, and
AGP 9 requires JDK 17 or newer, so the project's byte-code target and the IDE's JDK have to agree at 17.

This gate reads the repository and checks those facts. It **cannot** prove the build works: there is no Android
SDK, no `javac`, no Gradle distribution and no emulator in this sandbox — `services.gradle.org`, `dl.google.com`
and `repo1.maven.org` are all unreachable here, so the wrapper cannot even download the distribution it pins. The
check therefore answers "does the checked-in configuration fall inside the documented matrix, and is everything a
fresh clone needs present?" and prints, in its own last lines, everything it did *not* verify.

Exit status is non-zero on the first violation, so CI can hold the line without an SDK.
"""
from __future__ import annotations

import argparse
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Android Studio Rabbit 1 (2026.2.1): AGP 7.1-9.4. Source: developer.android.com/build/releases/about-agp.
RABBIT_1_AGP = ((7, 1), (9, 4))
# AGP -> minimum Gradle, from the same page. Only the rows that can matter for this project are kept.
MINIMUM_GRADLE = {
    (7, 1): (7, 2), (8, 0): (8, 0), (8, 1): (8, 0), (8, 2): (8, 2), (8, 3): (8, 4), (8, 4): (8, 6),
    (8, 5): (8, 7), (8, 6): (8, 7), (8, 7): (8, 9), (8, 8): (8, 10), (8, 9): (8, 11), (8, 10): (8, 11),
    (8, 11): (8, 13), (8, 12): (8, 13), (8, 13): (8, 13), (9, 0): (9, 1), (9, 1): (9, 3), (9, 2): (9, 4),
    (9, 3): (9, 5), (9, 4): (9, 6),
}
MAX_API = 37          # AGP 9.4's maximum compileSdk, per the same compatibility page.
MIN_JDK = 17          # AGP 9's minimum Gradle JDK; also what every module compiles to here.
GRADLE_JDK_RANGE = (17, 26)


def version(text: str) -> tuple[int, int]:
    parts = [int(p) for p in re.findall(r'\d+', text)[:2]]
    while len(parts) < 2:
        parts.append(0)
    return tuple(parts)  # type: ignore[return-value]


def minimum_gradle(agp: tuple[int, int]) -> tuple[int, int] | None:
    """The lowest table row at or below `agp`."""
    candidates = [row for row in MINIMUM_GRADLE if row <= agp]
    return MINIMUM_GRADLE[max(candidates)] if candidates else None


def read_modules(root: Path) -> dict[str, str]:
    settings = (root / 'settings.gradle.kts').read_text(encoding='utf-8')
    names = re.findall(r'"(:[\w:]+)"', settings)
    return {name: (root / name[1:].replace(':', '/') / 'build.gradle.kts').read_text(encoding='utf-8')
            for name in names}


ANDROID_PLUGINS = ('com.android.application', 'com.android.library', 'com.android.test')
ANDROID_ALIASES = ('libs.plugins.android.application', 'libs.plugins.android.library', 'libs.plugins.android.test')


def android_modules(modules: dict[str, str]) -> dict[str, str]:
    """Modules that apply an Android plugin, by literal id or by the version-catalog alias the build uses."""
    return {name: text for name, text in modules.items()
            if any(plugin in text for plugin in ANDROID_PLUGINS)
            or any(alias in text for alias in ANDROID_ALIASES)}


def check(root: Path = ROOT) -> list[str]:
    problems: list[str] = []
    catalog = tomllib.loads((root / 'gradle/libs.versions.toml').read_text(encoding='utf-8'))
    versions = catalog['versions']
    wrapper = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text(encoding='utf-8')
    properties = (root / 'gradle.properties').read_text(encoding='utf-8')
    settings = (root / 'settings.gradle.kts').read_text(encoding='utf-8')
    modules = read_modules(root)

    # 1. The IDE/plugin contract.
    agp = version(versions['agp'])
    if not RABBIT_1_AGP[0] <= agp <= RABBIT_1_AGP[1]:
        problems.append(f'AGP {versions["agp"]} is outside Rabbit 1\'s 7.1-9.4 range')
    url = next((line.split('=', 1)[1] for line in wrapper.splitlines() if line.startswith('distributionUrl=')), '')
    match = re.search(r'gradle-([\d.]+)-(bin|all)\.zip', url.replace('\\', ''))
    if match is None:
        problems.append(f'the wrapper URL names no Gradle distribution: {url!r}')
        gradle = (0, 0)
    else:
        gradle = version(match.group(1))
    floor = minimum_gradle(agp)
    if floor is not None and gradle < floor:
        problems.append(f'AGP {versions["agp"]} needs Gradle {floor[0]}.{floor[1]} or newer, '
                        f'the wrapper pins {gradle[0]}.{gradle[1]}')
    if 'distributionSha256Sum=' not in wrapper:
        problems.append('the wrapper pins no distribution checksum, so a swapped distribution would go unnoticed')

    # 2. The JDK the IDE will use.
    jdk_problem = []
    for name, text in modules.items():
        for interp in re.findall(r'jvmTarget\s*=\s*"([\d.]+)"', text):
            if version(interp) < (MIN_JDK, 0):
                jdk_problem.append(f'{name} targets JVM {interp}, below JDK {MIN_JDK}')
        for interp in re.findall(r'JavaVersion\.VERSION_(\d+)', text):
            if int(interp) < MIN_JDK:
                jdk_problem.append(f'{name} compiles with Java {interp}, below JDK {MIN_JDK}')
    problems.extend(jdk_problem)
    if not (GRADLE_JDK_RANGE[0] <= MIN_JDK <= GRADLE_JDK_RANGE[1]):
        problems.append('the JDK floor is outside the range Gradle 9.x runs on')
    if 'org.gradle.jvmargs' not in properties:
        problems.append('gradle.properties sets no org.gradle.jvmargs; the default heap is thin for 51 modules')
    else:
        heap = re.search(r'-Xmx(\d+)m', properties)
        if heap is None or int(heap.group(1)) < 2048:
            problems.append('org.gradle.jvmargs -Xmx is below 2048m')

    # 3. The Android modules themselves.
    compile_sdk = versions['compileSdk']
    if int(compile_sdk) > MAX_API:
        problems.append(f'compileSdk {compile_sdk} is above the newest API AGP 9.4 accepts ({MAX_API})')
    for name, text in android_modules(modules).items():
        if 'namespace' not in text:
            problems.append(f'{name} declares no namespace')
        if 'compileSdk' not in text and 'compileSdkVersion' not in text:
            problems.append(f'{name} declares no compileSdk')
    if 'android.useAndroidX=true' not in properties:
        problems.append('android.useAndroidX is not true, which breaks every AndroidX dependency')
    if 'android.builtInKotlin' not in properties:
        problems.append('the AGP 9 Kotlin bridge is not declared, so the KGP/AGP combination is accidental')

    # 4. What a fresh clone and the IDE's Run button need.
    if not (root / 'gradlew').is_file():
        problems.append('gradlew is missing')
    if not settings.startswith('pluginManagement') and 'pluginManagement {' not in settings:
        problems.append('settings.gradle.kts has no pluginManagement block, so AGP would not resolve')
    for repository in ('google()', 'mavenCentral()', 'gradlePluginPortal()'):
        if repository not in settings:
            problems.append(f'settings.gradle.kts does not offer {repository}')
    if 'RepositoriesMode.FAIL_ON_PROJECT_REPOS' not in settings:
        problems.append('project-level repositories are not refused, so a module could resolve from anywhere')
    app = modules.get(':app', '')
    for flavor in ('"play"', '"pro"'):
        if flavor not in app:
            problems.append(f':app does not declare the {flavor} flavor the documented commands build')
    if 'minSdk' not in catalog['versions'] or int(versions['minSdk']) < 24:
        problems.append('minSdk is below 24, which no maintained emulator image targets')
    launcher = launcher_activity(root / 'app/src/main/AndroidManifest.xml')
    if launcher is None:
        problems.append('no MAIN/LAUNCHER activity, so the IDE cannot run the app')
    else:
        name, exported = launcher
        if exported != 'true':
            problems.append(f'launcher activity {name} is not exported, so nothing can start it')

    if not (root / 'app/proguard-rules.pro').is_file() and 'proguardFiles' in app:
        problems.append('proguardFiles is configured but app/proguard-rules.pro is missing')
    return problems


def launcher_activity(manifest: Path) -> tuple[str, str | None] | None:
    import xml.etree.ElementTree as ET

    android = '{http://schemas.android.com/apk/res/android}'
    root = ET.parse(manifest).getroot()
    for activity in list(root.iter('activity')) + list(root.iter('activity-alias')):
        for intent_filter in activity.findall('intent-filter'):
            actions = {(action.get(android + 'name') or '') for action in intent_filter.findall('action')}
            categories = {(category.get(android + 'name') or '') for category in intent_filter.findall('category')}
            if ('android.intent.action.MAIN' in actions
                    and 'android.intent.category.LAUNCHER' in categories):
                return (activity.get(android + 'name') or '?', activity.get(android + 'exported'))
    return None


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--quiet', action='store_true', help='print only the verdict line')
    args = parser.parse_args(argv)
    problems = check()
    catalog = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text(encoding='utf-8'))
    wrapper = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text(encoding='utf-8')
    url = next(line.split('=', 1)[1] for line in wrapper.splitlines() if line.startswith('distributionUrl='))
    gradle = re.search(r'gradle-([\d.]+)-', url.replace('\\', '')).group(1)
    modules = read_modules(ROOT)
    for problem in problems:
        print(f'R104 studio FAILED: {problem}')
    if not args.quiet:
        floor = minimum_gradle(version(catalog['versions']['agp']))
        floor_text = f'{floor[0]}.{floor[1]}' if floor else '?'
        print(f'Rabbit 1 matrix: AGP {catalog["versions"]["agp"]} (allowed 7.1-9.4), Gradle {gradle} '
              f'(minimum for this AGP {floor_text}), JDK {MIN_JDK}+, '
              f'compileSdk {catalog["versions"]["compileSdk"]} (maximum {MAX_API})')
        print(f'{len(modules)} modules, {len(android_modules(modules))} Android modules; '
              f'launcher {launcher_activity(ROOT / "app/src/main/AndroidManifest.xml")}')
        print('NOT VERIFIED HERE: no Android SDK, JDK toolchain at the system path, Gradle distribution, '
              'emulator or device. The wrapper cannot download its distribution in this sandbox, so this gate '
              'checks configuration only - the first real build is on the laptop.')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
