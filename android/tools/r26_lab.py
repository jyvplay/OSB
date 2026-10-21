#!/usr/bin/env python3
"""No-Studio R26 entry. Host execution is never substituted for Android/native gates."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=('doctor', 'host', 'android', 'device'))
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    p.add_argument('--suite', choices=('r26', 'full'), default='r26')
    p.add_argument('--disposable', action='store_true')
    a = p.parse_args()
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    tools = a.tools.expanduser().resolve()

    def host_env():
        return bootstrap_host_tools.environment(tools)

    def run(command, env=None):
        subprocess.run(command, cwd=ROOT, env=env or os.environ.copy(), check=True)

    if a.mode == 'doctor':
        run([sys.executable, 'tools/r25_lab.py', 'doctor', '--tools', str(tools)])
        print(json_doctor(tools))
        return
    if a.mode == 'host':
        env = host_env()
        run([sys.executable, 'tools/run_r26_contracts.py', '--tools', str(tools)], env=env)
        run([sys.executable, 'tools/check_r26_beauty_reference.py'], env=env)
        run([sys.executable, 'tools/check_r26_layout_bounds.py'], env=env)
        run([sys.executable, 'tools/check_r26_android_syntax.py', '--tools', str(tools)], env=env)
        run([sys.executable, '-m', 'unittest', 'test_r26_tools', '-v'], env={**env, 'PYTHONPATH': str(ROOT / 'tools')})
        return
    # Native compile/device execution delegates to the recorded Gradle suites; host cache is refused there.
    cmd = [sys.executable, 'tools/r25_lab.py', a.mode]
    if a.mode == 'device':
        cmd += ['--suite', 'full' if a.suite == 'full' else 'readiness']
        if a.disposable:
            cmd += ['--disposable']
    run(cmd)

def json_doctor(tools):
    import json
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    return json.dumps(bootstrap_host_tools.doctor(tools), indent=2)

if __name__ == '__main__':
    main()
