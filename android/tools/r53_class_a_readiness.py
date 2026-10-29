#!/usr/bin/env python3
"""Verify or refresh docs/class-a-readiness-r53.json enumerating exact host-verified H+ scope vs remaining external Class-A hardware/SDK gates across 10 pillars."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_PATH = ROOT / "docs/class-a-readiness-r53.json"


def main() -> int:
    if not OUT_PATH.is_file():
        raise SystemExit(f"missing {OUT_PATH}")
    data = json.loads(OUT_PATH.read_text(encoding="utf-8"))
    verdict = data["runtime_honesty_verdict"]
    assert data["schema"] == "fusion.class_a_readiness.v5"
    assert data["round"] == "R53"
    assert verdict["can_claim_full_class_a_in_host_sandbox"] is False
    assert verdict["full_spec_modules_accepted"] == 0
    assert verdict["full_spec_modules_total"] == 50
    assert verdict["full_spec_modules_percentage"] == 0.0
    assert verdict["testable_subset_modules_verified"] == 50
    assert verdict["testable_subset_modules_total"] == 50
    assert verdict["testable_subset_modules_percentage"] == 100.0
    assert verdict["r53_new_pure_jvm_kernels"] == 96
    assert verdict["r53_new_compose_ui_files"] == 7
    assert verdict["r53_touched_production_files"] == 19
    assert verdict["r53_total_production_files_on_disk"] == 1101
    assert verdict["android_compose_ast_syntax_verified_files"] == 59
    assert len(data["pillars"]) == 10
    OUT_PATH.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"R53_CLASS_A_READINESS_OK pillars={len(data['pillars'])} full_class_a=0/50 testable_subsets=50/50")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
