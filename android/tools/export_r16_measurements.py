#!/usr/bin/env python3
"""Export actual CI measurement artifacts via bounded Checks annotations (artifact host is unreachable locally)."""
import base64,gzip,hashlib,json,os,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def collect_cases(directory):
    cases={}
    for p in sorted(directory.rglob('*.xml')):
        for case in ET.parse(p).iter('testcase'):
            name=case.get('classname','')+'.'+case.get('name','')
            status='FAIL' if case.find('failure') is not None or case.find('error') is not None else 'SKIP' if case.find('skipped') is not None else 'PASS'
            value={'result':status,'seconds':case.get('time'),'source':str(p.relative_to(ROOT))}
            if name in cases and cases[name]['result']!=status:raise ValueError('Conflicting device result '+name)
            cases[name]=value
    return cases

def collect():
    data={'run':os.environ['GITHUB_RUN_ID'],'candidate':os.environ['GITHUB_SHA'],
       'generation_cases':collect_cases(ROOT/'profile-generation-results'),
       'measurement_cases':collect_cases(ROOT/'benchmark/build/outputs/androidTest-results'),
       'metrics':[],'sources':{}}
    for name in ['profile-generation.json','profile-packaging.json']:
        if (ROOT/name).exists():data[name]=json.loads((ROOT/name).read_text())
    if 'profile-generation.json' in data:
        raw=(ROOT/'baselineprofile/src/main/baseline-prof.txt').read_bytes()
        assert hashlib.sha256(raw).hexdigest()==data['profile-generation.json']['sha256']
        data['profile']=raw.decode('utf-8')
    seen=set()
    for p in sorted((ROOT/'benchmark/build/outputs').rglob('*benchmarkData.json')):
        raw=p.read_bytes();digest=hashlib.sha256(raw).hexdigest()
        if digest not in seen:data['metrics'].append({'source':str(p.relative_to(ROOT)),'sha256':digest,'data':json.loads(raw)})
        seen.add(digest)
    for p in sorted((ROOT/'benchmark/src/main').rglob('*')):
        if p.is_file():data['sources'][str(p.relative_to(ROOT))]=hashlib.sha256(p.read_bytes()).hexdigest()
    return data

def emit(data):
    raw=json.dumps(data,sort_keys=True,separators=(',',':')).encode()
    if len(raw)>4*1024*1024:raise ValueError('Measurement receipt exceeds export cap')
    packed=base64.b64encode(gzip.compress(raw,mtime=0)).decode();chunks=[packed[i:i+3800] for i in range(0,len(packed),3800)]
    if len(chunks)>40:raise ValueError('Too many annotation chunks; use retained artifact instead')
    digest=hashlib.sha256(raw).hexdigest()
    for i,c in enumerate(chunks):print(f'::notice title=R16 measured artifacts::R16BUNDLE {i+1}/{len(chunks)} {digest} {c}')
    (ROOT/'profile-export.json').write_bytes(raw)
if __name__=='__main__':emit(collect())
