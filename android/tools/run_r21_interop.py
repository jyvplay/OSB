#!/usr/bin/env python3
"""Real separately compiled Java producer; Python csv/struct/zipfile/openpyxl consumers. Authored, not blind holdout."""
import base64,csv,hashlib,io,json,os,random,struct,subprocess,zipfile
from pathlib import Path
import openpyxl
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r21'
for line in (OUT/'result.txt').read_text().splitlines():
 if len(line)>66 and line[64:66]=='  ':assert hashlib.sha256((ROOT/line[66:]).read_bytes()).hexdigest()==line[:64],line[66:]
rng=random.Random(210021);requests=[];expected=[];pairs=[]
def wire(rows):
 o=io.StringIO(newline='');csv.writer(o,lineterminator='\r\n').writerows(rows);return o.getvalue().encode()
def add(raw,rows,columns=None):
 requests.append(base64.b64encode(raw).decode()+((' map '+','.join(map(str,columns))).replace(' map ','\t') if columns is not None else ''))
 expected.append(rows if columns is None else [[r[c] for c in columns] for r in rows])
values=['','αβ','emoji 🦊','a,b','quote " word','line\r\nbreak','000123','=1+1','+SUM(1,2)','@name',' leading ','\ttext','＝1+1','é']
for i in range(160):
 width=rng.randint(1,12);rows=[[rng.choice(values) for _ in range(width)] for _ in range(rng.randint(1,12))]
 raw=wire(rows);assert list(csv.reader(io.StringIO(raw.decode(),newline=''),strict=True))==rows
 if i%3==0:raw=b'\xef\xbb\xbf'+raw
 add(raw,rows);cols=rng.sample(list(range(width)),rng.randint(1,width));add(raw,rows,cols)
for i in range(80):
 a=[['Name','Value','private'],[f'person-{i}',str(i),'hidden-A']];b=[['Name','Value','SECRET'],[f'person-{i}',str(i),'hidden-B']]
 first=len(requests);add(wire(a),a,[0,1]);add(wire(b),b,[0,1]);pairs.append((first,first+1))
for raw in [b'a"b',b'"x" trailing',b'"open',b'a\rb',b'a,b\nc',b'\xff',b'\xc0\x80',b'\x00',b'x'*4097,b','*32,b'x\n'*257,b'x'*65537,b'_x0041_']:
 requests.append(base64.b64encode(raw).decode());expected.append(None)
cp=os.pathsep.join(str(OUT/n) for n in ['checks.jar','ExactReadback.jar','LiteralWorkbook.jar','CsvTable.jar','BrowserSessions.jar'])
r=subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-cp',cp,'dev.fusion.testing.R21Oracle'],input='\n'.join(requests)+'\n',capture_output=True,text=True,check=True)
lines=r.stdout.splitlines();assert len(lines)==len(expected);sizes=[];packages=[]
for line,want in zip(lines,expected):
 if want is None:assert line=='REFUSED';packages.append(None);continue
 frame,book=map(base64.b64decode,line.split('\t'));packages.append(book);sizes.append(len(book))
 f=io.BytesIO(frame);magic,n,w=struct.unpack('>iii',f.read(12));assert magic==0x46435431
 rows=[[f.read(struct.unpack('>i',f.read(4))[0]).decode() for _ in range(w)] for _ in range(n)]
 assert f.read()==b'' and rows==want
 wb=openpyxl.load_workbook(io.BytesIO(book),data_only=False,read_only=True);sheet=wb.active
 cells=list(sheet.iter_rows());actual=[[c.value if c.value is not None else '' for c in row] for row in cells]
 assert actual==want,(actual,want)
 for row in cells:
  for cell in row:assert cell.data_type!='f'
 wb.close()
 with zipfile.ZipFile(io.BytesIO(book)) as z:
  xml=z.read('xl/worksheets/sheet1.xml');assert b'<f>' not in xml and b'<f ' not in xml
for a,b in pairs:assert packages[a]==packages[b]
report={'result':'PASS','requests':len(expected),'successful_reader_cases':len(sizes),'refusals':sum(e is None for e in expected),'omitted_column_equal_byte_pairs':len(pairs),'workbook_bytes':[min(sizes),max(sizes)],'openpyxl':openpyxl.__version__,'seed':210021,'scope':__doc__+' Formula execution in Excel/LibreOffice not tested. Equal-byte pairs are environment-scoped, not anonymity.', 'requests_sha256':hashlib.sha256(('\n'.join(requests)+'\n').encode()).hexdigest(),'outputs_sha256':hashlib.sha256(r.stdout.encode()).hexdigest(),'sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),OUT/'result.txt']}}
(ROOT/'docs/verification/r21-interop.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
