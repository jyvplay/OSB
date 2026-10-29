#!/usr/bin/env python3
"""R53 fast host evidence lab (`host` vs `device` modes, strict Runtime Honesty)."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import bootstrap_host_tools


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=["host", "device"])
    p.add_argument("--tools", type=Path, default=Path.home() / ".cache/fusion-host-r23")
    p.add_argument("--device-id", default=None)
    a = p.parse_args()

    if a.mode == "device":
        if not a.device_id:
            print("device mode requires --device-id; refusing to claim device evidence.", file=sys.stderr)
            return 2
        print("device mode only labels manual screenshots; run adb steps yourself. Nothing executed.")
        return 0

    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    env["PYTHONPATH"] = os.pathsep.join([str(ROOT / "tools"), env.get("PYTHONPATH", "")])

    gates = [
        ["python3", "tools/run_r53_contracts.py"],
        ["python3", "tools/check_r53_reference.py"],
        ["python3", "tools/r53_class_a_readiness.py"],
        ["python3", "tools/render_r53_visual_preview.py"],
        ["python3", "tools/check_r26_android_syntax.py"],
        ["python3", "tools/fusion_workbench_server.py", "--self-test"],
        ["python3", "tools/check_project.py"],
        ["python3", "-m", "unittest", "discover", "-s", "tools", "-p", "test_*.py"],
    ]
    for cmd in gates:
        proc = subprocess.run(cmd, cwd=ROOT, env=env, check=False)
        if proc.returncode != 0:
            return proc.returncode
    print("R53_HOST_LAB_ALL_GATES_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
