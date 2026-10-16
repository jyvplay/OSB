# R16 experimental closure — continued during R17

## Runtime honesty
Actual tools: Git/gh and GitHub Actions/API; Python3; separately restored Kotlin2.2.21 and JRE17.0.9; cryptography46.0.3/OpenSSL; real local TCP sockets between independent Python and JVM processes; remote API34 Android managed devices and AndroidX macrobenchmark1.3.3. Compiler archive SHA512 was verified. Two host regression commands were run concurrently. No independent agents, theorem prover, physical reference devices, expert AX review or counsel participated. Android emulator execution is not physical-device evidence. No fake player/peer, TypeScript product or simulated OS was implemented.

R16's six-experiment plan is distinct from full acceptance of Together, Office or performance requirements. Full acceptance remains0/48. Latest exact closure results are appended below after collection; in-progress runs are not results.

## A. Formal model

Admissible objects: fresh caller-connected sockets;32-byte caller-protected high-entropy pair keys; two16-byte random challenges; fixed69-byte request/85-byte response ciphertext frames; lowercase64-character SHA256 media identifiers; nonnegative Long playback milliseconds and nanosecond timestamps; Boolean caller-advertised playing state. No URI, path, media file or player command is transmitted. The digest is a caller assertion, not proof of file ownership or actual playback.

Access: the transport takes ownership of a socket supplied explicitly by its caller, closes it on completion/refusal, and performs no discovery, listener startup, key pairing or automatic connection. Main-thread calls are prohibited by the API contract. The pilot UI remains blocked. JCA protects confidentiality/authentication conditional on key secrecy, correct RNG/provider and nonce non-reuse; the remote party is only identified as a key holder.

Resources: at most8 concurrent API calls per process, fixed network field sizes and100..5000ms configured whole-call deadline. A timer closes stalled sockets; elapsed-time validation rejects overdue returns. Scheduler/OS pauses prevent a hard real-time guarantee. No whole-system socket/RSS bound or compromised-process theorem is asserted.

Clock model: for equal-rate trustworthy clocks with nonnegative one-way delays, offset lies in `[peerSend-localReceive, peerReceive-localSend]`. RTT removes peer processing time. Units are nanoseconds. Midpoint is merely an estimate: asymmetry can bias it. Quantization, oscillator drift and dishonest authenticated timestamps remain outside this exact-integer model. A public interval must have ordered endpoints, nonnegative RTT and exactly matching width; overflow-sized intervals refuse.

Measurement objects: a nondebuggable profileable pro APK, explicit disposable API34 AVD, real startup/Files open-close transitions, generated ART rules, compiled profile assets and AndroidX measurements. EMULATOR suppression permits an experiment; it does not make emulator numbers reference-device budgets. No personal app data is reset by these benchmarks.

Quantifier order: choose a candidate, assumptions and finite corpus first; require every declared case to match its specified result/refusal, with at least one positive execution of the actual production path. One counterexample rejects that candidate until fresh validation. This is not `for every supported device/input/attacker, the complete original workflow succeeds`. Count each included Gradle module once only after qualifying executed behavior; assertion counts and generated file counts are not completion percentages.

Adjacent problems: authenticated state exchange vs co-watching; possession of a shared key vs personal identity; clock intervals vs synchronized playback; SHA labels vs actual content identity; loopback vs multi-device LAN; fixed-size parsing vs complete network containment; generated profiles vs speedup; ART compilation vs physical budget; source interfaces vs running LOK; original arithmetic vs full Office1:1.

## B. Outcome space

H+: bounded socket/AEAD/clock and actual measured-profile experiments can cross their execution thresholds. H−: specific proposed invariants can fail (public clock construction, unsigned test installation, receipt transport truncation and stable historical counterexample reproduction did fail). H-boundary: guarantees depend on clocks, RNG/keys, scheduler, ART/API, test environment and legal packaging. No impossibility theorem for the whole product is asserted.

## C. Verified imported results and open interface

`research/r16-primary.json` records seven pinned primary-source records: inherited LO/pyca inputs rechecked where recorded and AndroidX BaselineProfileRule source. The pinned AndroidX upstream file is explicitly NOT represented as the exact1.3.3 binary; actual resolved1.3.3 behavior is established by the CI run, not version inference from source. These records are a limited verified investigation, not an exhaustive best-known frontier survey.

