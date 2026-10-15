#!/usr/bin/env python3
"""CPython codec/ZIP/LCS oracles against separately compiled Kotlin production code.
Requires run_jvm_contracts.py first. No Android, synthetic provider, or device result.
"""
import argparse
import base64
import functools
import hashlib
import itertools
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--report',required=True)
    args=parser.parse_args()
    compiler=os.environ.get('KOTLINC','kotlinc')
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
    build=ROOT/'testing/build/contracts'
    documents=build/'core_documents.jar';ooxml=build/'format_ooxml.jar'
    hashes={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for folder in ('core/documents/src/main','format/ooxml/src/main') for p in (ROOT/folder).rglob('*.kt')}
    evidence=(build/'result.txt').read_text()
    for name,digest in hashes.items():
        if digest+'  '+name not in evidence: raise RuntimeError('Stale compiled-source evidence: '+name)
    checks=0
    def expect(ok):
        nonlocal checks
        checks+=1
        if not ok: raise AssertionError(f'Independent check {checks} failed')
    with tempfile.TemporaryDirectory(prefix='fusion-text-interop-') as work:
        work=Path(work)
        source=work/'TextInterop.kt'
        source.write_bytes((ROOT/'core/documents/src/interop/TextInterop.kt.txt').read_bytes())
        jar=work/'interop.jar'
        cp=os.pathsep.join(map(str,(documents,ooxml)))
        subprocess.run([compiler,'-jvm-target','17','-classpath',cp,str(source),'-include-runtime','-d',str(jar)],check=True,timeout=60)
        def run(mode,input,output,encoding=None):
            return subprocess.run([java,'-cp',str(jar)+os.pathsep+cp,'TextInteropKt',mode,str(input),str(output)]+([encoding] if encoding else []),capture_output=True,text=True,timeout=20)
        text='\ufeffGreek α / 漢 / 😀\r\nNUL\0 and LF\nCR\r'
        encodings={'UTF8':('utf-8',b''),'UTF8_BOM':('utf-8',b'\xef\xbb\xbf'),
                   'UTF16_LE':('utf-16-le',b''),'UTF16_BE':('utf-16-be',b''),
                   'UTF16_LE_BOM':('utf-16-le',b'\xff\xfe'),'UTF16_BE_BOM':('utf-16-be',b'\xfe\xff'),
                   'WINDOWS_1252':('cp1252',b'')}
        for name,(codec,bom) in encodings.items():
            value='€ “quoted” café\r\n\0' if name=='WINDOWS_1252' else text
            original=work/'utf8';encoded=work/'encoded';decoded=work/'decoded'
            original.write_bytes(value.encode('utf-8'))
            result=run('encode',original,encoded,name)
            expect(result.returncode==0)
            expected=bom+value.encode(codec)
            expect(encoded.read_bytes()==expected)
            # Independently serialize the reverse input, do not use the JVM's output.
            encoded.write_bytes(expected)
            result=run('decode',encoded,decoded,name)
            expect(result.returncode==0)
            expect(decoded.read_bytes()==value.encode('utf-8'))
        cases=work/'cases';outputs=work/'diffs';cases.mkdir()
        corpus=[''.join(parts) for length in range(4) for parts in itertools.product(('a\n','b\n'),repeat=length)]
        pairs=list(itertools.product(corpus,repeat=2))
        for i,(before,after) in enumerate(pairs):
            folder=cases/str(i);folder.mkdir()
            (folder/'before').write_bytes(before.encode());(folder/'after').write_bytes(after.encode())
        result=run('diff-batch',cases,outputs)
        expect(result.returncode==0)
        for i,(before,after) in enumerate(pairs):
            lines=(outputs/str(i)).read_text().splitlines();added,removed=map(int,lines[0].split('\t'))
            script=[(kind,base64.b64decode(value).decode()) for kind,value in (line.split('\t') for line in lines[1:])]
            expect(''.join(text for kind,text in script if kind!='ADDED')==before)
            expect(''.join(text for kind,text in script if kind!='REMOVED')==after)
            # Independent recursive deletion/insertion distance, not the production bottom-up LCS traversal.
            a=before.splitlines(keepends=True);b=after.splitlines(keepends=True)
            @functools.lru_cache(None)
            def distance(i,j):
                if i==len(a): return len(b)-j
                if j==len(b): return len(a)-i
                if a[i]==b[j]: return distance(i+1,j+1)
                return 1+min(distance(i+1,j),distance(i,j+1))
            expect(added+removed==distance(0,0))
        archive=work/'word.docx';extracted=work/'extracted'
        with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
            z.writestr('word/document.xml','<w:document><w:p><w:t>Independent café 漢😀</w:t></w:p></w:document>')
        expect(run('docx',archive,extracted).returncode==0)
        expect(extracted.read_text()=='Independent café 漢😀')
        with zipfile.ZipFile(archive,'a',zipfile.ZIP_DEFLATED) as z:
            with z.open('ignored.bin','w') as entry:
                for _ in range(4096): entry.write(b'\0'*8192)
                entry.write(b'\0')
        expect(run('docx',archive,extracted).returncode!=0)
    report={'checks':checks,'result':'PASS','diff_pairs':len(pairs),'oracles':['CPython codecs','Python zipfile','recursive edit-distance oracle'],
            'production_source_sha256':hashes,'native_evidence':False,
            'limitations':'Host only. JDK/Python ZIP implementations may share zlib ancestry. No process sandbox, XML validator, Android UI or SAF acceptance.'}
    Path(args.report).write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))

if __name__=='__main__': main()
