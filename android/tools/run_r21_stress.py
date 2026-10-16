#!/usr/bin/env python3
"""Actual near-cap CSV producer/readback corpora; no Android memory/performance bound."""
import base64,csv,hashlib,io,json,os,struct,subprocess,time
from pathlib import Path
import openpyxl
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r21'
for line in (OUT/'result.txt').read_text().splitlines():
 if len(line)>66 and line[64:66]=='  ':assert hashlib.sha256((ROOT/line[66:]).read_bytes()).hexdigest()==line[:64]
rows=[
 [['']*32 for _ in range(256)],
 [['=1+1']*32 for _ in range(256)],
 [['x'*4096]*15+['x'*4079]], # writer appends CRLF: exact65536 bytes
 [['<&>'*1365+'x']*15+['<&>'*1359+'xy']],
 [['🦊,\"\r\nα']*16 for _ in range(128)],
 [['000001','\u202eidentity','@formula','a\tb'] for _ in range(256)]]
requests=[];sizes=[]
for table in rows:
 out=io.StringIO(newline='');csv.writer(out,lineterminator='\r\n').writerows(table);raw=out.getvalue().encode();assert len(raw)<=65536
 requests.append(base64.b64encode(raw).decode());sizes.append(len(raw))
cp=os.pathsep.join(str(OUT/n) for n in ['checks.jar','ExactReadback.jar','LiteralWorkbook.jar','CsvTable.jar','BrowserSessions.jar'])
start=time.monotonic();r=subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-cp',cp,'dev.fusion.testing.R21Oracle'],input='\n'.join(requests)+'\n',capture_output=True,text=True,check=True);elapsed=time.monotonic()-start
lines=r.stdout.splitlines();assert len(lines)==len(rows);books=[];frames=[]
for line,expected in zip(lines,rows):
 frame,book=map(base64.b64decode,line.split('\t'));books.append(len(book));frames.append(len(frame));assert len(frame)<=131072 and len(book)<=2097152
 wb=openpyxl.load_workbook(io.BytesIO(book),read_only=True,data_only=False)
 cells=list(wb.active.iter_rows());assert [[c.value if c.value is not None else '' for c in row] for row in cells]==expected
 assert all(c.data_type!='f' for row in cells for c in row);wb.close()
assert max(sizes)==65536 and max(books)>524288
try:
 import resource
 rss=resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss if os.uname().sysname=='Linux' else None
except (ImportError,AttributeError):rss=None
report={'result':'PASS','cases':len(rows),'input_bytes':sizes,'frame_bytes':frames,'workbook_bytes':books,'producer_seconds_observed':elapsed,'producer_child_peak_rss_kib_linux':rss,'scope':__doc__+' Host observation excludes Python reader memory. Authored corpus, not independent authorship or blind holdout.','sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),OUT/'result.txt']}}
(ROOT/'docs/verification/r21-stress.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
