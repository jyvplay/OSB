#!/usr/bin/env python3
"""R128 oracle generator for `T` and `N` value conversion functions.

Pure-stdlib implementation. Expected values are computed from the documented definitions:

T(value)    -> the text if value is text, otherwise empty string ""
N(value)    -> the number if value is a number, 0 otherwise

The corpus is the source of truth for both `tools/check_r128_textnum.py` and
`R128TextNumTest.kt`.
"""
from __future__ import annotations

import sys


def render() -> str:
    rows = [("FUNCTION", "ARGS", "EXPECTED")]

    # ---- T(value) ----
    t_cases = [
        ("\"hello\"", "hello"),
        ("\"\"", ""),
        ("123", ""),
        ("TRUE", ""),
        ("FALSE", ""),
        ("\"42\"", "42"),
    ]
    for args, expected in t_cases:
        rows.append(("T", args, expected))

    # ---- N(value) ----
    n_cases = [
        ("42", "42.0"),
        ("3.14", "3.14"),
        ("0", "0.0"),
        ("-7.5", "-7.5"),
        ("TRUE", "1.0"),
        ("FALSE", "0.0"),
        ("\"123\"", "123.0"),
        ("\"3.14\"", "3.14"),
        ("\"not a number\"", "0.0"),
        ("\"\"", "0.0"),
    ]
    for args, expected in n_cases:
        rows.append(("N", args, expected))

    # ---- Domain refusals ----
    rows.append(("T", "", "#VALUE!"))           # wrong arity: 0 args
    rows.append(("T", "1,2", "#VALUE!"))        # wrong arity: 2 args
    rows.append(("N", "", "#VALUE!"))           # wrong arity: 0 args
    rows.append(("N", "1,2", "#VALUE!"))        # wrong arity: 2 args

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
