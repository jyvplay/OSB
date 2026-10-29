#!/usr/bin/env python3
"""R45 evidence lab. Honest modes only:
  host   : run_r45_contracts + check_r45_reference + regression contracts (R44..R34)
           + android syntax + layout-bounds + page-matrix + beauty reference
           + R45 touch-target + R45 manifest-scope + amnesic posture + Java2D visual preview
           + HID topology fixture + check_project + all Python unittest suites (incl. test_r45_tools).
  device : refuses unless --device-id is supplied; helper text only, zero behavior claimed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import bootstrap_host_tools

PYTHON_TESTS = [
    "test_bounded_process", "test_ci_annotations", "test_collect_native_evidence",
    "test_device_results", "test_engine_intake", "test_gradle_distribution",
    "test_module_audit", "test_module_testability", "test_native_lab",
    "test_native_source_audit", "test_pilot_reporting", "test_png_capture",
    "test_probe_android_sdk", "test_r17_evidence", "test_r18_evidence",
    "test_r19_evidence", "test_r20_evidence", "test_r21_evidence",
    "test_r22_evidence", "test_r23_evidence", "test_r23_input_tools",
    "test_r23_lab", "test_r24_evidence", "test_r24_runner", "test_r24c_tools",
    "test_r25_tools", "test_r26_tools", "test_r27_tools", "test_r28_tools",
    "test_r29_tools", "test_r30_tools", "test_r31_tools", "test_r32_tools",
    "test_r33_tools", "test_r34_tools", "test_r35_tools", "test_r36_tools",
    "test_r37_tools", "test_r38_tools", "test_r39_tools", "test_r40_tools",
    "test_r41_tools", "test_r42_tools", "test_r43_tools", "test_r44_tools",
    "test_r45_tools", "test_record_sdk_platform", "test_release_gate",
    "test_requirements",
]


def sh(args: list[str], env: dict[str, str], timeout: int = 300) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=ROOT, env=env, capture_output=True, text=True, timeout=timeout)


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=["host", "device"])
    p.add_argument("--tools", type=Path, default=Path.home() / ".cache/fusion-host-r23")
    p.add_argument("--device-id", default=None)
    a = p.parse_args()

    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    env["PYTHONPATH"] = os.pathsep.join([str(ROOT / "tools"), env.get("PYTHONPATH", "")])

    if a.mode == "device":
        if not a.device_id:
            print("device mode requires --device-id; refusing to claim device evidence.", file=sys.stderr)
            return 2
        print("device mode only labels manual screenshots; run adb steps yourself. Nothing executed.")
        return 0

    steps = [
        (["python3", "tools/run_r45_contracts.py"], "R45 host contracts (33 kernels + 1 touched)"),
        (["python3", "tools/check_r45_reference.py", "--verify"], "R45 differential reference oracle"),
        (["python3", "tools/run_r44_contracts.py", "--tools", str(a.tools)], "R44 contracts (regression)"),
        (["python3", "tools/check_r44_reference.py"], "R44 reference (regression)"),
        (["python3", "tools/run_r40_contracts.py", "--tools", str(a.tools)], "R40 contracts (DashEducate regression)"),
        (["python3", "tools/run_r36_contracts.py", "--tools", str(a.tools)], "R36 contracts (CI bridge regression)"),
        (["python3", "tools/run_r35_contracts.py", "--tools", str(a.tools)], "R35 contracts (CI bridge regression)"),
        (["python3", "tools/run_r34_contracts.py", "--tools", str(a.tools)], "R34 contracts (CI bridge regression)"),
        (["python3", "tools/check_r26_android_syntax.py"], "android syntax gate"),
        (["python3", "tools/check_r26_layout_bounds.py"], "layout-bounds self-test"),
        (["python3", "tools/check_r27_pagematrix.py"], "page-matrix self-test"),
        (["python3", "tools/check_r26_beauty_reference.py"], "beauty/contrast reference"),
        (["python3", "tools/check_r45_touch_targets.py"], "R45 touch-target static audit"),
        (["python3", "tools/check_r45_manifest_scope.py"], "R45 17-manifest scope gate"),
        (["python3", "tools/amnesic_posture.py"], "amnesic host procfs/sysfs posture audit"),
        (["python3", "tools/render_r45_visual_preview.py"], "Java2D workbench visual preview + WCAG audit"),
        (["python3", "tools/hid_topology.py", "--verbose", "tools/fixtures/r37/lsusb-verbose.txt",
          "--tree", "tools/fixtures/r37/lsusb-tree.txt"], "HID topology parser (fixture)"),
        (["python3", "tools/check_project.py"], "project structure & SHA-256 census"),
    ]

    ok = True
    lines: list[str] = []
    outputs: list[str] = []
    for args, label in steps:
        r = sh(args, env, timeout=600)
        outputs.append(f"$ {' '.join(args)}\n{r.stdout}{r.stderr}")
        status = "PASS" if r.returncode == 0 else "FAIL"
        lines.append(f"- {label}: {status}")
        if r.returncode != 0:
            ok = False

    for t in PYTHON_TESTS:
        r = sh(["python3", "-m", "unittest", t], env, timeout=300)
        outputs.append(f"$ python3 -m unittest {t}\n{r.stdout}{r.stderr}")
        m = re.search(r"Ran (\d+) tests?.*?\n\n(OK|FAILED)", r.stderr + r.stdout, re.S)
        lines.append(
            f"- python {t}: {'PASS ' + m.group(1) + '/' + m.group(1) if m and m.group(2) == 'OK' else 'FAIL'}"
        )
        if not m or m.group(2) != "OK":
            ok = False

    tool_versions = "unknown toolchain"
    manifest = a.tools.expanduser().resolve() / "host-tools.json"
    if manifest.is_file():
        versions = json.loads(manifest.read_text()).get("versions", [])
        flat = " / ".join(v.splitlines()[0].replace("info: ", "") for v in versions if v.strip())
        if flat:
            tool_versions = flat

    tracked = [
        "testing/src/r45/kotlin/dev/fusion/testing/R45Checks.kt",
        "docs/verification/r45-host-workbench/r45-workbench-preview.png",
    ]
    receipt = [
        f"R45 host lab {'PASS' if ok else 'FAIL'} ({tool_versions}).",
        f"Harness: {' '.join(sys.argv)}",
        *lines,
        "Hashes (sha256, consumers+artifacts):",
        *[f"  {hashlib.sha256((ROOT / t).read_bytes()).hexdigest()}  {t}" for t in tracked],
    ]
    (ROOT / "testing/build/r45").mkdir(parents=True, exist_ok=True)
    (ROOT / "testing/build/r45/lab-output.txt").write_text("\n".join(outputs))
    doc = ROOT / "docs/R45-RESULTS.md"
    text = doc.read_text() if doc.exists() else "# R45 Verification Results\n\n"
    stamp = "\n".join(receipt) + "\n"
    if "## Lab receipt" not in text:
        doc.write_text(text.rstrip() + "\n\n## Lab receipt\n\n```text\n" + stamp + "```\n")
    print(stamp)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
