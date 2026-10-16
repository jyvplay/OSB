# R20 preimplementation contract (2026-10-15)

Scope: native Android only; no Tails equivalence or universal anonymity. Existing original 48 modules: 47 substantial testable subsets, zero fully accepted. A separate no-INTERNET companion is an additional APK/module, not a renamed existing module. Its initial spike is not counted as a substantial feature.

Mechanism branches before implementation:
1. Separate-UID no-INTERNET APK. Actual IPv4/IPv6 socket creation must fail specifically with EACCES/EPERM; AF_UNIX positive control. No claim of blocking all Android IPC or other apps.
2. Secure window. Actual screen capture must contain a colored marker without FLAG_SECURE and exclude it with FLAG_SECURE. Not proof against cameras, root, accessibility or compromised OS.
3. Volatile scratchpad. Replace/disconnect the whole editor on pause/explicit discard, disable saved hierarchy, autofill, personalized-learning request and clipboard actions. Merely clearing text can leave undo history; attack that. No managed-heap/IME/OS secure-erasure theorem.
4. Counts-only task export. Publish aggregates only; no title, UUID, due date, revision or input hash. Equal count vectors must generate byte-identical payloads. Counts/context can identify users; not anonymization or a backup. Exact new-document readback remains mandatory.
5. Offline link disclosure projection. Validate explicit HTTP(S), strip query/fragment, retain raw path visibly. No networking or navigation; paths/host can still identify people. Reject credentials/malformed inputs, don't silently decode escapes.
6. Exact-origin browser permission leases. Bounded session-only, short lived, one-shot, origin/tab/profile/permission-bound. Navigation/close revocation; no actual browser permission/UI integration claimed until wired/tested. Never substitute a lease model for Tor/fingerprinting defenses or full browser.
7. Candidate-specific evidence policy. Reject 20 broad anonymity shortcuts; exact commit and named green cases required, not offline receipt authentication.

H+: bounded controls/projections are implementable under trusted Android/IME/caller assumptions. H-: an ordinary app cannot make the host OS, external services, user identity disclosure or all traffic anonymous; no universal Tails superiority claim is admissible. H-boundary: UID/permission boundary, lifecycle, screenshot backend, timestamps, same-origin identity, aggregate equality and verifier scope.

Threshold: fresh platform spike before companion feature code; production/public-consumer compilation, independent Python/openpyxl references, actual native cases for UI/OS claims; retain failures and add a patch-specific attack after repair. No fresh native pass inferred from R19. No agents, physical devices or expert/legal reviewers available. Search/fetch tools, gh/git, shell/Python, cached Kotlin/JRE/oracles and GitHub managed Android are available subject to actual execution.

## Postimplementation qualification (R20)

The same-count byte contract above is conditional on the same writer/runtime environment,
not a cross-runtime anonymity theorem. A subsequent four-timezone JVM experiment found
that the original fixed 1980 UTC timestamp disclosed timezone through DOS headers/ZIP extras.
The counterexample was retained before a fixed Unix epoch repair, and new host and native
checks are recorded in R20-ENGINEERING.md. This addition is not a claim that this specific
repair/spike had already been run when the original plan was written.
