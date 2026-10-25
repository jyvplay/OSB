# R44 lab results

Round shape: FOURTH consecutive sandbox reset, recovered by the
standing protocol (fetch → hard reset to `a7d2b13` → byte-clean →
toolchain reinstall → R43 372/372 baseline green BEFORE any edit).
Twenty-four new executed kernels across twenty-three modules.

## Host execution (this sandbox, 2026-10-24)
- `python3 tools/run_r44_contracts.py` → **R44 PASS: 378
  production-kernel assertions across 24 kernels** (post-repair) (23 compile
  groups; ooxml/lens groups carry declared intra-module deps).
  378 > 372; 24 > 23 kernels; modules tie the 23 record.
- `python3 tools/check_r44_reference.py` → PASS, 24 handwritten
  mirrors — the Debian version comparator is a FULL independent
  Python re-implementation agreeing on the tilde laws.
- `python3 -m unittest tools.test_r44_tools` → 33/33. HONESTY
  CORRECTION: an earlier draft claimed this count would exceed R43's
  38 — it does NOT (33 < 38); the exceeded axes this round are
  kernels, assertions, lab gates/suites/tests and the bridge, and
  this sentence now says exactly that.
- Static gates: touch delta +0; manifest scope unchanged.
- Census: deepen ×3 byte-identical; `--check` green. 50/50 testable
  (100%), **23/50 deepened (46%)**, 45/50 distinct (90%), 0/50
  accepted (0%). Inventory regenerated before the lab.

## Repairs (PLAN §I)
Zero post-execution kernel repairs — third consecutive clean round.
Two wrong test vectors fixed where kernels were right (the UPC-E
rule-0/1 interleave; a display-diagonal pin re-derived exactly); one
pre-compile visibility fix (DebVersion.cmpPart public); one
test-generator bug (double parens) caught by first compile; two
improvised junk assertions replaced with real vectors before any run
reached them.

## CI-caught ownership regression (the bridge doing its job)

The FIRST push of this round (`dcab79e`) broke every AGP lane that
compiles `:core:perf`: my new FrameBudget kernel REDECLARED the name
of `PerformancePolicy.kt`'s existing FrameBudget class — an ownership
violation the pre-write grep sweep missed (the pattern list never
included the literal name). Host isolation compiles could not see
it; the CI bridge saw it immediately. Repair per PLAN §I: the kernel
was CUT (the surface is owned), replaced by the grep-free Severity
kernel (:feature:alerts), the census row repaired, and the full
battery re-run from zero. Assertions settle at 378 (still > R43's
372). The missed-grep lesson joins the cut list.

## The CI bridge
`r44Contracts` joins the JVM lane. Once green: R37–R44, **161
kernels / 2777 assertions** under the real Gradle toolchain per
push. Authored-not-verified until the lane runs; the ledger records
it.

## Pinned facts (spot list)
- Debian: 1.0~rc1 < 1.0; 1.0~~ < 1.0~; 1.10 > 1.9; 1.0a < 1.0+ —
  executed in two independent implementations.
- UPC-E rule 0/1/2 INTERLEAVES the last digit as manufacturer digit
  3 (the wrong flat-pad expectation was mine; the kernel was right).
- EXIF orientation inverse is an involution over all eight values.
- ISSN 2434-561X's X digit computed, not quoted.
- /homework does not collapse under HOME=/home (component boundary).

## Authored Android (bridge files only)
All 24 kernels are new pure-logic files; zero manifest/UI/service
changes. `testing/build.gradle.kts` + `android.yml` register
`:testing:r44Contracts` (also runnable from Android Studio Rabbit 1).

## Scope
Host JVM + host Python + static analysis + CI annotations. No
device, emulator, rendering, live-network or wild-corpus evidence.
0/50 accepted; nothing here is A-grade and no such claim is made
(R44-ALTERNATIVES answers the Class-A question directly).

## Visual inspection (honest substitute)
Beauty/contrast reference green in the lab battery; layout-bounds
and page-matrix self-tests green; touch delta +0. Rendered review
remains a named downgrade.

## CI ledger (exact candidates, nothing inherited)
TWO pushes this round, both triaged by exact run id.

**Push 1 (`dcab79e`)** — the FrameBudget redeclaration broke every
AGP-compiling lane (native 40228827701, R21 40228827647, R22/R23
40228827593, R24 40228827637, R25 video 40228827621, R25 PNG
40228827602, API37 40228827737, atlas 40228827713 all failed on
`:core:perf:compileDebugKotlin`; companion 40228827614 passed —
its path set skips that module). This was a genuine regression I
introduced, caught by the bridge, repaired same-session.

