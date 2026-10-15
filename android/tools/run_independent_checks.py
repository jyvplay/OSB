#!/usr/bin/env python3
"""Optional SunJCE/OpenSSL and PNG/Pillow cross-checks, NOT native UI/device tests.
Requires Python cryptography/Pillow, JAVA_HOME and KOTLINC. Downloads nothing.
Only public test vectors/ephemeral fixtures are used. No production keys or user files.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import zlib
from PIL import Image, __version__ as pillow_version
import cryptography
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends.openssl.backend import backend
from png_capture import dimensions, SIGNATURE

ROOT = Path(__file__).resolve().parents[1]


def chunk(kind, body):
    return struct.pack('>I', len(body))+kind+body+struct.pack('>I', zlib.crc32(kind+body)&0xffffffff)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    compiler=os.environ.get('KOTLINC','kotlinc')
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
    sources=sorted((ROOT/'core/index/src/main/kotlin').rglob('*.kt'))
    helper=ROOT/'core/index/src/interop/IndexInterop.kt.txt'
    with tempfile.TemporaryDirectory(prefix='fusion-cross-verifier-') as temporary:
        directory=Path(temporary)
        lib=directory/'index.jar'; consumer=directory/'interop.jar'
        source=directory/'IndexInterop.kt';source.write_bytes(helper.read_bytes())
        subprocess.run([compiler,'-jvm-target','17',*map(str,sources),'-d',str(lib)],check=True)
        subprocess.run([compiler,'-jvm-target','17','-classpath',str(lib),str(source),'-include-runtime','-d',str(consumer)],check=True)
        cp=os.pathsep.join(map(str,(consumer,lib)))
        java_file=directory/'java.bin';python_file=directory/'python.bin'
        subprocess.run([java,'-cp',cp,'IndexInteropKt','emit',str(java_file)],check=True)
        data=java_file.read_bytes()
        key=bytes(range(32))  # Public TEST vector, not a provisioned key.
        plain=AESGCM(key).decrypt(data[8:20],data[20:],data[:8]+b'interop')
        fields=['interop','Título','Cross-provider α 😀\0','owned-notes']
        expected=struct.pack('>ii',1,1)+b''.join(struct.pack('>i',len(b))+b for b in (t.encode() for t in fields))
        if plain!=expected:raise ValueError('Independent plaintext serialization mismatch')
        nonce=os.urandom(12)
        python_file.write_bytes(data[:8]+nonce+AESGCM(key).encrypt(nonce,expected,data[:8]+b'interop'))
        subprocess.run([java,'-cp',cp,'IndexInteropKt','read',str(python_file)],check=True)
    count=0
    for mode in ('RGB','RGBA'):
        for width,height in ((1,1),(2,3),(257,133),(1024,768)):
            image=Image.new(mode,(width,height),tuple(range(37,37+len(mode))))
            buffer=io.BytesIO();image.save(buffer,format='PNG');data=buffer.getvalue()
            if dimensions(data)!=(width,height):raise ValueError('Pillow-produced dimensions disagree')
            Image.open(io.BytesIO(data)).load()
            count+=1
    for filter_type in range(5):
        raw=(bytes([filter_type])+bytes(range(8)))*2
        data=SIGNATURE+chunk(b'IHDR',struct.pack('>IIBBBBB',2,2,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
        if dimensions(data)!=(2,2):raise ValueError('Row-filter dimensions disagree')
        Image.open(io.BytesIO(data)).load();count+=1
    fingerprinted=sources+[helper,Path(__file__).resolve(),ROOT/'tools/png_capture.py']
    report={'crypto':'SunJCE to OpenSSL and OpenSSL to production Kotlin codec both executed successfully; independently serialized UTF-8 payload equal',
            'cryptography_version':cryptography.__version__,'openssl':backend.openssl_version_text(),'pillow_version':pillow_version,
            'png_cross_checks':count,'limitations':'Parser fixtures only; no native screenshots, UI review, Android Keystore or formal security proof',
            'source_sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in fingerprinted}}
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))


if __name__=='__main__':main()
