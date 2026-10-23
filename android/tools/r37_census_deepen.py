#!/usr/bin/env python3
"""One-shot R37 census deepening: append R37 kernel evidence to 14 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R37 pairing is stale (those must be repaired by hand, not
silently re-pinned). R37 edits three existing sources and their consumers --
XzHead (internal to a new row), RtspReason (new file), and VTodoParse plus the
R34 consumer/reference for the RFC 5545 IN-PROCESS spelling -- so those three
paths are declared as the only allowed-stale set and are re-pinned explicitly.
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R37_CHECKS = ['testing/src/r37/kotlin/dev/fusion/testing/R37Checks.kt',
              'tools/test_r37_tools.py', 'tools/check_r37_reference.py']
CMD = 'python3 tools/run_r37_contracts.py (host, needs KOTLINC/JAVA_HOME)'

# Files R37 edited that other rows already cite; re-pinned, never silently.
TOUCHED = {
    'feature/continuity/src/main/kotlin/dev/fusion/continuity/VTodoParse.kt':
        'RFC 5545 wire token IN-PROCESS (was IN-PROGRESS)',
    'testing/src/r34/kotlin/dev/fusion/testing/R34Checks.kt':
        'R34 consumer fixture follows the RFC 5545 token',
    'tools/check_r34_reference.py':
        'R34 reference follows the RFC 5545 token',
}

DEEPEN = {
    ':feature:notes': ('feature/notes/src/main/java/dev/fusion/notes/SlugHead.kt',
        'R37 deepens again with SlugHead: GitHub-style heading slugs plus duplicate suffixes (hyphens not collapsed, non-ASCII refused) executed on host JVM.'),
    ':feature:files': ('feature/files/src/main/kotlin/dev/fusion/filesui/SizeBucket.kt',
        'R37 deepens again with SizeBucket: IEC binary size scaling with exact unit boundaries and one-decimal rendering executed on host JVM.'),
    ':feature:continuity': ('feature/continuity/src/main/kotlin/dev/fusion/continuity/TaskTally.kt',
        'R37 deepens again with TaskTally: VTODO status tallies with a separate unspecified bucket executed on host JVM.'),
    ':format:archive': ('format/archive/src/main/kotlin/dev/fusion/archive/XzHead.kt',
        'R37 deepens again with XzHead: XZ stream header reader (magic, zero flags byte, assigned Check IDs including 0x0A SHA-256, CRC32 over flags) executed on host JVM against a real fixture.'),
    ':feature:editor': ('feature/editor/src/main/java/dev/fusion/editor/PasteWrap.kt',
        'R37 deepens again with PasteWrap: paste re-indentation onto a base depth with blank-line collapse and ragged indents refused, executed on host JVM.'),
    ':cast:airplay': ('cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspReason.kt',
        'R37 deepens again with RtspReason: RTSP status-line parsing plus the 44-code RFC 2326 §7.1.1 reason table executed on host JVM.'),
    ':privacy': ('privacy/src/main/java/dev/fusion/privacy/UtmStrip.kt',
        'R37 deepens again with UtmStrip: known tracking-parameter stripping with fragment preservation executed on host JVM.'),
    ':core:security': ('core/security/src/main/kotlin/dev/fusion/security/HibpRange.kt',
        'R37 deepens again with HibpRange: HIBP k-anonymity range-response scanning (35-char suffix scan, malformed lines refused) executed on host JVM.'),
    ':feature:maps': ('feature/maps/src/main/kotlin/dev/fusion/maps/PolyLine.kt',
        'R37 deepens again with PolyLine: Google encoded-polyline decode/encode at 1e-5 precision executed on host JVM against the canonical example.'),
    ':feature:media': ('feature/media/src/main/kotlin/dev/fusion/media/Mp4Box.kt',
        'R37 deepens again with Mp4Box: ISO BMFF top-level box walk (64-bit largesize, to-end boxes) executed on host JVM against a real fixture.'),
    ':feature:terminal': ('feature/terminal/src/main/kotlin/dev/fusion/terminal/OscLink.kt',
        'R37 deepens again with OscLink: OSC 8 hyperlink sequence parsing with param validation and the empty-URI close marker executed on host JVM.'),
    ':feature:find': ('feature/find/src/main/kotlin/dev/fusion/find/RankFuse.kt',
        'R37 deepens again with RankFuse: reciprocal-rank fusion with stable first-seen ties executed on host JVM.'),
    ':core:commands': ('core/commands/src/main/kotlin/dev/fusion/commands/ExitCode.kt',
        'R37 deepens again with ExitCode: exit-status naming across shell conventions, BSD sysexits and 128+N Linux signals executed on host JVM.'),
    ':feature:lens': ('feature/lens/src/main/kotlin/dev/fusion/lens/FovMath.kt',
        'R37 deepens again with FovMath: rectilinear field of view plus 35mm-equivalent scaling executed on host JVM.'),
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
    # This script owns the R37 files it cites, so their hashes are re-pinned
    # here; anything else is a real review stall and must fail loudly.
    owned = set(R37_CHECKS) | {prod for prod, _ in DEEPEN.values()} | set(TOUCHED)
    unexpected = [s for s in stale if s[1] not in owned]
    if unexpected:
        raise SystemExit(f'stale pairings outside the declared R37 edit set: {unexpected}')
    for module, path in stale:
        for r in data['rows']:
            if r['module'] == module:
                r['sha256'][path] = sha(path)
    seen = set()
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        prod, sentence = DEEPEN[r['module']]
        if prod in r['production']:
            # Idempotent re-run: never append the sentence twice, never move a hash
            # silently. A re-run must reproduce the same document.
            if sentence not in r['behavior']:
                raise SystemExit(f"{r['module']} lists {prod} without the R37 sentence")
        else:
            r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
            r['production'].append(prod)
        for c in R37_CHECKS:
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
    print('R37 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))

if __name__ == '__main__':
    main()
