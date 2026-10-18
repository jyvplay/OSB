#!/usr/bin/env python3
"""Read-only Linux USB authorization posture. NOT a keylogger/root/firmware detector or hardener.
No root, writes, subprocesses, serials, device names, networking or device deauthorization.
Only root-hub defaults are read. Existing devices and the laptop/hypervisor remain outside this check.
"""
import argparse
import json
from pathlib import Path
import re

ROOT = Path('/sys/bus/usb/devices')
MAX_BUSES = 256
MAX_ATTRIBUTE_BYTES = 32


def parse_authorization(data, *, interface=False):
    if not isinstance(data, bytes) or len(data) > MAX_ATTRIBUTE_BYTES:
        raise ValueError('Unbounded/non-byte authorization attribute')
    value = data.strip()
    allowed = (b'0', b'1') if interface else (b'0', b'1', b'2')
    if value not in allowed:
        raise ValueError('Unknown authorization value')
    return int(value)


def read_attribute(path, *, interface=False):
    try:
        with path.open('rb') as stream:
            data = stream.read(MAX_ATTRIBUTE_BYTES + 1)
        return {'value': parse_authorization(data, interface=interface), 'observation': 'READ'}
    except OSError:
        return {'value': None, 'observation': 'UNKNOWN_UNREADABLE'}
    except ValueError:
        return {'value': None, 'observation': 'UNKNOWN_FORMAT'}


def inspect(root=ROOT):
    # root is parameterized for parser/filesystem unit fixtures, not a claim about fixture hardware.
    report = {
        'schema': 1,
        'kind': 'READ_ONLY_USB_DEFAULTS_NOT_A_SECURITY_VERDICT',
        'inventory': 'UNKNOWN',
        'buses': [],
        'limits': [
            'Only Linux root-hub default authorization attributes; no existing-device audit.',
            'Zero buses or unreadable attributes are UNKNOWN, not an all-clear.',
            'A default of 0 affects NEW devices; it does not revoke already-authorized devices.',
            'Descriptor/vendor/product identities are forgeable; inline passive loggers can be invisible.',
            'Cannot verify firmware, physical custody, host keyloggers, pointer/screen/camera observation or DMA isolation.',
            'No settings changed. USB allowlisting must be configured separately with a tested recovery input/boot path.'
        ]
    }
    try:
        # Stop on overflow even if a hostile/non-sysfs directory contains unbounded unrelated entries.
        buses = []
        seen = 0
        for path in root.iterdir():
            seen += 1
            if seen > 4096:
                report['inventory'] = 'UNKNOWN_ENTRY_LIMIT'; return report
            if re.fullmatch(r'usb[0-9]{1,6}', path.name):
                buses.append(path)
                if len(buses) > MAX_BUSES:
                    report['inventory'] = 'UNKNOWN_BUS_LIMIT'; return report
    except OSError:
        return report
    if not buses:
        return report
    for bus in sorted(buses, key=lambda p: int(p.name[3:])):
        report['buses'].append({
            'bus': bus.name,
            'new_device_default': read_attribute(bus / 'authorized_default'),
            'new_interface_default': read_attribute(bus / 'interface_authorized_default', interface=True)
        })
    report['inventory'] = 'OBSERVED_ROOT_HUB_DEFAULTS_ONLY'
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    print(json.dumps(inspect(), indent=2))


if __name__ == '__main__':
    main()
