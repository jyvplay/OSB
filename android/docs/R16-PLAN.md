# R16 pre-implementation contracts

Starting point: R15 44/48 bounded testable subsets;0/48 fully accepted. The four unestablished modules are Together, LOK, benchmark and baselineprofile. No status-only helper, folder or imported interface counts as a completed subset.

## Six independent mechanisms / experiments

1. Actual bounded socket I/O between explicit peers: first probe real two-process loopback exchange, then implement Android-compatible synchronous transport. Caller owns/explicitly supplies connected socket; no auto discovery/listener/cloud. Fixed-size messages, single exchange, closure/deadline and bounded concurrency. Host loopback is not multi-device transport acceptance.
2. Authenticated confidential playback-state exchange: random challenges from BOTH endpoints, direction-separated AES-GCM AAD, caller-protected32-byte pair key, SHA256 media identifier and bounded nonnegative state/time fields. Actual independent Python/OpenSSL peer must interoperate and wrong-key/media/direction/replay/truncation must refuse. Key pairing/lifecycle, traffic analysis and dishonest authorized peers remain open.
3. Clock-offset interval from actual four timestamps: causal nonnegative one-way delays, equal clock rates for an exchange, ordered local/remote timestamps; checked integer arithmetic. Report interval and RTT, never invented≤40ms synchronization. Test finite algebraic boundaries and actual socket observations; no player/servo or clock certification.
4. Android macrobenchmark journeys: replace nullable clicks with required observable transitions; explicit disposable-emulator opt-in; cold startup and Files open/close on real nondebuggable profileable APK. Require actual reports before module promotion. Emulator metrics never establish physical budgets.
5. Actual baseline profile generation and packaging: BaselineProfileRule on real startup/Files journey, nonempty generated rules, retain hashes/provenance, rebuild with library profile, require nonempty ART assets and actual profile-required compilation/startup. No handwritten profile masquerading as measurement, no effect claim from generation alone.
6. LOK feasibility boundary: recheck exact existing primary source/interface/license evidence and actual available payload. Without an audited runnable engine, keep LOK unestablished. No unrelated tile/manifest/gate-only helper added to inflate its count. Full from-scratch Office1:1 remains the original unfulfilled requirement.

H+: each bounded candidate crosses its declared execution threshold. H−: a concrete counterexample/failed CI rejects it until repaired and freshly tested. H-boundary: platform/clock/key/codec/license/physical assumptions remain explicit. Repairs inherit no prior pass. Native execution is revision-specific. All S1–S30 and full workflow/legal/AX/physical gates remain separate.

Tools available: local Python/Kotlin/JRE and GitHub Actions remote Android tooling; no local Android SDK, independent agents, formal prover, physical devices or counsel. Record actual source investigation, failures, result artifacts and exact remaining tests in R16-ENGINEERING.md. Do not count an in-progress run or source-only change as success.