**Push 2 (`86cf4e7`, the repair)** — triggered lanes:

| Run id | Workflow | Conclusion | Triage |
|---|---|---|---|
| 40231857997 | Android native | failure | **JVM job SUCCESS — ALL EIGHT contract tasks r37..r44 (161 kernels / 2777 assertions under Gradle): the redeclaration is fixed and the bridge is verified**; device job: the 2 standing `R25PngCopyDeviceTest` signatures |
| 40231857998 | R24 desktop media atlas | failure | standing no-tests install lane |

Other lanes did not trigger on the repair commit's path set; their
push-1 failures are attributed above to the named compile error, not
to their own surfaces. Standing watches carry forward unchanged:
2 PNG signatures (every obs), atlas (every obs), screen-off 2/7,
IME 3/14, Xlsx 2/8, R25 MP4 1/6, audio-focus 1/10, R17 focus-repro
intermittent, NotesProcessDeath 1/3, R23MemoryTransport 1/1 (did
not recur on push 1's R22/R23 lane beyond the compile error).


## Lab receipt

R44 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r44_lab.py host
- host contracts: PASS
- reference recomputation: PASS
- R43 contracts (regression): PASS
- R43 reference (regression): PASS
- R42 contracts (regression): PASS
- R42 reference (regression): PASS
- R41 contracts (regression): PASS
- R41 reference (regression): PASS
- R40 contracts (regression): PASS
- R40 reference (regression): PASS
- R39 contracts (regression): PASS
- R39 reference (regression): PASS
- R38 contracts (regression): PASS
- R38 reference (regression): PASS
- R37 contracts (regression): PASS
- R37 reference (regression): PASS
- R34 contracts (repair regression): PASS
- R34 reference (repair regression): PASS
- android syntax gate: PASS
- layout-bounds self-test: PASS
- page-matrix self-test: PASS
- beauty/contrast reference: PASS
- touch-target static audit: PASS
- manifest-scope gate: PASS
- HID topology parser (fixture): PASS
- project structure: PASS
- python test_bounded_process: PASS 3/3
- python test_ci_annotations: PASS 8/8
- python test_collect_native_evidence: PASS 6/6
- python test_device_results: PASS 6/6
- python test_engine_intake: PASS 2/2
- python test_gradle_distribution: PASS 3/3
- python test_module_audit: PASS 6/6
- python test_module_testability: PASS 7/7
- python test_native_lab: PASS 6/6
- python test_native_source_audit: PASS 13/13
- python test_pilot_reporting: PASS 4/4
- python test_png_capture: PASS 7/7
- python test_probe_android_sdk: PASS 4/4
- python test_r17_evidence: PASS 7/7
- python test_r18_evidence: PASS 8/8
- python test_r19_evidence: PASS 5/5
- python test_r20_evidence: PASS 8/8
- python test_r21_evidence: PASS 9/9
- python test_r22_evidence: PASS 9/9
- python test_r23_evidence: PASS 10/10
- python test_r23_input_tools: PASS 5/5
- python test_r23_lab: PASS 4/4
- python test_r24_evidence: PASS 4/4
- python test_r24_runner: PASS 2/2
- python test_r24c_tools: PASS 21/21
- python test_r25_tools: PASS 8/8
- python test_r26_tools: PASS 9/9
- python test_r27_tools: PASS 11/11
- python test_r28_tools: PASS 10/10
- python test_r29_tools: PASS 10/10
- python test_r30_tools: PASS 10/10
- python test_r31_tools: PASS 9/9
- python test_r32_tools: PASS 12/12
- python test_r33_tools: PASS 13/13
- python test_r34_tools: PASS 12/12
- python test_r35_tools: PASS 11/11
- python test_r36_tools: PASS 13/13
- python test_r37_tools: PASS 19/19
- python test_r38_tools: PASS 24/24
- python test_r39_tools: PASS 30/30
- python test_r40_tools: PASS 38/38
- python test_r41_tools: PASS 33/33
- python test_r42_tools: PASS 36/36
- python test_r43_tools: PASS 38/38
- python test_r44_tools: PASS 33/33
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  e893589caa48abba8f476cd629a9fa3df3f887c2c71748692aa3bd1490a3a411  testing/src/r44/kotlin/dev/fusion/testing/R44Checks.kt
