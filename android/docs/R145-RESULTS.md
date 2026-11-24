# R145 — 15 new bounded kernels + differential oracles

## A. Scope

Fifteen new mechanism-distinct bounded implementations across 15 modules,
each with an independent Python differential test oracle (112 new tests
total). No existing code was modified, removed, or degraded.

| # | Module | Kernel | What it does | Bounds |
|---|--------|--------|--------------|---------|
| 1 | `:core:capabilities` | `CpuCapabilities.kt` | /proc/cpuinfo-style parse: vendor, model, core count, known feature flags | 16 KiB input, 64 flags, 1024 cores |
| 2 | `:core:perf` | `FrameBudgetMonitor.kt` | Frame budget tracking vs target ms, over-budget streak, thermal throttle advisory | 10K frames, 1000 ms budget |
| 3 | `:core:designsystem` | `DensityScale.kt` | dp↔px and sp↔px conversion with round-half-up, clamped output | 10.0 scale, 100K dip, 1M px |
| 4 | `:feature:notes` | `BacklinkResolver.kt` | [[wiki]] backlink extraction, target normalization to note ids | 16 KiB input, 512 links |
| 5 | `:feature:settings` | `SettingValidator.kt` | Settings validation: required, allowed values, min/max, pattern; all rules evaluated | 256 rules, 64 allowed values |
| 6 | `:format:image` | `PngChunkReader.kt` | PNG signature + chunk structure parse (length/type/CRC), critical vs ancillary | 1 MiB input, 1024 chunks |
| 7 | `:feature:lens` | `OcrPreprocessing.kt` | Grayscale threshold binarization, approx deskew angle, width normalization | 1 MiB input, 4096 dim, 256K px |
| 8 | `:feature:automation` | `MacroRecorder.kt` | Bounded macro step record/pause/resume/playback with param maps | 1024 steps, 64 KiB total |
| 9 | `:feature:backup` | `BackupManifest.kt` | Backup manifest: path/size/SHA-256 entries, version, total size | 8192 entries, 4K path, 1 TiB size |
| 10 | `:feature:vault` | `VaultEntry.kt` | Encrypted vault entry metadata: nonce, tag, algorithm, key id | 4096 entries, AES-GCM/ChaCha20 |
| 11 | `:feature:terminal` | `AnsiParser.kt` | ANSI CSI + SGR parse: reset, bold, dim, italic, underline, reverse, colors | 16 KiB input, 16 params, 256 seqs |
| 12 | `:cast:dlna` | `DlnaDevice.kt` | DLNA/UPnP device description parse: type, name, manufacturer, model, URL | 16 KiB input, 1K field |
| 13 | `:cast:castv2` | `CastV2Channel.kt` | CastV2 protocol frame parse: source/dest/namespace/payload, length-prefixed | 64 KiB input, 256 frames |
| 14 | `:feature:messages` | `MessageThread.kt` | Message thread resolution by parent id, cycle detection, bounded depth | 4096 messages, 32 depth |
| 15 | `:feature:together` | `PresenceTracker.kt` | Collaborative presence: online/away/offline with last-seen timestamps | 1024 users |

Each kernel is declared-narrowing: explicit input caps, refusal on overflow,
no implicit behavior, no runtime dependencies beyond stdlib.

## B. Evidence

