#!/usr/bin/env python3
"""Actual child JVM/ZIP metadata experiment; no Office or anonymity inference."""
import argparse,base64,hashlib,io,json,os,struct,subprocess,zipfile,zlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r20'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--expect',choices=['equal','different'],required=True);p.add_argument('--report',required=True);a=p.parse_args()
for line in (OUT/'result.txt').read_text().splitlines():
 if len(line)>66 and line[64:66]=='  ':assert hashlib.sha256((ROOT/line[66:]).read_bytes()).hexdigest()==line[:64]
frame=struct.pack('>4sqI',b'FTS2',0,0);frame+=struct.pack('>I',zlib.crc32(frame));request='T\t'+base64.b64encode(frame).decode()+'\n'
cp=os.pathsep.join(str(OUT/n) for n in ['checks.jar','LiteralWorkbook.jar','TaskCounts.jar','LinkDisclosure.jar','PermissionLeases.jar'])
rows=[];payloads=[]
for zone in ['UTC','America/New_York','Asia/Tokyo','Pacific/Chatham']:
 r=subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-Duser.timezone='+zone,'-cp',cp,'dev.fusion.testing.R20Oracle'],input=request,text=True,capture_output=True,check=True)
 raw=base64.b64decode(r.stdout.strip());payloads.append(raw)
 with zipfile.ZipFile(io.BytesIO(raw)) as z:entries=[{'name':i.filename,'dos_time':i.date_time,'extra_hex':i.extra.hex()} for i in z.infolist()]
 rows.append({'zone':zone,'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest(),'entries':entries})
equal=all(x==payloads[0] for x in payloads);matched=equal==(a.expect=='equal')
report={'expected':a.expect,'expectation_met':matched,'equal_across_four_timezones':equal,'rows':rows,
 'scope':'Real JVMs with explicit user.timezone and independent Python ZIP inspection; one empty task count report. Not all runtimes/timezones or anonymous sharing.',
 'sha256':{str(x.relative_to(ROOT)):hashlib.sha256(x.read_bytes()).hexdigest() for x in [Path(__file__),ROOT/'format/ooxml/src/main/kotlin/dev/fusion/ooxml/LiteralWorkbook.kt',OUT/'result.txt']}}
(ROOT/'docs/verification'/a.report).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report));assert matched
