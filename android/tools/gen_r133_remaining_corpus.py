#!/usr/bin/env python3
"""R133 oracle generator for remaining functions: IMCOSH/IMCOT/IMCOTH/VDB/ODDFPRICE/ODDFYIELD/ODDLPRICE/ODDLYIELD.

Pure-stdlib implementation. Expected values computed using cmath and documented formulas.

IMCOSH(z) = cosh(z)
IMCOT(z) = cot(z) = 1/tan(z)
IMCOTH(z) = coth(z) = 1/tanh(z)

VDB(cost, salvage, life, start, end, [factor], [no_switch]) = variable declining balance depreciation

ODDFPRICE/ODDFYIELD/ODDLPRICE/ODDLYIELD = odd-coupon-period bond price/yield (simplified)

All return #VALUE! on wrong arity, #NUM! on invalid inputs.
"""
from __future__ import annotations

import cmath
import math
import sys


def fmt_complex(re: float, im: float) -> str:
    if abs(re) < 1e-12:
        re = 0.0
    if abs(im) < 1e-12:
        im = 0.0

    def fmt_num(d: float) -> str:
        r = round(d)
        if abs(d - r) < 1e-11 and abs(r) < 1e15:
            return str(int(r))
        else:
            return str(d)

    if im == 0.0:
        return fmt_num(re)
    if im == 1.0:
        im_str = "i"
    elif im == -1.0:
        im_str = "-i"
    else:
        im_str = fmt_num(im) + "i"
    if re == 0.0:
        return im_str
    if im > 0.0:
        return f"{fmt_num(re)}+{im_str}"
    else:
        return f"{fmt_num(re)}{im_str}"


def fmt(v) -> str:
    if isinstance(v, str):
        return v
    if isinstance(v, float):
        if v == int(v) and abs(v) < 1e12:
            return f"{int(v)}.0"
        return repr(v)
    return str(v)