AES-GCM is used with128-bit tags and fresh96-bit nonces. Under independent uniform nonce sampling, the union-bound collision probability after q encryptions under a key is at most q(q−1)/2^97. Session challenges/direction-separated AAD do not excuse GCM nonce reuse. No lifetime q enforcement, entropy audit, FIPS validation or key lifecycle is supplied. Real OpenSSL interoperability checks wire compatibility, not those hypotheses.

For causal equal-rate timestamps, lower=offset−returnDelay and upper=offset+forwardDelay. This algebra establishes the conditional interval, not that a peer reports truth or devices actually share the assumed rate. There is no theorem-prover certificate.

LOK source/license/interface records do not provide an audited runnable payload or clean-room parity. `format/lok` still contains only its build declaration and unavailable bridge. No native engine payload is present or enabled. Literal unrestricted LibreOffice parity also cannot be silently equated with a product domain that explicitly disables macros and forbids some dependencies. Full parity on a properly specified admissible domain remains undelivered, not declared impossible.

## D. Twenty false solution shapes and detectors

1. A fake peer counts as transport: require real sockets and different Python/JVM PIDs.
2. One language agreeing with itself proves framing: independent OpenSSL peer in both roles.
3. Correct-key happy path is authentication: wrong key/tag/role attacks must refuse.
4. Fresh IV alone prevents cross-session replay: replay a captured request against a fresh server challenge.
5. Challenges replace IV uniqueness: explicit nonce hypothesis, not such a claim.
6. Shared secret identifies an individual: report only key possession.
7. Media hash proves actual playback: no player or content-ownership inference.
8. Clock midpoint is exact offset: asymmetric-delay corpus verifies interval and biased midpoint.
9. Valid factory protects a public data class: directly construct inverted/negative-RTT values and exercise copy.
10. Read timeout bounds the whole call: slow-trickle input and whole-call socket closure test.
11. Concurrency cap never leaks: occupy8 real sockets, reject ninth, close all, repeat.
12. Loopback is S22/multi-device acceptance: explicit separate unpassed gate.
13. A nullable UI click exercises a journey: require actual Files content appearance and disappearance.
14. An unsigned test APK can be measured: actual install failure retained; signing repaired and rerun.
15. Empty/handwritten profiles are generated evidence: require nonempty actual generated first-party rules and hashes.
16. Packaged bytes prove ART use: require profile-required compilation/startup test as a separate gate.
17. Emulator speedup proves a physical budget: report samples as observations only, no budget/speedup acceptance.
18. Green CI proves exported evidence is intact: SHA-bound reassembly rejects the actually truncated receipt.
19. Historical failure must always reproduce: record NOT_REPRODUCED rather than falsifying it or blocking the candidate suite.
20. A source bridge completes Office: LOK remains unestablished; no unrelated helper inflates its numerator.

Modal shortcut: substituting a component, plausible status or build success for the original workflow. Detectors above require actual socket/UI/profile behavior and retain a separate0/48 full-acceptance ledger.

## E–G. Mechanism portfolio, artifacts and structure-specific attacks

| Mechanism | Construction/artifact and established portion | Attacker/falsifier | Exact gap |
|---|---|---|---|
| Socket capability and bounded lifecycle | `TogetherChannel.kt`; ownership, fixed reads,8-slot cap, close/cancel/deadline | Trickle, EOF, ninth connection, occupied-slot recovery | Android/LAN lifecycle, pairing/foreground UX and whole-system limits; local transport gap, original-scale service gap |
| Session/direction AEAD | Same producer's JCA framing; `run_r16_interop.py` independent OpenSSL peer | Wrong key/media/role/tag, negative fields, captured-frame replay | Key custody/RNG/identity, trustworthy peers and media integration; no universal security proof |
| Causal clock interval | `PeerClock.kt`; checked interval factory AND public constructor | Inverted endpoints, negative RTT, copy bypass, width overflow, asymmetry, dishonest timestamps | Conditional exact-integer model only; real multi-device drift/servo remains original synchronization problem |
| Native journey measurement | `benchmark/.../{LabJourney,StartupBenchmark}.kt`; real required transitions and metrics | Missing UI, nullable clicks, unsigned test APK, debug target, stale results | Physical devices, complete journeys and budgets remain unpassed |
| Measured ART profile | Generator, collector, `baselineprofile/src/main/baseline-prof.txt` after verified import; real compilation test | Empty rules, incorrect owner selection, missing assets, missing required profile, truncated export | No blanket speedup, all-flavor/API/OEM or release acceptance |
| Engine feasibility boundary | Pinned LO inputs and actual payload inventory; no audited running engine | Treat headers, gates, numeric kernels or tiles as full LOK/Office | Full engine/license/fidelity/original rewrite gap; intentionally NOT counted as positive module delivery |

