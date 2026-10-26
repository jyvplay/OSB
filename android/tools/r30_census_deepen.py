#!/usr/bin/env python3
"""One-shot R30 census deepening: append R30 kernel evidence to 10 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R30 pairing is stale (those must be repaired by hand, not
silently re-pinned). R30 edits no existing source file, so the allowed-stale
set is empty.
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_audit
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R30_CHECKS = ['testing/src/r30/kotlin/dev/fusion/testing/R30Checks.kt',
              'tools/test_r30_tools.py', 'tools/check_r30_reference.py']
CMD = 'python3 tools/run_r30_contracts.py (host, needs KOTLINC/JAVA_HOME)'

DEEPEN = {
    ':cast:airplay': ('cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspMessage.kt',
        'R30 deepens with RtspMessage: strict RTSP/1.0 request parser/serializer (request line, CSeq, Content-Length framing, SDP body) executed on host JVM against a real fixture.'),
    ':feature:capture': ('feature/capture/src/main/kotlin/dev/fusion/capture/Timecode.kt',
        'R30 deepens with Timecode: millisecond to HH:MM:SS:FF conversion with fps-gated frame field and whole-day clamps executed on host JVM.'),
    ':feature:continuity': ('feature/continuity/src/main/kotlin/dev/fusion/continuity/VEventParse.kt',
        'R30 deepens with VEventParse: single-VEVENT ICS parser (CRLF framing, unfold, text unescape, UTC instants, DTEND-after-DTSTART) executed on host JVM against a real fixture.'),
    ':feature:together': ('feature/together/src/main/kotlin/dev/fusion/together/LamportClock.kt',
        'R30 deepens with LamportClock: Lamport causal clock (tick/receive/max-plus-one) with overflow refusal executed on host JVM.'),
    ':format:image': ('format/image/src/main/kotlin/dev/fusion/format/image/GifInfo.kt',
        'R30 deepens with GifInfo: GIF87a/89a logical-screen and frame-count walker (extension skipping, trailer exactness) executed on host JVM against a real fixture.'),
    ':feature:settings': ('feature/settings/src/main/kotlin/dev/fusion/settingsui/PatchScope.kt',
        'R30 deepens again with PatchScope: dotted-key settings patch scoping (exact/prefix permits, allow/refuse partition) executed on host JVM.'),
    ':format:archive': ('format/archive/src/main/kotlin/dev/fusion/archive/TarHeader.kt',
        'R30 deepens with TarHeader: POSIX ustar header parser (octal fields, checksum, magic/version, name+prefix join) executed on host JVM against a real fixture.'),
    ':feature:lens': ('feature/lens/src/main/kotlin/dev/fusion/lens/ZoomLadder.kt',
        'R30 deepens with ZoomLadder: half-stop digital-zoom ladder (ratio/nearest-index, 1x-16x clamp) executed on host JVM.'),
    ':feature:messages': ('feature/messages/src/main/kotlin/dev/fusion/messages/SmsSegments.kt',
        'R30 deepens with SmsSegments: GSM-7/extension/UCS-2 classification with septet counting and multipart segment math (counts only, no sending) executed on host JVM.'),
    ':core:model': ('core/model/src/main/kotlin/dev/fusion/model/DensitySelect.kt',
        'R30 deepens with DensitySelect: dpi-plus-touch density-tier selection (COMPACT/BALANCED/TOUCH, 180/280 cuts against the DensityProfile ladder) executed on host JVM.'),
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
    if stale:
        raise SystemExit(f'non-R30 stale pairings (repair by hand): {stale}')
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        prod, sentence = DEEPEN[r['module']]
        assert prod not in r['production'], prod
        r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
        r['production'].append(prod)
        for c in R30_CHECKS:
            if c not in r['checks']:
                r['checks'].append(c)
        if CMD not in r['command']:
            r['command'] = (r['command'] + '; ' + CMD) if r['command'] else CMD
        r['sha256'][prod] = sha(prod)
        for c in R30_CHECKS:
            r['sha256'][c] = sha(c)
        for p in r['production'] + r['checks']:
            r['sha256'][p] = sha(p)
    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + '\n')
    (ROOT / 'docs/MODULE-TESTABILITY.md').write_text(census.render(data))
    census.validate(json.loads(jp.read_text()))
    assert census.render(json.loads(jp.read_text())) == (ROOT / 'docs/MODULE-TESTABILITY.md').read_text()
    assert (ROOT / 'docs/MODULE-TESTABILITY.md').read_text().endswith('\n')
    print('R30 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))

if __name__ == '__main__':
    main()
