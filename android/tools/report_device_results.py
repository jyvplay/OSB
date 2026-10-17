#!/usr/bin/env python3
"""Publish actual AGP JUnit case outcomes. Missing results are not passes; no device capability inference."""
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def collect(root):
    cases={}
    files=sorted(Path(root).rglob('TEST-*.xml'))
    if len(files)>100: raise ValueError('Unexpected result file count')
    for path in files:
        if path.stat().st_size>16*1024*1024: raise ValueError('Result XML exceeds budget')
        tree=ET.parse(path)
        for case in tree.iter('testcase'):
            identity=(case.get('classname',''),case.get('name',''))
            if not all(identity) or any(len(field.encode("utf-8"))>512 for field in identity): raise ValueError('Invalid case identity')
            failed=case.find('failure') is not None or case.find('error') is not None
            skipped=case.find('skipped') is not None
            status='FAIL' if failed else 'SKIP' if skipped else 'PASS'
            row={'class':identity[0],'name':identity[1],'status':status}
            failure=case.find('failure')
            if failure is None: failure=case.find('error')
            if failure is not None:
                detail=failure.get('type','')+' '+failure.get('message','')+'\n'+(failure.text or '')
                def bounded(text,limit):return text.encode('utf-8')[:limit].decode('utf-8','replace')
                if len(detail.encode('utf-8'))>2600:
                    frames='\n'.join(line for line in detail.splitlines() if 'dev.fusion.' in line or 'Caused by:' in line)
                    detail=bounded(detail,900)+'\n[Relevant frames; middle omitted]\n'+bounded(frames,1100)+'\n[Stack tail]\n'+detail.encode('utf-8')[-500:].decode('utf-8','replace')
                row['failure_detail']=detail
            if identity in cases and cases[identity]!=row: raise ValueError('Conflicting duplicate results')
            cases[identity]=row
            if len(cases)>1000: raise ValueError('Too many cases')
    return {'source_commit':os.environ.get('GITHUB_SHA'),'cases':list(cases.values()),
            'counts':{s:sum(row['status']==s for row in cases.values()) for s in ('PASS','FAIL','SKIP')},
            'status':'observed' if cases else 'NO_EXECUTION_EVIDENCE',
            'limits':'Android emulator JUnit results only; not physical-device/AX/security/feature acceptance.'}


def messages(report,mode="all"):
    if mode in ('all','cases'):
        rows=[f"{row['class']}.{row['name']}={row['status']}" for row in report['cases']]
        if not rows: rows=['No executed device test cases found. No pass inferred.']
        chunk=[];size=0
        for row in rows:
            length=len(row.encode('utf-8'))+2
            if chunk and size+length>2500:
                text='Android test results: '+ '; '.join(chunk)
                yield '::notice::'+text.replace('%','%25').replace('\r','%0D').replace('\n','%0A')
                chunk=[];size=0
            chunk.append(row);size+=length
        if chunk:
            text='Android test results: '+ '; '.join(chunk)
            yield '::notice::'+text.replace('%','%25').replace('\r','%0D').replace('\n','%0A')

    for row in report['cases']:
        if mode in ('all','failures') and 'failure_detail' in row:
            text=f"Failure detail {row['class']}.{row['name']}: "+row['failure_detail']
            yield '::notice::'+text.replace('%','%25').replace('\r','%0D').replace('\n','%0A')

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--root',default='app/build/outputs/androidTest-results/managedDevice');parser.add_argument('--mode',choices=['all','cases','failures'],default='all');args=parser.parse_args()
    report=collect(args.root)
    Path('device-results.json').write_text(json.dumps(report,indent=2)+'\n')
    for message in messages(report,args.mode): print(message)
