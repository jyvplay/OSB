#!/usr/bin/env python3
"""Collect read-only observations from ONE explicitly selected, authorized adb device.
No logcat, notification data, contacts, file listings, network addresses or account IDs.
This does not benchmark hardware or mark any spike passed.
"""
import argparse
import datetime
import json
from pathlib import Path
import subprocess

# Values are observations, not capability conclusions (e.g. no DP-out inference).
COMMANDS = {
    'api': ['getprop', 'ro.build.version.sdk'],
    'android_release': ['getprop', 'ro.build.version.release'],
    'security_patch': ['getprop', 'ro.build.version.security_patch'],
    'manufacturer': ['getprop', 'ro.product.manufacturer'],
    'model': ['getprop', 'ro.product.model'],
    'soc_model': ['getprop', 'ro.soc.model'],
    'soc_manufacturer': ['getprop', 'ro.soc.manufacturer'],
    'hardware_chipname': ['getprop', 'ro.hardware.chipname'],
    'page_size': ['getconf', 'PAGESIZE'],
    'display_size': ['wm', 'size'],
    'display_density': ['wm', 'density'],
}

def observation(returncode, output):
    text = output.strip()
    return {'status': 'observed', 'value': text[:16384]} if returncode == 0 and text else {'status': 'unavailable', 'value': None}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='Explicit serial from adb devices; never written into report')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--include-fingerprint', action='store_true', help='Opt in to firmware fingerprint for the private reference-device ledger')
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args()
    if args.output.exists(): parser.error('Output exists; choose a new filename (no silent overwrite)')
    ready = subprocess.run([args.adb, '-s', args.serial, 'get-state'], capture_output=True, text=True, timeout=15)
    if ready.returncode != 0 or ready.stdout.strip() != 'device': parser.error('Selected device is not authorized/connected; no report written')
    commands = dict(COMMANDS)
    if args.include_fingerprint: commands['firmware_fingerprint'] = ['getprop', 'ro.build.fingerprint']
    report = {'schema': 1, 'collected_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'scope': 'read-only adb observations; not a benchmark, Android test pass, or spike acceptance',
              'identifiers': 'adb serial omitted; firmware fingerprint opt-in only', 'observations': {}}
    for key, command in commands.items():
        try:
            result = subprocess.run([args.adb, '-s', args.serial, 'shell', *command], capture_output=True, text=True, timeout=15)
            report['observations'][key] = observation(result.returncode, result.stdout)
        except subprocess.TimeoutExpired:
            report['observations'][key] = {'status': 'timeout', 'value': None}
    # Exclusive creation also handles an output created between validation and writing.
    with args.output.open('x', encoding='utf-8') as out: json.dump(report, out, indent=2)
    print(f'Wrote {args.output}. Review before sharing; no tests or device grades were inferred.')

if __name__ == '__main__': main()