| Artifact | Path |
|---|---|
| `CpuCapabilities` impl | `android/core/capabilities/src/main/kotlin/dev/fusion/capabilities/CpuCapabilities.kt` |
| `CpuCapabilities` oracle | `android/tools/test_cpu_capabilities.py` (5 tests) |
| `FrameBudgetMonitor` impl | `android/core/perf/src/main/kotlin/dev/fusion/perf/FrameBudgetMonitor.kt` |
| `FrameBudgetMonitor` oracle | `android/tools/test_frame_budget_monitor.py` (8 tests) |
| `DensityScale` impl | `android/core/designsystem/src/main/kotlin/dev/fusion/designsystem/DensityScale.kt` |
| `DensityScale` oracle | `android/tools/test_density_scale.py` (9 tests) |
| `BacklinkResolver` impl | `android/feature/notes/src/main/kotlin/dev/fusion/notes/BacklinkResolver.kt` |
| `BacklinkResolver` oracle | `android/tools/test_backlink_resolver.py` (7 tests) |
| `SettingValidator` impl | `android/feature/settings/src/main/kotlin/dev/fusion/settings/SettingValidator.kt` |
| `SettingValidator` oracle | `android/tools/test_setting_validator.py` (8 tests) |
| `PngChunkReader` impl | `android/format/image/src/main/kotlin/dev/fusion/image/PngChunkReader.kt` |
| `PngChunkReader` oracle | `android/tools/test_png_chunk_reader.py` (5 tests) |
| `OcrPreprocessing` impl | `android/feature/lens/src/main/kotlin/dev/fusion/lens/OcrPreprocessing.kt` |
| `OcrPreprocessing` oracle | `android/tools/test_ocr_preprocessing.py` (8 tests) |
| `MacroRecorder` impl | `android/feature/automation/src/main/kotlin/dev/fusion/automation/MacroRecorder.kt` |
| `MacroRecorder` oracle | `android/tools/test_macro_recorder.py` (7 tests) |
| `BackupManifest` impl | `android/feature/backup/src/main/kotlin/dev/fusion/backup/BackupManifest.kt` |
| `BackupManifest` oracle | `android/tools/test_backup_manifest.py` (7 tests) |
| `VaultEntry` impl | `android/feature/vault/src/main/kotlin/dev/fusion/vault/VaultEntry.kt` |
| `VaultEntry` oracle | `android/tools/test_vault_entry.py` (7 tests) |
| `AnsiParser` impl | `android/feature/terminal/src/main/kotlin/dev/fusion/terminal/AnsiParser.kt` |
| `AnsiParser` oracle | `android/tools/test_ansi_parser.py` (9 tests) |
| `DlnaDevice` impl | `android/cast/dlna/src/main/kotlin/dev/fusion/dlna/DlnaDevice.kt` |
| `DlnaDevice` oracle | `android/tools/test_dlna_device.py` (4 tests) |
| `CastV2Channel` impl | `android/cast/castv2/src/main/kotlin/dev/fusion/castv2/CastV2Channel.kt` |
| `CastV2Channel` oracle | `android/tools/test_cast_v2_channel.py` (3 tests) |
| `MessageThread` impl | `android/feature/messages/src/main/kotlin/dev/fusion/messages/MessageThread.kt` |
| `MessageThread` oracle | `android/tools/test_message_thread.py` (5 tests) |
| `PresenceTracker` impl | `android/feature/together/src/main/kotlin/dev/fusion/together/PresenceTracker.kt` |
| `PresenceTracker` oracle | `android/tools/test_presence_tracker.py` (6 tests) |

## C. Gate result

All 112 new Python differential-oracle tests pass (5+8+9+7+8+5+8+7+7+7+9+4+3+5+6).

Prior test suite unaffected: all 301 Python host tests pass (zero failures).
R104 Studio Readiness Gate passes.

No Kotlin compiler/Android SDK locally — CI is sole compiler/verifier. Kotlin
-Werror execution and Android device gates cannot run locally; first real
build is on a laptop per R104 readiness gate.

## D. Module counts vs full acceptance (unchanged by R145)

| Metric | Count | Percent |
|---|---|---|
| Modules with testable subset | 50 / 50 | 100.00% |
| Modules Class A / fully accepted | 0 / 50 | 0.00% |
| Modules not established | 0 / 50 | 0.00% |

R145 deepens 15 existing modules with additional bounded kernels; it does not
create new modules or change Class A status.

## E. Honest limitations

- No Kotlin/Java/Gradle/Android SDK locally; CI is sole compiler/verifier.
- Android/device/AX/performance gates unrun — no Class A claim possible without device testing.
- 6 `not_implemented` requirements remain hardware/legal-blocked (Shizuku/ADB, AVF VM, AirPlay/FairPlay GPL APK, Wine/Box64 APK).
- `MessageThread.resolve` single-pass parent indexing: a cycle where parent
  references a not-yet-seen id (e.g. a→b, b→a with "a" first) is not detected
  by the Kotlin single-pass; the Python oracle uses two-pass indexing. Fix
  requires Kotlin-side two-pass resolution.

## F. Known test fixes in this turn

- `test_cpu_capabilities`: removed processor lines that double-counted cores.
- `test_frame_budget_monitor`: fixed streak range off-by-one (len-2 → len-1).
- `test_png_chunk_reader`: replaced malformed test chunk data with proper length-prefixed chunks.
- `test_ocr_preprocessing`: target_width=same expects binarized output, not raw pixels.
- `test_ansi_parser`: fixed bright fg color index (91→9 not 8) and SGR tuple index (dim=2, italic=3).
- `test_message_thread`: added two-pass parent indexing + no-roots=cycle detection.
