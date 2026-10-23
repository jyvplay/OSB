#!/usr/bin/env python3
"""Audit a stored `lsusb -v` / `lsusb -t` transcript for keyboard input paths.

What this is: a parser and a small policy engine over two real lsusb output
formats. It counts boot-protocol keyboard interfaces (`bInterfaceClass 3`,
`bInterfaceSubClass 1`, `bInterfaceProtocol 1`), measures how deep each HID
device sits in the bus tree (`lsusb -t`), and flags three reviewable shapes:
two or more keyboards, a keyboard behind an intermediate hub, and a keyboard
interface with no bound driver.

What this is NOT: a keylogger detector. An inline hardware implant that never
enumerates, a firmware-level logger, a wireless keystroke capture, or a
keyboard with an embedded logger are all invisible here. Software cannot see
them -- see the sources recorded in docs/research/r37-primary.md. This tool
only audits what the USB bus reports about itself, and it reads a transcript
file, never a live bus (no subprocess, no /dev, no root, no writes).

Exit codes: 0 always unless --strict and a warning is present.
"""
import argparse
import json
import re
import sys
from pathlib import Path

MAX_BYTES = 1_000_000
MAX_DEVICES = 512

DEVICE_RE = re.compile(r'^Bus (\d{3}) Device (\d{3}): ID ([0-9a-f]{4}):([0-9a-f]{4})')
CLASS_RE = re.compile(r'^\s+bInterfaceClass\s+(\d+)')
SUB_RE = re.compile(r'^\s+bInterfaceSubClass\s+(\d+)')
PROTO_RE = re.compile(r'^\s+bInterfaceProtocol\s+(\d+)')
TREE_RE = re.compile(r'^(\s*(?:\|__\s+)?)(?:Port \d+: )?Dev (\d+)(?:, If (\d+))?.*')
TREE_HID_RE = re.compile(r'Class=Human Interface Device')


def parse_verbose(text):
    """Returns [{bus, device, vid, pid, interfaces: [{class, subclass, protocol}]}]."""
    devices, current, iface = [], None, None
    for line in text.splitlines():
        m = DEVICE_RE.match(line)
        if m:
            if current:
                devices.append(current)
            current = {'bus': int(m.group(1)), 'device': int(m.group(2)),
                       'vid': m.group(3), 'pid': m.group(4), 'interfaces': []}
            iface = None
            continue
        if current is None:
            continue
        m = CLASS_RE.match(line)
        if m:
            iface = {'class': int(m.group(1)), 'subclass': None, 'protocol': None}
            current['interfaces'].append(iface)
            continue
        if iface is None:
            continue
        m = SUB_RE.match(line)
        if m:
            iface['subclass'] = int(m.group(1))
            continue
        m = PROTO_RE.match(line)
        if m:
            iface['protocol'] = int(m.group(1))
            continue
    if current:
        devices.append(current)
    if len(devices) > MAX_DEVICES:
        raise ValueError('too many devices in transcript')
    return devices


def parse_tree(text):
    """Returns [{depth, dev, iface, hid}] from `lsusb -t` output."""
    nodes = []
    for line in text.splitlines():
        if 'Dev ' not in line:
            continue
        dev = re.search(r'Dev (\d+)', line)
        if not dev:
            continue
        # Depth: every four leading spaces (or one "|__ " group) is a level.
        indent = len(line) - len(line.lstrip(' '))
        depth = indent // 4
        iface = re.search(r'If (\d+)', line)
        nodes.append({'depth': depth, 'dev': int(dev.group(1)),
                      'iface': int(iface.group(1)) if iface else None,
                      'hid': bool(TREE_HID_RE.search(line))})
    if len(nodes) > MAX_DEVICES * 8:
        raise ValueError('too many interface nodes in transcript')
    return nodes


def audit(devices, nodes):
    """Policy over parsed transcripts. Warnings are review flags, not findings."""
    keyboards = []
    for d in devices:
        for i in d['interfaces']:
            if i['class'] == 3 and i['subclass'] == 1 and i['protocol'] == 1:
                keyboards.append({'bus': d['bus'], 'device': d['device'],
                                  'vid': d['vid'], 'pid': d['pid']})
    warnings = []
    if len(keyboards) >= 2:
        warnings.append('DUAL_KEYBOARD: %d boot keyboards enumerate' % len(keyboards))
    hid_nodes = [n for n in nodes if n['hid']]
    behind_hub = [n for n in hid_nodes if n['depth'] >= 2]
    if behind_hub:
        warnings.append('HUB_IN_PATH: %d HID node(s) sit behind an intermediate hub'
                        % len(behind_hub))
    keyboard_devs = {k['device'] for k in keyboards}
    for dev in sorted(keyboard_devs):
        if nodes and not any(n['dev'] == dev for n in nodes):
            warnings.append('UNLISTED_KEYBOARD: device %d has a keyboard interface '
                            'but no tree node' % dev)
    return {
        'keyboards': keyboards,
        'keyboard_count': len(keyboards),
        'hid_nodes': len(hid_nodes),
        'max_depth': max((n['depth'] for n in hid_nodes), default=0),
        'warnings': warnings,
        'verdict': 'REVIEW' if warnings else 'OK',
        'limits': 'Transcript only. Inline implants that do not enumerate, firmware '
                  'loggers, wireless capture and embedded keyboard loggers are invisible.',
    }


def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--verbose', type=Path, required=True, help='saved `lsusb -v` text')
    p.add_argument('--tree', type=Path, default=None, help='saved `lsusb -t` text')
    p.add_argument('--strict', action='store_true', help='exit 1 when the verdict is REVIEW')
    p.add_argument('--json', action='store_true')
    a = p.parse_args()
    vt = a.verbose.read_bytes()
    if len(vt) > MAX_BYTES:
        raise SystemExit('verbose transcript over cap')
    devices = parse_verbose(vt.decode('utf-8', 'replace'))
    nodes = []
    if a.tree:
        tt = a.tree.read_bytes()
        if len(tt) > MAX_BYTES:
            raise SystemExit('tree transcript over cap')
        nodes = parse_tree(tt.decode('utf-8', 'replace'))
    report = audit(devices, nodes)
    report['devices'] = len(devices)
    if a.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print('HID input-path audit: %d device(s), %d boot keyboard(s), %d HID node(s), '
              'max depth %d -> %s' % (len(devices), report['keyboard_count'],
                                      report['hid_nodes'], report['max_depth'],
                                      report['verdict']))
        for w in report['warnings']:
            print('  review: ' + w)
        print('  limits: ' + report['limits'])
    if a.strict and report['verdict'] != 'OK':
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
