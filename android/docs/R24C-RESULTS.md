# R24 continuation results — real bounded work, not full-spec completion

Work branch: `arena/a8ac5475-osb`. Native application: **`android/`**, not the root web prototype. Evaluation date:2026-10-18. [Try/build instructions](BUILD.md) · [Admissibility and success gates](R24C-PLAN.md) · [Verification boundaries](R24C-VERIFICATION-BOUNDARIES.md).

## What is actually implemented

| Area | Concrete production work | Boundary that is NOT silently replaced |
|---|---|---|
| Desktop/tabletop | Four projected planes; draggable first-party launch pins, interactive focused walls, same-plane pile/unpile/move, pan/zoom, named scene snapshots, strict codec, revision/lock checked atomic store | Fixed finite geometry/storage regime, not unlimited objects, unrestricted 3D physics, or external OS task control |
| Appearance | Default silver Art / light Business choices, retained Blue/Graphite, independent Flat/Tabletop, font-aware horizontally scrolling dock, explicit>=48dp Find/control actions, native play/globe glyphs | No automatic whole-OS theme, universal beauty/phi proof, all-page/TalkBack/physical acceptance |
| Maps | Real public-domain Natural Earth land outlines, Mercator pan/zoom, editable coordinate/name pins, private-file reload, spherical distance | 1:110m land, not street maps/GPS/geocoding/navigation or a free unlimited tile server |
| Local media | Actual Android MediaPlayer/SurfaceView activity, system document picker, explicit Play/pause/seek, audio-focus/noisy/lifecycle handling, retained images/SRT views | Native tests exercise a WAV, not audible fidelity or video pixels; provider calls can block; no streaming-service player inferred |
| Service links | Strict canonical YouTube/Spotify/SoundCloud URL admission, explicit disclosure/confirmation, external handoff | Handoff is not integrated playback, a content entitlement, or unrestricted free-for-all service licensing |
| Privacy companion | Observed input/IME/accessibility/display changes discard and latch; two actual IME changes exercise explicit empty rearm and stale generation rejection | No covert/preexisting keylogger detection, trusted firmware/host attestation, Tails equivalence or forensic erasure |
| Engineering tools | Separate production/consumer Kotlin compilation, real filesystem/two-JVM tests, native exact-case collector, actual target/image transport, strict receipt gate, extra real engine intake | Parser checks/source inventories are not native execution or independent attestation |

The floor polygon occupies **0.8316 of the scene before chrome/occlusion**, not83% of every full screenshot. Scene caps:512 pins,128 piles,16 named snapshots, world coordinates±1e6 and2MiB wire budget. Tiny wall marks in overview are decoration; focus a wall for usable controls. Scene snapshots do not include documents, windows, permissions or playback.

## Completion census — two different denominators of meaning

- **49/50 substantial code + runnable behavioral-test subsets:98%.**
- **1/50 unestablished:2%**, `:format:lok`.
- **0/50 full original-spec accepted:0%;50/50 unfinished at full scope:100%.**
- The register still contains231 requirements,36 cross-cutting obligations and30 unpassed formal spikes.
- Compared with the session baseline, eleven included production modules have implementation changes (22% of the current50-module inventory), plus test/build/evidence work. This is **not** eleven new or completed modules. The maps addition increases the previous48/49 subset census to49/50.

[Auditable per-module census](MODULE-TESTABILITY.md) and [full-scope blockers](MODULE-COMPLETION.md). Neither a large assertion count nor a successful subset promotes full-module acceptance.

## Exact execution ledger

### Executed full local host pipelines

**12fbf4f04228965c181c12f40763afc3392075fc**, executed host command exit0 in**93.071s**:

-170 Python tool tests PASS (0.740s inside that execution).
-**433681 R24C assertions PASS**, separately compiled production and consumer, including real files and competing JVM processes.
-**65097 retained R24 assertions PASS**:12 appearance configurations,16640 dock configurations,2688 transient input presentations.
-Natural Earth asset digest/provenance check, source ownership/traceability/census checks PASS.

