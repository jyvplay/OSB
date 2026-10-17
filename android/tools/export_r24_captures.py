#!/usr/bin/env python3
"""Mirror only small actual native test captures through Checks API; no image generation/reference art."""
import base64
import hashlib
from pathlib import Path
import re
from png_capture import dimensions
ROOT=Path(__file__).resolve().parents[1]
NAME=re.compile(r'r24-(business|art)-(office_blue|studio_graphite)\.png')

def main():
    found={}
    for p in (ROOT/'app/build/outputs/managed_device_android_test_additional_output').rglob('r24-*.png'):
        if not NAME.fullmatch(p.name):continue
        if p.stat().st_size>32768:
            print('::notice::Actual native capture exceeds bounded annotation mirror; artifact-only: '+p.name);continue
        data=p.read_bytes();w,h=dimensions(data)
        if w>400 or h>2000:raise ValueError('Unexpected native downsample capture dimensions')
        if p.name in found and found[p.name]!=data:raise ValueError('Ambiguous capture name, no image selected')
        found[p.name]=data
    for name,data in sorted(found.items()):
        print('::notice title=Actual downsampled native screenshot::R24PNG '+name+' '+hashlib.sha256(data).hexdigest()+' '+base64.b64encode(data).decode())
    if not found:print('::notice::No small actual capture exported. Do not infer visual-file review from native functional results.')
if __name__=='__main__':main()
