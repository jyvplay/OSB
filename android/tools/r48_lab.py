#!/usr/bin/env python3
"""R48 evidence lab. Honest modes only:
  host   : run_r48_contracts + check_r48_reference + r48_class_a_readiness
           + run_r47_contracts + check_r47_reference
           + run_r46_contracts + check_r46_reference
           + run_r45_contracts + check_r45_reference
           + regression contracts across bridged R25Png + R26..R36 + R40 + R44
           + android syntax (17 Compose/Android files incl. Editor, Browser, Vault, Notes, Terminal)
           + native_source_audit --check + layout-bounds + page-matrix + beauty reference
           + R45 touch-target + R45 manifest-scope + amnesic posture
           + Java2D R48 visual preview + pairwise 2D non-overlap/WCAG/360dp audit
           + warm-JVM R48 workbench daemon self-test + HID topology fixture + check_project
           + all 52 Python unittest suites (incl. test_r48_tools).
  device : refuses unless --device-id is supplied; helper text only, zero behavior claimed.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
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
    "test_r45_tools", "test_r46_tools", "test_r47_tools", "test_r48_tools",
    "test_record_sdk_platform", "test_release_gate", "test_requirements",
]


def sh(args: list[str], env: dict[str, str], timeout: int = 720) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=ROOT, env=env, capture_output=True, text=True, timeout=timeout)


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=["host", "device"])
    p.add_argument("--tools", type=Path, default=Path.home() / ".cache/fusion-host-r23")
    p.add_argument("--device-id", default=None)
    p.add_argument("--jobs", type=int, default=3)
    a = p.parse_args()

    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    env["PYTHONPATH"] = os.pathsep.join([str(ROOT / "tools"), env.get("PYTHONPATH", "")])

    if a.mode == "device":
        if not a.device_id:
            print("device mode requires --device-id; refusing to claim device evidence.", file=sys.stderr)
            return 2
        print("device mode only labels manual screenshots; run adb steps yourself. Nothing executed.")
        return 0

    # Phase 1: Settle cached R48/R47/R46/R45 jars, R26 shared dir, & tracked visual previews sequentially first
    warmup_steps = [
        (["python3", "tools/run_r48_contracts.py"], "R48 host contracts (66 new + 7 touched across 50/50 modules, 3,861 assertions)"),
        (["python3", "tools/run_r47_contracts.py"], "R47 host contracts (regression, 2,393 assertions)"),
        (["python3", "tools/run_r46_contracts.py"], "R46 host contracts (regression, 885 assertions)"),
        (["python3", "tools/run_r45_contracts.py"], "R45 host contracts (regression, 508 assertions)"),
        (["python3", "tools/run_r26_contracts.py"], "R26 contracts (shared r26 scratch warmup, 157 assertions)"),
        (["python3", "tools/render_r48_visual_preview.py"], "Java2D R48 workbench visual preview + 172 pairwise-2D/WCAG/360dp checks"),
    ]
    # Phase 2: Disjoint scratch-dir / read-only verification gates
    parallel_steps = [
        (["python3", "tools/check_r48_reference.py"], "R48 differential reference oracle (56 cross-language checks)"),
        (["python3", "tools/r48_class_a_readiness.py"], "R48 Class-A readiness & external hardware gate manifest (0/50 Class A)"),
        (["python3", "tools/native_source_audit.py", "--check"], "native main-source package ownership & dependency edge audit (661 .kt)"),
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
        (["python3", "tools/run_r29_contracts.py"], "R29 contracts (CI bridge regression, 128 assertions)"),
        (["python3", "tools/run_r28_contracts.py"], "R28 contracts (CI bridge regression, 112 assertions)"),
        (["python3", "tools/run_r27_contracts.py"], "R27 contracts (CI bridge regression, 106 assertions)"),
        (["python3", "tools/check_r26_android_syntax.py"], "android syntax gate (17 Compose/Android files)"),
        (["python3", "tools/check_r26_layout_bounds.py"], "layout-bounds self-test"),
        (["python3", "tools/check_r27_pagematrix.py"], "page-matrix self-test"),
        (["python3", "tools/check_r26_beauty_reference.py"], "beauty/contrast reference"),
        (["python3", "tools/check_r45_touch_targets.py"], "touch-target static audit (48dp floor)"),
        (["python3", "tools/check_r45_manifest_scope.py"], "17-manifest air-gap & permission scope gate"),
        (["python3", "tools/amnesic_posture.py"], "amnesic host procfs/sysfs posture audit"),
        (["python3", "tools/fusion_workbench_server.py", "--self-test"], "warm OpenJDK 17 R48 workbench daemon self-test"),
        (["python3", "tools/hid_topology.py", "--verbose", "tools/fixtures/r37/lsusb-verbose.txt",
          "--tree", "tools/fixtures/r37/lsusb-tree.txt"], "HID topology parser (fixture)"),
        (["python3", "tools/check_project.py"], "project structure & SHA-256 census"),
    ]

    ok = True
    lines: list[str] = []
    outputs: list[str] = []

    for args, label in warmup_steps:
        r = sh(args, env, timeout=720)
        outputs.append(f"$ {' '.join(args)}\n{r.stdout}{r.stderr}")
        status = "PASS" if r.returncode == 0 else "FAIL"
        lines.append(f"- {label}: {status}")
        if r.returncode != 0:
            ok = False

    with ThreadPoolExecutor(max_workers=max(1, a.jobs)) as pool:
        step_results = list(pool.map(lambda item: (item[0], item[1], sh(item[0], env, timeout=720)), parallel_steps))
    for args, label, r in step_results:
        outputs.append(f"$ {' '.join(args)}\n{r.stdout}{r.stderr}")
        status = "PASS" if r.returncode == 0 else "FAIL"
        lines.append(f"- {label}: {status}")
        if r.returncode != 0:
            ok = False

    total_unit = 0
    with ThreadPoolExecutor(max_workers=max(1, a.jobs + 1)) as pool:
        unit_results = list(pool.map(lambda t: (t, sh(["python3", "-m", "unittest", t], env, timeout=300)), PYTHON_TESTS))
    for t, r in unit_results:
        outputs.append(f"$ python3 -m unittest {t}\n{r.stdout}{r.stderr}")
        m = re.search(r"Ran (\d+) tests?.*?\n\n(OK|FAILED)", r.stderr + r.stdout, re.S)
        if m and m.group(2) == "OK":
            n = int(m.group(1))
            total_unit += n
            lines.append(f"- python {t}: PASS {n}/{n}")
        else:
            lines.append(f"- python {t}: FAIL")
            ok = False

    tool_versions = "unknown toolchain"
    manifest = a.tools.expanduser().resolve() / "host-tools.json"
    if manifest.is_file():
        versions = json.loads(manifest.read_text()).get("versions", [])
        flat = " / ".join(v.splitlines()[0].replace("info: ", "") for v in versions if v.strip())
        if flat:
            tool_versions = flat

    total_gates = len(warmup_steps) + len(parallel_steps)
    tracked = [
        "testing/src/r48/kotlin/dev/fusion/testing/R48Checks.kt",
        "docs/verification/r48-host-workbench/r48-workbench-preview.png",
        "docs/verification/r48-host-workbench/r48-visual-audit.json",
        "docs/class-a-readiness-r48.json",
    ]
    receipt = [
        f"R48 host lab {'PASS' if ok else 'FAIL'} ({tool_versions}) | {total_gates} gates + {len(PYTHON_TESTS)} unittest suites ({total_unit} checks).",
        f"Harness: {' '.join(sys.argv)}",
        *lines,
        "Hashes (sha256, consumers+artifacts):",
        *[f"  {hashlib.sha256((ROOT / t).read_bytes()).hexdigest()}  {t}" for t in tracked],
    ]
    (ROOT / "testing/build/r48").mkdir(parents=True, exist_ok=True)
    (ROOT / "testing/build/r48/lab-output.txt").write_text("\n".join(outputs), encoding="utf-8")
    doc = ROOT / "docs/R48-RESULTS.md"
    text = doc.read_text(encoding="utf-8") if doc.exists() else "# R48 Verification Results\n\n"
    stamp = "\n".join(receipt) + "\n"
    if "## Lab receipt" not in text:
        doc.write_text(text.rstrip() + "\n\n## Lab receipt\n\n```text\n" + stamp + "```\n", encoding="utf-8")
    print(stamp)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
