#!/usr/bin/env python3
"""R43 touch-target gate: reuses the R28 static detector; R43 authors no Compose UI.

STRICT still pins the R28 ConsentDialog (it must stay clean; R43 touches no
UI). The full-tree scan is a manual review queue with an R43 delta count, NOT
rendered proof.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import check_r28_touch_targets as base

STRICT = ['app/src/main/java/dev/fusion/shell/ConsentDialog.kt']
R28_BASELINE_FINDINGS = 175

def main():
    base.self_test()
    total_sites = total_findings = 0
    queued = {}
    for kt in sorted(ROOT.rglob('src/main/**/*.kt')):
        if '/build/' in str(kt):
            continue
        sites, findings = base.audit_file(kt)
        total_sites += sites
        total_findings += len(findings)
        if findings:
            queued[str(kt.relative_to(ROOT))] = [(ln, ','.join(m)) for ln, m in findings]
    delta = total_findings - R28_BASELINE_FINDINGS
    print(f'R43 static touch-target scan: {total_sites} Button-family sites, '
          f'{total_findings} static findings across {len(queued)} files '
          f'(delta {delta:+d} vs R28 baseline {R28_BASELINE_FINDINGS}; manual queue, NOT rendered proof).')
    failed = [s for s in STRICT if s in queued]
    if failed:
        print(f'STRICT FAIL (authored UI must be clean): {failed}')
        return 1
    for s in STRICT:
        sites, _ = base.audit_file(ROOT / s)
        print(f'  strict clean: {s} ({sites} sites)')
    return 0

if __name__ == '__main__':
    sys.exit(main())
