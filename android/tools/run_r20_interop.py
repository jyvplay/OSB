#!/usr/bin/env python3
"""Actual Java producers, independent Python references and openpyxl; authored corpus, not a blind holdout."""
import base64,datetime,hashlib,io,json,os,random,struct,subprocess,uuid,zipfile,zlib
from pathlib import Path
from urllib.parse import urlsplit
import openpyxl
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r20';rng=random.Random(20201015)
for line in (OUT/'result.txt').read_text().splitlines():
 if len(line)>66 and line[64:66]=='  ':assert hashlib.sha256((ROOT/line[66:]).read_bytes()).hexdigest()==line[:64]
requests=[];expected=[]
for case in range(160):
 n=rng.randrange(129);flags=[rng.randrange(4) for _ in range(n)];counts=[flags.count(x) for x in range(4)]
 for variant in range(2):
  raw=struct.pack('>4sqI',b'FTS2',case+variant,len(flags))
  for j,f in enumerate(flags if variant==0 else list(reversed(flags))):
   title=(f'Sensitive source {case}/{j} é🙂' if variant==0 else '_x0041_\uffff omitted').encode()
   raw+=str(uuid.UUID(int=(case+1)*10000+variant*1000+j)).encode()+struct.pack('>BH',f,len(title))+title+struct.pack('>q',-(2**63) if variant else 1)
  raw+=struct.pack('>I',zlib.crc32(raw));requests.append('T\t'+base64.b64encode(raw).decode());expected.append(('T',counts,variant))
for i in range(140):
 scheme=rng.choice(['http','https']);port=rng.choice([-1,80,443,444]);path=f'/path%2Fretained/{i}'
 authority='EXAMPLE.com'+('' if port==-1 else ':'+str(port));query='?name=private&x='+str(i) if i%2 else '';fragment='#secret' if i%3 else ''
 raw=f'{scheme}://{authority}{path}{query}{fragment}';u=urlsplit(raw)
 suffix='' if port==-1 or (scheme,port) in [('https',443),('http',80)] else ':'+str(port)
 output=f'{scheme}://example.com{suffix}{path}'
 requests.append('L\t'+base64.b64encode(raw.encode()).decode());expected.append(('L',output,bool(query),bool(fragment)))
for i in range(300):
 p='p';tab=1;origin='https://example.com/a';kind='CAMERA';now=rng.randrange(100);ttl=rng.choice([1,2,60000]);at=now+rng.choice([-1,0,1,ttl-1,ttl,ttl+1])
 otherp=rng.choice(['p','q']);othertab=rng.choice([1,2]);otherorigin=rng.choice(['https://example.com:443/b','https://other.example/a','http://example.com/a']);otherkind=rng.choice(['CAMERA','MICROPHONE']);revoke=rng.choice(['none','none','tab','profile'])
 good=at>=now and at<now+ttl and otherp==p and othertab==tab and otherorigin=='https://example.com:443/b' and otherkind==kind and revoke=='none'
 requests.append('\t'.join(map(str,['P',p,tab,origin,kind,now,ttl,otherp,othertab,otherorigin,otherkind,at,revoke])));expected.append(('P',good))
cp=os.pathsep.join(str(OUT/f) for f in ['checks.jar','LiteralWorkbook.jar','TaskCounts.jar','LinkDisclosure.jar','PermissionLeases.jar'])
r=subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-cp',cp,'dev.fusion.testing.R20Oracle'],input='\n'.join(requests)+'\n',text=True,capture_output=True,check=True)
lines=r.stdout.splitlines();assert len(lines)==len(expected);previous=None;sizes=[]
for line,e in zip(lines,expected):
 if e[0]=='T':
  raw=base64.b64decode(line);sizes.append(len(raw));book=openpyxl.load_workbook(io.BytesIO(raw));assert book.sheetnames==['Counts'];cells=list(book.active.values);c=e[1]
  assert cells==[('State','Open','Completed'),('Active',c[0],c[1]),('Archived',c[2],c[3])],cells
  with zipfile.ZipFile(io.BytesIO(raw)) as z:
   assert not any('docProps' in n or 'externalLink' in n for n in z.namelist())
   text=b''.join(z.read(n) for n in z.namelist());assert b'Sensitive' not in text and b'uuid' not in text.lower()
  if e[2]:assert previous==raw,'Omitted source data influenced report bytes'
  previous=raw
 elif e[0]=='L':
  p=line.split('\t');assert base64.b64decode(p[0]).decode()==e[1] and p[1:]==[str(e[2]).lower(),str(e[3]).lower()]
 else:assert line==str(e[1]).lower()+',false',(line,e)
report={'result':'PASS','cases':len(expected),'summary_readbacks':320,'link_references':140,'lease_references':300,'summary_bytes_min':min(sizes),'summary_bytes_max':max(sizes),
 'scope':'Actual separate Java libraries/public consumer plus Python references and openpyxl 3.1.5. Authored postimplementation corpus, not independent authorship or blind holdout. Equal count vectors give identical payloads in this runtime; no anonymity or all-Office claim.',
 'requests_sha256':hashlib.sha256(('\n'.join(requests)+'\n').encode()).hexdigest(),'outputs_sha256':hashlib.sha256(r.stdout.encode()).hexdigest(),
 'sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),OUT/'result.txt']}}
(ROOT/'docs/verification/r20-interop.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
