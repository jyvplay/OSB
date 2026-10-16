#!/usr/bin/env python3
"""Actual public JVM versioned codec/transitions/projection vs independent Python reference."""
import base64,datetime,hashlib,json,os,random,struct,subprocess,uuid,zlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r18';rng=random.Random(1801)
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
cp=os.pathsep.join(str(OUT/n) for n in ['checks.jar','TaskBook.jar','OwnedSheetRequest.jar']);epoch=datetime.date(1970,1,1)
def wire(rev,tasks,version=2):
    raw=struct.pack('>4sqI',b'FTS1' if version==1 else b'FTS2',rev,len(tasks))
    for t in tasks:
        title=t['title'].encode();day=(t['due']-epoch).days if t['due'] else -(2**63)
        raw+=t['id'].encode()+struct.pack('>BH',int(t['done'])+2*int(t['archived']),len(title))+title+struct.pack('>q',day)
    return base64.b64encode(raw+struct.pack('>I',zlib.crc32(raw))).decode()
requests=[];expected=[]
for i in range(100):
    rev=i;tasks=[]
    for j in range(12):
        tasks.append(dict(id=str(uuid.UUID(int=rng.getrandbits(128))),title=f'{"ALPHA" if j%3==0 else "Beta"} {j} 界🙂',due=None if j%4==0 else datetime.date(2028,2,20+j%10),done=bool(j%2),archived=bool(j%3==1)))
    version=1 if i%2 else 2
    if version==1:
        tasks=[t|{'archived':False} for t in tasks]
    frame=wire(rev,tasks,version)
    requests.append('C\t'+frame);expected.append(wire(rev,tasks))
    for scope in ['ACTIVE','OPEN','COMPLETED','ARCHIVED']:
        for order in ['INSERTION','DUE']:
            query='alpha' if i%3 else '界'
            selected=[t for t in tasks if query in t['title'].lower() and (t['archived'] if scope=='ARCHIVED' else not t['archived'] and (scope=='ACTIVE' or (t['done'] if scope=='COMPLETED' else not t['done'])))]
            if order=='DUE':selected.sort(key=lambda t:(t['due'] is None,t['due'] or datetime.date.max))
            requests.append('\t'.join(['Q',frame,base64.b64encode(query.encode()).decode(),scope,order]));expected.append(','.join(t['id'] for t in selected))
    target=tasks[0];changed=[t|{'title':'Edited 界','due':None} if t['id']==target['id'] else t for t in tasks]
    requests.append('\t'.join(['E',frame,target['id'],base64.b64encode('Edited 界'.encode()).decode(),'']));expected.append(wire(rev+1,changed))
    archived=[t|{'archived':True} if t['id']==target['id'] else t for t in tasks]
    requests.append('\t'.join(['A',frame,target['id'],'1']));expected.append(wire(rev+1,archived))
r=subprocess.run([java,'-cp',cp,'dev.fusion.testing.R18Oracle'],input='\n'.join(requests)+'\n',capture_output=True,text=True,check=True)
actual=r.stdout.splitlines();assert len(actual)==len(expected),(len(actual),len(expected))
for i,(a,b) in enumerate(zip(actual,expected)):assert a==b,(i,a,b)
receipt={'result':'PASS','cases':len(expected),'breakdown':{'legacy_or_current_decode_and_FTS2_encode':100,'scope_query_order':800,'edit':100,'archive':100},
 'sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),OUT/'checks.jar',OUT/'TaskBook.jar']},
 'scope':'Same-author corpus using independent Python struct/zlib/date/sort reference and separate JVM producer; not independent expert review or Android/AX/reminder acceptance'}
(ROOT/'docs/verification/r18-interop.json').write_text(json.dumps(receipt,indent=2)+'\n');print(f'R18: {len(expected)} independent Python/JVM cases PASS')
