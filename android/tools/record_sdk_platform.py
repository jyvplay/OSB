#!/usr/bin/env python3
"""Inspect an ACTUALLY installed official SDK37.0 package. Not a build, runtime or provenance attestation."""
import argparse
import hashlib
import json
import os
from pathlib import Path


def base_release37(levels):
    return levels in (['37'], ['37.0']) # Same released base, NOT higher minor/beta/370/prefix matching.


def diagnostics(sdk):
    root=Path(sdk)/'platforms'
    if not root.is_dir(): return {'platforms':'ABSENT'}
    rows=[]
    for path in sorted(root.iterdir())[:128]:
        if not path.name.startswith('android-37'): continue
        row={'directory':path.name}
        try:
            source=path/'source.properties'
            if source.stat().st_size<=16384:
                row['properties']=source.read_text(encoding='utf-8')
            row['jar_bytes']=(path/'android.jar').stat().st_size
        except (OSError,ValueError) as error: row['error']=str(error)[:500]
        rows.append(row)
    return {'actual37_candidates':rows}


def record(sdk):
    platform=Path(sdk)/'platforms/android-37.0'
    source=platform/'source.properties';jar=platform/'android.jar'
    if source.stat().st_size>16384: raise ValueError('SDK property budget exceeded')
    text=source.read_text(encoding='utf-8')
    levels=[line.partition('=')[2].strip() for line in text.splitlines() if line.partition('=')[0].strip()=='AndroidVersion.ApiLevel']
    if not base_release37(levels): raise ValueError('Actual SDK metadata does not uniquely declare API37')
    size=jar.stat().st_size
    if not 0<size<=128*1024*1024: raise ValueError('Actual SDK jar size outside probe budget')
    digest=hashlib.sha256();consumed=0
    with jar.open('rb') as stream:
        while chunk:=stream.read(1024*1024):
            consumed+=len(chunk)
            if consumed>128*1024*1024: raise ValueError('Growing SDK jar exceeded byte budget')
            digest.update(chunk)
    if consumed!=size: raise ValueError('SDK jar changed during inspection')
    return {'scope':'Actual installed SDK37.0 compilation package ONLY; not runtime37/Rabbit GUI/build/upstream authenticity',
            'platform':str(platform),'source_properties':text,'jar_bytes':size,'jar_sha256':digest.hexdigest()}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdk',type=Path,default=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '/SDK-NOT-CONFIGURED'))
    p.add_argument('--report',type=Path,default=Path('testing/build/r23/installed-sdk37.json'))
    a=p.parse_args()
    try: result=record(a.sdk)
    except (OSError,ValueError) as error:
        message=json.dumps({'error':str(error),'observed':diagnostics(a.sdk)})
        print('::error::SDK37.0 inspection REFUSED: '+message.replace('%','%25'))
        raise SystemExit('Actual SDK37.0 inspection REFUSED: '+str(error))
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,indent=2)+'\n')
    print('::notice::Installed SDK37.0 compilation platform (NOT runtime37): '+json.dumps(result))


if __name__=='__main__':main()
