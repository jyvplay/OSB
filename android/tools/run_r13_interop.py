#!/usr/bin/env python3
"""Actual Kotlin bytes consumed by separately maintained openpyxl/icalendar; not LibreOffice testing."""
import base64,hashlib,io,json,os,random,shlex,subprocess,zipfile,xml.etree.ElementTree as ET
from decimal import Decimal
from pathlib import Path
import openpyxl,icalendar
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r13';report=ROOT/'docs/verification/r13-interop.json';report.unlink(missing_ok=True)
receipt=(OUT/'result.txt').read_text()
for line in receipt.splitlines():
 if len(line)>66 and line[64:66]=='  ':assert hashlib.sha256((ROOT/line[66:]).read_bytes()).hexdigest()==line[:64],line
r=random.Random(130014);b64=lambda b:base64.b64encode(b).decode();requests=[];expected=[]
def add(op,fields,value):requests.append('\t'.join([op]+fields));expected.append((op,value))
for i in range(100):
 b=r.randbytes(r.randrange(16385));add('D',[b64(b)],f'{len(b)}:{hashlib.sha256(b).hexdigest()}')
 words=[''.join(r.choice(['a','α','🙂',' ','$',';',"'",'\\','"']) for _ in range(r.randrange(30))) for _ in range(r.randrange(1,10))]
 command=' '.join(shlex.quote(w) for w in words);assert shlex.split(command)==words
 add('T',[b64(command.encode())],','.join(b64(w.encode()) for w in words))
 summary=''.join(r.choice(['a','界','🙂',',',';','\\','\n']) for _ in range(r.randrange(300)))
 description=summary+'\nBEGIN:VEVENT\nUID:attacker'
 add('C',[b64(summary.encode()),b64(description.encode())],(summary,description))
 text=''.join(r.choice(['a','界','🙂','\r','\n','\t','&','<','>','"',"'",' ']) for _ in range(r.randrange(1,200)))
 number=str(Decimal(r.randrange(-100000,100000))/100);name=f'Sheet{i}&'
 add('W',[b64(name.encode()),b64(text.encode()),number],(name,text,Decimal(number)))
# New regression attacks from the old reader's own roundtrip failure; not removed from the random corpus.
for text in ['a\\;b','a\\\\b','a\\nb','a\\b']:
 add('C',[b64(text.encode()),b64(text.encode())],(text,text))
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
cp=os.pathsep.join(str(OUT/p) for p in ['checks.jar','ContentDigest.jar','ShellWords.jar','ActionPlan.jar','CalendarExport.jar','LiteralWorkbook.jar'])
p=subprocess.run([java,'-cp',cp,'dev.fusion.testing.R13Oracle'],input='\n'.join(requests)+'\n',text=True,capture_output=True,check=True)
actual=p.stdout.splitlines();assert len(actual)==len(expected),(len(actual),len(expected),p.stderr)
for i,(output,(op,value)) in enumerate(zip(actual,expected)):
 assert output!='REFUSED',(i,op)
 if op in ('D','T'):assert output==value,(i,op,output,value)
 elif op=='C':
  b=base64.b64decode(output);assert all(len(line)<=75 for line in b.split(b'\r\n'))
  cal=icalendar.Calendar.from_ical(b);events=cal.walk('VEVENT');assert len(events)==1
  e=events[0];assert str(e['summary'])==value[0] and str(e['description'])==value[1],(i,repr(str(e['summary'])),repr(value[0]),repr(str(e['description'])),repr(value[1]),b.decode())
  assert str(e['uid'])=='case@fusion'
  assert e.decoded('dtstart').isoformat()=='2026-10-15T12:00:00+00:00'
  assert e.decoded('dtstamp')==e.decoded('dtstart')
  assert e.decoded('dtend').timestamp()-e.decoded('dtstart').timestamp()==60
 elif op=='W':
  b=base64.b64decode(output)
  with zipfile.ZipFile(io.BytesIO(b)) as z:
   assert len(z.namelist())==5 and len(set(z.namelist()))==5
   assert z.testzip() is None
   for name in z.namelist():
    root=ET.fromstring(z.read(name));assert not any(n.tag.endswith('}f') for n in root.iter())
  book=openpyxl.load_workbook(io.BytesIO(b),data_only=False)
  assert book.sheetnames==[value[0]];sheet=book.active
  assert sheet['A1'].value==value[1],(i,sheet['A1'].value,value[1])
  assert sheet['B1'].value=='=1+1' and sheet['B1'].data_type=='s'
  assert Decimal(str(sheet['C1'].value))==value[2]
  assert sheet['D1'].value is True and sheet['E1'].value is None
  book.close()
report.write_text(json.dumps({'passed':len(actual),'failed':0,'seed':130014,'oracles':{'openpyxl':openpyxl.__version__,'icalendar':icalendar.__version__,'other':'Python hashlib/shlex/zipfile/ElementTree'},'counts':{o:sum(k==o for k,v in expected) for o in ['D','T','C','W']},'script_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'compiled_receipt_sha256':hashlib.sha256(receipt.encode()).hexdigest(),'requests_sha256':hashlib.sha256(('\n'.join(requests)+'\n').encode()).hexdigest(),'scope':'Same-agent corpus, independent maintained readers; no LibreOffice/Excel/Android execution inferred'},indent=2)+'\n')
print(f'{len(actual)} cross-language checks PASS, including100 real XLSX packages read by openpyxl and104 ICS events read by icalendar.')
