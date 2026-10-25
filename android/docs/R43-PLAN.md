# R43 plan — A–J contract (23 kernels, 23 modules)

## Runtime honesty
Used: kotlinc-jvm 2.2.21 + JRE 17.0.9 (THIRD consecutive sandbox reset;
recovery protocol ran: fetch → hard reset to `24753ef` → byte-clean →
toolchain reinstall → R42 362/362 baseline green BEFORE any edit),
CPython 3.11, git/gh, one web search (Tails currency; the keylogger
search was again not re-run — stated in the PRD, pins now two days
old). Not available, never claimed: Android SDK/emulator/device
in-sandbox, provers, fuzzers, human review. `r43Contracts` is
authored-not-verified until CI runs; the ledger records the outcome.

## A. Formal model
Admissible: pure-logic kernels in existing modules; host tools; the two
bridge files. Access: bytes/strings/numbers in; no clocks (TimeCoarse
and AlertKey take epochs as arguments), no network, no device state.
Counted: executed assertions, mirrors, tool tests, census rows,
CI-executed tasks. Success (quantifiers explicit): FOR EVERY kernel K:
compiled group + executed contracts + independent mirror + census row;
AND totals strictly exceed R42 (23>22 kernels, 372>362 assertions,
23>21 modules, 38>36 tool tests). Units/boundaries per KDoc (Damm-free
this round; QR lattice 21..177; Apdex 4T fixed; BPM 20..400; API table
21..36). Adjacent problems not substituted: permission bits ≠
enforcement (PdfPerm); RP-ID suffix ≠ full WebAuthn validity (PSL flag
carried IN THE RESULT TYPE); coarsening ≠ anonymity (TimeCoarse);
policy table ≠ header serialization (RefererPolicy); recognition ≠
cryptography (AgeId).

## B. Outcome space
H+ (achieved): 23/372/23 + the r43 bridge task. H− (standing): all-A in
one turn impossible; 0/50 accepted; spreadsheet gains frozen panes
(upholding the cut twips formula as cut), writer gains Trojan-Source
hygiene + duplicate-line cleanup. H∂: one REAL kernel hole found by
first execution (TrackerTier's regex accepted embedded whitespace via a
`.*` tail) — repaired to `\S*`, attack kept, re-run from zero; three
wrong test vectors fixed where kernels were right (rows-only pane
classification; snippet tie-start 35; Apdex 0.75) — each message now
carries the corrected reasoning.

## C. Frontier
Imported with hypotheses: ECMA-376 frozen panes; Unicode bidi controls
(Trojan Source class); ISO 32000-1 Table 22; W3C Referrer Policy's
eight tokens + default; Debian Policy §5.1; public Android API table
(21..36, Baklava last — API 37 refused until named); BEP 12; ISO 18004
size law; WebAuthn dot-boundary rule MINUS PSL (flagged); npm caret/
tilde expansions; age-spec string forms; Apdex formula; RFC 2326
transport subset; RFC 5322 msg-id dot-atoms. Open interface unchanged:
rendering/device/live-network named in RESULTS §Scope.

## D. Negative space (15+ refused shapes)
1. Modal shortcut: RP-ID matching WITHOUT the PSL caveat — "example.org
   matches org" would silently pass; the kernel RETURNS pslChecked=false
   and the test asserts the dangerous match to prove why the flag exists.
2. PdfPerm as DRM enforcement. Scope string + tool grep.
3. BidiStrip auto-stripping legit RTL silently. KDoc pins user-visible
   choice; strip() keeps RTL letters (tested).
4. Referrer "hardening" that breaks the spec table. Full 8x table executed.
5. TimeCoarse marketed as anonymization. NOT-anonymity grep.
6. AgeId decoding keys. Zero-cryptography grep; identity strings only
   flagged SECRET-SHAPED.
