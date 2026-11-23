#!/usr/bin/env python3
"""R132 oracle generator for complex trigonometric/hyperbolic functions: IMCSC, IMSEC, IMSECH, IMSINH, IMTAN.

Pure-stdlib implementation. Expected values computed using cmath.

IMCSC(z)  = 1 / sin(z)
IMSEC(z)  = 1 / cos(z)
IMSECH(z) = 1 / cosh(z)
IMSINH(z) = sinh(z)
IMTAN(z)  = tan(z)

All return #VALUE! on wrong arity, #NUM! on non-complex input.
"""
from __future__ import annotations

import cmath
import sys


def fmt_complex(re: float, im: float) -> str:
    """Format a complex number matching Kotlin's formatComplex exactly."""
    if abs(re) < 1e-12:
        re = 0.0
    if abs(im) < 1e-12:
        im = 0.0

    def fmt_num(d: float) -> str:
        r = round(d)  # Kotlin Math.round -> nearest integer as Long
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


def render() -> str:
    rows = [("FUNCTION", "ARGS", "EXPECTED")]

    test_cases = [
        "1+2i", "3-4i", "0-0i", "5i", "2+3i",
        "0+0i", "1+0i", "0+1i", "-1+0i", "10+0i",
    ]

    for z_str in test_cases:
        z = complex(z_str.replace("i", "j"))

        # IMCSC = 1/sin(z)
        s = cmath.sin(z)
        if abs(s) < 1e-12:
            rows.append(("IMCSC", z_str, "#DIV/0!"))
        else:
            inv = 1 / s
            rows.append(("IMCSC", z_str, fmt_complex(inv.real, inv.imag)))

        # IMSEC = 1/cos(z)
        c = cmath.cos(z)
        if abs(c) < 1e-12:
            rows.append(("IMSEC", z_str, "#DIV/0!"))
        else:
            inv = 1 / c
            rows.append(("IMSEC", z_str, fmt_complex(inv.real, inv.imag)))

        # IMSECH = 1/cosh(z)
        ch = cmath.cosh(z)
        if abs(ch) < 1e-12:
            rows.append(("IMSECH", z_str, "#DIV/0!"))
        else:
            inv = 1 / ch
            rows.append(("IMSECH", z_str, fmt_complex(inv.real, inv.imag)))

        # IMSINH = sinh(z)
        sh = cmath.sinh(z)
        rows.append(("IMSINH", z_str, fmt_complex(sh.real, sh.imag)))

        # IMTAN = tan(z)
        t = cmath.tan(z)
        if abs(t) > 1e12:
            rows.append(("IMTAN", z_str, "#DIV/0!"))
        else:
            rows.append(("IMTAN", z_str, fmt_complex(t.real, t.imag)))

    # Refusals - each function needs >= 2 refusal rows
    rows.append(("IMCSC", "", "#VALUE!"))                # wrong arity: 0 args
    rows.append(("IMCSC", "notcomplex", "#NUM!"))        # invalid complex
    rows.append(("IMSEC", "1+2i,3-4i", "#VALUE!"))       # wrong arity: 2 args
    rows.append(("IMSEC", "abc", "#NUM!"))               # invalid complex
    rows.append(("IMSECH", "", "#VALUE!"))               # wrong arity: 0 args
    rows.append(("IMSECH", "notcomplex", "#NUM!"))       # invalid complex
    rows.append(("IMSINH", "1+2i,3-4i", "#VALUE!"))      # wrong arity: 2 args
    rows.append(("IMSINH", "abc", "#NUM!"))              # invalid complex
    rows.append(("IMTAN", "", "#VALUE!"))                # wrong arity: 0 args
    rows.append(("IMTAN", "notcomplex", "#NUM!"))        # invalid complex

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
