#!/usr/bin/env python3
"""Standalone No-Android-Studio CLI & Live Server Workbench for Fusion Shell (R45 .. R51).

Allows testing and using Rich Writer (.docx multi-section / scholarly .odt / .epub 3.3 / .rtf / LaTeX SVG /
Tagged PDF/UA-1 + Linearized + Signed .pdf / Galley Paginator / BibTeX / TrackRevisions), Multi-Sheet &
Dynamic Array Spill Spreadsheet (215-function FormulaEval + 36 Spill ops + Structured Tables + R1C1 +
GoalSeek/Simplex LP + 3D Spans + .ods OpenFormula + Arrow IPC + .pptx Slides), Browser Flexbox + CHIPS +
Permissions-Policy + DNR Trie + DOMPurify + Adblock + COOP/COEP + DoH, Wasm 2.0 (128-bit 0xFD v128 SIMD +
f64 + multi-table + bulk-memory) + WIT Canonical ABI + WASI Preview 1 Sandbox, Tails 7.15.1 LUKS2 Argon2id
1 GiB + Tor Vanguards-Lite (Prop 333) + WebTunnel + OHTTP + 20-Vector Hardware Shield (GhostTouch/WIGHT,
Thunderclap PCIe IOMMU DMA, Hall Spoof, Rowhammer, Corneal Glint, BLUFFS, NFC, BadUSB), Visual Audit PNG
rendering, and the live HTTP Browser Workbench directly from any terminal without Android Studio.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import amnesic_posture
import check_r45_reference
import check_r46_reference
import check_r47_reference
import check_r48_reference
import check_r49_reference
import check_r50_reference
import check_r51_reference
import check_r52_reference
import check_r53_reference
import fusion_workbench_server
import render_r45_visual_preview
import render_r46_visual_preview
import render_r47_visual_preview
import render_r48_visual_preview
import render_r49_visual_preview
import render_r50_visual_preview
import render_r51_visual_preview
import render_r52_visual_preview
import render_r53_visual_preview
import run_r45_contracts
import run_r46_contracts
import run_r47_contracts
import run_r48_contracts
import run_r49_contracts
import run_r50_contracts
import run_r51_contracts
import run_r52_contracts
import run_r53_contracts


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("contracts", help="Compile & run R45..R51 production kernels on host JVM")
    sub.add_parser("diff-oracle", help="Run R45..R51 independent Python differential oracles")
    sub.add_parser("amnesic-audit", help="Inspect host Linux amnesic/IOMMU/swap/USB posture")
    sub.add_parser("visual-preview", help="Render & WCAG-audit Java2D R51 workbench PNG previews")
    sub.add_parser("serve-self-test", help="Run one-shot warm Kotlin JVM R51 workbench daemon self-test")
    serve_p = sub.add_parser("serve", help="Launch live interactive browser workbench on 0.0.0.0:8765")
    serve_p.add_argument("--host", default="0.0.0.0")
    serve_p.add_argument("--port", type=int, default=8765)
    sub.add_parser("all", help="Run R45..R51 contracts, differential oracles, amnesic audit, daemon self-test, and visual previews")

    args = ap.parse_args(argv)
    if args.cmd == "contracts":
        run_r45_contracts.main()
        run_r46_contracts.main()
        run_r47_contracts.main()
        run_r48_contracts.main()
        run_r49_contracts.main()
        run_r50_contracts.main()
        run_r51_contracts.main()
        run_r52_contracts.main()
        run_r53_contracts.main()
        return 0
    if args.cmd == "diff-oracle":
        check_r45_reference.main(["--verify"])
        check_r46_reference.main(["--verify"])
        check_r47_reference.main()
        check_r48_reference.main()
        check_r49_reference.main()
        check_r50_reference.main()
        check_r51_reference.main()
        check_r52_reference.main()
        check_r53_reference.main()
        return 0
    if args.cmd == "amnesic-audit":
        return amnesic_posture.main(["--json"])
    if args.cmd == "visual-preview":
        return render_r53_visual_preview.main()
    if args.cmd == "serve-self-test":
        return fusion_workbench_server.main(["--self-test"])
    if args.cmd == "serve":
        return fusion_workbench_server.main(["--host", args.host, "--port", str(args.port)])
    if args.cmd == "all":
        run_r45_contracts.main()
        run_r46_contracts.main()
        run_r47_contracts.main()
        run_r48_contracts.main()
        run_r49_contracts.main()
        run_r50_contracts.main()
        run_r51_contracts.main()
        run_r52_contracts.main()
        run_r53_contracts.main()
        check_r45_reference.main(["--verify"])
        check_r46_reference.main(["--verify"])
        check_r47_reference.main()
        check_r48_reference.main()
        check_r49_reference.main()
        check_r50_reference.main()
        check_r51_reference.main()
        check_r52_reference.main()
        check_r53_reference.main()
        amnesic_posture.main([])
        fusion_workbench_server.main(["--self-test"])
        render_r45_visual_preview.main()
        render_r46_visual_preview.main()
        render_r47_visual_preview.main()
        render_r48_visual_preview.main()
        render_r49_visual_preview.main()
        render_r50_visual_preview.main()
        render_r51_visual_preview.main()
        render_r52_visual_preview.main()
        render_r53_visual_preview.main()
        return 0
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
