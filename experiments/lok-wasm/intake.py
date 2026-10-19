#!/usr/bin/env python3
"""Pinned nonexecuting tar intake for an experimental LOK WASM payload. No license/runtime acceptance."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import sys
import tarfile

EXPECTED_BYTES = 70940538
EXPECTED_SHA = '591c9b099587bb6bea8d92e2cf73f93395f655e1656800cc8689ab740d229c1d'
ALLOWED = {'lokjs.js', 'lokjs.wasm', 'soffice.data', 'soffice.data.js.metadata'}
LIMITS = {'lokjs.js': 24*1024*1024, 'lokjs.wasm': 256*1024*1024, 'soffice.data': 256*1024*1024, 'soffice.data.js.metadata': 4*1024*1024}


def inspect(archive, destination):
    archive=Path(archive);destination=Path(destination)
    if archive.stat().st_size != EXPECTED_BYTES: raise ValueError('Not the declared pinned archive size')
    digest=hashlib.sha256()
    with archive.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''):digest.update(chunk)
    if digest.hexdigest()!=EXPECTED_SHA: raise ValueError('Pinned archive digest mismatch')
    destination.mkdir(parents=True,exist_ok=False)
    seen={};total=0;entries=0
    with tarfile.open(archive,'r:gz') as t:
        for member in t:
            entries+=1
            path=PurePosixPath(member.name)
            if entries>32 or path.is_absolute() or '..' in path.parts or '\\' in member.name:raise ValueError('Unsafe/archive entry budget')
            if member.isdir():continue
            name=path.name
            if not member.isfile() or name not in ALLOWED or name in seen or not 1<=member.size<=LIMITS[name]:raise ValueError('Unexpected/duplicate/link/oversized archive member')
            total+=member.size
            if total>512*1024*1024:raise ValueError('Expanded archive budget exceeded')
            h=hashlib.sha256();copied=0
            with t.extractfile(member) as source,(destination/name).open('xb') as target:
                while True:
                    chunk=source.read(1024*1024)
                    if not chunk:break
                    copied+=len(chunk)
                    if copied>member.size:raise ValueError('Growing member')
                    h.update(chunk);target.write(chunk)
            if copied!=member.size:raise ValueError('Truncated member')
            seen[name]={'bytes':copied,'sha256':h.hexdigest()}
    if set(seen)!=ALLOWED:raise ValueError('Missing required engine files')
    with (destination/'lokjs.wasm').open('rb') as f:
        if f.read(8)!=b'\x00asm\x01\x00\x00\x00':raise ValueError('Not a WASM v1 module')
    return {'archive_bytes':EXPECTED_BYTES,'archive_sha256':EXPECTED_SHA,'members':seen,
            'scope':'Actual pinned archive/hash/framing inspection ONLY; not upstream reproducibility, engine execution, Android or license closure',
            'integrated_android':False,'legal_accepted':False}


if __name__=='__main__':
    try:report=inspect(sys.argv[1],sys.argv[2])
    except Exception as error:raise SystemExit('WASM intake refused: '+str(error))
    Path(sys.argv[3]).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
