#!/usr/bin/env python3
"""Real two-process loopback socket/authentication feasibility, not a fake remote player."""
import hashlib,hmac,json,os,socket,subprocess,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
KEY=bytes(range(32)) # Public fixture, NEVER a pairing credential.
def receive(s,n):
    out=b''
    while len(out)<n:
        part=s.recv(n-len(out))
        if not part:raise EOFError()
        out+=part
    return out
if len(sys.argv)>1:
    with socket.create_connection(('127.0.0.1',int(sys.argv[1])),timeout=2) as s:
        challenge=receive(s,32)
        s.sendall(hmac.digest(KEY,challenge,'sha256'))
        assert receive(s,2)==b'OK'
    print(json.dumps({'pid':os.getpid(),'accepted':True}))
else:
    started=time.monotonic()
    with socket.socket() as listener:
        listener.bind(('127.0.0.1',0));listener.listen(1);listener.settimeout(3)
        child=subprocess.Popen([sys.executable,__file__,str(listener.getsockname()[1])],stdout=subprocess.PIPE,text=True)
        try:
            with listener.accept()[0] as s:
                s.settimeout(2);challenge=os.urandom(32);s.sendall(challenge)
                assert hmac.compare_digest(receive(s,32),hmac.digest(KEY,challenge,'sha256'));s.sendall(b'OK')
            stdout,_=child.communicate(timeout=3);assert child.returncode==0
            peer=json.loads(stdout);assert peer['pid']!=os.getpid()
        finally:
            if child.poll() is None:child.kill();child.wait()
    result={'result':'PASS','parent_pid':os.getpid(),'peer':peer,'elapsed_seconds':time.monotonic()-started,
       'source_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
       'scope':'Actual Python two-process loopback/socket/HMAC probe only; NOT Android, multi-device, pairing, media synchronization or Syncplay acceptance'}
    (ROOT/'docs/verification/r16-transport-spike.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
