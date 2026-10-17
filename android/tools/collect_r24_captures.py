#!/usr/bin/env python3
"""Collect only actual completed-run PNG annotations; a capture is not full UI/AX acceptance."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
from png_capture import dimensions
ROOT=Path(__file__).resolve().parents[1]

def api(url):return json.loads(subprocess.check_output(['gh','api',url],text=True))
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('run',type=int);a=p.parse_args()
    run=api(f'repos/jyvplay/OSB/actions/runs/{a.run}')
    if run['status']!='completed':raise SystemExit('Run is not complete; no final capture inference')
    jobs=api(f'repos/jyvplay/OSB/actions/runs/{a.run}/jobs?per_page=100');images={}
    for job in jobs['jobs']:
        page=1
        while True:
            values=api(job['check_run_url']+f'/annotations?per_page=100&page={page}')
            for row in values:
                m=re.fullmatch(r'R24PNG (r24-(?:business|art)-(?:office_blue|studio_graphite)\.png) ([0-9a-f]{64}) ([A-Za-z0-9+/=]+)',row.get('message',''))
                if not m:continue
                if m[1] in images or len(m[3])>44000:raise ValueError('Duplicate/oversize capture')
                data=base64.b64decode(m[3],validate=True)
                if len(data)>32768 or hashlib.sha256(data).hexdigest()!=m[2]:raise ValueError('Capture hash/budget mismatch')
                width,height=dimensions(data)
                path=ROOT/'testing/build/r24/captures'/run['head_sha']/m[1];path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
                images[m[1]]={'path':str(path),'bytes':len(data),'width':width,'height':height,'sha256':m[2]}
            if len(values)<100:break
            page+=1
    result={'run':a.run,'candidate':run['head_sha'],'whole_conclusion':run['conclusion'],'scope':'Actual downsampled native test images, NOT expert AX/physical/whole-product acceptance','images':images}
    print(json.dumps(result,indent=2))
    if not images:raise SystemExit('No actual capture mirrored; do not fabricate screenshots')
if __name__=='__main__':main()