Archive: [host-12fbf4f](verification/r24c/host-12fbf4f/pipeline.json), complete output/source hashes/toolchain beside it. The earlier307f2d9 full host result remains in `verification/r24c/host/`. These are host tests, not an APK, IDE GUI, native media output, physical-device benchmark, or rerun of every historical suite. The disposable local JRE17.0.9 is old and not a full JDK; maintained JDK17/SDK37 compilation is remote.

The later viewport/transport patch also completed the full host command: **172 Python tests,439839 R24C assertions,65097 R24 assertions**, exit0. It adds6158 viewport-admission checks. [Archive](verification/r24c/host-viewport-patch/pipeline.json) binds the actual compiled input hashes; it ran before the patch's commit and is not mislabeled as unchanged12fbf4f. Initial stale inventory was rejected, regenerated, then checked again.

### Native candidate trail (failures deliberately retained)

| Candidate | Continuation API34 | Full API34 | Privacy34 | Actual privacy37 | Retained focused suites |
|---|---|---|---|---|---|
| bac8225 | Instrumentation compile failure,0 cases | Instrumentation compile failure,0 cases; separate build/JVM/lint/contracts passed |19/0/0 |19/0/0 | Shared instrumentation compile defect,0 cases |
|307f2d9 |5/4/0 FAILURE |137/5/0 FAILURE; separate build/JVM/lint/contracts passed |17/2/0 FAILURE |19/0/0 SUCCESS | R21 8/0/0;R23 27/0/0;appearance9/0/0 SUCCESS |
|0a7b22e |7/2/0 FAILURE |139/3/0 FAILURE; separate build/JVM/lint/contracts passed |19/0/0 SUCCESS |19/0/0 SUCCESS | R21 8/0/0;R23 27/0/0;appearance9/0/0 SUCCESS |
|12fbf4f |8/1/0 device FAILURE; whole workflow CANCELLED |141/1/0 FAILURE; separate build/JVM/lint/contracts passed |19/0/0 SUCCESS |19/0/0 SUCCESS | R21 8/0/0;R23 27/0/0;appearance9/0/0 SUCCESS |

Numbers are PASS/FAIL/SKIP, not percentages of the specification. Suites overlap; do not sum them into unique product coverage. Every raw source/run/jobs/annotations/artifact receipt is retained as `verification/r24c-first-*`, `r24c-second-*`, `r24c-third-*`, and `r24c-fourth-*`.

At0a7b22e **all five tabletop cases passed**. Audio actually played/paused but failed the keyboard-seek observation; atlas saved its point but had no visible pin target at the next step. The full run additionally caught an older real-IME long-text visibility timeout. [Third repair and revised diagnosis](verification/r24c-third-repair.md) describes the explicit dialog-IME close, asserted real keyboard-focus staging and stronger tagged AX+model checks. No timeout extension, ignored assertion or direct production state injection was substituted. Those repairs require their own native results.

**12fbf4f now has complete collected outcomes.** Audio's complete tested path and the retained real-IME case passed. Both app runs still caught the actual **zero-sized map canvas**. A standalone and a called engine-intake job also shared a concurrency group: the standalone succeeded and the caller's intake was cancelled. Neither partial device successes nor unrelated engine success bypass the whole-workflow gate.

The next patch adds actual focus/visible-native-frame viewport admission, before/after Dialog normal-bound checks, a canvas-preserving single map toolbar, explicit dock text-line budgets/real label-layout checks, original native PNG retention, and job-wide bounded screenshot transport. [Fourth repair details and actual failing image](verification/r24c-fourth-repair.md). Native execution of these repairs is still required; no green result is invented.

### Actual Servo artifact frontier

