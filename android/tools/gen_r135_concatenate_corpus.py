#!/usr/bin/env python3
"""Pure-stdlib R135 oracle generator for CONCATENATE.

CONCATENATE(text1, [text2], ...) joins up to 255 text items into one text string.
Each argument is converted to its display text before concatenation:
- Numbers render with Excel-style formatting (no trailing .0 for integers)
- Booleans render as TRUE/FALSE
- Errors propagate (#VALUE!, #NUM!, etc.)
- Empty cells / omitted arguments render as empty string
- Text renders verbatim

Expected column is the concatenated result; refusals are #VALUE! / #NUM! literals.
"""
import sys

ROWS: list[tuple[str, str, str]] = []

# ---- Basic concatenation ----
ROWS.append(("CONCATENATE", "\"Hello\"", "Hello"))
ROWS.append(("CONCATENATE", "\"Hello\", \" \"", "Hello "))
ROWS.append(("CONCATENATE", "\"Hello\", \" \", \"World\"", "Hello World"))
ROWS.append(("CONCATENATE", "\"A\", \"B\", \"C\"", "ABC"))
ROWS.append(("CONCATENATE", "\"\", \"\"", ""))
ROWS.append(("CONCATENATE", "\"abc\"", "abc"))

# ---- Numeric arguments ----
ROWS.append(("CONCATENATE", "42", "42"))
ROWS.append(("CONCATENATE", "\"Value: \", 42", "Value: 42"))
ROWS.append(("CONCATENATE", "3.14159", "3.14159"))
ROWS.append(("CONCATENATE", "\"Price: \", 19.99", "Price: 19.99"))
ROWS.append(("CONCATENATE", "1000", "1000"))

# ---- Boolean arguments ----
ROWS.append(("CONCATENATE", "TRUE", "TRUE"))
ROWS.append(("CONCATENATE", "FALSE", "FALSE"))
ROWS.append(("CONCATENATE", "\"Result: \", TRUE", "Result: TRUE"))
ROWS.append(("CONCATENATE", "\"Flag: \", FALSE", "Flag: FALSE"))

# ---- Error propagation ----
ROWS.append(("CONCATENATE", "#REF!", "#REF!"))
ROWS.append(("CONCATENATE", "\"A\", #VALUE!", "#VALUE!"))
ROWS.append(("CONCATENATE", "#DIV/0!", "#DIV/0!"))

# ---- Empty / omitted ----
ROWS.append(("CONCATENATE", "\"Hello\", \"\"", "Hello"))
ROWS.append(("CONCATENATE", "\"\", \"World\"", "World"))

# ---- Mixed types ----
ROWS.append(("CONCATENATE", "\"Item\", 42, \" of \", 100, TRUE", "Item42 of 100TRUE"))

# ---- Refusals ----
ROWS.append(("CONCATENATE", "", "#VALUE!"))              # zero arguments

OUT = ["FUNCTION\tARGS\tEXPECTED"]
for fn, args, expected in ROWS:
    OUT.append(f"{fn}\t{args}\t{expected}")
print("\n".join(OUT))
