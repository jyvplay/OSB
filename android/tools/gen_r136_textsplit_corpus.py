#!/usr/bin/env python3
"""Pure-stdlib R136 oracle generator for TEXTSPLIT.

TEXTSPLIT(text, col_delimiter, [row_delimiter], [ignore_empty], [match_mode], [pad_with])
- Splits text into a 2D array using col_delimiter (columns) and optional row_delimiter (rows)
- ignore_empty: if TRUE, empty result strings are omitted
- match_mode: 0 = case-sensitive (default), 1 = case-insensitive
- pad_with: value for missing cells in rectangular output (default #N/A)

Expected column is the top-left cell value (scalar FormulaEval context). Refusals are #VALUE! / #N/A literals.
"""
import sys

ROWS: list[tuple[str, str, str]] = []

# ---- Basic column split ----
ROWS.append(("TEXTSPLIT", '"a,b,c", ","', "a"))
ROWS.append(("TEXTSPLIT", '"one|two|three", "|"', "one"))
ROWS.append(("TEXTSPLIT", '"a,b", ","', "a"))
ROWS.append(("TEXTSPLIT", '"single", ","', "single"))
ROWS.append(("TEXTSPLIT", '"a,,b", ","', "a"))

# ---- Row delimiter (2D split) ----
ROWS.append(("TEXTSPLIT", '"a,b;c,d", ",", ";"', "a"))
ROWS.append(("TEXTSPLIT", '"x,y", ",", ";"', "x"))

# ---- Ignore empty ----
ROWS.append(("TEXTSPLIT", '"a,,b", ",", , TRUE', "a"))
ROWS.append(("TEXTSPLIT", '"a,,b", ",", , FALSE', "a"))

# ---- Match mode (case insensitive) ----
ROWS.append(("TEXTSPLIT", '"A,B,C", ",", , FALSE, 0', "A"))
ROWS.append(("TEXTSPLIT", '"a,b,c", ",", , FALSE, 1', "a"))

# ---- Pad with ----
ROWS.append(("TEXTSPLIT", '"a,b;c", ",", ";", , , "-"', "a"))
ROWS.append(("TEXTSPLIT", '"a;b,c", ",", ";", , , "-"', "a"))

# ---- Error propagation ----
ROWS.append(("TEXTSPLIT", "#VALUE!, ','", "#VALUE!"))
ROWS.append(("TEXTSPLIT", '"a,b", ""', "#VALUE!"))
ROWS.append(("TEXTSPLIT", '"a,b"', "#VALUE!"))   # missing col_delimiter

# ---- Empty text ----
ROWS.append(("TEXTSPLIT", '"" , ","', ""))

# ---- Multi-char delimiters ----
ROWS.append(("TEXTSPLIT", '"a<>b<>c", "<>"', "a"))
ROWS.append(("TEXTSPLIT", '"a,,b", ",,"', "a"))

OUT = ["FUNCTION\tARGS\tEXPECTED"]
for fn, args, expected in ROWS:
    OUT.append(f"{fn}\t{args}\t{expected}")
print("\n".join(OUT))