7. ApiName inventing API 37's name. Refusal test.
8. Apdex with configurable multiplier. 4T fixed; stated.
9. Tap tempo by mean (one miss wrecks it). Median tested against a miss.
10. Tracker URLs by `.*` regex — the ACTUAL bug this round; repaired,
    attack kept.
11. QR capacity tables hand-waved in. Geometry only.
12. VersionRange matching prereleases loosely. Refused by name.
13. Breadcrumb trimming mid-component. Boundary-only, floor reported.
14. DebControl folding first lines. Continuation-before-field refusal.
15. SnippetWindow scanning all starts O(n·W). Candidate-edge set with
    a brute-force agreement property instead.
16. AspectSnap by linear ratio diff (asymmetric). Log-distance tested
    via the portrait case.
17. AlertKey escaping pipes (version-unstable keys). Refusal instead.
18. CaseCycle-style silent locale bugs — N/A this round but the
    PercentChange negative-base restriction is the same move: refuse
    where conventions conflict.

## E. Mechanism portfolio (six, independence kept)
| # | Mechanism | Artifacts | Proved | Falsifier |
|---|---|---|---|---|
| 1 | Classification tables | SheetFreeze, RefererPolicy, ApiName, Apdex bands | truth tables executed | one wrong cell |
| 2 | Hygiene scanners | BidiStrip, DupLines | strip/balance/idempotence | non-idempotent strip |
| 3 | Bit/lattice laws | PdfPerm, QrVersion, TimeCoarse, AlertKey | roundtrips + bijections | broken roundtrip |
| 4 | Suffix/range algebra | RpIdMatch, VersionRange, AspectSnap | boundary vectors + log symmetry | dot-boundary breach |
| 5 | Field grammars | DebControl, RtspTransport, MsgIdParse, TrackerTier | spec shapes + refusal walls | accepted malformation |
| 6 | Selection/robust stats | SnippetWindow, Bpm, SeatColor, BreadcrumbTrim, PercentChange | optimality/median/separation properties | brute-force disagreement |

## F. Artifact requirement — met
Executed code + counted assertions + independent mirrors everywhere;
the RP-ID danger case is executed POSITIVELY to justify the honesty flag.

## G. Second-order adversary (spot)
A(SheetFreeze): rows-only freeze classification (caught my own wrong
vector). A(BidiStrip): pop-before-push. A(PdfPerm): encode of all-false
and decode-encode fixpoint. A(Referrer): http→https is NOT a downgrade.
A(DebControl): " ." marker; tab continuation. A(TrackerTier): the
whitespace URL (kernel repair). A(VersionRange): tilde on 0.0.x still
bumps minor. A(SnippetWindow): end-minus-width candidate wins a tie.
A(AgeId): 51-ones payload pads clean; bech32m variant refused.
A(Apdex): 4T inclusive edge. A(RtspTransport): single-port range legal.

## H. Verification binding
Bound: kotlinc exits + R43Checks (372/23); 23 handwritten mirrors
(downgrade stated in-file); census ×3 idempotent + --check; inventory
regenerated pre-lab; gates; lab receipt; CI ids post-push incl. the
first r43Contracts run. Downgraded: no new held-out stdlib family fit
these surfaces; SeatColor/BreadcrumbTrim/AlertKey are stated contracts
(their KDocs say so).

## I. Repair log
One KERNEL repair post-execution (TrackerTier regex `.*`→`\S*`; attack
kept; full re-run from zero). Three wrong test vectors (kernel right
each time) with corrected reasoning in the messages. One consumer
compile fix (a non-enum `.values()` guard). One mirror tautology
caught and replaced BEFORE its first run; the no-tautology gate grew a
consumer-side wide-tolerance check.

## J. Stopping
Stopped at gates green + lab stamped + CI triaged + ledger. Open: 0/50
accepted. Next highest-information test (R44): the editor
span-exclusion wiring (first TOUCHED-path census flow), r34/r36 Gradle
backports, and a PSL-data decision so RpIdMatch's flag can one day
flip true honestly.
