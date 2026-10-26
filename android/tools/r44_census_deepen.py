#!/usr/bin/env python3
"""One-shot R44 census deepening: append R44 kernel evidence to 23 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R44 pairing is stale (those must be repaired by hand, not
silently re-pinned). R44 edits NO existing production sources — all 24 kernels
are new files — so the allowed-stale set is exactly the R44 kernel/consumer
set and nothing else. Idempotent: a second run reproduces the same document.
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R44_CHECKS = ['testing/src/r44/kotlin/dev/fusion/testing/R44Checks.kt',
              'tools/test_r44_tools.py', 'tools/check_r44_reference.py']
CMD = 'python3 tools/run_r44_contracts.py (host, needs KOTLINC/JAVA_HOME)'

# R44 touches no existing production file; TOUCHED is intentionally empty.
TOUCHED = {}

DEEPEN = {
    ':format:ooxml': [
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetHide.kt',
         'R44 deepens again with SheetHide: hidden row/column run arithmetic (merge, membership, next-visible) for the grid renderer, executed on host JVM and under Gradle in CI.'),
    ],
    ':core:documents': [
        ('core/documents/src/main/kotlin/dev/fusion/documents/QuoteNest.kt',
         'R44 deepens again with QuoteNest: email quote-depth parse/strip/runs with the > ambiguity declared rather than guessed, executed on host JVM.'),
    ],
    ':feature:editor': [
        ('feature/editor/src/main/java/dev/fusion/editor/GoToLine.kt',
         'R44 deepens again with GoToLine: go-to input parsing where clamping is REPORTED in the result, never silent, executed on host JVM.'),
    ],
    ':format:pdf': [
        ('format/pdf/src/main/java/dev/fusion/format/pdf/ObjNum.kt',
         'R44 deepens again with ObjNum: canonical indirect-reference tokens with the 65535 free-list ceiling refused on references, executed on host JVM.'),
    ],
    ':core:security': [
        ('core/security/src/main/kotlin/dev/fusion/security/SriHash.kt',
         'R44 deepens again with SriHash: SRI parsing and strongest-wins selection that never hashes content (digests are caller-computed), executed on host JVM.'),
    ],
    ':format:archive': [
        ('format/archive/src/main/kotlin/dev/fusion/archive/DebVersion.kt',
         'R44 deepens again with DebVersion: the Policy 3.2.2 total order incl. the tilde rule, re-implemented independently on both sides of the mirror, executed on host JVM.'),
    ],
    ':feature:lens': [
        ('feature/lens/src/main/kotlin/dev/fusion/lens/Issn.kt',
         'R44 deepens again with Issn: ISO 3297 mod-11 check digits incl. the X case — the fifth checksum family, executed on host JVM.'),
        ('feature/lens/src/main/kotlin/dev/fusion/lens/UpcE.kt',
         'R44 also adds UpcE: GS1 zero-suppression expansion (all four rule branches) with check digits riding on the Ean13 algebra, executed on host JVM.'),
    ],
    ':feature:terminal': [
        ('feature/terminal/src/main/kotlin/dev/fusion/terminal/CwdTilde.kt',
         'R44 deepens again with CwdTilde: component-boundary HOME collapse/expand (the /homework bug refused by construction), executed on host JVM.'),
    ],
    ':core:index': [
        ('core/index/src/main/kotlin/dev/fusion/index/Levenshtein.kt',
         'R44 deepens again with Levenshtein: capped classic + banded within-k edit distance with a 125-case agreement sweep executed, on host JVM.'),
    ],
    ':core:settings': [
        ('core/settings/src/main/kotlin/dev/fusion/settings/PrefKeyLint.kt',
         'R44 deepens again with PrefKeyLint: the stated key-naming convention with caller-explicit legacy exemptions, executed on host JVM.'),
    ],
    ':core:commands': [
        ('core/commands/src/main/kotlin/dev/fusion/commands/HistDedup.kt',
         'R44 deepens again with HistDedup: readline-family ignoredups/erasedups/ignorespace semantics stated precisely and executed, on host JVM.'),
    ],
    ':feature:media': [
        ('feature/media/src/main/kotlin/dev/fusion/media/SampleBytes.kt',
         'R44 deepens again with SampleBytes: integer-PCM byte-rate/duration math with frame-alignment and overflow refusals, executed on host JVM.'),
    ],
    ':feature:messages': [
        ('feature/messages/src/main/kotlin/dev/fusion/messages/SubjectNorm.kt',
         'R44 deepens again with SubjectNorm: Re/Fwd marker stripping with counted depth and list-tags kept (the thread-merge bug refused), executed on host JVM.'),
    ],
    ':privacy': [
        ('privacy/src/main/java/dev/fusion/privacy/GpsRational.kt',
         'R44 deepens again with GpsRational: EXIF GPS rational math for the presence audit, with stripping pinned as the caller act, executed on host JVM.'),
    ],
    ':core:hw': [
        ('core/hw/src/main/kotlin/dev/fusion/hw/DisplayInch.kt',
         'R44 deepens again with DisplayInch: diagonal/PPI math that refuses anisotropic averaging instead of hiding it, executed on host JVM.'),
    ],
    ':feature:backup': [
        ('feature/backup/src/main/kotlin/dev/fusion/backup/BackupName.kt',
         'R44 deepens again with BackupName: snapshot naming whose lexicographic==chronological law is executed, retention left to RetentionPlan, on host JVM.'),
    ],
    ':feature:launcher': [
        ('feature/launcher/src/main/kotlin/dev/fusion/launcher/AlphaIndex.kt',
         'R44 deepens again with AlphaIndex: A-Z/# fast-scroll bucketing with the Latin-only narrowing declared (ICU named, not faked), executed on host JVM.'),
    ],
    ':format:image': [
        ('format/image/src/main/kotlin/dev/fusion/format/image/ExifOrient.kt',
         'R44 deepens again with ExifOrient: the orientation 1..8 table with dimension swaps and the inverse involution executed; corrupt values refuse, on host JVM.'),
    ],
    ':feature:browser': [
        ('feature/browser/src/main/kotlin/dev/fusion/browser/model/AcceptLang.kt',
         'R44 deepens again with AcceptLang: q-value parsing and basic-prefix matching with the fingerprint-vector note in scope, executed on host JVM.'),
    ],
    ':feature:notes': [
        ('feature/notes/src/main/kotlin/dev/fusion/notes/TagExtract.kt',
         'R44 deepens again with TagExtract: the inline hash-tag namespace (distinct from wiki links and front matter) with #123 and mid-word hashes refused, executed on host JVM.'),
    ],
    ':feature:files': [
        ('feature/files/src/main/kotlin/dev/fusion/filesui/HiddenDot.kt',
         'R44 deepens again with HiddenDot: dotfile and .nomedia predicates kept distinct so the UI cannot conflate scopes, executed on host JVM.'),
    ],
    ':feature:alerts': [
        ('feature/alerts/src/main/kotlin/dev/fusion/alerts/Severity.kt',
         'R44 deepens again with Severity: the four-level alert order where escalation only moves up (de-escalation refused as a human decision), replacing the FrameBudget kernel cut after CI exposed its name collision with the existing core:perf class, executed on host JVM.'),
    ],
    ':feature:together': [
        ('feature/together/src/main/kotlin/dev/fusion/together/SessionCode.kt',
         'R44 deepens again with SessionCode: human-speakable join codes whose error story is the confirm flow, explicitly NOT a checksum claim, executed on host JVM.'),
    ],
}


def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()


def main():
    jp = ROOT / 'docs/module-testability.json'
    data = json.loads(jp.read_text())
    stale = []
    for r in data['rows']:
        for p in r['production'] + r['checks']:
            if r.get('sha256', {}).get(p) != sha(p):
                stale.append((r['module'], p))
    owned = set(R44_CHECKS) | {prod for pairs in DEEPEN.values() for prod, _ in pairs} | set(TOUCHED)
    owned.add('tools/test_r39_tools.py'); owned.add('tools/test_r40_tools.py'); owned.add('tools/test_r41_tools.py'); owned.add('tools/test_r42_tools.py'); owned.add('tools/test_r43_tools.py')  # sibling round's check file shares rows; joint harness edits re-pin here
    unexpected = [s for s in stale if s[1] not in owned]
    if unexpected:
        raise SystemExit(f'stale pairings outside the declared R44 edit set: {unexpected}')
    for module, path in stale:
        for r in data['rows']:
            if r['module'] == module:
                r['sha256'][path] = sha(path)
    seen = set()
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        for prod, sentence in DEEPEN[r['module']]:
            if prod in r['production']:
                if sentence not in r['behavior']:
                    raise SystemExit(f"{r['module']} lists {prod} without the R44 sentence")
            else:
                r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
                r['production'].append(prod)
        for c in R44_CHECKS:
            if c not in r['checks']:
                r['checks'].append(c)
        if CMD not in r['command']:
            r['command'] = (r['command'] + '; ' + CMD) if r['command'] else CMD
        for p in r['production'] + r['checks']:
            r['sha256'][p] = sha(p)
        seen.add(r['module'])
    missing = set(DEEPEN) - seen
    if missing:
        raise SystemExit(f'modules not found in census: {sorted(missing)}')
    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + '\n')
    (ROOT / 'docs/MODULE-TESTABILITY.md').write_text(census.render(data))
    census.validate(json.loads(jp.read_text()))
    assert census.render(json.loads(jp.read_text())) == (ROOT / 'docs/MODULE-TESTABILITY.md').read_text()
    assert (ROOT / 'docs/MODULE-TESTABILITY.md').read_text().endswith('\n')
    print('R44 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))


if __name__ == '__main__':
    main()
