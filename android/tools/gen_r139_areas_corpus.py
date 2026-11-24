#!/usr/bin/env python3
"""Pure-stdlib R139 oracle generator for AREAS.

AREAS(reference) returns the number of areas in a reference.
In the single-sheet engine:
- A single cell reference = 1 area
- A range reference = 1 area (contiguous block = 1 area)
- Array literals / other expressions = 1 area

Expected column is the result number. Refusals are #VALUE! literals.
"""
import sys

ROWS: list[tuple[str, str, str]] = []

# ---- Single cell references ----
ROWS.append(("AREAS", "A1", "1"))
ROWS.append(("AREAS", "Z100", "1"))
ROWS.append(("AREAS", "XFD1048576", "1"))

# ---- Range references ----
ROWS.append(("AREAS", "A1:B2", "1"))
ROWS.append(("AREAS", "A1:A10", "1"))
ROWS.append(("AREAS", "C5:F20", "1"))
ROWS.append(("AREAS", "1:1", "1"))    # entire row
ROWS.append(("AREAS", "A:A", "1"))    # entire column

# ---- Array literals ----
ROWS.append(("AREAS", "{1,2,3}", "1"))
ROWS.append(("AREAS", "{1;2;3}", "1"))
ROWS.append(("AREAS", "{1,2;3,4}", "1"))

# ---- Error propagation ----
ROWS.append(("AREAS", "#VALUE!", "#VALUE!"))
ROWS.append(("AREAS", "#REF!", "#REF!"))

# ---- Argument count validation ----
ROWS.append(("AREAS", "", "#VALUE!"))         # zero arguments
ROWS.append(("AREAS", "A1, B1", "#VALUE!"))   # too many arguments

# ---- Non-reference expressions ----
ROWS.append(("AREAS", "42", "1"))             # number literal
ROWS.append(("AREAS", "\"text\"", "1"))       # text literal
ROWS.append(("AREAS", "TRUE", "1"))           # boolean literal
ROWS.append(("AREAS", "A1+B1", "1"))          # expression result

OUT = ["FUNCTION\tARGS\tEXPECTED"]
for fn, args, expected in ROWS:
    OUT.append(f"{fn}\t{args}\t{expected}")
print("\n".join(OUT))
