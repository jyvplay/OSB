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
    jobs=api(f'repos/jyvplay/OSB/actions/runs/{a.run}/jobs?per_page=100');images={};chunks={};incomplete={}
    for job in jobs['jobs']:
        page=1
        while True:
            values=api(job['check_run_url']+f'/annotations?per_page=100&page={page}')
            for row in values:
                message=row.get('message','')
                m=re.fullmatch(r'R24PNG2 (r24-(?:business|art)-(?:office_blue|studio_graphite)\.png) (\d+)/(\d+) ([0-9a-f]{64}) ([A-Za-z0-9+/=]+)',message)
                if m:
                    name,i,n,digest,payload=m[1],int(m[2]),int(m[3]),m[4],m[5]
                    if not 1<=i<=n<=48 or len(payload)>3700:raise ValueError('Invalid bounded capture chunk')
                    current=chunks.setdefault(name,{'n':n,'digest':digest,'parts':{}})
                    if current['n']!=n or current['digest']!=digest or i in current['parts']:raise ValueError('Duplicate/inconsistent capture chunk')
                    current['parts'][i]=payload
            if len(values)<100:break
            page+=1
    for name,row in chunks.items():
        if len(row['parts'])!=row['n']:
            incomplete[name]={'received':len(row['parts']),'required':row['n']};continue # Never display a missing/guessed PNG; independent complete images may still be reviewed.
        data=base64.b64decode(''.join(row['parts'][i] for i in range(1,row['n']+1)),validate=True)
        if len(data)>128*1024 or hashlib.sha256(data).hexdigest()!=row['digest']:raise ValueError('Capture hash/budget mismatch')
        width,height=dimensions(data)
        path=ROOT/'testing/build/r24/captures'/run['head_sha']/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
        images[name]={'path':str(path),'bytes':len(data),'width':width,'height':height,'sha256':row['digest']}
    result={'run':a.run,'candidate':run['head_sha'],'whole_conclusion':run['conclusion'],'scope':'Actual downsampled native test images, NOT expert AX/physical/whole-product acceptance','images':images,'incomplete_not_displayed':incomplete}
    print(json.dumps(result,indent=2))
    if not images:raise SystemExit('No actual capture mirrored; do not fabricate screenshots')
if __name__=='__main__':main()
