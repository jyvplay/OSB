# R23 conditional SDK37 / Rabbit1 toolchain experiment

Recorded2026-10-16 after memory candidate b3f2f1d focused24/24 and companion14/14 actual managed API34 passes, BEFORE changing the pinned Android toolchain. Full b3f2f1d main regression still in progress at this decision.

## Fresh external frontier

- Official Studio compatibility search result on2026-10-16: Rabbit1/2026.2.1 supports AGP7.1–9.4; API37 minimum is AGP9.1.1/Panda3 Patch1. [1](https://developer.android.com/studio/releases). Page chunks0/2/24 also retrieved, but those portions primarily repeat download/license terms; compatibility table above was retrieved by search, not read from those chunks. No GUI execution inferred.
- Full AGP9.1.1 release page (both chunks) actually fetched: API37 and below; Gradle9.3.1, build-tools36.0.0, JDK17, KGP2.2.10 minimum. https://developer.android.com/build/releases/agp-9-1-0-release-notes
- Built-in Kotlin migration page chunks0/2 fetched: AGP9 enables built-in Kotlin by default; temporary `android.builtInKotlin=false` is supported before AGP10. Existing external Kotlin2.2.21/Compose plugin may be retained only with explicit opt-out and a real build. https://developer.android.com/build/migrate-to-built-in-kotlin
- AGP9.0 release notes page chunk0 retrieved, new public DSL migration identified. Need verify legacy-DSL compatibility rather than assume the existing kotlinOptions DSL works automatically. https://developer.android.com/build/releases/agp-9-0-0-release-notes
- Actual GitHub API query `repos/gradle/gradle-distributions/releases/tags/v9.3.1`: published2026-01-29T14:29:25Z, official binary asset347704202,137037885bytes, digest `sha256:b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`. Pin distribution SHA from that real response, not a guessed checksum. Existing reviewed wrapper bootstrap JAR hash remains unchanged; this is initially a distribution update, not a claim of regenerated wrapper scripts/JAR. No local download/execution possible with JRE-only host cache.

## Hypotheses and admission

H+: AGP9.1.1/Gradle9.3.1 + explicit supported temporary legacy Kotlin/DSL opt-outs can compile target/compile37 with unchanged bounded app code. Rabbit1 support is a DOCUMENTED compatibility claim, not a GUI trial.
H−: SDK37/resolver unavailable, opt-out unsupported, DSL/compiler incompatibility, lint/runtime regressions. A failure is evidence, not success because documentation looked plausible.
Boundary: API37 compile/target does NOT execute API37 runtime or physical/OEM/AX/engine/license gates. No fully accepted module can be inferred.

Cheapest falsifier: real CI SDK37 install + both app flavors/companion assemble. Then entire JVM/contracts/lint, actual API34 companion14, memory/FD/picker24 and full native122 required, whole workflows success, exact new candidate. New toolchain inherits **no** b3f2f1d runtime/build acceptance. If no adequate verifier or time to repair, restore last known working production toolchain and retain this failed experiment/gap; do not leave an unverified SDK37 promise or quietly call SDK36 equivalent.

No broad API warning suppression or fake SDK stubs. Real SDK installation is required. No secret credential or license-acceptance bypass is authorized.

## Actual outcome, not a retrospective pass

Candidate4a7aa4d63f9484d99c958a1ec64cea0730ed5f9b: main38865132612, privacy38865132640, focused38865132614 all FAILED before native execution. Stable SDK catalog NOT_LISTED; install failed; real Gradle9/AGP9 parsing rejected old managedDevices.devices DSL. Each receipt0/0/0 means no cases executed, NOT success. Unit gate now refuses this actual repaired/failed candidate without inheriting b3f2f1d24/14/122 success.
Working8.13.2/Gradle8.13/SDK36 configuration restored. Original SDK37 deviation remains open. Preview/stable catalog observation and a future localDevices DSL migration are the next cheap falsifiers, not a justification to fakeSDK37 or suppress unsupportedAPI warnings. Studio chunk25 subsequently fetched the actual compatibility table; this still does not execute Rabbit1 GUI or install a platform.
