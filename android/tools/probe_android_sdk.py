#!/usr/bin/env python3
"""Non-installing real sdkmanager stable/preview catalog probe, NOT Android build/API/GUI acceptance.
The SDK tool may refresh its own metadata/cache; no install/license/hardening command is invoked.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
from bounded_process import capture


def platforms(text):
    if len(text) > 4 * 1024 * 1024: raise ValueError('Catalog byte/character budget exceeded')
    result = set()
    for line in text.splitlines():
        name = line.split('|', 1)[0].strip()
        if not name.startswith('platforms;'): continue
        if not re.fullmatch(r'platforms;android-[A-Za-z0-9._-]{1,64}', name):
            raise ValueError('Unexpected platform package syntax')
        result.add(name)
        if len(result) > 128: raise ValueError('Platform count budget exceeded')
    return sorted(result)


def base37(names):
    # Real sdkmanager emits android-37.0. Do not confuse a missing old spelling with every SDK37 package absent.
    return sorted(set(names) & {'platforms;android-37', 'platforms;android-37.0'})


def probe(manager):
    manager = Path(manager).expanduser().resolve()
    report = {'kind': 'Real SDK catalog only; NOT compilation/runtime/Rabbit GUI acceptance',
              'manager': str(manager), 'version': None, 'catalogs': [], 'sdk37_in_any_observed_catalog': None,
              'package_policy': 'Base37 accepts only released android-37 or android-37.0; not higher minors/beta/canary',
              'limits': 'Two real non-installing list commands;120s/4MiB stdout/256KiB stderr each; no fake SDK or license acceptance.'}
    if not manager.is_file():
        report['error'] = 'sdkmanager file absent; availability UNKNOWN'
        return report
    try:
        report['version'] = capture([manager, '--version'], timeout=30, max_output=8192).decode('utf-8', errors='strict').strip()
    except (OSError, RuntimeError, ValueError, TimeoutError, subprocess.SubprocessError) as error:
        report['error'] = type(error).__name__ + ': ' + str(error)[:1000]
        return report
    for channel in (0, 3):
        row = {'channel': channel, 'platforms': None, 'sdk37_exact': None}
        try:
            raw = capture([manager, '--list', '--channel=' + str(channel)], timeout=120, max_output=4*1024*1024)
            row['platforms'] = platforms(raw.decode('utf-8', errors='strict'))
            row['sdk37_exact'] = 'platforms;android-37' in row['platforms']
            row['sdk37_base_packages'] = base37(row['platforms'])
        except (OSError, RuntimeError, ValueError, TimeoutError, subprocess.SubprocessError) as error:
            row['error'] = type(error).__name__ + ': ' + str(error)[:1000]
        report['catalogs'].append(row)
    observed = [bool(row['sdk37_base_packages']) if row['platforms'] is not None else None for row in report['catalogs']]
    report['sdk37_in_any_observed_catalog'] = True if True in observed else (None if None in observed else False)
    return report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdkmanager', type=Path)
    p.add_argument('--report', type=Path, default=Path('testing/build/r23/sdk-catalog.json'))
    a = p.parse_args()
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '/SDK-NOT-CONFIGURED'
    manager = a.sdkmanager or Path(sdk)/'cmdline-tools/latest/bin'/('sdkmanager.bat' if os.name == 'nt' else 'sdkmanager')
    report = probe(manager)
    a.report.parent.mkdir(parents=True, exist_ok=True)
    a.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    # Absence from a successful catalog is an observation, not a fake build pass. UNKNOWN exits nonzero.
    raise SystemExit(1 if report['sdk37_in_any_observed_catalog'] is None else 0)


if __name__ == '__main__': main()
