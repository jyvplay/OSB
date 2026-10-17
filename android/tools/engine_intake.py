#!/usr/bin/env python3
"""Bounded real ELF/AAR intake, never dlopen/execute an engine. NOT integration/legal/runtime acceptance."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import struct
import zipfile

MAX_ARTIFACT=512*1024*1024
MAX_ELF=128*1024*1024

def elf_header(stream,size):
    if not 64<=size<=MAX_ELF:raise ValueError('ELF size outside intake regime')
    stream.seek(0);h=stream.read(64)
    if len(h)!=64 or h[:4]!=b'\x7fELF' or h[4]!=2 or h[5]!=1:raise ValueError('Requires actual ELF64 little-endian header')
    kind,machine=struct.unpack_from('<HH',h,16)
    if kind!=3 or machine not in (62,183):raise ValueError('Not a supported64-bit shared-library architecture')
    offset=struct.unpack_from('<Q',h,32)[0];entry,count=struct.unpack_from('<HH',h,54)
    if entry!=56 or not 1<=count<=256 or not 64<=offset or offset+entry*count>min(size,1024*1024):raise ValueError('ELF program table outside bounded header contract')
    stream.seek(offset);table=stream.read(entry*count)
    if len(table)!=entry*count:raise ValueError('Truncated ELF program table')
    loads=[]
    for i in range(count):
        typ,flags,pos,virtual,physical,filesz,memsz,align=struct.unpack_from('<IIQQQQQQ',table,i*entry)
        if typ!=1:continue
        if filesz>memsz or pos+filesz>size or virtual+memsz>=2**64 or (align not in (0,1) and align&(align-1)):raise ValueError('Invalid PT_LOAD file range')
        loads.append({'offset':pos,'virtual':virtual,'file_bytes':filesz,'memory_bytes':memsz,'alignment':align,
                      'strict_16k_condition':align>=16384 and (virtual-pos)%16384==0})
    if not loads:raise ValueError('No PT_LOAD segments')
    return {'elf_class':64,'machine':machine,'abi':'arm64-v8a' if machine==183 else 'x86_64','loads':loads,
            'strict_16k_header_condition':all(r['strict_16k_condition'] for r in loads),
            'scope':'Header condition only; not APK zip alignment, symbol/API closure, execution or Android compatibility-mode behavior'}

def inspect(path):
    path=Path(path);size=path.stat().st_size
    if not 1<=size<=MAX_ARTIFACT:raise ValueError('Artifact size budget exceeded')
    digest=hashlib.sha256();consumed=0
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''):
            consumed+=len(chunk)
            if consumed>MAX_ARTIFACT:raise ValueError('Growing artifact exceeded byte budget')
            digest.update(chunk)
    if consumed!=size:raise ValueError('Artifact changed while hashing')
    result={'artifact':path.name,'bytes':size,'sha256':digest.hexdigest(),'integrated':False,'runtime_verified':False,'legal_accepted':False}
    if path.suffix.lower() in ['.aar','.apk','.zip','.jar']:
        # Bound central-directory acquisition BEFORE ZipFile allocates entries; no ZIP64/multidisk intake.
        with path.open('rb') as stream:
            stream.seek(max(0,size-65557));tail=stream.read(65557)
        eocd=tail.rfind(b'PK\x05\x06')
        if eocd<0 or len(tail)-eocd<22:raise ValueError('Missing bounded ZIP EOCD')
        disk,central_disk,count_disk,count,cd_size,cd_offset,comment=struct.unpack_from('<4H2IH',tail,eocd+4)
        if disk or central_disk or count_disk!=count or count>4096 or cd_size>4*1024*1024 or cd_offset+cd_size>size or eocd+22+comment!=len(tail):
            raise ValueError('ZIP central-directory budget/framing refused')
        with zipfile.ZipFile(path) as z:
            entries=z.infolist()
            if len(entries)>4096 or sum(i.file_size for i in entries)>MAX_ARTIFACT:raise ValueError('ZIP member/expanded-size budget exceeded')
            names=set();libs=[];notices=[]
            for info in entries:
                p=PurePosixPath(info.filename)
                if len(info.filename)>512 or p.is_absolute() or '..' in p.parts or info.filename in names or '\\' in info.filename or info.flag_bits&1:
                    raise ValueError('Unsafe/duplicate/encrypted archive member')
                names.add(info.filename)
                if info.filename.endswith('.so'):
                    with z.open(info) as stream:header=elf_header(stream,info.file_size)
                    libs.append({'member':info.filename,'header':header})
                if any(x in p.name.upper() for x in ['LICENSE','NOTICE','SBOM']):notices.append(info.filename)
            result.update(native_libraries=libs,notice_candidates=notices,
                          gap='AAR/APK header/notice inventory only; no engine ABI bridge/compositor/fidelity/permissions/provenance or expert license clearance inferred')
    else:
        with path.open('rb') as f:result['elf']=elf_header(f,size)
        result['gap']='An ELF is not an Android engine integration. PT_LOAD16K is only one necessary strict-intake condition; test actual APK/NDK/ABI/runtime.'
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('artifact',type=Path);p.add_argument('--report',type=Path)
    a=p.parse_args()
    try:r=inspect(a.artifact)
    except (OSError,ValueError,zipfile.BadZipFile) as e:raise SystemExit('Engine intake REFUSED: '+str(e))
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(r,indent=2)+'\n')
    print(json.dumps(r,indent=2))
if __name__=='__main__':main()
