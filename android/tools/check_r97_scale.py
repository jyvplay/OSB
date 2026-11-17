#!/usr/bin/env python3
"""Independent arithmetic check for R97 colour scales and data bars.

`check_r97_verdicts.py` re-decides the whole rule set; this lane isolates the *numeric* part, where a plausible
implementation can still be wrong by one hue or one rounding step:

1. **per-visual recomputation** — every `scale:` entry in a row is recomputed from *its own* rule: thresholds
   resolved (`min`/`max`/`num`/`percent`/`percentile`), the cell's position between the two neighbouring stops,
   per-channel sRGB interpolation with half-up rounding. A cell with two scales must satisfy both, which the merged
   decoration in `verdicts.tsv` cannot show;
2. **endpoints** — the minimum cell is exactly the first declared colour and the maximum cell exactly the last;
3. **containment** — every interpolated colour lies, channel by channel, inside the box spanned by its two
   neighbouring stop colours. A swapped stop, an inverted weight or a rounding bug leaves that box;
4. **bar arithmetic** — every `bar:` fraction equals `(value - low) / (high - low)` clamped to `0..1`, and no
   non-numeric cell carries one.

No spreadsheet engine ran. This is agreement between two implementations plus invariants that hold for any correct
interpolation; it is not evidence about how Excel paints a gradient.

Utility: `python3 tools/check_r97_scale.py testing/build/r97/artifacts`
"""
from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import check_r97_verdicts as base  # noqa: E402  (the rule/value parsing is shared, the arithmetic is not)

TOLERANCE = 1e-12


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def channels(hex_colour: str) -> tuple[int, int, int]:
    require(len(hex_colour) == 6, f'colour {hex_colour!r} is not six hex digits')
    return int(hex_colour[0:2], 16), int(hex_colour[2:4], 16), int(hex_colour[4:6], 16)


def expected_mix(start: str, end: str, weight: float) -> str:
    weight = min(max(weight, 0.0), 1.0)
    a, b = channels(start), channels(end)
    return ''.join(f'{int(math.floor(a[i] * (1 - weight) + b[i] * weight + 0.5)):02X}' for i in range(3))


def stops_of(rule, blocks, grid) -> list[float] | None:
    return base.thresholds(rule, blocks, grid)


def expected_entries(rule, blocks, grid, cell) -> list[str]:
    """The visuals this rule must produce for this cell, in the same encoding the probe writes."""
    if rule.kind == 'colorScale':
        points = base.thresholds(rule, blocks, grid)
        require(points is not None and len(points) >= 2, 'a colour scale with no resolvable thresholds')
        kind, payload = grid.get(cell)
        if kind == 'num':
            value = float(payload)
        elif kind == 'blank':
            value = points[0]
        else:
            raise Fail(f'a colour scale over a {kind} cell should have been refused')
        return [f'scale:{base.interpolate(rule.colors, points, value)}']
    if rule.kind == 'dataBar':
        points = base.thresholds(rule, blocks, grid)
        require(points is not None and len(points) >= 2, 'a bar with no window')
        kind, payload = grid.get(cell)
        if kind != 'num':
            raise Fail(f'a bar was emitted for a {kind} cell')
        value = float(payload)
        low, high = points[0], points[-1]
        fraction = (1.0 if value >= high else 0.0) if high <= low else min(max((value - low) / (high - low), 0.0), 1.0)
        return [f'bar:{base.number_text(fraction)}']
    return base.visuals(rule)


def check_containment(rule, blocks, grid, cell, produced: str, where: str) -> None:
    points = base.thresholds(rule, blocks, grid)
    require(points is not None and len(points) >= 2, f'{where}: a scale visual has no resolvable thresholds')
    kind, payload = grid.get(cell)
    value = float(payload) if kind == 'num' else points[0]
    ours = produced.split(':', 1)[1]
    low_index = len(points) - 2
    for index in range(len(points) - 1):
        if points[index] <= value <= points[index + 1]:
            low_index = index
            break
    a = channels(rule.colors[low_index])
    b = channels(rule.colors[low_index + 1])
    got = channels(ours)
    for channel in range(3):
        require(min(a[channel], b[channel]) <= got[channel] <= max(a[channel], b[channel]),
                f'{where}: channel {channel} of {ours} is outside {rule.colors[low_index]}..{rule.colors[low_index + 1]}')
    if value <= points[0]:
        require(ours == rule.colors[0].upper(), f'{where}: below the first stop must be {rule.colors[0]}')
    if value >= points[-1]:
        require(ours == rule.colors[-1].upper(), f'{where}: above the last stop must be {rule.colors[-1]}')


def verify(folder: Path) -> dict[str, int]:
    require(folder.is_dir(), f'no artifact folder {folder}')
    block_table = base.rules_by_sheet(folder / 'rules.tsv')
    value_table = base.values_by_sheet(folder / 'values.tsv')
    rows = base.table(folder / 'verdicts.tsv', base.VERDICT_FIELDS)
    totals = {'scales': 0, 'bars': 0, 'cells': 0, 'rules': 0}
    seen_rules: set[tuple[str, int]] = set()
    for row in rows:
        sheet, cell_text = row[0], row[1]
        blocks = block_table[sheet]
        grid = base.Grid(value_table.get(sheet, {}))
        cell = base.parse_cell(cell_text)
        if row[7] == '-':
            continue
        fired = [] if row[3] == '-' else [int(token) for token in row[3].split(base.SEPARATOR)]
        entries = row[7].split(base.SEPARATOR)
        expected: list[str] = []
        by_priority = {}
        for priority in fired:
            rule = next(rule for _, (_, rules) in blocks.items() for rule in rules if rule.priority == priority)
            by_priority[priority] = rule
            expected.extend(expected_entries(rule, blocks, grid, cell))
        require(len(expected) == len(entries),
                f'{sheet}!{cell_text}: {len(entries)} visuals written, {len(expected)} owed by {len(fired)} rule(s)')
        cursor = 0
        for priority in fired:
            rule = by_priority[priority]
            for entry in expected_entries(rule, blocks, grid, cell):
                written = entries[cursor]
                cursor += 1
                where = f'{sheet}!{cell_text} p{priority}'
                require(written == entry, f'{where}: wrote {written!r}, recomputed {entry!r}')
                if written.startswith('scale:'):
                    check_containment(rule, blocks, grid, cell, written, where)
                    totals['scales'] += 1
                    seen_rules.add((sheet, priority))
                elif written.startswith('bar:'):
                    fraction = float(written.split(':', 1)[1])
                    require(-TOLERANCE <= fraction <= 1 + TOLERANCE, f'{where}: fraction {fraction} is outside 0..1')
                    totals['bars'] += 1
                    seen_rules.add((sheet, priority))
        totals['cells'] += 1
    totals['rules'] = len(seen_rules)
    require(totals['scales'] > 0, 'no colour scale was checked')
    require(totals['bars'] > 0, 'no data bar was checked')
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder)
    except Fail as failure:
        print(f'R97 scale verification FAILED: {failure}')
        return 1
    except base.Fail as failure:
        print(f'R97 scale verification FAILED: {failure}')
        return 1
    print(f'R97 scales: {totals["scales"]} colour-scale visual(s) and {totals["bars"]} data-bar visual(s) over '
          f'{totals["cells"]} cell(s), {totals["rules"]} range-relative rule(s), recomputed and bounded')
    return 0


if __name__ == '__main__':
    sys.exit(main())
