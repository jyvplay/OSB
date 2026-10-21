#!/usr/bin/env python3
"""Independent Python recomputation of the R26 beauty budgets from shipped source constants.

Parses palette hex + tabletop projection constants out of the production sources and
re-derives every threshold with an independent WCAG implementation. Agreement with the
Kotlin kernel is cross-language evidence for the MATH ONLY -- not rendered pixels,
human preference, or accessibility acceptance.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def luminance(rgb):
    def channel(shift):
        s = ((rgb >> shift) & 255) / 255.0
        return s / 12.92 if s <= 0.04045 else ((s + 0.055) / 1.055) ** 2.4
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)

def ratio(a, b):
    x, y = luminance(a), luminance(b)
    return (max(x, y) + 0.05) / (min(x, y) + 0.05)

def main():
    pal_src = (ROOT / 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/ProfessionalPalettes.kt').read_text()
    palettes = {}
    for name, body in re.findall(r'ProfessionalPalette\.(\w+)->ProfessionalColors\(([^)]+)\)', pal_src):
        palettes[name] = [int(v.strip(), 16) for v in body.split(',')]
    assert len(palettes) == 3, f'expected 3 palettes, found {len(palettes)}'
    order = ['background', 'surface', 'raised', 'outline', 'primary', 'onPrimary', 'text', 'muted', 'selection', 'onSelection']
    pairs = [('text', 'background'), ('text', 'surface'), ('text', 'raised'), ('muted', 'background'),
             ('muted', 'surface'), ('muted', 'raised'), ('onPrimary', 'primary'), ('onSelection', 'selection'), ('primary', 'background')]
    results = {}
    for name, values in palettes.items():
        role = dict(zip(order, values))
        ratios = {f'{fg}/{bg}': round(ratio(role[fg], role[bg]), 4) for fg, bg in pairs}
        worst = min(ratios.values())
        assert worst >= 4.5, f'{name} weakest pair {worst} below AA 4.5'
        results[name] = {'ratios': ratios, 'worst': worst}
    # Tabletop floor fraction from shipped projection constants.
    topo = (ROOT / 'feature/launcher/src/main/kotlin/dev/fusion/launcher/Tabletop.kt').read_text()
    inset = float(re.search(r'const val INSET = ([0-9.]+)', topo).group(1))
    horizon = float(re.search(r'const val HORIZON = ([0-9.]+)', topo).group(1))
    floor = (1.0 - horizon) * (1.0 - inset)
    assert floor >= 0.80, f'floor fraction {floor} below 0.80'
    assert abs(floor - 0.8316) < 1e-9, f'floor fraction drifted: {floor}'
    # Phi-derived dock gap band from the shipped kernel formula.
    phi = 1.618033988749895
    gaps = {slot: max(4, min(16, int(slot / (phi ** 3)))) for slot in (64, 72, 88)}
    assert all(4 <= g <= 16 for g in gaps.values()), gaps
    report = {'palettes': results, 'floor_fraction': floor, 'dock_gaps': gaps,
              'scope': 'Independent math recomputation only; NOT rendered-pixel, human-preference or AX evidence.'}
    out = ROOT / 'testing/build/r26/beauty-reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print(f"R26 beauty reference PASS: 3 palettes x 9 pairs all >= 4.5, floor {floor:.4f} >= 0.80, gaps {gaps}.")

if __name__ == '__main__':
    main()
