#!/usr/bin/env python3
"""Read ONLY exposed Linux USB/Thunderbolt/IOMMU policy counts; not the user's laptop or a safe verdict."""
import argparse
import json
from pathlib import Path
import re
from inspect_usb_posture import inspect as usb

def limited_count(path):
    try:
        n=0
        for _ in path.iterdir():
            n+=1
            if n>1024:return {'count':None,'observation':'UNKNOWN_LIMIT'}
        return {'count':n,'observation':'OBSERVED_DIRECTORY_COUNT_ONLY'}
    except OSError:return {'count':None,'observation':'UNKNOWN_UNREADABLE'}
def attribute(path,allowed):
    try:
        with path.open('rb') as stream:value=stream.read(65)
        if len(value)>64 or value.strip() not in allowed:return 'UNKNOWN_FORMAT'
        return value.strip().decode('ascii')
    except OSError:return 'UNKNOWN_UNREADABLE'
def inspect():
    domains=[];root=Path('/sys/bus/thunderbolt/devices')
    try:
        for i,path in enumerate(root.iterdir()):
            if i>=1024:break
            if re.fullmatch(r'domain[0-9]{1,6}',path.name):
                domains.append({'domain':path.name,'security':attribute(path/'security',{b'none',b'user',b'secure',b'dponly',b'usbonly',b'nopcie'}),
                                'iommu_dma_protection':attribute(path/'iommu_dma_protection',{b'0',b'1'})})
    except OSError:pass
    return {'scope':'CURRENT PROCESS exposed sysfs only; not authenticated host/laptop hardware, no physical trust verdict',
            'settings_changed':False,'usb':usb(),'iommu_groups':limited_count(Path('/sys/kernel/iommu_groups')),'thunderbolt_domains':domains,
            'conclusion':'NO_HARDWARE_KEYLOGGER_FIRMWARE_OR_ANONYMITY_VERDICT',
            'limits':['Defaults affect new devices, not passive implants or already admitted devices.',
                      'Class/vendor/descriptor identity is not authentication.','IOMMU groups/none security alone do not establish all-device DMA protection.',
                      'Do not disable USB without a tested recovery/console path. No privileged policy was applied.',
                      'An Android guest cannot protect a hostile host; APK bytes are not whole-system footprint.']}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--report',type=Path);a=p.parse_args();r=inspect()
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(r,indent=2)+'\n')
    print(json.dumps(r,indent=2))
if __name__=='__main__':main()
