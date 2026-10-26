#!/usr/bin/env python3
"""Page/view beauty-audit matrix: tracks WHICH screens have layout-bounds exports.

The matrix below names every auditable screen/state. A status file maps page ids to
{"export": <bounds.json path>} or {"waiver": <reason>}. This gate validates the schema
and reports coverage = exported/total. It FAILS on unknown ids, missing entries, or
exports that violate the 48dp/inside budget -- it never invents exports.

Without arguments, runs a self-test on a fixed sample (not a page pass).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import check_r26_layout_bounds as bounds

MATRIX = [
    'tabletop-overview', 'tabletop-focused-table', 'tabletop-focused-back', 'tabletop-focused-left',
    'tabletop-focused-right', 'flat-desktop', 'art-dock', 'business-taskbar', 'control-panel',
    'settings-appearance', 'media-player', 'media-services', 'offline-atlas', 'png-copy',
    'privacy-scratch', 'privacy-pad', 'editor', 'tasks', 'notes', 'browser', 'stream-player',
    'vector-maps', 'orbot-handoff', 'beauty-dock',
]

def audit(status):
    assert set(status) == set(MATRIX), f'matrix mismatch: missing={set(MATRIX)-set(status)} extra={set(status)-set(MATRIX)}'
    exported, waived, violations = [], {}, {}
    for page in MATRIX:
        entry = status[page]
        assert set(entry) <= {'export', 'waiver'} and len(entry) == 1, f'bad entry: {page}'
        if 'waiver' in entry:
            assert isinstance(entry['waiver'], str) and len(entry['waiver']) >= 20, f'short waiver: {page}'
            waived[page] = entry['waiver']
        else:
            doc = json.loads(Path(entry['export']).read_text())
            found = bounds.audit(doc)
            if found:
                violations[page] = found
            exported.append(page)
    return {'total': len(MATRIX), 'exported': len(exported), 'waived': len(waived),
            'coverage': round(len(exported) / len(MATRIX), 4), 'violations': violations}

def main():
    if len(sys.argv) == 1:
        doc = {'viewport': [0, 0, 390, 844], 'targets': [
            {'id': 'ok', 'rect': [8, 8, 72, 72]}, {'id': 'bad', 'rect': [8, 80, 40, 120]}]}
        assert bounds.audit(doc) == {'bad': ['small-target']}
        status = {p: {'waiver': 'R27 self-test waiver: no device export exists yet.'} for p in MATRIX}
        result = audit(status)
        assert result['exported'] == 0 and result['coverage'] == 0.0 and result['waived'] == len(MATRIX)
        print(f"R27 page-matrix self-test PASS: {len(MATRIX)} pages tracked, schema + detector verified, coverage honestly 0.0 (no exports).")
        return
    status = json.loads(Path(sys.argv[1]).read_text())
    result = audit(status)
    print(json.dumps(result, indent=2))
    if result['violations']:
        raise SystemExit(1)

if __name__ == '__main__':
    main()