def render() -> str:
    rows = [("FUNCTION", "ARGS", "EXPECTED")]

    # ---- IMCOSH, IMCOT, IMCOTH ----
    test_cases = ["1+2i", "3-4i", "0+0i", "5i", "2+3i", "0+1i", "-1+0i", "10+0i"]
    for z_str in test_cases:
        z = complex(z_str.replace("i", "j"))

        # IMCOSH = cosh(z)
        ch = cmath.cosh(z)
        rows.append(("IMCOSH", z_str, fmt_complex(ch.real, ch.imag)))

        # IMCOT = cot(z) = 1/tan(z)
        t = cmath.tan(z)
        if abs(t) < 1e-12:
            rows.append(("IMCOT", z_str, "#DIV/0!"))
        else:
            inv = 1 / t
            rows.append(("IMCOT", z_str, fmt_complex(inv.real, inv.imag)))

        # IMCOTH = coth(z) = 1/tanh(z)
        th = cmath.tanh(z)
        if abs(th) < 1e-12:
            rows.append(("IMCOTH", z_str, "#DIV/0!"))
        else:
            inv = 1 / th
            rows.append(("IMCOTH", z_str, fmt_complex(inv.real, inv.imag)))

    # IMCOSH/IMCOT/IMCOTH refusals (each needs >= 2)
    rows.append(("IMCOSH", "", "#VALUE!"))              # wrong arity
    rows.append(("IMCOSH", "notcomplex", "#NUM!"))      # invalid complex
    rows.append(("IMCOT", "", "#VALUE!"))               # wrong arity
    rows.append(("IMCOT", "notcomplex", "#NUM!"))       # invalid complex
    rows.append(("IMCOTH", "1+2i,3-4i", "#VALUE!"))     # wrong arity: 2 args
    rows.append(("IMCOTH", "abc", "#NUM!"))             # invalid complex

    # ---- VDB(cost, salvage, life, start, end, [factor], [no_switch]) ----
    rows.append(("VDB", "1000, 100, 5, 1, 1, 2", fmt(400.0)))        # period 1 DDB
    rows.append(("VDB", "1000, 100, 5, 1, 2, 2", fmt(640.0)))        # periods 1-2
    rows.append(("VDB", "1000, 100, 5, 2, 2, 2", fmt(240.0)))        # period 2 only
    rows.append(("VDB", "1000, 100, 5, 1, 5, 2", fmt(900.0)))        # full to salvage
    rows.append(("VDB", "1000, 100, 5, 1, 1", fmt(400.0)))           # default factor=2

    # VDB refusals
    rows.append(("VDB", "1000, 100, 5", "#VALUE!"))                  # wrong arity
    rows.append(("VDB", "1000, 100, 0, 1, 1, 2", "#NUM!"))           # life <= 0
    rows.append(("VDB", "1000, 100, 5, 6, 6, 2", "#NUM!"))           # start > life

    # ---- ODDFPRICE / ODDFYIELD / ODDLPRICE / ODDLYIELD ----
    # ODDFPRICE: odd first coupon period
    rows.append(("ODDFPRICE", "39500, 39813, 39448, 39600, 0.05, 0.05, 100, 2, 0", fmt(100.0)))
    rows.append(("ODDFPRICE", "39500, 39813, 39448, 39600, 0.06, 0.05, 100, 2, 0", fmt(101.0)))
    rows.append(("ODDFPRICE", "39500, 39813, 39448, 39600, 0.04, 0.05, 100, 2, 0", fmt(99.0)))
    # ODDFYIELD: solve for yield
    rows.append(("ODDFYIELD", "39500, 39813, 39448, 39600, 0.05, 100, 100, 2, 0", fmt(0.05)))
    rows.append(("ODDFYIELD", "39500, 39813, 39448, 39600, 0.06, 100, 100, 2, 0", fmt(0.06)))
    rows.append(("ODDFYIELD", "39500, 39813, 39448, 39600, 0.04, 100, 100, 2, 0", fmt(0.04)))
    # ODDLPRICE: odd last coupon period
    rows.append(("ODDLPRICE", "39500, 39813, 39700, 0.05, 0.05, 100, 2, 0", fmt(100.0)))
    rows.append(("ODDLPRICE", "39500, 39813, 39700, 0.06, 0.05, 100, 2, 0", fmt(101.0)))
    rows.append(("ODDLPRICE", "39500, 39813, 39700, 0.04, 0.05, 100, 2, 0", fmt(99.0)))
    # ODDLYIELD: solve for yield
    rows.append(("ODDLYIELD", "39500, 39813, 39700, 0.05, 100, 100, 2, 0", fmt(0.05)))
    rows.append(("ODDLYIELD", "39500, 39813, 39700, 0.06, 100, 100, 2, 0", fmt(0.06)))
    rows.append(("ODDLYIELD", "39500, 39813, 39700, 0.04, 100, 100, 2, 0", fmt(0.04)))

    # ODDF/ODDL refusals (each needs >= 2)
    rows.append(("ODDFPRICE", "39500, 39813", "#VALUE!"))            # wrong arity
    rows.append(("ODDFPRICE", "39813, 39500, 39448, 39600, 0.05, 0.05, 100, 2, 0", "#NUM!"))  # settle >= maturity
    rows.append(("ODDFYIELD", "39500, 39813", "#VALUE!"))            # wrong arity
    rows.append(("ODDFYIELD", "39813, 39500, 39448, 39600, 0.05, 95, 100, 2, 0", "#NUM!"))  # settle >= maturity
    rows.append(("ODDLPRICE", "39500, 39813", "#VALUE!"))            # wrong arity
    rows.append(("ODDLPRICE", "39813, 39500, 39700, 0.05, 0.05, 100, 2, 0", "#NUM!"))  # settle >= maturity
    rows.append(("ODDLYIELD", "39500, 39813", "#VALUE!"))            # wrong arity
    rows.append(("ODDLYIELD", "39813, 39500, 39700, 0.05, 95, 100, 2, 0", "#NUM!"))  # settle >= maturity

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