[Standalone run39090098092](https://github.com/jyvplay/OSB/actions/runs/39090098092), exact12fbf4f, **SUCCESS**: the actual v0.7.0 AAR was downloaded on the runner and its39,860,285 bytes matched pinned SHA-256 `6b73d23cf63fb3d7c19c908cddafbd475585199583bd16f6aa268b84c6093105`.

Actual `jni/arm64-v8a/libc++_shared.so` and `libservoshell.so` were inspected; both satisfy the parser's strict16K PT_LOAD header congruence condition. No outer-archive filename matched its LICENSE/NOTICE/SBOM heuristic; that is not proof that no notice exists inside nested content. **Integrated=false, runtime_verified=false, legal_accepted=false.** No native code was loaded. This is not APK zip alignment, supported runtime/ABI closure, web compatibility, source reproducibility or license clearance. The local asset download failed; do not confuse the successful remote intake with local execution.

[Actual intake](verification/r24c/servo-0.7.0-intake.json) · [New engine frontier and concrete next experiment](R24C-ADDITIONAL-ENGINE-FRONTIER.md) · [Primary source metadata](research/r24c-servo-source.json). The additional ONLYOFFICE research retains the current primary licensing/mobile-feature boundary rather than pretending an unrestricted native Office editor was integrated.

## Visual evidence, not fabricated artwork

The first actually inspected new frame is the third-candidate silver native capture,280×622: [actual image](verification/r24c-native-previews/third-failed-workflow/r24c-silver.png). Its provenance and hash are beside it. It comes from a **failed overall workflow** whose desktop cases passed, not a whole-app green result.

Observed separated cards, silver/light surfaces, a slanted floor and overflow dock; generic media/map icons prompted the next distinct native play/globe glyphs. Preview text is small and labels can ellipsize. Only named controls/frames are measured; this is not an all-pages/all-states screenshot sweep, color-vision study, visual fidelity oracle, TalkBack test, or beauty certification. The earlier image exporter lost later chunks/target records; incomplete images were not displayed. The fourth candidate retained exactly50 annotations and still lost the last wall chunks. The next transport bounds both per-step and job-wide volume and retains a capture index so absence is visible rather than guessed. Three additional fourth-candidate frames were actually inspected, including the demonstrably empty map: [provenance and observations](verification/r24c-native-previews/fourth-failed-workflow/provenance.json).

Phi remains an optional proportion heuristic. The retrieved research does not establish a universal golden-ratio UI preference. Material target/contrast guidance is translated into finite checks, not a proof that every page is accessible. [Source ledger](research/r24c-primary.md).

## What remains open / next highest-information tests

1. Close all native exact-source failures, including the retained real-IME case; collect/inspect every actually transported frame and visible-target record. Fix the intake CI concurrency collision and re-run affected workflow gates.
2. Test real selected-document audio and video through the system picker, video pixels, audibility on hardware, audio-focus/noisy races and a deliberately slow/revoked content provider. The current main-thread provider call is not bounded by a Handler deadline.
3. Landscape/tablet/laptop AVD and physical-device matrices; all-pages/states at large fonts, pointer/keyboard, TalkBack, contrast and clipped targets. No Android Studio Rabbit1 GUI execution is claimed.
4. Actual supported-ABI browser engine wrapper plus renderer/permission/profile isolation and a held-out web corpus; actual LOK JNI/ABI/16K packaging, office fidelity and license closure. Artifact/source availability alone is not integration.
5. Physical/host privacy threat tests and trusted boot/device-lab work. A passive preexisting logger can be observationally identical to the unattached world at this app interface; no APK can honestly certify its absence from these callbacks.

No invented Android classes, fake service playback, mock native screenshots, database/prover execution, additional autonomous agent, or physical lab was used. Actual work used Python, Kotlin2.2.21 on a disposable host JVM, real files/processes, git/gh, GitHub Actions JDK/SDK/Gradle/KVM AVDs, official-source web retrieval and native screenshot/AX observations. Detailed24 shortcut detectors/strongest attacks and the H+/H−/H∂ boundaries are in the linked plan/verification/privacy documents.
