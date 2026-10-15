#!/usr/bin/env python3
"""Verify and collect completed profile-experiment receipt; success is never inferred from source."""
import argparse,base64,gzip,hashlib,io,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def api(path):return json.loads(subprocess.check_output(['gh','api',path],text=True))
def decode(annotations):
    parts={};digest=None;total=None
    for a in annotations:
        msg=a.get('message','')
        if not msg.startswith('R16BUNDLE '):continue
        m=re.fullmatch(r'R16BUNDLE (\d+)/(\d+) ([0-9a-f]{64}) ([A-Za-z0-9+/=]+)',msg)
        if not m:raise ValueError('Malformed receipt')
        i,n=int(m[1]),int(m[2])
        if not 1<=i<=n<=9 or i in parts:raise ValueError('Invalid/duplicate chunk')
        if total is not None and (total!=n or digest!=m[3]):raise ValueError('Inconsistent chunk identity')
        total,digest=n,m[3];parts[i]=m[4]
    if total is None:raise ValueError('No receipt; do not infer execution')
    if len(parts)!=total:raise ValueError('Missing chunk')
    packed=base64.b64decode(''.join(parts[i] for i in range(1,total+1)),validate=True)
    with gzip.GzipFile(fileobj=io.BytesIO(packed)) as f:raw=f.read(4*1024*1024+1)
    if len(raw)>4*1024*1024 or hashlib.sha256(raw).hexdigest()!=digest:raise ValueError('Receipt limit/hash mismatch')
    data=json.loads(raw)
    if 'profile' in data:
        if hashlib.sha256(data['profile'].encode()).hexdigest()!=data['profile-generation.json']['sha256']:raise ValueError('Profile hash mismatch')
    return data

def collect(runid):
    prefix=ROOT/f'docs/verification/r16-measured-{runid}'
    run=api(f'repos/jyvplay/OSB/actions/runs/{runid}')
    if run['status']!='completed':raise ValueError('Run incomplete')
    jobs=api(f'repos/jyvplay/OSB/actions/runs/{runid}/jobs?per_page=100')
    matches=[j for j in jobs['jobs'] if j['name'].startswith('API34 actual profile')]
    if len(matches)!=1:raise ValueError('Missing/ambiguous measurement job')
    job=matches[0];annotations=[];page=1
    while True:
        batch=api(job['check_run_url']+f'/annotations?per_page=100&page={page}');annotations+=batch
        if len(batch)<100:break
        page+=1
    for name,value in [('run',run),('jobs',jobs),('annotations',annotations)]:
        Path(str(prefix)+'-'+name+'.json').write_text(json.dumps(value,indent=2)+'\n')
    data=decode(annotations)
    if data['candidate']!=run['head_sha'] or data['run']!=str(runid):raise ValueError('Wrong run/revision')
    Path(str(prefix)+'-receipt.json').write_text(json.dumps(data,indent=2)+'\n')
    print(json.dumps({'run':runid,'candidate':run['head_sha'],'conclusion':run['conclusion'],
        'generation_cases':data['generation_cases'],'measurement_cases':data['measurement_cases'],
        'profile_rules':data.get('profile-generation.json',{}).get('rules'),'metrics_files':len(data['metrics'])},indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('run');a=p.parse_args();collect(a.run)
