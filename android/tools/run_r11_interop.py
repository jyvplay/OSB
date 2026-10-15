#!/usr/bin/env python3
"""Independent Python checks against real R11 Kotlin kernels. Run run_jvm_contracts.py first."""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import random
import re
import struct
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--report',default='docs/verification/r11-independent-checks.json')
report_path=root/parser.parse_args().report
report_path.unlink(missing_ok=True) # Never leave stale PASS evidence after a new failure.
built=root/'testing/build/contracts'
source=root/'testing/src/interop/kotlin/dev/fusion/testing/R11Bridge.kt'
jar=built/'r11-bridge.jar'
classpath=os.pathsep.join(str(built/name) for name in ('feature_browser.jar','feature_notes.jar'))
subprocess.run([os.environ.get('KOTLINC','kotlinc'),'-jvm-target','17','-include-runtime','-classpath',classpath,str(source),'-d',str(jar)],check=True)
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
command=[java,'-cp',str(jar)+os.pathsep+classpath,'dev.fusion.testing.R11Bridge']
checks=0
def expect(condition):
    global checks
    checks+=1
    if not condition: raise AssertionError(f'Independent check {checks} failed')

rng=random.Random(1107)
texts=[];expected=[]
# Deliberately a narrower, independently regex-specified language: flat ASCII wiki references.
# Does not establish full code-span/fence/CommonMark conformance.
for i in range(300):
    tokens=['prose🙂']
    for _ in range(rng.randrange(0,20)):
        name=rng.choice(['Note_A','Note_B','a'*64,'x'*65,'bad/path',''])
        tokens.append(rng.choice(['[[',r'\[['])+name+']]')
    text=' '.join(tokens);texts.append(text)
    expected.append(','.join(sorted(set(re.findall(r'(?<!\\)\[\[([A-Za-z0-9_-]{1,64})\]\]',text)))))
wire=''.join(hashlib.sha256(text.encode()).hexdigest()+'\t'+base64.b64encode(text.encode()).decode()+'\n' for text in texts)
response=subprocess.run(command+['notes'],input=wire,text=True,capture_output=True,check=True)
rows=response.stdout.split('\n')[:-1]
expect(len(rows)==len(expected))
for actual,wanted in zip(rows,expected): expect(actual==wanted)

with tempfile.TemporaryDirectory() as directory:
    path=Path(directory)/'session.bin'
    subprocess.run(command+['browser',str(path)],check=True)
    body=path.read_bytes();size=struct.unpack('>i',body[:4])[0]
    expect(size==len(body)-36)
    payload=body[4:4+size];expect(hashlib.sha256(payload).digest()==body[-32:])
    data=io.BytesIO(payload)
    def num(fmt): return struct.unpack('>'+fmt,data.read(struct.calcsize('>'+fmt)))[0]
    def string():
        raw=data.read(num('H')).replace(b'\xc0\x80',b'\0')
        return raw.decode('utf-8','surrogatepass').encode('utf-16','surrogatepass').decode('utf-16')
    def optional(): return num('q') if num('B') else None
    expect(num('i')==0x46425253);expect(num('i')==1);expect(string()=='Session🙂');expect(num('q')==5)
    expect(num('i')==1);expect(num('q')==1);expect(optional()==3);expect(num('B')==1)
    expect(num('q')==2);expect(string()=='VERTICAL');expect(num('i')==350)
    expect(num('i')==2)
    tabs=[]
    for _ in range(2): tabs.append([num('q'),num('q'),string(),string(),num('B'),optional(),num('B')])
    expect(tabs==[[2,1,'https://example.com/a%2Fb?q=%2F','Title\0🙂',0,4,0],[3,1,'http://other.example/','',0,4,0]])
    expect(num('i')==1);expect(num('q')==4);expect(num('q')==1);expect(string()=='Group');expect(string()=='GREEN');expect(num('B')==0);expect(data.read()==b'')

report={'status':'PASS','checks':checks,'notes_cases':300,'seed':1107,
    'scope':'Independent regex oracle for flat ASCII wiki links and Python SHA-256 revisions; independent struct/hashlib/modified-UTF decoder of one real Kotlin browser-session file including NUL and supplementary Unicode.',
    'not_proved':'Full wiki/CommonMark grammar, native UI/renderer, disk adapter, isolation, performance, security and whole-product acceptance.',
    'source_sha256':{str(path.relative_to(root)):hashlib.sha256(path.read_bytes()).hexdigest() for path in [source,Path(__file__),*sorted((root/'feature/browser/src/main/kotlin').rglob('*.kt')),root/'feature/notes/src/main/kotlin/dev/fusion/notes/NoteLinks.kt',root/'feature/notes/src/main/kotlin/dev/fusion/notes/NoteFiles.kt']}}
report_path.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
