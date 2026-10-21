# Building, Testing & Opening Fusion Shell (`OSB/android`)

## 1. Zero-SDK Sandbox Verification (Works Immediately Without Android Studio or Android SDK)

From `/home/user/OSB/android`, run the single unified R26 host verification pipeline:

```bash
python3 tools/r26_lab.py host
```

If setting up on a clean Linux `x86_64` host without cached tools:

```bash
python3 tools/bootstrap_host_tools.py install ~/.cache/fusion-host-r23
python3 tools/r26_lab.py host --tools ~/.cache/fusion-host-r23
```

What `python3 tools/r26_lab.py host` executes end-to-end (no mocks, no simulations):
1. **`tools/check_project.py`** — Structural & security contract check across all **56 Gradle modules**, version catalog accessors (`gradle/libs.versions.toml`), Gradle wrapper SHA-256 (`81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`), manifests, read-only SAF bounds, non-exported `CopyDestinationActivity`, disabled notification listener, historical binary migration fixtures, `docs/MODULE-COMPLETION.md`, `docs/MODULE-TESTABILITY.md` (`56/56 = 100.00%`), `requirements.csv` (`231` rows), and `docs/FULL-SPEC-REVIEW.md`.
2. **`python3 -m unittest discover -s tools -p 'test_*.py'`** — Executes **192 Python unit tests** including the negative-space modal shortcut detector, evidence-gate mutation rejections, BadUSB composite USB sysfs classification, and version-catalog verification.
3. **`tools/r25_lab.py host`** — Compiles and runs R24, R24C, and R25 production Kotlin contracts (`512,086` assertions) plus `53` independent `Pillow` PNG CRC/pixel round-trip cases.
4. **`tools/prove_r26_models.py`** — Runs **Z3 SMT Prover (`z3-solver 5.3.1`)** proving **6/6** formal theorems (`unsat` negated-property refutations) and writes `docs/verification/r26-z3-proofs.json`.
5. **`tools/beauty_audit.py`** — Evaluates October 2026 WCAG 2.2 AA (`SC 1.4.3`, `SC 1.4.11`, `SC 2.5.8`), APCA perceptual contrast (`Lc`), `PHI = 1.618...` harmonic spacing/aspect bounds, `83.16%` slanted 3D tabletop floor dominance, adaptive dock capacity math, and actual decoded PNG frames (`docs/verification/r26-beauty-audit.json`).
6. **`tools/amnesic_posture.py`** — Reads real Linux `/proc/swaps`, `/proc/mounts` (`tmpfs`), `/sys/class/iommu`, `/etc/usbguard`, `/sys/bus/usb/devices` (`03` HID + `08` Mass Storage composite BadUSB check), and kernel hardening sysctls (`docs/verification/r26-host-amnesic-posture.json`).
7. **`tools/run_r26_contracts.py`** — Compiles all 19 pure-JVM R26 production Kotlin files with `kotlinc 2.2.21 -Werror`, runs `285` assertions, and executes cross-language binary file interchange against independent Python libraries (`openpyxl 3.1.5`, `XlsxWriter 3.2.5`, `Pillow`, `pypdf`).

---

## 2. Opening & Running in Android Studio Rabbit 1 (Laptop / Full SDK Host)

1. Launch **Android Studio Rabbit 1** and select **Open** -> choose the **`OSB/android`** directory (`/path/to/OSB/android`, **not** the repository parent folder).
2. Configure **Gradle JDK = Vendor JDK 17** (Temurin / JetBrains Runtime 17) under *Settings -> Build, Execution, Deployment -> Build Tools -> Gradle*.
3. Install SDK components in *SDK Manager*:
   - **Android SDK Platform 37** (`platforms;android-37.0`) and **Android SDK Platform 34** (`platforms;android-34`)
   - **Android SDK Build-Tools 36.0.0** (`build-tools;36.0.0`)
4. Sync Gradle (`File -> Sync Project with Gradle Files`), select run configuration **`app` (`proDebug` or `playDebug`)** or **`privacy` (`debug`)**, and run on an API 34+ AVD or connected device.

### Command-Line Gradle Build & Test Commands

```bash
cd android
# Build APKs, run lint, and run all pure-JVM unit tests + R25/R26 contracts:
python3 tools/r26_lab.py android

# Run destructive managed-device Android instrumentation tests ONLY on an expendable AVD:
python3 tools/r26_lab.py device --disposable-device
```

> **Safety Note (`fusionDisposable=true`)**: Managed-device instrumentation suites reset system font scale (`1.0` / `2.0`), MediaStore test items, and app data. Never pass `-Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true` against a personal phone containing user data.
