#!/usr/bin/env python3
"""Generate traceability CSV or check it. A passed check is NOT a feature test."""
import argparse
import csv
import io
import json
import re
import sys
import module_audit
import release_gate
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIELDS = ['id', 'tier', 'phase', 'sources', 'route', 'tests', 'status', 'gates', 'feature']

def _check(condition, message):
    if not condition:
        raise AssertionError(message)

def render(rows):
    out = io.StringIO(newline='')
    writer = csv.DictWriter(out, fieldnames=FIELDS, lineterminator='\n')
    writer.writeheader()
    for row in rows:
        writer.writerow({key: '; '.join(row[key]) if isinstance(row[key], list) else row[key] for key in FIELDS})
    return out.getvalue()

def validate(rows, spikes):
    _check(len({r['id'] for r in rows}) == len(rows), 'Duplicate requirement ID')
    _check(len({s['id'] for s in spikes}) == len(spikes), 'Duplicate spike ID')
    spike_ids = {s['id'] for s in spikes}
    for row in rows:
        _check(set(row) == set(FIELDS), f"Invalid columns for {row.get('id')}")
        _check(re.fullmatch(r'[A-Z]{2}-\d{2}[a-s]?', row['id']), row['id'])
        _check(row['tier'] in {'A', 'A/B', 'B', 'C', 'X'}, row['id'])
        _check(all(row[f] for f in ('phase', 'route', 'tests', 'sources', 'feature')), row['id'])
        _check(row['status'] in {'not_implemented', 'partial_unverified', 'excluded', 'superseded', 'verified'}, row['id'])
        _check(set(row['gates']) <= spike_ids, row['id'])
    # Exact ID families from owner's tables. Prime labels patch a row, not add one.
    counts = dict(SH=12, WM=16, SR=8, FM=18, NC=6, ST=9, AP=5, TD=6, PR=13,
                  FF=17, MD=9, SC=9, BK=6, CT=6, CA=15, AX=8, AU=5, RG=8, CM=3, BL=5, MS=6)
    expected = {f'{f}-{n:02d}' for f,count in counts.items() for n in range(1,count+1)}
    expected |= {f'PR-07{c}' for c in 'abcdefghijklmnopqrs'}
    expected |= {f'LN-{n:02d}' for n in list(range(1,16))+list(range(20,24))}
    expected |= {'IN-01', 'IN-07', 'IN-08'}
    _check({r['id'] for r in rows} == expected, 'Register differs from normalized owner tables')
    for spike in spikes:
        _check(spike['status'] in {'pending', 'passed', 'failed', 'blocked'}, 'Traceability validation failed')
        if spike['status'] == 'passed':
            _check(spike['evidence'], f"{spike['id']} needs recorded device/license evidence")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--release', action='store_true', help='Fail closed until real acceptance evidence exists')
    args = parser.parse_args()
    rows = json.loads((ROOT/'docs/feature-register.json').read_text())
    spikes = json.loads((ROOT/'docs/spikes.json').read_text())
    validate(rows, spikes)
    generated = render(rows)
    path = ROOT/'requirements.csv'
    if args.check or args.release:
        _check(path.read_text() == generated, 'Stale requirements.csv; run python3 tools/requirements.py')
    else:
        path.write_text(generated)
    print(f'{len(rows)} requirement rows; {len(spikes)} unique spike IDs; structural traceability valid.')
    if args.release:
        audit = json.loads((ROOT/'docs/module-audit.json').read_text())
        included = module_audit.included_modules(ROOT)
        module_audit.validate(audit, included)
        try:
            print(release_gate.verify(ROOT, rows, spikes, audit, included))
        except (ValueError, KeyError, TypeError, OSError) as error:
            print('RELEASE BLOCKED: '+str(error), file=sys.stderr)
            return 1
    return 0

if __name__ == '__main__':
    try:
        sys.exit(main())
    except (AssertionError, KeyError, ValueError) as error:
        print(f'Traceability error: {error}', file=sys.stderr)
        sys.exit(1)
