#!/usr/bin/env python3
"""Reproduce reviewed behavioral-subset census. Not a source-to-completeness inference."""
import argparse
import hashlib
import json
from pathlib import Path
import module_audit
ROOT=Path(__file__).resolve().parents[1]

def require(ok,message):
    if not ok: raise ValueError(message)

def validate(data,root=ROOT):
    require(data.get('schema')==1 and bool(data.get('standard')),'Missing testability standard')
    rows=data['rows'];names=[r['module'] for r in rows]
    require(len(names)==len(set(names)) and set(names)==set(module_audit.included_modules(root)),'Census must cover all included modules exactly once')
    for row in rows:
        require(type(row['substantial_testable_subset']) is bool,'Testability must be Boolean')
        require(bool(row['behavior']) and bool(row['limit']),'Behavior and limits required')
        yes=row['substantial_testable_subset']
        require(row['kind']==('behavioral_subset' if yes else 'not_established'),'Mismatched classification')
        if yes:
            require(bool(row['command']) and bool(row['production']) and bool(row['checks']),'A label or compiled folder is not testability')
            prefix=row['module'][1:].replace(':','/')+'/'
            require(all(p.startswith(prefix) for p in row['production']),'Production artifact must belong to counted module')
        else:
            require(not row['production'] and not row['checks'] and not row['command'],'Unestablished row must not masquerade as an executable claim')
        for path in row['production']+row['checks']:
            p=Path(path)
            require(not p.is_absolute() and '..' not in p.parts,'Invalid evidence path')
            file=root/p
            require(file.is_file() and not file.is_symlink(),'Missing real evidence file: '+path)
            require(row.get('sha256',{}).get(path)==hashlib.sha256(file.read_bytes()).hexdigest(),'Census review stale: '+path)

def counts(data):
    total=len(data['rows']);yes=sum(r['substantial_testable_subset'] for r in data['rows'])
    return {'total':total,'substantial_testable_subsets':yes,'not_established':total-yes,
            'testable_percent':round(100*yes/total,2),'not_established_percent':round(100*(total-yes)/total,2)}

def render(data):
    c=counts(data)
    lines=['# R21 — substantial executable testability, NOT full feature completion','',
      f"**{c['substantial_testable_subsets']}/{c['total']} modules ({c['testable_percent']:.2f}%) meet this bounded testability standard; {c['not_established']}/{c['total']} ({c['not_established_percent']:.2f}%) are not established by it.**",'',
      f"**Fully specification-accepted modules remain 0/{c['total']} (0%).** Do not substitute the testability figure for delivery or acceptance.", '',data['standard'],'',
      'This is an assistant-authored review with executable path/source-hash checks, not an independent expert audit. A positive row can cover a narrow kernel in an otherwise missing feature. A negative row can contain considerable untested Android code. Existing Gradle unit targets below were invoked in prior native CI, but per-case current outcomes must be read from the exact run; a runnable target is not an inferred new pass. Android targets require the disposable-emulator opt-in described in BUILD.md.', '',
      '| Module | Substantial testable subset? | Concrete behavior / existing source | Reproducible test | Exact limit |','|---|---|---|---|---|']
    for r in data['rows']:
        artifacts='; '.join('`'+p+'`' for p in r['production'])
        checks='; '.join('`'+p+'`' for p in r['checks'])
        target='`'+r['command']+'` — '+checks if r['command'] else 'No qualifying pairing recorded'
        lines.append(f"| `{r['module']}` | {'Yes — subset only' if r['substantial_testable_subset'] else 'Not established'} | {r['behavior']} {artifacts} | {target} | {r['limit']} |")
    lines+=['','Generated from `module-testability.json`; `python3 tools/module_testability.py --check`. SHA-256 binding rejects stale source/test pairings. Structural checks do not automatically judge the truth or sufficiency of the authored behavioral descriptions.','']
    return '\n'.join(lines)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    data=json.loads((ROOT/'docs/module-testability.json').read_text());validate(data)
    report=ROOT/'docs/MODULE-TESTABILITY.md';text=render(data)
    if args.check: require(report.read_text()==text,'Stale testability report')
    else: report.write_text(text)
    print(json.dumps(counts(data),sort_keys=True))

if __name__=='__main__':main()
