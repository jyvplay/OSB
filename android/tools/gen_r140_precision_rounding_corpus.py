#!/usr/bin/env python3
"""Pure-stdlib R140 oracle generator for CEILING.PRECISE, FLOOR.PRECISE, ISO.CEILING, MODE.MULT.

CEILING.PRECISE(number, [significance]) — rounds up (toward +inf) to nearest multiple of significance.
FLOOR.PRECISE(number, [significance]) — rounds down (toward -inf) to nearest multiple of significance.
ISO.CEILING(number, [significance]) — same as CEILING.PRECISE (ISO standard variant).
MODE.MULT(number1, [number2], ...) — returns all most-frequent values (first value in scalar context).

Expected column is the result number. Refusals are #NUM! / #DIV/0! / #N/A / #VALUE! literals.
"""
import sys
import math

ROWS: list[tuple[str, str, str]] = []

# ---- CEILING.PRECISE ----
ROWS.append(("CEILING.PRECISE", "4.3", "5"))
ROWS.append(("CEILING.PRECISE", "4.3, 2", "6"))
ROWS.append(("CEILING.PRECISE", "-4.3", "-4"))
ROWS.append(("CEILING.PRECISE", "-4.3, 2", "-4"))
ROWS.append(("CEILING.PRECISE", "4.3, -2", "6"))
ROWS.append(("CEILING.PRECISE", "0", "0"))
ROWS.append(("CEILING.PRECISE", "5", "5"))
ROWS.append(("CEILING.PRECISE", "5, 2", "6"))
ROWS.append(("CEILING.PRECISE", "5.5, 0.5", "5.5"))

# ---- FLOOR.PRECISE ----
ROWS.append(("FLOOR.PRECISE", "4.3", "4"))
ROWS.append(("FLOOR.PRECISE", "4.3, 2", "4"))
ROWS.append(("FLOOR.PRECISE", "-4.3", "-5"))
ROWS.append(("FLOOR.PRECISE", "-4.3, 2", "-6"))
ROWS.append(("FLOOR.PRECISE", "4.3, -2", "4"))
ROWS.append(("FLOOR.PRECISE", "0", "0"))
ROWS.append(("FLOOR.PRECISE", "5", "5"))
ROWS.append(("FLOOR.PRECISE", "5.5, 0.5", "5.5"))

# ---- ISO.CEILING ----
ROWS.append(("ISO.CEILING", "4.3", "5"))
ROWS.append(("ISO.CEILING", "-4.3", "-4"))
ROWS.append(("ISO.CEILING", "4.3, 2", "6"))
ROWS.append(("ISO.CEILING", "0", "0"))

# ---- MODE.MULT ----
ROWS.append(("MODE.MULT", "1, 2, 2, 3", "2"))
ROWS.append(("MODE.MULT", "1, 1, 2, 2, 3", "1"))
ROWS.append(("MODE.MULT", "5, 5, 5, 3, 3, 1", "5"))
ROWS.append(("MODE.MULT", "1", "1"))
ROWS.append(("MODE.MULT", "1, 2, 3", "#N/A"))
ROWS.append(("MODE.MULT", "", "#N/A"))
ROWS.append(("MODE.MULT", "1, 1, 2", "1"))

# ---- Error propagation ----
ROWS.append(("CEILING.PRECISE", "#VALUE!", "#VALUE!"))
ROWS.append(("FLOOR.PRECISE", "#VALUE!", "#VALUE!"))
ROWS.append(("ISO.CEILING", "#VALUE!", "#VALUE!"))
ROWS.append(("MODE.MULT", "#VALUE!", "#VALUE!"))

# ---- Argument validation ----
ROWS.append(("CEILING.PRECISE", "", "#VALUE!"))
ROWS.append(("FLOOR.PRECISE", "", "#VALUE!"))
ROWS.append(("ISO.CEILING", "", "#VALUE!"))
ROWS.append(("MODE.MULT", "", "#N/A"))

# ---- Significance zero ----
ROWS.append(("CEILING.PRECISE", "4.3, 0", "#DIV/0!"))
ROWS.append(("FLOOR.PRECISE", "4.3, 0", "#DIV/0!"))
ROWS.append(("ISO.CEILING", "4.3, 0", "#DIV/0!"))

OUT = ["FUNCTION\tARGS\tEXPECTED"]
for fn, args, expected in ROWS:
    OUT.append(f"{fn}\t{args}\t{expected}")
print("\n".join(OUT))
