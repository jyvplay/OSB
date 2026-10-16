#!/usr/bin/env python3
"""Independent Python struct/CRC/Unicode/calendar reference vs actual public JVM codec."""
import base64,datetime,hashlib,json,os,random,struct,subprocess,uuid,zlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r17';rng=random.Random(1701)
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
cp=os.pathsep.join(str(OUT/n) for n in ['checks.jar','TaskBook.jar'])
requests=[];expected=[];epoch=datetime.date(1970,1,1)
for i in range(100):
    title=f'Task {i} 界🙂';uid=str(uuid.UUID(int=rng.getrandbits(128)));done=i%2
    date=None if i%3==0 else datetime.date(rng.randrange(1,10000),rng.randrange(1,13),rng.randrange(1,29))
    revision=rng.randrange(2**63);utf=title.encode();day=(date-epoch).days if date else -(2**63)
    raw=struct.pack('>4sqI',b'FTS2',revision,1)+uid.encode()+struct.pack('>BH',done,len(utf))+utf+struct.pack('>q',day)
    frame=raw+struct.pack('>I',zlib.crc32(raw));encoded=base64.b64encode(frame).decode();btitle=base64.b64encode(utf).decode();d=date.isoformat() if date else ''
    requests += [f'E\t{uid}\t{btitle}\t{d}\t{done}\t{revision}',f'D\t{encoded}']
    expected += [encoded,f'{revision}\t{uid}\t{btitle}\t{d}\t{done}']
r=subprocess.run([java,'-cp',cp,'dev.fusion.testing.R17Oracle'],input='\n'.join(requests)+'\n',capture_output=True,text=True,check=True)
actual=r.stdout.splitlines();assert actual==expected,'Wire/date disagreement'
result={'result':'PASS','cases':len(expected),'direction':'100 JVM encoded frames match Python bytes;100 Python frames read by JVM',
 'sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),OUT/'TaskBook.jar',OUT/'checks.jar']},
 'scope':'Bounded plaintext task framing/date semantics; no authentication, Android persistence or reminder acceptance'}
(ROOT/'docs/verification/r17-interop.json').write_text(json.dumps(result,indent=2)+'\n');print('R17: 200 independent Python/JVM wire/date cases PASS')
