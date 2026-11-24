# R144 — 10 new bounded kernels + differential oracles

## A. Scope

Ten new mechanism-distinct bounded implementations, each with an independent
Python differential test oracle. No existing code was modified, removed, or
degraded.

| # | Module | Kernel | What it does | Bounds |
|---|--------|--------|--------------|---------|
| 1 | `:core:files` | `BomDetector.kt` | UTF BOM detection/stripping: UTF-8/16LE/16BE/32LE/32BE; longer BOMs win | 1 MiB input, 5 BOMs |
| 2 | `:core:model` | `VersionRange.kt` | SemVer range matcher: >=,<=,==,!=,>,<,^ (caret),~ (tilde), bare == | 64-char range, 3 components |
| 3 | `:core:commands` | `CommandRouter.kt` | Bounded command registry with allowlist, arity caps, required flags | 128 commands, 16 args, 8 flags |
| 4 | `:core:storage` | `AtomicWriteJournal.kt` | Write-ahead journal: record/commit/rollback/pending status | 1024 entries, 4K path, SHA-256 hash |
| 5 | `:core:hw` | `CpuidParser.kt` | CPUID leaf/sub-leaf register store; vendor/brand strings; feature flags by bit | 64 leaves, 16 sub-leaves |
| 6 | `:core:index` | `BoyerMooreSearch.kt` | Boyer-Moore-Horspool substring search with bad-character shift table | 1 MiB haystack, 4K needle |
| 7 | `:core:documents` | `PlainTextNormalizer.kt` | Zero-width stripping, ASCII whitespace collapse, trim, optional lowercase | 64 KiB input |
| 8 | `:core:security` | `ConstantTimeCompare.kt` | Constant-time byte-array comparison (no early exit, length XORed in) | 64 KiB per array |
| 9 | `:core:settings` | `SettingsCatalog.kt` | Dotted-key settings catalog with put/get/remove/contains | 4096 entries, 256 key, 4K value |
| 10 | `:core:windowing` | `GeometryClamp.kt` | Window rectangle clamp to work area with min/max size, multi-display | 16 displays, 65536 coord |

Each kernel is declared-narrowing: explicit input caps, refusal on overflow,
no implicit behavior, no runtime dependencies beyond stdlib.

## B. Evidence

| Artifact | Path |
|---|---|
| `BomDetector` impl | `android/core/files/src/main/kotlin/dev/fusion/files/BomDetector.kt` |
| `BomDetector` oracle | `android/tools/test_bom_detector.py` (13 tests) |
| `VersionRange` impl | `android/core/model/src/main/kotlin/dev/fusion/model/VersionRange.kt` |
| `VersionRange` oracle | `android/tools/test_version_range.py` (14 tests) |
| `CommandRouter` impl | `android/core/commands/src/main/kotlin/dev/fusion/commands/CommandRouter.kt` |
| `CommandRouter` oracle | `android/tools/test_command_router.py` (13 tests) |
| `AtomicWriteJournal` impl | `android/core/storage/src/main/kotlin/dev/fusion/storage/AtomicWriteJournal.kt` |
| `AtomicWriteJournal` oracle | `android/tools/test_atomic_write_journal.py` (12 tests) |
| `CpuidParser` impl | `android/core/hw/src/main/kotlin/dev/fusion/hw/CpuidParser.kt` |
| `CpuidParser` oracle | `android/tools/test_cpuid_parser.py` (11 tests) |
| `BoyerMooreSearch` impl | `android/core/index/src/main/kotlin/dev/fusion/index/BoyerMooreSearch.kt` |
| `BoyerMooreSearch` oracle | `android/tools/test_boyer_moore_search.py` (14 tests) |
| `PlainTextNormalizer` impl | `android/core/documents/src/main/kotlin/dev/fusion/documents/PlainTextNormalizer.kt` |
| `PlainTextNormalizer` oracle | `android/tools/test_plain_text_normalizer.py` (12 tests) |
| `ConstantTimeCompare` impl | `android/core/security/src/main/kotlin/dev/fusion/security/ConstantTimeCompare.kt` |
| `ConstantTimeCompare` oracle | `android/tools/test_constant_time_compare.py` (8 tests) |
| `SettingsCatalog` impl | `android/core/settings/src/main/kotlin/dev/fusion/settings/SettingsCatalog.kt` |
| `SettingsCatalog` oracle | `android/tools/test_settings_catalog.py` (12 tests) |
| `GeometryClamp` impl | `android/core/windowing/src/main/kotlin/dev/fusion/windowing/GeometryClamp.kt` |
| `GeometryClamp` oracle | `android/tools/test_geometry_clamp.py` (13 tests) |

## C. Gate result

All 133 new Python differential-oracle tests pass (13+14+13+12+11+14+12+8+12+13).

Prior test suite unaffected: all 301 Python host tests pass (zero failures).
R104 Studio Readiness Gate passes.

No Kotlin compiler/Android SDK locally — CI is sole compiler/verifier. Kotlin
-Werror execution and Android device gates cannot run locally; first real
build is on a laptop per R104 readiness gate.

## D. Module counts vs full acceptance (unchanged by R144)

| Metric | Count | Percent |
|---|---|---|
| Modules with testable subset | 50 / 50 | 100.00% |
| Modules Class A / fully accepted | 0 / 50 | 0.00% |
| Modules not established | 0 / 50 | 0.00% |

R144 deepens 10 existing modules with additional bounded kernels; it does not
create new modules or change Class A status.

## E. Honest limitations

- No Kotlin/Java/Gradle/Android SDK locally; CI is sole compiler/verifier.
- Android/device/AX/performance gates unrun — no Class A claim possible without device testing.
- 6 `not_implemented` requirements remain hardware/legal-blocked (Shizuku/ADB, AVF VM, AirPlay/FairPlay GPL APK, Wine/Box64 APK).
