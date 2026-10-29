#!/usr/bin/env python3
"""Verify R45 touch-target minimums (>= 48dp) across TileLabelFit and XML/Compose surfaces."""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def verify() -> dict[str, object]:
    tile_kt = (ROOT / "app/src/main/java/dev/fusion/shell/TileLabelFit.kt").read_text()
    m = re.search(r"const\s+val\s+MIN_TOUCH_TARGET_DP\s*=\s*(\d+)", tile_kt)
    if not m:
        raise SystemExit("TileLabelFit.kt missing MIN_TOUCH_TARGET_DP constant")
    floor_dp = int(m.group(1))
    if floor_dp < 48:
        raise SystemExit(f"TileLabelFit.MIN_TOUCH_TARGET_DP={floor_dp} < 48dp WCAG/Android floor")

    default_slot = re.search(r"minSlotDp:\s*Int\s*=\s*(\d+)", tile_kt)
    if not default_slot or int(default_slot.group(1)) < 48:
        raise SystemExit("TileLabelFit.planDock default minSlotDp must be >= 48dp")

    # Scan layout XMLs (if any) for explicit interactive Button/ImageButton widths/heights < 48dp
    xml_violations: list[str] = []
    for xml in sorted(ROOT.rglob("src/main/res/layout/*.xml")):
        txt = xml.read_text()
        for dim_m in re.finditer(r'android:min(?:Width|Height)="(\d+)dp"', txt):
            if int(dim_m.group(1)) < 48:
                xml_violations.append(f"{xml.relative_to(ROOT)}:{dim_m.group(0)}")
    if xml_violations:
        raise SystemExit(f"sub-48dp minWidth/minHeight in layout XML: {xml_violations}")

    return {
        "tile_label_fit_floor_dp": floor_dp,
        "default_dock_slot_dp": int(default_slot.group(1)),
        "xml_violations": len(xml_violations),
    }


def main() -> int:
    summary = verify()
    print(f"R45 touch-target gate OK: {summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