These mechanisms were separated in the preimplementation plan, not presented as independently authored expert work. Every branch yields executable source/experiment, a conditional quantitative bound or a concrete negative artifact; none is counted merely by a status label.

## H. Executed verification

- Fresh host build in this continuation: **17131 assertions PASS**. Most are clock algebra combinations, not17131 network sessions.
- **64 real cross-process Java/OpenSSL socket cases PASS**, including25 exchanges in each role and14 adversarial cases.
- Rebuilt R15 regression: **5510 assertions +700 cross-language cases PASS**.
- Rebuilt R14 regression: **1893 assertions +600 cross-language cases PASS**.
- Native R16 at **7a1eee3 / run38695979143:74 PASS,0 FAIL,0 SKIP**, both debug builds, JVM/contracts and lint success. All three new Together Android cases passed. Retained `verification/r17-r16-native-*`.
- Earlier6506159 run failed before the candidate suite because its historical focus counterexample did not reproduce;0 candidate cases, not a failed Together case. Retained `r17-r16-before-*`.
- Profile run38695979269 succeeded, but its4 exported chunks were each truncated to4096 characters. The full receipt cannot be hash-verified; no detailed measurements/rule counts are imported from that corrupt bundle. Raw observations are retained.

## I. Repairs inherit no trust

The actual pre-repair public consumer produced `PeerClock(offsetLowerNanos=10, offsetUpperNanos=-10, roundTripNanos=-1)`; `r16-clock-counterexample.json` retains old library/source hashes and stdout. Constructor invariants now protect direct calls and copy, with width checked outside overflowing Long arithmetic. Fresh asymmetric/boundary/concurrency and full interoperability checks passed.

The unsigned benchmark APK failed with INSTALL_PARSE_FAILED_NO_CERTIFICATES. Explicit debug signing of the nondebuggable test variant repaired installation; subsequent measurement workflow succeeded. Nondebuggable target and profile-required compilation remain mandatory.

The receipt exporter used48000-character chunks; actual returned messages were4096 characters. Export now uses3800-character payloads and measured first-party-owned profile selection, with full-origin hash and selection semantics retained. Tests exercise truncation, hash mismatch, duplicate/missing chunks and reversed order, including an actual codec roundtrip constrained to the observed API limit. Detailed profile acceptance still requires the new completed run.

Historical focus results are now classified REPRODUCED/NOT_REPRODUCED/INVALID_EXPERIMENT. Invalid experiments still fail. A valid old-source pass weakens the causal claim, not the candidate assertion. Current focus regression remains in the full native suite.

## J. Closure threshold / next highest-information test

Collect and hash-verify the repaired measured-profile run; require the generation case, all3 benchmark cases, nonempty packaged assets and actual profile-required startup. Import only the measured profile bytes whose hash matches that receipt. Update the census only then. R16's original full Together/Office/physical/legal/AX obligations remain open regardless of bounded-plan closure. Follow-up R17 functionality begins only after this experimental closure, not by calling unfinished full modules complete.


## Verified closure (R17 continuation)

The repaired experiment **bb338a5 / run38697143892** completed successfully. Its receipt reassembled and SHA256-verified:1 generator case and3 benchmark cases PASS.855 first-party-owned measured rules were selected from14895 observed total rules; both original and selected hashes are retained. Imported profile SHA256 `8b99ceddc7cb6dbb80e40425ab57d1bcec3e7aa181fcbe4501d4edcb0cda8b12` matches the rebuilt APK's source-profile receipt. Packaged ART assets were8429-byte baseline.prof and418-byte baseline.profm; profile-required startup passed. See `verification/r16-measured-38697143892-*` and `baselineprofile/PROVENANCE.md`.

Actual emulator TTID samples (ms): compilation-none2399.156176/1799.370496/1987.479377; profile-required1277.462739/2104.234029/1842.704522. Three observations per mode are not a causal speedup claim. Files open/close recorded frame counts10/10/12; no unsupported frame-duration or budget claim is inferred. TTID is NOT the Room restore≤500ms metric.

**R16's six bounded experiments/reporting are closed with their stated outcomes, including the negative LOK boundary.** Together, benchmark and baselineprofile now qualify: **47/48 (97.92%) testable subsets;1/48 (2.08%) unestablished;0/48 fully accepted;48/48 unfinished.** This is not completion of the original workflows or acceptance gates. R17 may now implement the next bounded productivity increment.
