#!/usr/bin/env python3
"""Independent bounded readback of ACTUAL engine outputs; no mock outputs are created."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile
import zlib
from pypdf import PdfReader

MARKER='OSB R25 actual LibreOfficeKit canary'

def verify(directory):
    directory=Path(directory);runtime=json.loads((directory/'runtime.json').read_text())
    if runtime.get('engineExecuted') is not True or runtime.get('integratedAndroid') or runtime.get('legalAccepted'):raise ValueError('Not a successful scoped actual engine run')
    for name in ['output.odt','output.pdf','tile.raw','detail.raw']:
        if not 1<=(directory/name).stat().st_size<=2*1024*1024:raise ValueError('Actual output outside byte budget')
    with zipfile.ZipFile(directory/'output.odt') as z:
        infos=z.infolist()
        if len(infos)>256 or sum(i.file_size for i in infos)>16*1024*1024:raise ValueError('ODT readback budget')
        if z.read('mimetype')!=b'application/vnd.oasis.opendocument.text':raise ValueError('Not ODT')
        content=ET.fromstring(z.read('content.xml'))
        if MARKER not in ''.join(content.itertext()):raise ValueError('Engine ODT lost fixture text')
    reader=PdfReader(directory/'output.pdf',strict=True)
    text='\n'.join(p.extract_text() for p in reader.pages)
    if not 1<=len(reader.pages)<=8 or MARKER not in text:raise ValueError('Independent PDF readback failed: pages='+str(len(reader.pages))+' text='+repr(text[:300]))
    observed=runtime['observation'];w=observed['tileWidth'];h=observed['tileHeight'];mode=observed['tileMode']
    if (w,h)!=(256,320) or mode not in (0,1):raise ValueError('Unsupported actual tile geometry/mode')
    def frame(name,require_text):
        pixels=bytearray((directory/(name+'.raw')).read_bytes())
        if len(pixels)!=w*h*4:raise ValueError('Wrong actual tile byte count')
        if mode==1:
            for i in range(0,len(pixels),4):pixels[i],pixels[i+2]=pixels[i+2],pixels[i]
        opaque=sum(pixels[i+3]>240 for i in range(0,len(pixels),4))
        dark=sum(max(pixels[i:i+3])<100 and pixels[i+3]>240 for i in range(0,len(pixels),4))
        light=sum(min(pixels[i:i+3])>230 and pixels[i+3]>240 for i in range(0,len(pixels),4))
        def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
        rows=b''.join(b'\x00'+pixels[y*w*4:(y+1)*w*4] for y in range(h))
        png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b'')
        (directory/(name+'.png')).write_bytes(png) # Actual raw engine pixels, even when the visual gate fails.
        if opaque<1000 or light<1000 or (require_text and dark<10):raise ValueError('Actual '+name+' contrast insufficient: opaque='+str(opaque)+' dark='+str(dark)+' light='+str(light))
        return {'opaque':opaque,'dark':dark,'light':light}
    overview=frame('tile',False)
    region=observed['detailRegionTwips']
    if region!=[0,0,min(observed['documentWidthTwips'],3840),min(observed['documentHeightTwips'],4800)]:raise ValueError('Wrong actual detail tile region')
    detail=frame('detail',True)
    report={'source_candidate':runtime.get('sourceCandidate'),'archive_sha256':runtime['archiveSha256'],
            'actual_browser':runtime['browserVersion'],'actual_linear_memory_bytes':observed['heapBytes'],
            'document_twips':[observed['documentWidthTwips'],observed['documentHeightTwips']],
            'pdf_pages':len(reader.pages),'pixel_samples':{'whole_page':overview,'detail_96dpi':detail},'detail_region_twips':region,
            'outputs':{name:{'bytes':(directory/name).stat().st_size,'sha256':hashlib.sha256((directory/name).read_bytes()).hexdigest()} for name in ['output.odt','output.pdf','tile.raw','tile.png','detail.raw','detail.png']},
            'accepted_scope':'One real controlled Writer fixture: load, tile, saveAs ODT/PDF; independent ODT/XML + pypdf readback. NOT Android embedding, generic Office fidelity, malicious-document safety, upstream reproducibility or license closure.',
            'integrated_android':False,'full_office_accepted':False,'legal_accepted':False}
    (directory/'verified.json').write_text(json.dumps(report,indent=2)+'\n')
    return report

if __name__=='__main__':
    try:result=verify(sys.argv[1])
    except Exception as error:
        message='Actual independent engine readback FAILED: '+type(error).__name__+': '+str(error)
        print('::error::'+message[:2500].replace('%','%25').replace('\n','%0A'))
        raise SystemExit(1)
    text=json.dumps(result,separators=(',',':'))
    if len(text.encode())>3500:raise ValueError('Observation notice budget exceeded')
    print('::notice::R25LOKWASM1 '+text)
