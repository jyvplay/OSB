#!/usr/bin/env python3
"""Pure-stdlib R137 oracle generator for HYPERLINK.

HYPERLINK(link_location, [friendly_name]) — returns friendly_name when provided,
otherwise returns link_location as text.

Expected column is the result text. Refusals are #VALUE! literals.
"""
import sys

ROWS: list[tuple[str, str, str]] = []

# ---- Basic hyperlink ----
ROWS.append(("HYPERLINK", '"https://example.com"', "https://example.com"))
ROWS.append(("HYPERLINK", '"https://example.com", "Example"', "Example"))
ROWS.append(("HYPERLINK", '"https://example.com", ""', ""))
ROWS.append(("HYPERLINK", '"https://example.com/page?q=1", "Page 1"', "Page 1"))

# ---- Friendly name variations ----
ROWS.append(("HYPERLINK", '"https://a.com", "Link A"', "Link A"))
ROWS.append(("HYPERLINK", '"https://b.com", "Link B"', "Link B"))
ROWS.append(("HYPERLINK", '"https://c.com", "Click here"', "Click here"))

# ---- Error propagation ----
ROWS.append(("HYPERLINK", "#VALUE!", "#VALUE!"))
ROWS.append(("HYPERLINK", "#VALUE!, ''", "#VALUE!"))
ROWS.append(("HYPERLINK", '"https://example.com", #VALUE!', "#VALUE!"))
ROWS.append(("HYPERLINK", "#REF!, 'Click'", "#REF!"))

# ---- Argument count validation ----
ROWS.append(("HYPERLINK", "", "#VALUE!"))          # zero arguments
ROWS.append(("HYPERLINK", '"a", "b", "c"', "#VALUE!"))  # too many arguments

# ---- Empty link ----
ROWS.append(("HYPERLINK", '""', "#VALUE!"))       # empty link location

# ---- Special characters in URL ----
ROWS.append(("HYPERLINK", '"https://example.com/path with spaces", "Link"', "Link"))
ROWS.append(("HYPERLINK", '"https://example.com?x=1&y=2", "Query"', "Query"))
ROWS.append(("HYPERLINK", '"file:///C:/docs/report.pdf", "Report"', "Report"))
ROWS.append(("HYPERLINK", '"mailto:someone@example.com", "Email"', "Email"))
ROWS.append(("HYPERLINK", '"https://example.org", "Org"', "Org"))

OUT = ["FUNCTION\tARGS\tEXPECTED"]
for fn, args, expected in ROWS:
    OUT.append(f"{fn}\t{args}\t{expected}")
print("\n".join(OUT))
