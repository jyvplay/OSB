#!/usr/bin/env python3
"""Static Compose touch-target audit: a no-SDK substitute for visual inspection.

For every Button-family call site (Button, OutlinedButton, TextButton,
IconButton, FilledTonalButton, ElevatedButton) in main sources, the next 8
lines must contain a 48dp minimum (literal `48.dp` or
`minimumInteractiveComponentSize`) AND a `testTag(`. Violations are STATIC
findings: they prove a missing declaration, never a rendered size. Rendered
48dp/inside-viewport proof still requires device layout-bounds exports via
check_r26_layout_bounds.py / check_r27_pagematrix.py.

Gate: the detector self-test must pass, and every STRICT file (R28-authored
UI) must be clean. Pre-existing findings are reported per file as a manual
review queue; the script never rewrites sources.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUTTON = re.compile(r'\b(Button|OutlinedButton|TextButton|IconButton|FilledTonalButton|ElevatedButton)\s*\(')
SIZE = re.compile(r'48\.dp|minimumInteractiveComponentSize')
TAG = re.compile(r'testTag\s*\(')
STRICT = ['app/src/main/java/dev/fusion/shell/ConsentDialog.kt']

def audit_file(path):
    lines = Path(path).read_text().splitlines()
    findings = []
    sites = 0
    for i, line in enumerate(lines):
        if not BUTTON.search(line):
            continue
        sites += 1
        window = '\n'.join(lines[i:i + 8])
        missing = []
        if not SIZE.search(window):
            missing.append('no-48dp')
        if not TAG.search(window):
            missing.append('no-testTag')
        if missing:
            findings.append((i + 1, missing))
    return sites, findings

def self_test():
    good = '''OutlinedButton(onClick = {}, modifier = Modifier.heightIn(min = 48.dp).testTag("x")) { Text("X") }'''
    no_size = '''TextButton(onClick = {}, modifier = Modifier.testTag("x")) { Text("X") }'''
    no_tag = '''Button(onClick = {}, modifier = Modifier.heightIn(min = 48.dp)) { Text("X") }'''
    multi = '''Button(
        onClick = {},
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("y"),
    ) { Text("Y") }'''
    import tempfile, os
    cases = [(good, 0), (no_size, 1), (no_tag, 1), (multi, 0)]
    for body, want in cases:
        with tempfile.NamedTemporaryFile('w', suffix='.kt', delete=False) as f:
            f.write(body)
        try:
            sites, findings = audit_file(f.name)
        finally:
            os.unlink(f.name)
        assert sites == 1 and len(findings) == want, (body[:40], sites, findings)
    with tempfile.NamedTemporaryFile('w', suffix='.kt', delete=False) as f:
        f.write('// no buttons here\nval x = ButtonState.DEFAULT\n')
    try:
        assert audit_file(f.name) == (0, [])
    finally:
        os.unlink(f.name)
    print('touch-target detector self-test PASS (4 samples + negative control).')

def main():
    self_test()
    strict = sys.argv[1:] or STRICT
    total_sites = total_findings = 0
    queued = {}
    for kt in sorted((ROOT).rglob('src/main/**/*.kt')):
        if '/build/' in str(kt):
            continue
        sites, findings = audit_file(kt)
        total_sites += sites
        total_findings += len(findings)
        if findings:
            queued[str(kt.relative_to(ROOT))] = [(ln, ','.join(m)) for ln, m in findings]
    print(f'static touch-target scan: {total_sites} Button-family sites, '
          f'{total_findings} static findings across {len(queued)} files (manual review queue, NOT rendered proof).')
    for rel in sorted(queued):
        print(f'  queue {rel}: {len(queued[rel])} sites {queued[rel][:4]}')
    failed = [s for s in strict if s in queued]
    if failed:
        print(f'STRICT FAIL (R28-authored UI must be clean): {failed}')
        return 1
    for s in strict:
        p = ROOT / s
        if p.is_file():
            sites, _ = audit_file(p)
            print(f'  strict clean: {s} ({sites} sites)')
        else:
            print(f'  strict missing (not yet written): {s}')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
