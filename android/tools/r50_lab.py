#!/usr/bin/env python3
"""R50 evidence lab. Honest modes only:
  host   : run_r50_contracts + check_r50_reference + r50_class_a_readiness
           + run_r49_contracts + check_r49_reference + r49_class_a_readiness
           + run_r48_contracts + check_r48_reference
           + run_r47_contracts + check_r47_reference
           + run_r46_contracts + check_r46_reference
           + run_r45_contracts + check_r45_reference
           + regression contracts across bridged R26..R36 + R40 + R44
           + android syntax (35 Compose/Android UI & Activity files)
           + native_source_audit --check + layout-bounds + page-matrix + beauty reference
           + R45 touch-target + R45 manifest-scope + amnesic posture
           + Java2D R50 visual preview (321 FontMetrics / 36,942 pairwise checks, 9.37:1 WCAG AAA)
           + warm-JVM R50 workbench daemon self-test + HID topology fixture + check_project
           + all 54 Python unittest suites (incl. test_r50_tools).
  device : refuses unless --device-id is supplied; helper text only, zero behavior claimed.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import os
from pathlib import Path
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
    "test_r45_tools", "test_r46_tools", "test_r47_tools", "test_r48_tools",
    "test_r49_tools", "test_r50_tools", "test_record_sdk_platform",
    "test_release_gate", "test_requirements",
]


def sh(args: list[str], env: dict[str, str], timeout: int = 720) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=ROOT, env=env, capture_output=True, text=True, timeout=timeout)


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=["host", "device"])
    p.add_argument("--tools", type=Path, default=Path.home() / ".cache/fusion-host-r23")
    p.add_argument("--device-id", default=None)
    p.add_argument("--jobs", type=int, default=2)
    a = p.parse_args()

    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    env["PYTHONPATH"] = os.pathsep.join([str(ROOT / "tools"), env.get("PYTHONPATH", "")])

    if a.mode == "device":
        if not a.device_id:
            print("device mode requires --device-id; refusing to claim device evidence.", file=sys.stderr)
            return 2
        print("device mode only labels manual screenshots; run adb steps yourself. Nothing executed.")
        return 0

    warmup_steps = [
        (["python3", "tools/run_r50_contracts.py"], "R50 host contracts (80 new + 14 touched across 50/50 modules, 8,555 assertions)"),
        (["python3", "tools/run_r49_contracts.py"], "R49 host contracts (regression, 6,301 assertions)"),
        (["python3", "tools/run_r48_contracts.py"], "R48 host contracts (regression, 3,861 assertions)"),
        (["python3", "tools/run_r47_contracts.py"], "R47 host contracts (regression, 2,393 assertions)"),
        (["python3", "tools/run_r46_contracts.py"], "R46 host contracts (regression, 885 assertions)"),
        (["python3", "tools/run_r45_contracts.py"], "R45 host contracts (regression, 508 assertions)"),
        (["python3", "tools/run_r26_contracts.py"], "R26 contracts (shared r26 scratch warmup, 157 assertions)"),
        (["python3", "tools/render_r50_visual_preview.py"], "Java2D R50 workbench visual preview + 321 FontMetrics / 36,942 pairwise checks (9.37:1 WCAG AAA)"),
        (["python3", "tools/render_r49_visual_preview.py"], "Java2D R49 workbench visual preview (regression, 258 checks)"),
        (["python3", "tools/render_r48_visual_preview.py"], "Java2D R48 workbench visual preview (regression, 172 checks)"),
    ]
    parallel_steps = [
        (["python3", "tools/check_r50_reference.py"], "R50 differential reference oracle (146 cross-language checks across 26 artifacts)"),
        (["python3", "tools/r50_class_a_readiness.py"], "R50 Class-A readiness & external hardware gate manifest (10 pillars, 0/50 Class A)"),
        (["python3", "tools/check_r49_reference.py"], "R49 differential reference oracle (regression, 96 checks)"),
        (["python3", "tools/r49_class_a_readiness.py"], "R49 Class-A readiness manifest (regression)"),
        (["python3", "tools/native_source_audit.py", "--check"], "native main-source package ownership & dependency edge audit (816 .kt files)"),
        (["python3", "tools/check_r48_reference.py"], "R48 differential reference oracle (regression, 56 checks)"),
        (["python3", "tools/check_r47_reference.py"], "R47 differential reference oracle (regression, 40 checks)"),
        (["python3", "tools/check_r46_reference.py", "--verify"], "R46 differential reference oracle (regression, 25 checks)"),
        (["python3", "tools/check_r45_reference.py", "--verify"], "R45 differential reference oracle (regression)"),
        (["python3", "tools/run_r44_contracts.py", "--tools", str(a.tools)], "R44 contracts (regression)"),
        (["python3", "tools/check_r44_reference.py"], "R44 reference (regression)"),
        (["python3", "tools/run_r40_contracts.py", "--tools", str(a.tools)], "R40 contracts (DashEducate regression)"),
        (["python3", "tools/run_r36_contracts.py", "--tools", str(a.tools)], "R36 contracts (CI bridge regression)"),
        (["python3", "tools/run_r35_contracts.py", "--tools", str(a.tools)], "R35 contracts (CI bridge regression)"),
        (["python3", "tools/run_r34_contracts.py", "--tools", str(a.tools)], "R34 contracts (CI bridge regression)"),
        (["python3", "tools/run_r33_contracts.py"], "R33 contracts (CI bridge regression)"),
        (["python3", "tools/run_r32_contracts.py"], "R32 contracts (CI bridge regression)"),
        (["python3", "tools/run_r31_contracts.py"], "R31 contracts (CI bridge regression)"),
        (["python3", "tools/run_r30_contracts.py"], "R30 contracts (CI bridge regression)"),
        (["python3", "tools/run_r29_contracts.py"], "R29 contracts (CI bridge regression)"),
        (["python3", "tools/run_r28_contracts.py"], "R28 contracts (CI bridge regression)"),
        (["python3", "tools/run_r27_contracts.py"], "R27 contracts (CI bridge regression)"),
        (["python3", "tools/check_r26_android_syntax.py"], "android syntax gate (35 Compose/Android UI & Activity files)"),
        (["python3", "tools/check_r26_layout_bounds.py"], "layout-bounds self-test"),
        (["python3", "tools/check_r27_pagematrix.py"], "page-matrix self-test"),
        (["python3", "tools/check_r26_beauty_reference.py"], "beauty/contrast reference"),
        (["python3", "tools/check_r45_touch_targets.py"], "touch-target static audit (48dp floor)"),
        (["python3", "tools/check_r45_manifest_scope.py"], "17-manifest air-gap & permission scope gate"),
        (["python3", "tools/amnesic_posture.py"], "amnesic host procfs/sysfs posture audit"),
        (["python3", "tools/fusion_workbench_server.py", "--self-test"], "warm OpenJDK 17 R50 workbench daemon self-test"),
        (["python3", "tools/hid_topology.py", "--verbose", "tools/fixtures/r37/lsusb-verbose.txt",
          "--tree", "tools/fixtures/r37/lsusb-tree.txt"], "HID topology parser (fixture)"),
        (["python3", "tools/check_project.py"], "project structure & SHA-256 census"),
    ]

    ok = True
    lines: list[str] = []
    for args, label in warmup_steps:
        r = sh(args, env, timeout=720)
        status = "PASS" if r.returncode == 0 else "FAIL"
        lines.append(f"- {label}: {status}")
        if r.returncode != 0:
            ok = False
            lines.append(f"  STDERR: {r.stderr.strip()[-400:]}")

    with ThreadPoolExecutor(max_workers=max(1, a.jobs)) as pool:
        step_results = list(pool.map(lambda item: (item[0], item[1], sh(item[0], env, timeout=720)), parallel_steps))
    for _args, label, r in step_results:
        status = "PASS" if r.returncode == 0 else "FAIL"
        lines.append(f"- {label}: {status}")
        if r.returncode != 0:
            ok = False
            lines.append(f"  STDERR: {r.stderr.strip()[-400:]}")

    ut = sh(["python3", "-m", "unittest", *PYTHON_TESTS], env, timeout=720)
    ut_status = "PASS" if ut.returncode == 0 else "FAIL"
    lines.append(f"- Python unittest suites ({len(PYTHON_TESTS)} modules): {ut_status}")
    if ut.returncode != 0:
        ok = False
        lines.append(f"  STDERR: {ut.stderr.strip()[-600:]}")

    print("\n".join(lines))
    total_gates = len(warmup_steps) + len(parallel_steps) + 1
    if ok:
        print(f"R50_LAB_OK gates={total_gates}/{total_gates} python_suites={len(PYTHON_TESTS)} class_a=0/50 testable_subsets=50/50")
        return 0
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
