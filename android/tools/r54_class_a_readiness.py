#!/usr/bin/env python3
"""Verify docs/class-a-readiness-r54.json: host-verified H+ scope vs the remaining external Class-A gates."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_PATH = ROOT / "docs/class-a-readiness-r54.json"


def main() -> int:
    if not OUT_PATH.is_file():
        raise SystemExit(f"missing {OUT_PATH}")
    data = json.loads(OUT_PATH.read_text(encoding="utf-8"))
    verdict = data["runtime_honesty_verdict"]
    assert data["schema"] == "fusion.class_a_readiness.v6"
    assert data["round"] == "R54"
    assert verdict["can_claim_full_class_a_in_host_sandbox"] is False
    assert verdict["full_spec_modules_accepted"] == 0
    assert verdict["full_spec_modules_total"] == 50
    assert verdict["full_spec_modules_percentage"] == 0.0
    assert verdict["testable_subset_modules_verified"] == 50
    assert verdict["testable_subset_modules_total"] == 50
    assert verdict["testable_subset_modules_percentage"] == 100.0
    assert verdict["r54_new_pure_jvm_kernels"] == 22
    assert verdict["r54_new_compose_ui_files"] == 3
    assert verdict["r54_hardware_vectors_total"] == 35
    assert verdict["r54_recall_methods"] == 7
    assert len(verdict["unavailable_gates"]) >= 7
    assert len(data["pillars"]) == 11
    for pillar in data["pillars"]:
        assert pillar["grade"] in {"H+", "H-", "H∂"}
        assert pillar["host_verified"] and pillar["host_kernels"] >= 1
    OUT_PATH.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"R54_CLASS_A_READINESS_OK pillars={len(data['pillars'])} full_class_a=0/50 testable_subsets=50/50")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
