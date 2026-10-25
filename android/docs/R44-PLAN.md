# R44 plan — A–J contract (24 kernels, 23 modules)

## Runtime honesty
Used: kotlinc-jvm 2.2.21 + JRE 17.0.9 (FOURTH consecutive sandbox
reset; recovery: fetch → hard reset to `a7d2b13` → byte-clean →
toolchain reinstall → R43 372/372 baseline green BEFORE any edit),
CPython 3.11, git/gh, one web search (Tails; the official
release-schedule page is now the cadence primary — "a new version on
each Firefox release, usually every 2 weeks"). The keylogger search
was not re-run (pins three days old; PRD says so). Not available,
never claimed: Android SDK/emulator/device in-sandbox, provers,
fuzzers, human review. `r44Contracts` is authored-not-verified until
CI runs.

## A. Formal model
Admissible: pure-logic kernels; host tools; the two bridge files.
Access: bytes/strings/numbers in; no clocks/network/device state.
Counted: executed assertions, mirrors, tool tests, census rows,
CI-executed tasks. Success: FOR EVERY kernel K: compiled group +
executed contracts + independent mirror + census row; AND totals
strictly exceed R43 (24>23 kernels, 380>372 assertions; modules 23
tie the R43 record; tool tests and the lab battery grow). Not
substituted: SRI parsing ≠ hashing (caller digests); GPS conversion ≠
stripping (the privacy act is the caller's); session codes ≠ checksums
(the confirm flow is the error story, stated); clamping ≠ silent
correction (GoToLine reports); dot-hidden ≠ media-excluded (two
predicates).

## B. Outcome space
H+ (achieved): 24/380/23 + the r44 bridge task. H− (standing): all-A
in one turn impossible; 0/50 accepted; spreadsheet gains hidden-run
arithmetic, writer gains quote-depth hygiene and honest go-to.
H∂ (§I): zero post-execution kernel repairs — third consecutive clean
round on kernels; two wrong test vectors (UPC-E rule-0/1 interleave;
a diagonal pin), one visibility fix (DebVersion.cmpPart public, the
JwtSplit precedent), one generator bug in my own test-main (double
parens) caught by first compile.

## C. Frontier
Imported with hypotheses: ECMA-376 hidden flags; RFC-convention quote
markers (ambiguity declared); ISO 32000-1 §7.3.10 (65535 free-list
ceiling); W3C SRI strongest-wins; Debian Policy §3.2.2 (tilde law
executed on BOTH sides independently); ISO 3297 mod-11; GS1 UPC-E
zero-suppression (all four branches executed); readline history
semantics; EXIF orientation table with the inverse involution; RFC
9110 q-values; PCM arithmetic; frame-budget ceil law.

## D. Negative space (15+, modal first)
1. MODAL: AlphaIndex "supporting" all scripts by charCode math —
   wrong for every non-Latin locale while demoing fine in English.
   Why it fails: locale collation is ICU data, not arithmetic.
   Detector: the é→'#' assertion pins the declared narrowing; any
   silent "improvement" breaks it and forces the ICU decision.
2. SRI that computes hashes (crypto creep). never-hashes grep.
3. SessionCode with a retrofitted checksum claim. Scope grep.
4. GoToLine silently clamping. Result carries requested+clamped.
5. DebVersion via string compare (1.10 < 1.9 bug). Executed.
6. UPC-E by zero-padding only (wrong for rules 0-2). All branches.
7. QuoteNest heuristically "detecting" code blocks. Declared instead.
8. ObjNum tolerant whitespace (round-trip drift). Canon strict.
9. ExifOrient guessing on 0/9 (flipped photos). Refusal.
10. GPS "anonymization" by conversion. The caller strips; stated.
11. Tag parser matching #123 (issue refs are not tags). Refused.
12. HistDedup normalizing entries (history must be exact). Stated.
13. Frame drops as duration>budget booleans (loses multi-miss).
    The ceil law executed at 40ms→2.
14. ISSN check via mod 10 (it is mod 11 with X). X case executed.
15. .nomedia treated as just-a-dotfile (scope conflation). Two
    predicates, both tested.
16. Levenshtein unbounded on huge inputs (DoS-by-API). Cap + banded.
17. Subject markers stripped inside words (Reaction:). Boundary rule.
18. CwdTilde substring-prefix (the /homework bug). Component test.

## E. Mechanism portfolio (six)
| # | Mechanism | Artifacts | Falsifier |
|---|---|---|---|
| 1 | Run/interval arithmetic | SheetHide, FrameBudget | non-conserved merge |
| 2 | Ordered-alphabet total orders | DebVersion, AlphaIndex, BackupName | 1.10<1.9 style inversion |
| 3 | Expansion/compression tables | UpcE, ExifOrient, QuoteNest | wrong branch output |
| 4 | Checksum algebra (mod-11 joins the family) | Issn (+4 prior families) | undetected flip |
| 5 | Banded dynamic programming | Levenshtein | classic/banded disagreement |
| 6 | Intake grammars w/ honesty flags | SriHash, ObjNum, AcceptLang, MsgId-lineage, GoToLine, TagExtract, PrefKeyLint, SessionCode, CwdTilde, HistDedup, SampleBytes, GpsRational, DisplayInch, HiddenDot, SubjectNorm | accepted malformation |

## F. Artifacts — met everywhere (code + counted assertions + mirrors).

## G. Adversary (spot)
A(SheetHide): touching runs; containment. A(DebVersion): ~~ vs ~;
+b1 after release. A(UpcE): all four branches + NS1 refusal + wrong
check. A(Levenshtein): 125-case sweep; length-gap==k edge. A(Accept):
q=0; tie order; one-directional prefix. A(ExifOrient): involution over
all 8. A(FrameBudget): one-ns-over edge. A(BackupName): leap day; the
lex==chrono law. A(SubjectNorm): Reaction:; Re[4]. A(SessionCode):
I/L/O/U each refused.

## H. Verification binding
Bound: kotlinc exits + R44Checks (380/24); 24 handwritten mirrors
(stated; the Debian comparator re-implemented fully in Python);
census ×3 idempotent + --check; inventory pre-lab; gates; lab
receipt; CI ids incl. first r44Contracts run. Downgraded: stated
contracts named in KDocs (PrefKeyLint, SessionCode, HistDedup
compose order).

## I. Repair log
Zero post-execution kernel repairs (third consecutive). Two wrong
test vectors fixed where kernels were right (UPC-E interleave rule;
diagonal pinned to the computed value instead of a rounded guess);
one pre-compile visibility fix (cmpPart public); my test-main
generator emitted double parens — caught by first compile, fixed;
two improvised junk assertions replaced with real vectors BEFORE any
run reached them.

## J. Stopping
Stopped at gates green + lab stamped + CI triaged + ledger. Open:
0/50 accepted. Next highest-information test (R45): the editor
span-exclusion wiring (TOUCHED census flow) and the r34/r36 bridge
backports; PSL data decision still queued.
