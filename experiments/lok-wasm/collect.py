#!/usr/bin/env python3
"""Collect actual completed engine experiment receipts and hash-checked pixels; never manufacture absent outputs."""
import base64,hashlib,json,re,struct,subprocess,sys
from pathlib import Path

def api(path):return json.loads(subprocess.check_output(['gh','api',path],text=True))
run=api(f'repos/jyvplay/OSB/actions/runs/{int(sys.argv[1])}')
if run['status']!='completed':raise SystemExit('No final evidence from an incomplete experiment')
root=Path(sys.argv[2]);root.mkdir(parents=True,exist_ok=True)
jobs=api(f"repos/jyvplay/OSB/actions/runs/{run['id']}/jobs?per_page=100")
rows=[];parts={};report=None
for job in jobs['jobs']:
    rows+=api(job['check_run_url']+'/annotations?per_page=100')
for row in rows:
    m=row['message']
    if m.startswith('R25LOKWASM1 '):
        if report is not None:raise ValueError('Duplicate verified report')
        report=json.loads(m.split(' ',1)[1])
        if report['source_candidate']!=run['head_sha']:raise ValueError('Source mismatch')
    match=re.fullmatch(r'R25LOKPNG1 (tile\.png|detail\.png) (\d+)/(\d+) ([0-9a-f]{64}) ([A-Za-z0-9+/=]+)',m)
    if match:
        name,i,n,digest,payload=match[1],int(match[2]),int(match[3]),match[4],match[5]
        if not 1<=i<=n<=12 or len(payload)>3700:raise ValueError('Invalid actual PNG fragment')
        value=parts.setdefault(name,{'count':n,'sha':digest,'parts':{}})
        if value['count']!=n or value['sha']!=digest or i in value['parts']:raise ValueError('Ambiguous actual PNG')
        value['parts'][i]=payload
for name,value in [('run',run),('jobs',jobs),('annotations',rows)]:
    (root/(name+'.json')).write_text(json.dumps(value,indent=2)+'\n')
if report is None or run['conclusion']!='success':raise SystemExit('No successful independent engine result; raw failure retained')
if set(parts)!={'tile.png','detail.png'}:raise ValueError('Missing actual engine images')
for name,value in parts.items():
    if len(value['parts'])!=value['count']:raise ValueError('Incomplete actual image')
    data=base64.b64decode(''.join(value['parts'][i] for i in range(1,value['count']+1)),validate=True)
    if len(data)>32*1024 or hashlib.sha256(data).hexdigest()!=value['sha'] or data[:8]!=b'\x89PNG\r\n\x1a\n' or struct.unpack_from('>II',data,16)!=(256,320):raise ValueError('Actual image hash/shape differs')
    if report['outputs'][name]['sha256']!=value['sha']:raise ValueError('Image/independent report mismatch')
    (root/name).write_bytes(data)
(root/'verified.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'candidate':run['head_sha'],'run':run['id'],'conclusion':run['conclusion'],'actual_engine_images':sorted(parts),'scope':report['accepted_scope']},indent=2))
