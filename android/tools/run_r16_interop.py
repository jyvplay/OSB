#!/usr/bin/env python3
"""Independent Python/OpenSSL peer over REAL sockets to a separately compiled Kotlin process."""
import hashlib,json,os,random,select,socket,struct,subprocess,time
from pathlib import Path
import cryptography
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends.openssl.backend import backend
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r16'
JAVA=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
CP=os.pathsep.join(str(OUT/n) for n in ['checks.jar','TogetherChannel.jar'])
KEY=bytes(range(32));MAGIC=b'FTG1';DIGEST=b'\xaa'*32;cases=[];rng=random.Random(1601)
def recv(s,n):
    data=b''
    while len(data)<n:
        b=s.recv(n-len(data))
        if not b:raise EOFError()
        data+=b
    return data
def launch(*args):return subprocess.Popen([JAVA,'-cp',CP,'dev.fusion.testing.R16Peer',*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
def complete(p,expected=None):
    try:out,err=p.communicate(timeout=5)
    except subprocess.TimeoutExpired:p.kill();p.communicate();raise
    if expected is None:assert p.returncode==0 and out.startswith('STATE '),(p.returncode,out,err)
    else:assert p.returncode==2 and any(out.strip()=='REFUSED '+x for x in expected),(p.returncode,out,err)
    return out.strip()
def client_case(label,attack=None,replay=None):
    p=launch('server');assert select.select([p.stdout],[],[],5)[0],'No JVM endpoint readiness'
    line=p.stdout.readline();assert line.startswith('PORT '),line
    started=time.monotonic();cn=os.urandom(16) if replay is None else replay[0]
    frame=None;sn=None;position=rng.randrange(2**63);playing=rng.randrange(2)
    with socket.create_connection(('127.0.0.1',int(line.split()[1])),timeout=3) as s:
        if attack=='slow':
            for b in b'FTG1x':
                try:s.sendall(bytes([b]))
                except OSError:break
                time.sleep(.3)
        elif attack=='magic':s.sendall(b'BAD!'+cn)
        else:
            s.sendall(MAGIC+cn);header=recv(s,20);assert header[:4]==MAGIC;sn=header[4:]
            context=MAGIC+cn+sn+bytes([2 if attack=='role' else 1])
            digest=b'\xbb'*32 if attack=='media' else DIGEST
            body=digest+struct.pack('>qB',-1 if attack=='negative' else position,2 if attack=='flag' else playing)
            nonce=os.urandom(12);key=bytes(32) if attack=='key' else KEY
            frame=nonce+AESGCM(key).encrypt(nonce,body,context)
            if attack=='tamper':frame=frame[:-1]+bytes([frame[-1]^1])
            if replay is not None:
                assert sn!=replay[1];frame=replay[2]
            s.sendall(frame[:-1] if attack=='truncate' else frame)
            if attack=='truncate':s.shutdown(socket.SHUT_WR)
            if attack is None and replay is None:
                response=recv(s,85);body=AESGCM(KEY).decrypt(response[:12],response[12:],MAGIC+cn+sn+b'\x02')
                assert body[:32]==DIGEST and struct.unpack('>qB',body[32:41])==(2345,1)
                t1,t2=struct.unpack('>qq',body[41:]);assert 0<=t1<=t2
    failures={'slow':['SocketException','SocketTimeoutException'],'magic':['IllegalArgumentException'],
      'media':['IllegalArgumentException'],'negative':['IllegalArgumentException'],'flag':['IllegalArgumentException'],
      'key':['AEADBadTagException'],'role':['AEADBadTagException'],'tamper':['AEADBadTagException'],
      'truncate':['EOFException'],'replay':['AEADBadTagException']}
    expected=failures['replay'] if replay is not None else failures.get(attack)
    out=complete(p,expected)
    if expected is None:assert out==f'STATE {position} {str(bool(playing)).lower()}',out
    elapsed=time.monotonic()-started
    if attack=='slow':assert elapsed<3,'Whole-call deadline did not close trickle input'
    cases.append({'case':label,'result':'PASS','endpoint_output':out,'elapsed_seconds':elapsed})
    return cn,sn,frame

def server_case(label,attack=None):
    with socket.socket() as listener:
        listener.bind(('127.0.0.1',0));listener.listen(1);listener.settimeout(5)
        p=launch('client',str(listener.getsockname()[1]))
        with listener.accept()[0] as s:
            s.settimeout(3);header=recv(s,20);assert header[:4]==MAGIC;cn=header[4:];sn=os.urandom(16)
            s.sendall(MAGIC+sn);frame=recv(s,69)
            body=AESGCM(KEY).decrypt(frame[:12],frame[12:],MAGIC+cn+sn+b'\x01');received=time.monotonic_ns()
            assert body==DIGEST+struct.pack('>qB',2345,1)
            position=rng.randrange(2**63);playing=rng.randrange(2)
            times=(-1,0) if attack=='negative-clock' else (0,2**63-1) if attack=='processing' else (received,time.monotonic_ns())
            response=(b'\xbb'*32 if attack=='media' else DIGEST)+struct.pack('>qBqq',position,playing,*times)
            nonce=os.urandom(12);context=MAGIC+cn+sn+bytes([1 if attack=='role' else 2])
            frame=nonce+AESGCM(KEY).encrypt(nonce,response,context);s.sendall(frame)
        failures={'negative-clock':['IllegalArgumentException'],'processing':['IllegalArgumentException'],
          'media':['IllegalArgumentException'],'role':['AEADBadTagException']}
        out=complete(p,failures.get(attack))
        if attack is None:
            assert out.startswith(f'STATE {position} {str(bool(playing)).lower()} RTT '),out
            assert int(out.split()[-1])>=0
        cases.append({'case':label,'result':'PASS','endpoint_output':out})

for i in range(25):saved=client_case(f'OpenSSL-client-JVM-server-{i}')
for i in range(25):server_case(f'JVM-client-OpenSSL-server-{i}')
for attack in ['key','media','negative','flag','role','tamper','truncate','magic','slow']:client_case(attack,attack)
client_case('fresh-server-challenge-rejects-old-request',replay=saved)
for attack in ['negative-clock','processing','media','role']:server_case('reply-'+attack,attack)
result={'result':'PASS','cases':len(cases),'details':cases,'cryptography':cryptography.__version__,
 'openssl':backend.openssl_version_text(),'sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),OUT/'TogetherChannel.jar',OUT/'checks.jar']},
 'scope':'Real loopback sockets between separate Kotlin JVM and independent Python/OpenSSL endpoints; no multi-device, real player, pairing UI, Syncplay or physical performance acceptance'}
(ROOT/'docs/verification/r16-interop.json').write_text(json.dumps(result,indent=2)+'\n');print(f'R16: {len(cases)} real cross-process Java/OpenSSL socket cases PASS')
