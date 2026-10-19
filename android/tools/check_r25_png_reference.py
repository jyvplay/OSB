#!/usr/bin/env python3
"""Independent actual PNG decoder/file readback, never native UI evidence or an anonymity certificate."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import struct
import subprocess
import tempfile
import zlib
from PIL import Image, PngImagePlugin, __version__ as PILLOW_VERSION

ROOT=Path(__file__).resolve().parents[1]

def chunks(data):
    at=8;result=[]
    while at<len(data):
        n=struct.unpack_from('>I',data,at)[0];kind=data[at+4:at+8];value=data[at+8:at+8+n]
        assert zlib.crc32(kind+value)&0xffffffff==struct.unpack_from('>I',data,at+8+n)[0]
        result.append((kind,value));at+=n+12
    assert at==len(data)
    return result

def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)

def filtered_png(width,height,channels,filter_kind):
    rng=random.Random(width*10000+height*100+channels*10+filter_kind)
    raw=bytes(rng.randrange(256) for _ in range(width*height*channels));rows=[];stride=width*channels
    def paeth(a,b,c):
        p=a+b-c;d=[abs(p-a),abs(p-b),abs(p-c)];return (a,b,c)[d.index(min(d))]
    for y in range(height):
        current=raw[y*stride:(y+1)*stride];prior=raw[(y-1)*stride:y*stride] if y else bytes(stride)
        out=bytearray([filter_kind])
        for i,x in enumerate(current):
            left=current[i-channels] if i>=channels else 0;up=prior[i];corner=prior[i-channels] if i>=channels else 0
            predictor=(0,left,up,(left+up)//2,paeth(left,up,corner))[filter_kind]
            out.append((x-predictor)&255)
        rows.append(out)
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,6 if channels==4 else 2,0,0,0))
    data+=chunk(b'tEXt',b'Author\0R25 private metadata marker')
    encoded=zlib.compress(b''.join(rows));split=len(encoded)//2
    data+=chunk(b'IDAT',encoded[:split])+chunk(b'IDAT',encoded[split:])+chunk(b'IEND',b'')
    return data,raw

def run():
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    cp=os.pathsep.join(str(ROOT/'testing/build/r25'/name) for name in ('png-checks.jar','png-production.jar'))
    count=0;digest=hashlib.sha256();diagnostics=[]
    with tempfile.TemporaryDirectory(prefix='r25-png-reference-') as tmp:
        root=Path(tmp)
        cases=[]
        for mode in ('RGB','RGBA'):
            for size in ((1,1),(3,2),(31,17),(64,64),(129,37)):
                image=Image.frombytes(mode,size,bytes((i*71+size[0])%256 for i in range(size[0]*size[1]*len(mode))))
                meta=PngImagePlugin.PngInfo();meta.add_text('Author','R25 original private marker');meta.add_itxt('Comment','Original user metadata',zip=True)
                exif=Image.Exif();exif[315]='R25 owner';exif[274]=6
                file=root/f'pillow-{mode}-{size[0]}-{size[1]}.png';image.save(file,pnginfo=meta,exif=exif)
                cases.append((file.read_bytes(),None))
        for width,height in ((1,1),(3,4),(17,9),(63,31)):
            for channels in (3,4):
                for filter_kind in range(5):cases.append(filtered_png(width,height,channels,filter_kind))
        for index,(source,expected) in enumerate(cases):
            before=root/f'{index}-source.png';after=root/f'{index}-copy.png';before.write_bytes(source)
            p=subprocess.run([java,'-cp',cp,'dev.fusion.testing.PngChecks','clean',str(before),str(after)],check=True,capture_output=True,text=True,timeout=15)
            report=json.loads(p.stdout);clean=after.read_bytes()
            assert before.read_bytes()==source
            with Image.open(before) as original, Image.open(after) as copied:
                original.load();copied.load()
                assert original.mode==copied.mode and original.size==copied.size
                assert original.tobytes()==copied.tobytes()
                assert original.convert("RGBA").tobytes()==copied.convert("RGBA").tobytes()
                if expected is not None:assert original.tobytes()==expected
                assert not copied.getexif() and 'Author' not in copied.info and 'Comment' not in copied.info
            first=chunks(source);second=chunks(clean)
            assert b''.join(v for k,v in first if k==b'IDAT')==b''.join(v for k,v in second if k==b'IDAT')
            assert all(k in (b'IHDR',b'IDAT',b'IEND',b'sRGB',b'gAMA',b'cHRM',b'tRNS') for k,v in second)
            assert report['input_sha256']==hashlib.sha256(source).hexdigest() and report['output_sha256']==hashlib.sha256(clean).hexdigest()
            assert report['removed']>0
            digest.update(source);digest.update(clean);count+=1
        # Actual invalid files: do not create an approved output, even when CRCs are correct.
        data,raw=filtered_png(1,1,4,0)
        for bad in (data+b'not-part-of-image',data[:-1],data[:8]+chunk(b'IHDR',struct.pack('>IIBBBBB',4097,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(5)))+chunk(b'IEND',b'')):
            before=root/'bad.png';after=root/'bad-copy.png';before.write_bytes(bad)
            p=subprocess.run([java,'-cp',cp,'dev.fusion.testing.PngChecks','clean',str(before),str(after)],capture_output=True,text=True,timeout=15)
            assert p.returncode!=0 and not after.exists();count+=1
    return {'decoder':'Pillow '+PILLOW_VERSION,'executed_file_cases':count,'corpus_digest':digest.hexdigest(),
            'scope':'Real production JVM transform + independent PNG decoding/CRC/file readback. Encoded pixel samples only; not visual orientation/printing/anonymity or Android execution.'}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path);a=p.parse_args();result=run()
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))
