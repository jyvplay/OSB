#!/usr/bin/env python3
"""R127 oracle generator for DOLLAR / FIXED / TIMEVALUE / COLUMN / ROW / COLUMNS / ROWS.

Pure-stdlib implementation. Expected values are computed from the documented definitions:

DOLLAR(number, [decimals=2])  -> "$" + rounded fixed-decimal string
FIXED(number, [decimals=2], [no_commas=0]) -> fixed-decimal string, optional comma suppression
TIMEVALUE(time_text)          -> fraction of day from "HH:MM:SS" / "HH:MM"
COLUMN(ref)                   -> 1-based column index of the cell reference
ROW(ref)                      -> 1-based row index of the cell reference
COLUMNS(array)                -> number of columns in the array/range
ROWS(array)                   -> number of rows in the array/range

The corpus is the source of truth for both `tools/check_r127_textref.py` and
`R127TextRefTest.kt`.
"""
from __future__ import annotations

import math
import sys


# ---------------- helpers ----------------

def round_half_up(v: float) -> int:
    """Round half away from zero (Excel-style)."""
    return int(math.floor(v + 0.5)) if v >= 0 else int(math.ceil(v - 0.5))


def fmt_fixed(num: float, decimals: int, no_commas: bool) -> str:
    """Fixed-decimal formatting with optional thousands separators."""
    scale = 10.0 ** decimals
    scaled = round_half_up(abs(num) * scale)
    int_part = scaled // scale
    frac_part = scaled % scale
    int_str = str(int_part) if no_commas else format_with_commas(int_part)
    frac_str = f"{frac_part:0{decimals}d}"
    sign = "-" if num < 0 else ""
    return f"{sign}{int_str}.{frac_str}"


def format_with_commas(v: int) -> str:
    s = str(v)
    if len(s) <= 3:
        return s
    out = []
    for i, ch in enumerate(s):
        if i > 0 and (len(s) - i) % 3 == 0:
            out.append(",")
        out.append(ch)
    return "".join(out)


def dollar(num: float, decimals: int) -> str:
    return "$" + fmt_fixed(num, decimals, no_commas=False)


# ---------------- corpus rendering ----------------

def render() -> str:
    rows = [("FUNCTION", "ARGS", "EXPECTED")]

    # ---- DOLLAR(number, [decimals=2]) ----
    dollar_cases = [
        (1234.5, 2, "$1,234.50"),
        (1234.5, 0, "$1,235"),
        (1234.5, 1, "$1,234.5"),
        (-1234.5, 2, "-$1,234.50"),
        (0.0, 2, "$0.00"),
        (1234.567, 2, "$1,234.57"),
        (1234.5, None, "$1,234.50"),  # default 2 decimals
    ]
    for num, dec, expected in dollar_cases:
        args = f"{num}" if dec is None else f"{num},{dec}"
        rows.append(("DOLLAR", args, expected))

    # ---- FIXED(number, [decimals=2], [no_commas=0]) ----
    fixed_cases = [
        (1234.5, 2, 0, "1,234.50"),
        (1234.5, 2, 1, "1234.50"),
        (1234.5, 0, 0, "1,235"),
        (-1234.5, 2, 0, "-1,234.50"),
        (0.0, 2, 0, "0.00"),
        (1234.567, 2, 0, "1,234.57"),
        (1234.5, None, None, "1,234.50"),  # defaults
    ]
    for num, dec, nocommas, expected in fixed_cases:
        parts = [str(num)]
        if dec is not None:
            parts.append(str(dec))
        if nocommas is not None:
            parts.append(str(nocommas))
        rows.append(("FIXED", ",".join(parts), expected))

    # ---- TIMEVALUE(time_text) ----
    time_cases = [
        ("12:00:00", 0.5),
        ("00:00:00", 0.0),
        ("23:59:59", 0.9999884259259259),
        ("01:30:00", 0.0625),
        ("00:01:00", 1.0 / 1440.0),
        ("00:00:01", 1.0 / 86400.0),
        ("12:30", 0.5208333333333334),
    ]
    for t, expected in time_cases:
        rows.append(("TIMEVALUE", f'"{t}"', repr(expected)))

    # ---- COLUMN(ref) / ROW(ref) ----
    ref_cases = [
        ("COLUMN", "A1", 1.0),
        ("COLUMN", "Z1", 26.0),
        ("COLUMN", "AA1", 27.0),
        ("COLUMN", "$C$5", 3.0),
        ("ROW", "A1", 1.0),
        ("ROW", "A10", 10.0),
        ("ROW", "$C$5", 5.0),
    ]
    for fn, ref, expected in ref_cases:
        rows.append((fn, ref, repr(expected)))

    # ---- COLUMNS(array) / ROWS(array) ----
    dim_cases = [
        ("COLUMNS", "A1:C5", 3.0),
        ("COLUMNS", "A1:A5", 1.0),
        ("COLUMNS", "A1:Z1", 26.0),
        ("ROWS", "A1:C5", 5.0),
        ("ROWS", "A1:A5", 5.0),
        ("ROWS", "A1:Z1", 1.0),
    ]
    for fn, ref, expected in dim_cases:
        rows.append((fn, ref, repr(expected)))

    # ---- Domain refusals ----
    rows.append(("DOLLAR", "abc", "#VALUE!"))          # non-numeric
    rows.append(("DOLLAR", "1.5,2,3", "#VALUE!"))      # wrong arity (3 args)
    rows.append(("DOLLAR", "1.5,-1", "#VALUE!"))       # negative decimals
    rows.append(("FIXED", "abc", "#VALUE!"))           # non-numeric
    rows.append(("FIXED", "1.5,2,3", "#VALUE!"))       # wrong arity (4 args)
    rows.append(("FIXED", "1.5,-1", "#VALUE!"))        # negative decimals
    rows.append(("TIMEVALUE", "not-a-time", "#VALUE!"))  # bad time text
    rows.append(("TIMEVALUE", "25:00:00", "#NUM!"))    # hour out of range
    rows.append(("TIMEVALUE", "00:60:00", "#NUM!"))    # minute out of range
    rows.append(("COLUMN", "", "#VALUE!"))              # empty ref
    rows.append(("COLUMN", "A1,B2", "#VALUE!"))        # wrong arity (2 args)
    rows.append(("ROW", "", "#VALUE!"))                 # empty ref
    rows.append(("ROW", "A1,B2", "#VALUE!"))            # wrong arity (2 args)
    rows.append(("COLUMNS", "", "#VALUE!"))             # empty ref
    rows.append(("COLUMNS", "A1,B2", "#VALUE!"))        # wrong arity (2 args)
    rows.append(("ROWS", "", "#VALUE!"))                # empty ref
    rows.append(("ROWS", "A1,B2", "#VALUE!"))           # wrong arity (2 args)

    lines = ["\t".join(str(c) for c in row) for row in rows]
    return "\n".join(lines) + "\n"


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else None
    text = render()
    if path:
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
