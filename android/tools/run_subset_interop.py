#!/usr/bin/env python3
"""Python byte/pixel/time/catalog oracles against separately compiled actual Kotlin libraries.
Run run_subset_contracts.py first. No mocked Android APIs or external packages.
"""
import base64, csv, hashlib, io, json, os, random, struct, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/subsets'
REPORT=ROOT/'docs/verification/r12b-interop.json'
REPORT.unlink(missing_ok=True)
# Refuse stale compiled artifacts by checking the preceding compiler receipt.
receipt=(OUT/'result.txt').read_text()
for line in receipt.splitlines():
    if len(line)>66 and line[64:66]=='  ': assert hashlib.sha256((ROOT/line[66:]).read_bytes()).hexdigest()==line[:64],line
r=random.Random(120014);b64=lambda b:base64.b64encode(b).decode()
requests=[];expected=[];counts={}
def add(kind,fields,want):
    requests.append('\t'.join([kind]+fields));expected.append(want);counts[kind]=counts.get(kind,0)+1
for _ in range(120):
    w=r.randrange(1,20);h=r.randrange(1,20);turn=r.randrange(-12,13)
    a=[[r.randrange(-2**31,2**31) for x in range(w)] for y in range(h)]
    raw=b''.join(struct.pack('>i',v) for row in a for v in row)
    for t in range(turn%4): a=[list(row) for row in zip(*a[::-1])]
    target=b''.join(struct.pack('>i',v) for row in a for v in row)
    add('I',[str(w),str(h),str(turn),b64(raw)],f'{len(a[0])},{len(a)},{b64(target)}')
for _ in range(100):
    vals=[''.join(r.choice(['a','界','🙂','\n','\0','=','é']) for j in range(r.randrange(100))) for i in range(2)]
    frame=('FSET1\ndensity='+b64(vals[1].encode())+'\ntheme='+b64(vals[0].encode())+'\n').encode()
    add('S',[b64(v.encode()) for v in vals],b64(frame));add('D',[b64(frame)],b64(frame))
def backup(entries):
    body=struct.pack('>II',0x46424331,len(entries))
    for name,data in sorted(entries.items()):
        n=name.encode('ascii');body+=struct.pack('>I',len(n))+n+struct.pack('>I',len(data))+data
    return body+hashlib.sha256(body).digest()
for i in range(80):
    entries={f'n{j}.md':r.randbytes(r.randrange(500)) for j in range(r.randrange(8))}
    frame=backup(entries)
    add('B',[n+':'+b64(v) for n,v in entries.items()],b64(frame));add('R',[b64(frame)],b64(frame))
# Recomputed checksum attacks must reach structural validation, not just digest failure.
for name in ['../x.md','CON.md','x/y.md','a..md']:
    add('R',[b64(backup({name:b'x'}))],'REFUSED')
for i in range(100):
    start=r.randrange(1000);end=start+r.randrange(1,1000);position=r.randrange(2000)
    def ts(ms):return f'00:00:{ms//1000:02d},{ms%1000:03d}'
    text=f'cue {i} α🙂 <b>literal</b>'
    frame=f'1\r\n{ts(start)} --> {ts(end)}\r\n{text}\r\n'.encode()
    add('T',[b64(frame),str(position)],b64(text.encode()) if start<=position<end else '')
for i in range(100):
    keys=[f'k{n}' for n in range(20)];pins=r.sample(keys,r.randrange(10));seen=r.sample(keys,r.randrange(15));running={k:r.choice([False,True]) for k in seen}
    wanted=[k for k in pins if k in seen]+sorted(k for k in seen if running[k] and k not in pins)
    add('L',[','.join(pins),','.join(k+':'+str(int(running[k])) for k in seen)],','.join(wanted))
for _ in range(300):
    data=r.randbytes(r.randrange(16))
    try: data.decode('utf-8','strict');ok=True
    except UnicodeDecodeError:ok=False
    add('V',[b64(data)],str(ok).lower())
for _ in range(200):
    rows=[[''.join(r.choice(['a','α','🙂',',','"','\n','\r',' ','=']) for c in range(r.randrange(20))) for col in range(r.randrange(1,8))] for row in range(r.randrange(1,8))]
    output=io.StringIO(newline='');csv.writer(output,lineterminator='\r\n').writerows(rows)
    frame=output.getvalue()
    # CPython's own parser independently checks writer-generated corpus before Kotlin sees it.
    assert list(csv.reader(io.StringIO(frame,newline=''),strict=True))==rows
    want=';'.join(','.join(b64(cell.encode()) for cell in row) for row in rows)
    add('C',[b64(frame.encode())],want)
for text in ['"open','a"b','"x"z','x\ry']:
    add('C',[b64(text.encode())],'REFUSED')
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
cp=os.pathsep.join(str(OUT/n) for n in ['checks.jar','core_settings.jar','feature_media.jar','format_image.jar','feature_backup.jar','feature_launcher.jar','format_native.jar'])
p=subprocess.run([java,'-cp',cp,'dev.fusion.testing.SubsetOracle'],input='\n'.join(requests)+'\n',capture_output=True,text=True,check=True)
actual=p.stdout.splitlines();assert len(actual)==len(expected),(len(actual),len(expected),p.stderr)
for i,(a,e) in enumerate(zip(actual,expected)):assert a==e,(i,requests[i],a,e)
REPORT.write_text(json.dumps({'seed':120014,'passed':len(expected),'failed':0,'operations':counts,'requests_sha256':hashlib.sha256(('\n'.join(requests)+'\n').encode()).hexdigest(),'expected_sha256':hashlib.sha256(('\n'.join(expected)+'\n').encode()).hexdigest(),'compiler_receipt_sha256':hashlib.sha256(receipt.encode()).hexdigest(),'oracle_script_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'scope':'Python-generated held-out corpus, actual JVM execution; same assistant authorship, not independent expert review or Android evidence'},indent=2)+'\n')
print(f'{len(expected)} cross-language checks passed: {counts}')
