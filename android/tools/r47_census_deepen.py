#!/usr/bin/env python3
"""Idempotently deepen 48/50 module rows in docs/module-testability.json with 61 R47 kernels + 5 TOUCHED files.

Never modifies docs/module-audit.json (full-spec Class A acceptance remains strictly 0/50 = 0.00%).
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import module_testability as census
import run_r47_contracts

R47_CHECKS = [
    "testing/src/r47/kotlin/dev/fusion/testing/R47Checks.kt",
    "tools/test_r47_tools.py",
]
CMD = "python3 tools/run_r47_contracts.py"

TOUCHED: dict[str, tuple[str, str]] = {
    "app/src/main/java/dev/fusion/shell/MainActivity.kt": (
        ":app",
        "R47 wires TorSocksIsolation (<torS0X>0), ImuSideChannelGuard (0Hz focus blackout), SecureKeypadScrambler, WasiPreview1Host, and SecureSurfacePolicy into MainActivity.",
    ),
    "app/src/main/java/dev/fusion/shell/AdaptiveDock.kt": (
        ":app",
        "R47 upgrades AdaptiveDock with >=48dp touch targets and 360dp compact single-line anti-clipping pill layout.",
    ),
    "app/src/main/java/dev/fusion/shell/UsbWatchReceiver.kt": (
        ":app",
        "R47 wires UsbGuardPolicy pre-bind lockscreen and composite HID BadUSB filtering into UsbWatchReceiver.",
    ),
    "feature/maps/src/main/java/dev/fusion/maps/MapScreen.kt": (
        ":feature:maps",
        "R47 wires offline GeoJsonFeature Haversine track and bounding-box summary overlay into MapScreen.",
    ),
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt": (
        ":format:ooxml",
        "R47 expands FormulaEval from 35 to 61 pure ECMA-376 functions (XLOOKUP, XMATCH, HLOOKUP, CHOOSE, SUMIFS, COUNTIFS, AVERAGEIFS, IFS, SWITCH, XOR, TEXTJOIN, SUBSTITUTE, FIND, SEARCH, PROPER, EXACT, REPT, VALUE, NPV, PMT, FV, PV, MOD, POWER, LOG10, LN, EXP, CEILING, FLOOR, SIGN, TRUNC).",
    ),
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    deepen: dict[str, list[tuple[str, str]]] = {}
    for sp in run_r47_contracts.KERNELS:
        deepen.setdefault(sp.module, []).append(
            (sp.path, f"R47 adds {sp.name} pure-JVM kernel ({sp.domain}).")
        )

    jp = ROOT / "docs/module-testability.json"
    data = json.loads(jp.read_text(encoding="utf-8"))

    owned = {f for items in deepen.values() for f, _ in items} | set(R47_CHECKS) | set(TOUCHED)
    stale: list[tuple[str, str]] = []
    for r in data["rows"]:
        for p, pinned in r["sha256"].items():
            if p not in owned and sha(p) != pinned:
                stale.append((r["module"], p))
    if stale:
        raise SystemExit(f"refusing to silently re-pin modified files outside DEEPEN/TOUCHED: {stale}")

    seen_mods: set[str] = set()
    touched_applied: set[str] = set()
    for r in data["rows"]:
        mod = r["module"]
        for t_path, (t_mod, t_note) in TOUCHED.items():
            if mod == t_mod or t_path in r["production"] or t_path in r["sha256"]:
                if mod == t_mod and t_path not in r["production"]:
                    r["production"].append(t_path)
                touched_applied.add(t_path)
                if t_note not in r["behavior"]:
                    r["behavior"] = r["behavior"].rstrip() + " " + t_note
                r["sha256"][t_path] = sha(t_path)

        items = deepen.get(mod, [])
        is_touched_mod = any(t_mod == mod for _, (t_mod, _) in TOUCHED.items())
        if not items and not is_touched_mod:
            continue

        for prod, note in items:
            if prod not in r["production"]:
                r["production"].append(prod)
            if note not in r["behavior"]:
                r["behavior"] = r["behavior"].rstrip() + " " + note
        for chk in R47_CHECKS:
            if chk not in r["checks"]:
                r["checks"].append(chk)
        if CMD not in r["command"]:
            r["command"] = (r["command"] + "; " + CMD) if r["command"] else CMD
        for p in r["production"] + r["checks"]:
            r["sha256"][p] = sha(p)
        seen_mods.add(mod)

    assert len(seen_mods) == 48, f"Expected 48 deepened modules, got {len(seen_mods)}"
    assert touched_applied == set(TOUCHED), f"Missing touched: {set(TOUCHED) - touched_applied}"

    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    (ROOT / "docs/MODULE-TESTABILITY.md").write_text(census.render(data), encoding="utf-8")
    reloaded = json.loads(jp.read_text(encoding="utf-8"))
    census.validate(reloaded)
    assert census.render(reloaded) == (ROOT / "docs/MODULE-TESTABILITY.md").read_text(encoding="utf-8")
    print(f"R47 census deepen OK ({len(seen_mods)}/50 modules deepened, {len(touched_applied)} touched):", json.dumps(census.counts(reloaded)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
