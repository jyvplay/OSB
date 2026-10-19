#!/usr/bin/env python3
"""Explicit reference-decoder install, not bundled app code. Tested CPython3.11 Linux x86_64 only."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import sys

VERSION='13.0.0'
SHA='a625df9a2a5597fa5e5266e01aefc82604bb85d291e3547981e863560d75da6c'
DEFAULT=Path.home()/'.cache/fusion-r25-reference'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=('install','doctor'));p.add_argument('--directory',type=Path,default=DEFAULT);a=p.parse_args()
    directory=a.directory.expanduser().resolve()
    if a.mode=='doctor':print(json.dumps({'directory':str(directory),'installed':(directory/'python/PIL/__init__.py').is_file(),'scope':'Reference-tool presence only, not execution'}));return
    if platform.system()!='Linux' or platform.machine() not in ('x86_64','AMD64') or sys.version_info[:2]!=(3,11):raise SystemExit('This pinned wheel bootstrap requires CPython3.11/Linux x86_64; no unpinned substitute')
    repo=Path(__file__).resolve().parents[2]
    if directory==repo or repo in directory.parents:raise SystemExit('Keep reference binaries outside the repository')
    directory.mkdir(parents=True,exist_ok=True)
    lock=directory/'requirements.lock';lock.write_text(f'pillow=={VERSION} --hash=sha256:{SHA}\n')
    subprocess.run([sys.executable,'-m','pip','install','--only-binary=:all:','--require-hashes','--target',str(directory/'python'),'--report',str(directory/'install.json'),'-r',str(lock)],check=True)
    record={'package':'Pillow','version':VERSION,'pypi_wheel_sha256':SHA,'scope':'Pinned independent PNG decoder for controlled test files only; not an APK dependency, signed upstream provenance or security/fidelity acceptance'}
    (directory/'receipt.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record))

if __name__=='__main__':main()
