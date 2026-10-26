#!/usr/bin/env python3
"""R31 manifest-scope gate: the app manifest must keep its exact R28 component/permission scope.

R31 authors no Android components at all (10 host-only kernels), so the
manifest must be unchanged: 2 activities, 1 receiver, 0 services/providers,
2 permissions. Static XML evidence only; merged-manifest truth needs an AGP
build.
"""
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = '{http://schemas.android.com/apk/res/android}'

def main():
    root = ET.fromstring((ROOT / 'app/src/main/AndroidManifest.xml').read_text())
    perms = sorted(e.attrib[NS + 'name'].split('.')[-1]
                   for e in root.findall('uses-permission'))
    assert perms == ['INTERNET', 'READ_MEDIA_IMAGES'], perms
    app = root.find('application')
    acts = sorted(e.attrib[NS + 'name'] for e in app.findall('activity'))
    recvs = sorted(e.attrib[NS + 'name'] for e in app.findall('receiver'))
    assert acts == ['.MainActivity', '.PngCopyActivity'], acts
    assert recvs == ['.UsbWatchReceiver'], recvs
    assert app.findall('service') == [] and app.findall('provider') == []
    recv = app.find('receiver')
    assert recv.attrib[NS + 'exported'] == 'true'
    actions = sorted(a.attrib[NS + 'name'] for f in recv.findall('intent-filter') for a in f.findall('action'))
    assert actions == ['android.hardware.usb.action.USB_DEVICE_ATTACHED',
                       'android.hardware.usb.action.USB_DEVICE_DETACHED'], actions
    print('R31 manifest-scope PASS: 2 activities, 1 receiver, 0 services/providers, '
          'INTERNET + READ_MEDIA_IMAGES only (static XML; merged truth needs AGP).')

if __name__ == '__main__':
    main()
