#!/usr/bin/env python3
"""SDK-free host + Android Studio Rabbit 1 / CI lab runner for Fusion Shell R26."""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def build_env(explicit_tools: Path | None) -> tuple[dict[str, str], Path]:
    env = dict(os.environ)
    tools_dir = explicit_tools or (Path.home() / ".cache/fusion-host-r23")
    py_dirs = [
        str(p)
        for p in (
            Path.home() / ".cache/fusion-host-r23/python",
            Path.home() / ".cache/fusion-r25-reference/python",
            Path.home() / ".cache/fusion-r26-tools",
        )
        if p.is_dir()
    ]
    if py_dirs:
        existing = [env["PYTHONPATH"]] if env.get("PYTHONPATH") else []
        env["PYTHONPATH"] = os.pathsep.join(py_dirs + existing)
    return env, tools_dir


def run_cmd(cmd: list[str], env: dict[str, str]) -> None:
    subprocess.run(cmd, cwd=ROOT, env=env, check=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", choices=["host", "android", "device"])
    parser.add_argument("--tools", type=Path, help="Optional host kotlinc/jdk directory")
    parser.add_argument("--disposable-device", action="store_true")
    args = parser.parse_args()

    env, tools_dir = build_env(args.tools)
    if args.target in ("host", "android"):
        run_cmd([sys.executable, "tools/r25_lab.py", "host", "--tools", str(tools_dir)], env)
        run_cmd([sys.executable, "tools/prove_r26_models.py", "--report", "docs/verification/r26-z3-proofs.json"], env)
        run_cmd([sys.executable, "tools/beauty_audit.py", "--report", "docs/verification/r26-beauty-audit.json"], env)
        run_cmd([sys.executable, "tools/amnesic_posture.py", "--report", "docs/verification/r26-host-amnesic-posture.json"], env)
        run_cmd([sys.executable, "tools/run_r26_contracts.py"], env)

    if args.target == "android":
        run_cmd(
            [
                "./gradlew",
                "--no-daemon",
                ":testing:r25PngContracts",
                ":testing:r26Contracts",
                ":core:fileops:test",
                ":format:poi:test",
                ":feature:office:testDebugUnitTest",
                ":feature:quickpanel:testDebugUnitTest",
                ":feature:photos:testDebugUnitTest",
                ":feature:ime:testDebugUnitTest",
                ":app:assembleProDebug",
                ":app:assemblePlayDebug",
                ":privacy:assembleDebug",
                ":app:lintProDebug",
            ],
            env,
        )
    elif args.target == "device":
        if not args.disposable_device:
            raise SystemExit("Refusing to run destructive device reset without --disposable-device.")
        run_cmd(
            [
                "./gradlew",
                "--no-daemon",
                ":app:pixel6Api34ProDebugAndroidTest",
                "-Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true",
                "-Pandroid.experimental.enableAdditionalTestOutput=true",
                "-Pandroid.testInstrumentationRunnerArguments.class=dev.fusion.shell.R26IntegrationAndAttackDeviceTest",
            ],
            env,
        )


if __name__ == "__main__":
    main()
