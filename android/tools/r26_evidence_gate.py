#!/usr/bin/env python3
"""Fail-closed exact-candidate R26 native evidence gate layered directly atop frozen R25 identities."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

from r25_evidence_gate import expected as retained_r25

R26_CASES = {
    "dev.fusion.shell.R26IntegrationAndAttackDeviceTest." + name
    for name in [
        "gateIAttackRapidImeToggleAtTwoHundredPercentNeverCrossesSnapshotThreads",
        "gateIAttackPngCopyCancelAndRecreationRefuseStaleExportAndKeepDownloadsSource",
        "crossPlaneDragToWallQuickSavedViewAndArtSilverBusinessLightNativeCaptures",
        "officeWorkbenchGazetteerAtlasAndConsentedEmbedGuardExecuteOnDevice",
        "hardenedScrambledKeypadPermutesPerKeystrokeQuarantinesBurstAndCapturesFrame",
    ]
}


def expected(suite: str) -> set[str]:
    if suite == "r26":
        return set(R26_CASES)
    if suite == "full":
        return retained_r25("full") | R26_CASES
    return retained_r25(suite)


def verify(receipt: dict, candidate: str, suite: str) -> dict:
    if not re.fullmatch("[0-9a-f]{40}", candidate) or receipt.get("candidate") != candidate:
        raise ValueError("Wrong exact source candidate SHA")
    if receipt.get("conclusion") != "success" or receipt.get("device_conclusion") != "success":
        raise ValueError("Workflow or managed-device job did not succeed")
    if suite == "privacy":
        ok_runtime = receipt.get("suite") == "privacy" and receipt.get("platform_api") in (34, 37)
    elif suite == "full":
        ok_runtime = receipt.get("suite") == "full" and receipt.get("platform_api") == 34
    elif suite == "continuation":
        ok_runtime = receipt.get("suite") == "continuation" and receipt.get("platform_api") == 34
    else:
        ok_runtime = receipt.get("suite") == "focused" and receipt.get("platform_api") == 34
    if not ok_runtime:
        raise ValueError("Wrong runtime/suite classification")
    rows = receipt.get("cases", [])
    names = [r["case"] for r in rows]
    if len(set(names)) != len(names) or set(names) != expected(suite):
        raise ValueError(f"Missing/duplicate/undeclared named method: got={len(names)} expected={len(expected(suite))}")
    if any(r.get("result") != "PASS" for r in rows):
        raise ValueError("Every named case must execute PASS; skips/failures rejected")
    if receipt.get("counts") != {"PASS": len(rows), "FAIL": 0, "SKIP": 0}:
        raise ValueError("Native pass count mismatch")
    return {
        "candidate": candidate,
        "suite": suite,
        "api": receipt["platform_api"],
        "passed_cases": len(rows),
        "scope": (
            "Exact named native emulator subset ONLY. Does not promote full-spec unverified gates, "
            "physical OEM hardware, inline hardware keyloggers, or proprietary API licensing."
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("receipt", type=Path)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--suite", choices=["full", "r26", "readiness", "png", "media", "web", "continuation", "privacy"], required=True)
    args = parser.parse_args()
    print(json.dumps(verify(json.loads(args.receipt.read_text()), args.candidate, args.suite), indent=2))


if __name__ == "__main__":
    main()
