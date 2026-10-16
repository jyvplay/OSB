#!/usr/bin/env python3
"""Collect actual generated rules, or verify packaged ART profile. Never invent missing output."""
import argparse, hashlib, json, zipfile, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def collect():
    candidates=list((ROOT/'benchmark/build/outputs').rglob('*generate-baseline-prof.txt'))
    if len(candidates)!=1: raise ValueError(f'Expected exactly one actual generated profile, got {candidates}')
    source=candidates[0];raw=source.read_bytes();lines=raw.decode('utf-8').splitlines()
    rules=[s for s in lines if s and not s.startswith('#')]
    if not rules or not any('Ldev/fusion/' in s for s in rules):raise ValueError('No generated Fusion rules')
    # Retain measured first-party rules only; never synthesize or claim full-profile speedup.
    selected=[s for s in rules if re.match(r'^[HSP]*Ldev/fusion/',s)]
    if not selected:raise ValueError('No measured first-party owned rules')
    original_sha256=hashlib.sha256(raw).hexdigest()
    raw=('\n'.join(selected)+'\n').encode()
    target=ROOT/'baselineprofile/src/main/baseline-prof.txt';target.write_bytes(raw)
    result={'source':str(source.relative_to(ROOT)),'sha256':hashlib.sha256(raw).hexdigest(),
        'rules':len(selected),'fusion_rules':len(selected),'original_rules':len(rules),'original_sha256':original_sha256,'selection':'Measured first-party owned rules only',
        'scope':'Actual API34 emulator startup/Files journey; not performance improvement or physical acceptance'}
    (ROOT/'profile-generation.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
def packaged():
    apks=list((ROOT/'app/build/outputs/apk/pro/benchmark').glob('*.apk'))
    if len(apks)!=1:raise ValueError('Expected one pro benchmark APK')
    with zipfile.ZipFile(apks[0]) as z:
        profiles={n:z.read(n) for n in ['assets/dexopt/baseline.prof','assets/dexopt/baseline.profm']}
    for name,data in profiles.items():
        if len(data)<=8 or not data.startswith(b'pro\0' if name.endswith('.prof') else b'prm\0'):
            raise ValueError('Invalid/missing ART asset '+name)
    result={'apk_sha256':hashlib.sha256(apks[0].read_bytes()).hexdigest(),
      'profile_source_sha256':hashlib.sha256((ROOT/'baselineprofile/src/main/baseline-prof.txt').read_bytes()).hexdigest(),
      'assets':{k:{'bytes':len(v),'sha256':hashlib.sha256(v).hexdigest(),'version':v[4:8].decode('ascii')} for k,v in profiles.items()},
      'scope':'Nonempty packaged ART assets; this alone does not prove installation, speedup or physical budgets'}
    (ROOT/'profile-packaging.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--packaged',action='store_true');a=p.parse_args()
    packaged() if a.packaged else collect()
