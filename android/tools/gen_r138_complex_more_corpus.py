#!/usr/bin/env python3
"""Pure-stdlib R138 oracle generator for IMCSCH, IMLOG2, IMLOG10.

IMCSCH(inumber) — hyperbolic cosecant of a complex number = 1/sinh(z)
IMLOG2(inumber) — base-2 logarithm of a complex number
IMLOG10(inumber) — base-10 logarithm of a complex number

Complex numbers in x+yi or x+yj format. Expected column is the result text.
Refusals are #NUM! / #DIV/0! literals.
"""
import sys
import math

ROWS: list[tuple[str, str, str]] = []

def fmt_complex(re, im, suffix='i'):
    """Format a complex number matching the Kotlin formatComplex output."""
    if abs(re) < 1e-12:
        re = 0.0
    if abs(im) < 1e-12:
        im = 0.0
    def fmt_num(d):
        r = round(d)
        if abs(d - r) < 1e-11 and abs(r) < 1e15:
            return str(int(r))
        return str(d)
    if im == 0.0:
        return fmt_num(re)
    if im == 1.0:
        im_str = suffix
    elif im == -1.0:
        im_str = f"-{suffix}"
    else:
        im_str = f"{fmt_num(im)}{suffix}"
    if re == 0.0:
        return im_str
    if im > 0.0:
        return f"{fmt_num(re)}+{im_str}"
    else:
        return f"{fmt_num(re)}{im_str}"

def imcsch(re, im, suffix='i'):
    sinh_re = math.sinh(re) * math.cos(im)
    sinh_im = math.cosh(re) * math.sin(im)
    den = sinh_re * sinh_re + sinh_im * sinh_im
    if den == 0.0:
        return "#DIV/0!"
    return fmt_complex(sinh_re / den, -sinh_im / den, suffix)

def imlog(re, im, base, suffix='i'):
    r = math.hypot(re, im)
    if r == 0.0:
        return "#NUM!"
    theta = math.atan2(im, re)
    return fmt_complex(math.log(r) / base, theta / base, suffix)

# ---- IMCSCH tests ----
ROWS.append(("IMCSCH", '"0+1i"', imcsch(0, 1)))
ROWS.append(("IMCSCH", '"1+0i"', imcsch(1, 0)))
ROWS.append(("IMCSCH", '"1+1i"', imcsch(1, 1)))
ROWS.append(("IMCSCH", '"2+3i"', imcsch(2, 3)))
ROWS.append(("IMCSCH", '"0+0j"', imcsch(0, 0, 'j')))  # j suffix
ROWS.append(("IMCSCH", '"1"', imcsch(1, 0)))  # real-only

# ---- IMLOG2 tests ----
ROWS.append(("IMLOG2", '"1+0i"', imlog(1, 0, math.log(2))))
ROWS.append(("IMLOG2", '"2+0i"', imlog(2, 0, math.log(2))))  # log2(2) = 1
ROWS.append(("IMLOG2", '"4+0i"', imlog(4, 0, math.log(2))))  # log2(4) = 2
ROWS.append(("IMLOG2", '"1+1i"', imlog(1, 1, math.log(2))))
ROWS.append(("IMLOG2", '"0+0i"', "#NUM!"))  # log of zero
ROWS.append(("IMLOG2", '"1+0j"', imlog(1, 0, math.log(2), 'j')))  # j suffix

# ---- IMLOG10 tests ----
ROWS.append(("IMLOG10", '"1+0i"', imlog(1, 0, math.log(10))))
ROWS.append(("IMLOG10", '"10+0i"', imlog(10, 0, math.log(10))))  # log10(10) = 1
ROWS.append(("IMLOG10", '"100+0i"', imlog(100, 0, math.log(10))))  # log10(100) = 2
ROWS.append(("IMLOG10", '"1+1i"', imlog(1, 1, math.log(10))))
ROWS.append(("IMLOG10", '"0+0i"', "#NUM!"))  # log of zero

# ---- Error propagation ----
ROWS.append(("IMCSCH", "#VALUE!", "#VALUE!"))
ROWS.append(("IMLOG2", "#VALUE!", "#VALUE!"))
ROWS.append(("IMLOG10", "#VALUE!", "#VALUE!"))

OUT = ["FUNCTION\tARGS\tEXPECTED"]
for fn, args, expected in ROWS:
    OUT.append(f"{fn}\t{args}\t{expected}")
print("\n".join(OUT))
