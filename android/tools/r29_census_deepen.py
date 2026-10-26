#!/usr/bin/env python3
"""One-shot R29 census deepening: append R29 kernel evidence to 11 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R29 pairing is stale (those must be repaired by hand, not
silently re-pinned).
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_audit
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R29_CHECKS = ['testing/src/r29/kotlin/dev/fusion/testing/R29Checks.kt',
              'tools/test_r29_tools.py', 'tools/check_r29_reference.py']
CMD = 'python3 tools/run_r29_contracts.py (host, needs KOTLINC/JAVA_HOME)'

DEEPEN = {
    ':feature:beam': ('feature/beam/src/main/kotlin/dev/fusion/beam/ShareManifest.kt',
        'R29 deepens with ShareManifest: validated share offer (bare names, mime/sha/total caps, canonical digest, frame codec) executed on host JVM against a real fixture.'),
    ':core:storage': ('core/storage/src/main/kotlin/dev/fusion/storage/QuotaLedger.kt',
        'R29 deepens with QuotaLedger: per-scope byte quotas with allocate/release accounting and snapshot frames executed on host JVM.'),
    ':feature:alerts': ('feature/alerts/src/main/kotlin/dev/fusion/alerts/AlertThrottle.kt',
        'R29 deepens with AlertThrottle: per-key token-bucket throttle with backwards-clock clamp and refuse-when-full table executed on host JVM.'),
    ':feature:files': ('feature/files/src/main/kotlin/dev/fusion/filesui/FileSort.kt',
        'R29 deepens with FileSort: total file-list ordering (key, case-folded name, raw name) executed on host JVM.'),
    ':core:commands': ('core/commands/src/main/kotlin/dev/fusion/commands/AliasTable.kt',
        'R29 deepens with AliasTable: bounded alias table with cycle-safe argv[0] expansion executed on host JVM.'),
    ':feature:accessibility': ('feature/accessibility/src/main/kotlin/dev/fusion/a11y/ContrastRatio.kt',
        'R29 deepens with ContrastRatio: WCAG 2.x relative-luminance/ratio math with AA/AAA thresholds executed on host JVM.'),
    ':core:settings': ('core/settings/src/main/kotlin/dev/fusion/settings/LocaleMatch.kt',
        'R29 deepens with LocaleMatch: BCP-47-subset locale negotiation (exact, same-language, list default) executed on host JVM.'),
    ':feature:backup': ('feature/backup/src/main/kotlin/dev/fusion/backup/RetentionPlan.kt',
        'R29 deepens with RetentionPlan: grandfather-father-son selection over caller-supplied day/week/month buckets executed on host JVM.'),
    ':cast:dlna': ('cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/DidlLite.kt',
        'R29 deepens with DidlLite: DIDL-Lite item parser (identity, class, title, first res, strict duration) reusing the shared XML unescaper, executed on host JVM against real fixtures.'),
    ':format:ooxml': ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/CsvDialect.kt',
        'R29 deepens with CsvDialect: delimiter sniffing plus quote-aware translation into the CsvTable comma grammar, executed end-to-end on host JVM against real fixtures.'),
    ':app': ('app/src/main/java/dev/fusion/shell/UsbAllowlist.kt',
        'R29 executes UsbAllowlist store logic (vid:pid grammar, capped add/remove/contains) on host JVM against an in-memory fake of the two Android APIs it touches; the SharedPreferences wiring itself is authored-only.'),
}

def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()

def main():
    jp = ROOT / 'docs/module-testability.json'
    data = json.loads(jp.read_text())
    # Pre-check: every existing pairing must be fresh, except the one R29
    # knowingly edited (UpnpParsing.kt gains unescapeXml, additive).
    stale = []
    for r in data['rows']:
        for p in r['production'] + r['checks']:
            if r.get('sha256', {}).get(p) != sha(p):
                stale.append((r['module'], p))
    allowed = {(':cast:dlna', 'cast/dlna/src/main/java/dev/fusion/cast/dlna/UpnpParsing.kt')}
    bad = [s for s in stale if s not in allowed]
    if bad:
        raise SystemExit(f'non-R29 stale pairings (repair by hand): {bad}')
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        prod, sentence = DEEPEN[r['module']]
        assert prod not in r['production'], prod
        r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
        r['production'].append(prod)
        for c in R29_CHECKS:
            if c not in r['checks']:
                r['checks'].append(c)
        if CMD not in r['command']:
            r['command'] = (r['command'] + '; ' + CMD) if r['command'] else CMD
        r['sha256'][prod] = sha(prod)
        for c in R29_CHECKS:
            r['sha256'][c] = sha(c)
        for p in r['production'] + r['checks']:  # refresh the one R29-edited sibling
            r['sha256'][p] = sha(p)
    census.validate(data)
    jp.write_text(json.dumps(data, indent=1) + '\n')
    (ROOT / 'docs/MODULE-TESTABILITY.md').write_text(census.render(data))
    census.validate(json.loads(jp.read_text()))
    assert census.render(json.loads(jp.read_text())) == (ROOT / 'docs/MODULE-TESTABILITY.md').read_text()
    print('R29 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))

if __name__ == '__main__':
    main()
