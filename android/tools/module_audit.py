#!/usr/bin/env python3
"""Reproduce module counts, without treating source presence or assertion totals as completion."""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def included_modules(root=ROOT):
    return re.findall(r'"(:[\w:]+)"', (root/'settings.gradle.kts').read_text())

def validate(audit, included):
    names = [r['module'] for r in audit['modules']]
    assert len(names) == len(set(names)) and set(names) == set(included), 'Audit and included modules differ'
    assert audit['common_acceptance_gates'] and all(type(v) is bool for v in audit['common_acceptance_gates'].values())
    for row in audit['modules']:
        assert row['status'] in {'incomplete', 'complete'}
        assert row['present_scope'] and row['remaining_gap']
        assert row['evidence_level'] in {'partial_kernel_contracts', 'compiled_only', 'runner_executed', 'android_uncompiled', 'android_compiled', 'partial_android_execution', 'empty_profile'}
        if row['status'] == 'complete':
            assert all(audit['common_acceptance_gates'].values()), 'A complete module cannot bypass common acceptance gates'
            assert row.get('reviewed_acceptance_record'), 'Whole-module acceptance needs a reviewed record, not a test count'
    slots = [r['slot'] for r in audit['architecture_mapping']]
    assert len(slots) == len(set(slots))
    for row in audit['architecture_mapping']:
        assert row['kind'] in {'exact_name', 'partial_owner_not_equivalent', 'absent'}
        assert set(row['candidates']) <= set(included)
        if row['kind'] == 'exact_name': assert row['candidates'] == [row['slot']]
        if row['kind'] == 'absent': assert not row['candidates']

def counts(audit):
    total = len(audit['modules'])
    complete = sum(row['status'] == 'complete' for row in audit['modules'])
    return dict(total=total, complete=complete, unfinished=total-complete,
                complete_percent=100*complete/total if total else 0,
                unfinished_percent=100*(total-complete)/total if total else 0)

def render(audit):
    c = counts(audit)
    architecture = audit['architecture_mapping']
    exact = sum(r['kind'] == 'exact_name' for r in architecture)
    alternatives = sum(r['kind'] == 'partial_owner_not_equivalent' for r in architecture)
    absent = sum(r['kind'] == 'absent' for r in architecture)
    kernel_count = sum(r['evidence_level'] == 'partial_kernel_contracts' for r in audit['modules'])
    compiled_count = sum(r['evidence_level'] == 'compiled_only' for r in audit['modules'])
    lines = [f'# Native module completion audit — {audit["revision"]}', '',
        f"**Included Gradle modules: {c['total']}. Specification-complete: {c['complete']} ({c['complete_percent']:.2f}%). Still requiring completion/acceptance: {c['unfinished']} ({c['unfinished_percent']:.2f}%).**", '',
        'This is not a time/effort estimate. A module is complete only after its entire assigned scope, integration, mandatory safety/accessibility/device tests and license/build gates have reviewed acceptance. A source file, successful compilation or many assertions is insufficient.', '',
        '## Common gates (build evidence is narrower than acceptance)', '']
    lines += [f"- {k.replace('_', ' ')}: {'passed' if v else 'unpassed'}" for k,v in audit['common_acceptance_gates'].items()]
    lines += ['', audit.get('build_evidence','No build evidence recorded.'), '', '## All included modules', '', '| Module | Present source scope | Current evidence | Accepted | Exact remaining gap |', '|---|---|---|---|---|']
    for r in audit['modules']:
        lines.append(f"| `{r['module']}` | {r['present_scope']} | {r['evidence_level'].replace('_',' ')} | {'1/1 (100%)' if r['status']=='complete' else '0/1 (0%)'} | {r['remaining_gap']} |")
    lines += ['', f'The {kernel_count} `partial kernel contracts` rows have executed tests of selected production logic, not whole-module acceptance. {compiled_count} `compiled only` rows were compiled as sources but not exercised by these runners. `:testing` is the executed harness, not another completed product feature. `android compiled` means source compilation, not working assigned behavior; `partial android execution` means named subset cases executed, including any recorded failures. See R11B-ENGINEERING.md for exact runtime results. No row is a full feature pass.', '',
        '## Specification architecture reconciliation', '',
        f'The architecture-name inventory below has **{len(architecture)} named main-build slots**, not {c["total"]} interchangeable completed features. Of these, **{exact} match included names**, **{alternatives} have partial alternative owners**, and **{absent} have no separate included module**. {c["total"]-exact} current included modules are additional/alternative owners. Name matching is not capability fulfillment. Calculator is required by PR-09 even though its dedicated module is additional to the named architecture list.', '',
        '| Requested name | Existing candidate(s) | Mapping (not completion) |', '|---|---|---|']
    for r in audit['architecture_mapping']:
        lines.append(f"| `{r['slot']}` | {', '.join('`'+x+'`' for x in r['candidates']) or '—'} | {r['kind'].replace('_',' ')} |")
    matched = {r['slot'] for r in audit['architecture_mapping'] if r['kind']=='exact_name'}
    extras = sorted({r['module'] for r in audit['modules']} - matched)
    lines += ['', 'Additional/alternative current names: '+', '.join('`'+s+'`' for s in extras)+'.', '',
        '**Separate obligations, not falsely added to the 48-module denominator:** own Meet is a required Lens/service-process capability (LN-20); placement in a dedicated `:feature:meet` module remains an architectural decision, and the service is unimplemented. The GPL receiver must be a separate APK and is absent. Play/pro are flavors of one `:app`, not two completed modules. Required process isolation is also not satisfied merely by creating a similarly named folder.', '',
        'Sources: `settings.gradle.kts`, per-module source review, `docs/feature-register.json`, `docs/verification/r11b-jvm-contracts.txt` and `docs/R11B-ENGINEERING.md`. Reproduce with `python3 tools/module_audit.py --check`. Review data lives in `docs/module-audit.json`; this report is generated.', '']
    return '\n'.join(lines)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    audit = json.loads((ROOT/'docs/module-audit.json').read_text())
    validate(audit, included_modules())
    text = render(audit)
    path = ROOT/'docs/MODULE-COMPLETION.md'
    if args.check: assert path.read_text() == text, 'Stale module report'
    else: path.write_text(text)
    print(json.dumps(counts(audit), sort_keys=True))

if __name__ == '__main__': main()
