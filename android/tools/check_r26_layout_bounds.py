#!/usr/bin/env python3
"""Layout-bounds beauty gate: validates an exported rect list against the 48dp/inside budget.

Input JSON: {"viewport": [l, t, r, b], "targets": [{"id": ..., "rect": [l, t, r, b]}, ...]}.
Mirrors DesktopMetrics.targetViolations exactly (0.01 tolerance). Intended for CI exports
of real Compose layout bounds; the sample below is a schema test, not a page pass.
"""
import json
import sys
from pathlib import Path

MIN_TARGET = 48.0
TOL = 0.01

def violations(rect, viewport):
    left, top, right, bottom = rect
    out = []
    if right - left + TOL < MIN_TARGET or bottom - top + TOL < MIN_TARGET:
        out.append('small-target')
    vl, vt, vr, vb = viewport
    if not (left >= vl - TOL and top >= vt - TOL and right <= vr + TOL and bottom <= vb + TOL):
        out.append('clipped-target')
    return out

def audit(doc):
    return {t['id']: violations(t['rect'], doc['viewport']) for t in doc['targets'] if violations(t['rect'], doc['viewport'])}

def main():
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if path is None:
        # Schema self-test with a fixed sample (2 clean, 1 small, 1 clipped).
        doc = {'viewport': [0, 0, 390, 844], 'targets': [
            {'id': 'ok-a', 'rect': [8, 8, 72, 72]},
            {'id': 'ok-b', 'rect': [8, 80, 382, 140]},
            {'id': 'bad-small', 'rect': [8, 150, 40, 190]},
            {'id': 'bad-clip', 'rect': [350, 800, 420, 860]}]}
        found = audit(doc)
        assert found == {'bad-small': ['small-target'], 'bad-clip': ['clipped-target']}, found
        print('R26 layout-bounds self-test PASS: schema + both detectors fire on the fixed sample.')
        return
    doc = json.loads(path.read_text())
    found = audit(doc)
    print(json.dumps({'file': str(path), 'targets': len(doc['targets']), 'violations': found}, indent=2))
    if found:
        raise SystemExit(1)

if __name__ == '__main__':
    main()
