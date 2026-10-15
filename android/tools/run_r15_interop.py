#!/usr/bin/env python3
"""Real JVM outputs vs Python rational/struct/URL/decimal and cryptography/OpenSSL; not device acceptance."""
import base64,decimal,hashlib,json,os,random,struct,subprocess,urllib.parse,zlib
from fractions import Fraction as Q
from pathlib import Path
import cryptography
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends.openssl.backend import backend
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r15';report=ROOT/'docs/verification/r15-interop.json';report.unlink(missing_ok=True)
receipt=(OUT/'result.txt').read_text()
for line in receipt.splitlines():
 if len(line)>66 and line[64:66]=='  ':assert hashlib.sha256((ROOT/line[66:]).read_bytes()).hexdigest()==line[:64],line
r=random.Random(150015);requests=[];expected=[]
b64=lambda b:base64.b64encode(b).decode()
def add(op,fields,answer):requests.append('\t'.join([op]+list(map(str,fields))));expected.append((op,answer))
for i in range(100):
 w,h,vw,vh=[r.randrange(1,32769) for _ in range(4)];turn=r.randrange(4);mirror=r.randrange(2);mode=r.choice(['FIT','CROP']);x=r.randrange(w+1);y=r.randrange(h+1)
 # Exact rational affine composition, represented as matrices independently of Kotlin's case-coordinate expressions.
 matrices=[((1,0,0),(0,1,0)),((0,-1,h),(1,0,0)),((-1,0,w),(0,-1,h)),((0,1,0),(-1,0,w))]
 xx,yy=[a*x+b*y+c for a,b,c in matrices[turn]];rw,rh=(w,h) if turn%2==0 else (h,w)
 if mirror:xx=rw-xx
 scale=(min if mode=='FIT' else max)(Q(vw,rw),Q(vh,rh));answer=(Q(xx)*scale+(vw-rw*scale)/2,Q(yy)*scale+(vh-rh*scale)/2)
 add('G',[w,h,vw,vh,turn,mirror,mode,x,y],answer)
for i in range(100):
 now=active=paused=0;stamps=[]
 for j in range(r.randrange(1,10)):
  interval=r.randrange(100000);gap=r.randrange(100000);active+=interval;paused+=gap;now+=interval;stamps.append(now);now+=gap;stamps.append(now)
 interval=r.randrange(100000);active+=interval;now+=interval;stamps.append(now)
 add('T',stamps,f'{now},{active},{paused}')
for i in range(100):
 author='Author🙂'+str(i);text=''.join(r.choice(['α','界','🙂','a','\n','\t','\x00']) for _ in range(r.randrange(1000)));stamp=r.randrange(1<<62)
 def field(s):b=s.encode();return struct.pack('>i',len(b))+b
 body=b'FMT1'+struct.pack('>i',1)+field('m1')+field(author)+field(text)+struct.pack('>q',stamp)
 add('M',[b64(author.encode()),b64(text.encode()),stamp],body+struct.pack('>I',zlib.crc32(body)))
for i in range(100):
 plain=r.randbytes(r.randrange(4096));key=r.randbytes(32);context=r.randbytes(r.randrange(128))
 add('V',[b64(plain),b64(key),b64(context)],(plain,key,context))
for i in range(100):
 module=r.choice(['files','editor','notes','calculator','browser','settings']);ws=r.randrange(1,4);url=f'fusion://open/{module}?workspace={ws}'
 parsed=urllib.parse.urlsplit(url);assert parsed.scheme=='fusion' and parsed.netloc=='open' and urllib.parse.parse_qs(parsed.query)=={'workspace':[str(ws)]}
 add('L',[b64(url.encode())],f'{module},{ws},{url}')
with decimal.localcontext() as ctx:
 ctx.prec=34;ctx.rounding=decimal.ROUND_HALF_EVEN
 for i in range(100):
  a,b,c=[r.randrange(1,100000) for _ in range(3)];values=[str(a),f'=A1*{b}',f'=A2/{c}'];answer=[decimal.Decimal(a),decimal.Decimal(a)*b,(decimal.Decimal(a)*b)/c]
  add('S',map(lambda s:b64(s.encode()),values),answer)
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
cp=os.pathsep.join(str(OUT/n) for n in ['checks.jar','ViewportTransform.jar','RecordingTimeline.jar','TranscriptArchive.jar','VaultEnvelope.jar','LaunchLinks.jar','SheetFrame.jar'])
def run(lines):
 p=subprocess.run([java,'-cp',cp,'dev.fusion.testing.R15Oracle'],input='\n'.join(lines)+'\n',text=True,capture_output=True,check=True,timeout=60)
 result=p.stdout.splitlines();assert len(result)==len(lines),(p.stdout,p.stderr);return result
actual=run(requests);reverse=[];reverse_expected=[]
for i,(value,(op,want)) in enumerate(zip(actual,expected)):
 assert value!='REFUSED',(i,op)
 if op=='G':
  actual_point=list(map(float,value.split(',')));assert all(abs(float(a)-b)<1e-6 for a,b in zip(want,actual_point)),(i,want,value)
 elif op in ('T','L'):assert value==want,(i,value,want)
 elif op=='M':assert base64.b64decode(value)==want,i
 elif op=='V':
  frame=base64.b64decode(value);plain,key,context=want
  assert frame[:4]==b'FVE1' and struct.unpack('>i',frame[4:8])[0]==len(plain)
  assert AESGCM(key).decrypt(frame[8:20],frame[20:],frame[:20]+context)==plain
  # Independently generate a Python ciphertext and require actual JVM authenticated decoding.
  nonce=r.randbytes(12);header=b'FVE1'+struct.pack('>i',len(plain))+nonce
  python_frame=header+AESGCM(key).encrypt(nonce,plain,header+context)
  reverse.append('\t'.join(['O',b64(python_frame),b64(key),b64(context)]));reverse_expected.append(b64(plain))
 elif op=='S':
  frame=base64.b64decode(value).decode();lines=frame.splitlines();assert lines[0]=='FSR1' and len(lines)==4
  for row,(line,number) in enumerate(zip(lines[1:],want),1):
   key,v=line.split('\t');assert key==f'A{row}' and decimal.Decimal(v)==number,(i,line,number)
assert run(reverse)==reverse_expected
report.write_text(json.dumps({'passed':len(actual)+len(reverse),'failed':0,'seed':150015,'forward_counts':{op:sum(o==op for o,a in expected) for op in ['G','T','M','V','L','S']},'reverse_AEAD_cases':len(reverse),'oracles':{'cryptography':cryptography.__version__,'openssl':backend.openssl_version_text(),'other':'Python fractions/struct/zlib/urllib.parse/decimal'},'coordinate_absolute_tolerance':'1e-6 source/viewport coordinate units','scope':'Same-author corpus; independent maintained crypto implementation and Python standard libraries. No device, Office-engine, key-management, FIPS or full-security acceptance.','script_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'compiled_receipt_sha256':hashlib.sha256(receipt.encode()).hexdigest(),'requests_sha256':hashlib.sha256(('\n'.join(requests)+'\n').encode()).hexdigest()},indent=2)+'\n')
print(f'R15: {len(actual)+len(reverse)} cross-language cases PASS, including bidirectional Java/OpenSSL AEAD')
