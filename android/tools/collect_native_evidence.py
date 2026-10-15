#!/usr/bin/env python3
"""Collect completed GitHub native run receipts; never infer passed cases from build success."""
import argparse, json, re, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def parse_cases(annotations):
    cases=[];seen=set();no_execution=False
    for a in annotations:
        message=a.get('message','')
        if not message.startswith('Android test results: '):continue
        if message=='Android test results: No executed device test cases found. No pass inferred.':
            no_execution=True;continue
        for item in message.removeprefix('Android test results: ').split('; '):
            m=re.fullmatch(r'(dev\.fusion\.[A-Za-z0-9_.$]+)=(PASS|FAIL|SKIP)',item)
            if not m or m[1] in seen:raise ValueError('Malformed/duplicate native case: '+item)
            seen.add(m[1]);cases.append({'case':m[1],'result':m[2]})
    if no_execution and cases:raise ValueError('Conflicting execution/no-execution annotations')
    return cases

def api(path):return json.loads(subprocess.check_output(['gh','api',path],text=True))
def collect(run_id,prefix):
    base='repos/jyvplay/OSB';run=api(f'{base}/actions/runs/{run_id}')
    if run['status']!='completed':raise ValueError('Run not complete; cannot publish final evidence')
    jobs=api(f'{base}/actions/runs/{run_id}/jobs?per_page=100')
    assert len(jobs['jobs'])==jobs['total_count']
    assert all(j['head_sha']==run['head_sha'] for j in jobs['jobs'])
    device=[j for j in jobs['jobs'] if j['name'].startswith('API34 managed-device')]
    if len(device)!=1:raise ValueError('Exactly one actual managed-device job required')
    annotations=[];page=1
    while True:
        batch=api(device[0]['check_run_url']+f'/annotations?per_page=100&page={page}')
        annotations+=batch
        if len(batch)<100:break
        page+=1
    cases=parse_cases(annotations)
    counts={s:sum(c['result']==s for c in cases) for s in ['PASS','FAIL','SKIP']}
    result={'run':run_id,'candidate':run['head_sha'],'url':run['html_url'],'conclusion':run['conclusion'],
            'device_conclusion':device[0]['conclusion'],'counts':counts,'cases':cases,
            'scope':'Actual retained API34 named outcomes, not physical/full-product/AX/legal acceptance'}
    artifacts=api(f'{base}/actions/runs/{run_id}/artifacts?per_page=100')
    for name,value in [('run',run),('jobs',jobs),('annotations',annotations),('artifacts',artifacts),('result',result)]:
        (ROOT/f'docs/verification/{prefix}-{name}.json').write_text(json.dumps(value,indent=2)+'\n')
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('run',type=int);p.add_argument('prefix');a=p.parse_args()
    if not re.fullmatch(r'[a-z0-9-]+',a.prefix):raise ValueError('Invalid evidence prefix')
    r=collect(a.run,a.prefix);print(json.dumps({k:v for k,v in r.items() if k!='cases'}))
if __name__=='__main__':main()
