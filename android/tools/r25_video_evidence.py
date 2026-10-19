#!/usr/bin/env python3
"""Check actual native decoded Surface PNGs with an independent decoder. Not service streaming or hardware fidelity."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
from PIL import Image

FIXTURE='970274fa5f8f5f653bc6e2a57f3d3731d5c228edbae7102fb3dad799e8ba5c4d'

def check(root):
    reports=list(Path(root).rglob('r25-video-evidence.json'))
    if len(reports)!=1:raise ValueError('Exactly one actual native video observation required')
    if reports[0].stat().st_size>16384:raise ValueError('Observation byte budget')
    report=json.loads(reports[0].read_text())
    if report.get('origin')!='android-instrumentation' or report.get('api')!=34 or report.get('input_sha256')!=FIXTURE:raise ValueError('Wrong actual native input/runtime')
    samples=report['samples']
    if [r['color'] for r in samples]!=['red','green','blue']:raise ValueError('Actual ordered frames missing')
    positions=[r['position_ms'] for r in samples]
    if not all(type(x) is int for x in positions) or not 0<positions[0]<positions[1]<positions[2]<=6100 or positions[1]<1500 or positions[2]<3500:raise ValueError('Actual player progress does not align with frame phases')
    files={}
    for row in samples:
        name='r25-decoded-'+row['color']+'.png';paths=list(Path(root).rglob(name))
        if len(paths)!=1 or not 1<=paths[0].stat().st_size<=4096:raise ValueError('Actual decoded frame missing/ambiguous/oversized')
        data=paths[0].read_bytes()
        with Image.open(paths[0]) as image:
            if image.size!=(64,48):raise ValueError('Actual Surface dimensions wrong')
            rgb=image.convert('RGB');pixels=[rgb.getpixel((x,y)) for y in range(12,36) for x in range(16,48)]
            mean=[sum(p[i] for p in pixels)//len(pixels) for i in range(3)]
        if mean!=row['mean_rgb']:raise ValueError('Native measurement differs from independent PNG decode')
        r,g,b=mean;color=row['color']
        if not ((color=='red' and r>180 and g<80 and b<80) or (color=='green' and g>80 and r<60 and b<60) or (color=='blue' and b>180 and r<80 and g<80)):raise ValueError('No required actual native decoded colour')
        files[name]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'base64':base64.b64encode(data).decode()}
    return {'candidate':os.environ.get('GITHUB_SHA'),'input_sha256':FIXTURE,'api':34,'samples':samples,'files':files,
            'scope':'Actual native MediaPlayer Surface pixels plus real progress/independent PNG decode. Controlled silent AVC fixture only; NOT audible quality, generic codecs, hardware decoder, YouTube/Spotify/SoundCloud or Studio GUI acceptance.'}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path('app/build/outputs/managed_device_android_test_additional_output'));a=p.parse_args()
    result=check(a.root);Path('r25-video-result.json').write_text(json.dumps(result,indent=2)+'\n')
    summary={k:v for k,v in result.items() if k!='files'};summary['files']={n:{k:v for k,v in f.items() if k!='base64'} for n,f in result['files'].items()}
    print('::notice::R25VIDEO1 '+json.dumps(summary,separators=(',',':')))
    for name,record in result['files'].items():
        message='R25VIDEOPNG1 '+name+' '+record['sha256']+' '+record['base64']
        if len(message)>3900:raise ValueError('Actual Surface preview annotation too large; retain artifact, do not truncate')
        print('::notice::'+message)
