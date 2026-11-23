#!/usr/bin/env python3
"""R129 oracle generator for `SHEET` and `SHEETS` sheet reference functions.

Pure-stdlib implementation. Expected values are computed from the documented definitions:

SHEET([value])  -> the sheet number of the referenced cell. Single-sheet engine always
                   returns 1. Arity 0-1.
SHEETS([ref])   -> the number of sheets in the reference. Single-sheet engine always
                   returns 1. Arity 0-1.

Both return `#VALUE!` on wrong arity (>1 argument).

The corpus is the source of truth for both `tools/check_r129_sheet.py` and
`R129SheetTest.kt`.
"""
from __future__ import annotations

import sys


def render() -> str:
    rows = [("FUNCTION", "ARGS", "EXPECTED")]

    # ---- SHEET([value]) ----
    sheet_cases = [
        ("", "1.0"),           # no arg: single sheet -> 1
        ("A1", "1.0"),         # cell ref
        ('"hello"', "1.0"),    # any arg is accepted
        ("42", "1.0"),
    ]
    for args, expected in sheet_cases:
        rows.append(("SHEET", args, expected))

    # ---- SHEETS([ref]) ----
    sheets_cases = [
        ("", "1.0"),           # no arg: single sheet -> 1
        ("A1", "1.0"),         # cell ref
        ("A1:C5", "1.0"),      # range ref
    ]
    for args, expected in sheets_cases:
        rows.append(("SHEETS", args, expected))

    # ---- Domain refusals ----
    rows.append(("SHEET", "A1,B2", "#VALUE!"))   # wrong arity: 2 args
    rows.append(("SHEET", "A1,B2,C3", "#VALUE!")) # wrong arity: 3 args
    rows.append(("SHEETS", "A1,B2", "#VALUE!"))   # wrong arity: 2 args
    rows.append(("SHEETS", "A1,B2,C3", "#VALUE!")) # wrong arity: 3 args

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
