#!/usr/bin/env python3
"""Mirror only small actual native test captures through Checks API; no image generation/reference art."""
import argparse
import base64
import hashlib
from pathlib import Path
import re
from png_capture import dimensions
ROOT=Path(__file__).resolve().parents[1]
NAME=re.compile(r'r24-(business|art)-(office_blue|studio_graphite)\.png')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name',required=True);parser.add_argument('--group',required=True,type=int,choices=range(6));args=parser.parse_args()
    if not NAME.fullmatch(args.name):raise ValueError('Unknown capture name')
    # Checks has an observed50-annotation/job cap. Mirror two complementary280px previews only;
    # all four actual PNG files remain in the normal artifact.
    selected={'r24-business-office_blue.png','r24-art-studio_graphite.png'}
    if args.name not in selected:return
    found={}
    for p in (ROOT/'app/build/outputs/managed_device_android_test_additional_output').rglob('r24-*.png'):
        if not NAME.fullmatch(p.name) or p.name!=args.name:continue
        if p.stat().st_size>128*1024:
            print('::notice::Actual native capture exceeds bounded annotation mirror; artifact-only: '+p.name);continue
        data=p.read_bytes();w,h=dimensions(data)
        if w>400 or h>2000:raise ValueError('Unexpected native downsample capture dimensions')
        if p.name in found and found[p.name]!=data:raise ValueError('Ambiguous capture name, no image selected')
        found[p.name]=data
    for name,data in sorted(found.items()):
        encoded=base64.b64encode(data).decode();parts=[encoded[i:i+3700] for i in range(0,len(encoded),3700)]
        if len(parts)>48:raise ValueError('Capture annotation count budget exceeded')
        for i,part in enumerate(parts):
            if i//8!=args.group:continue
            # Actual earlier Checks messages truncated at4096; never "repair" padding or invent missing pixels.
            print(f'::notice title=Actual native screenshot chunk::R24PNG2 {name} {i+1}/{len(parts)} {hashlib.sha256(data).hexdigest()} {part}')
    if not found and args.group==0:print('::notice::No small actual capture exported. Do not infer visual-file review from native functional results.')
if __name__=='__main__':main()
