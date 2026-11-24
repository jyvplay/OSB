#!/usr/bin/env python3
"""Independent differential oracle for Units.convert (feature/calc)."""
from __future__ import annotations

import math
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_VALUE = 10_000_000_000_000_000.0

DIMENSIONS = {
    "length": {
        "m": 1.0, "cm": 0.01, "mm": 0.001, "km": 1000.0,
        "in": 0.0254, "ft": 0.3048, "yd": 0.9144, "mi": 1609.344,
    },
    "mass": {
        "kg": 1.0, "g": 0.001, "mg": 0.000001, "t": 1000.0,
        "lb": 0.45359237, "oz": 0.028349523125,
    },
    "time": {
        "s": 1.0, "ms": 0.001, "min": 60.0, "h": 3600.0,
        "d": 86400.0,
    },
}

TEMPERATURE_UNITS = {"c", "f", "k"}


def convert(value, from_unit, to_unit):
    assert math.isfinite(value), "value must be finite"
    assert abs(value) <= MAX_VALUE, "value magnitude exceeds limit"
    assert from_unit and from_unit.strip(), "fromUnit must not be blank"
    assert to_unit and to_unit.strip(), "toUnit must not be blank"

    f = from_unit.lower()
    t = to_unit.lower()

    if f in TEMPERATURE_UNITS and t in TEMPERATURE_UNITS:
        if f == "c":
            celsius = value
        elif f == "f":
            celsius = (value - 32.0) * 5.0 / 9.0
        elif f == "k":
            celsius = value - 273.15
        else:
            raise AssertionError("unreachable")

        if t == "c":
            result = celsius
        elif t == "f":
            result = celsius * 9.0 / 5.0 + 32.0
        elif t == "k":
            result = celsius + 273.15
        else:
            raise AssertionError("unreachable")

        assert math.isfinite(result), "non-finite result"
        return {"value": result, "fromUnit": from_unit, "toUnit": to_unit, "dimension": "temperature"}

    for dim, units in DIMENSIONS.items():
        if f in units and t in units:
            from_mult = units[f]
            to_mult = units[t]
            assert from_mult != 0.0 and to_mult != 0.0, "unit multiplier must not be zero"
            result = value * from_mult / to_mult
            assert math.isfinite(result), "non-finite result"
            return {"value": result, "fromUnit": from_unit, "toUnit": to_unit, "dimension": dim}

    raise AssertionError("unknown units or mismatched dimensions")


class UnitsTest(unittest.TestCase):
    def test_length_m_to_cm(self):
        r = convert(1.0, "m", "cm")
        self.assertAlmostEqual(r["value"], 100.0)
        self.assertEqual(r["dimension"], "length")

    def test_length_km_to_m(self):
        r = convert(1.5, "km", "m")
        self.assertAlmostEqual(r["value"], 1500.0)

    def test_length_in_to_cm(self):
        r = convert(1.0, "in", "cm")
        self.assertAlmostEqual(r["value"], 2.54)

    def test_mass_kg_to_g(self):
        r = convert(1.0, "kg", "g")
        self.assertAlmostEqual(r["value"], 1000.0)

    def test_mass_lb_to_kg(self):
        r = convert(1.0, "lb", "kg")
        self.assertAlmostEqual(r["value"], 0.45359237)

    def test_time_min_to_s(self):
        r = convert(5.0, "min", "s")
        self.assertAlmostEqual(r["value"], 300.0)

    def test_temp_c_to_f(self):
        r = convert(0.0, "c", "f")
        self.assertAlmostEqual(r["value"], 32.0)
        self.assertEqual(r["dimension"], "temperature")

    def test_temp_f_to_c(self):
        r = convert(32.0, "f", "c")
        self.assertAlmostEqual(r["value"], 0.0)

    def test_temp_c_to_k(self):
        r = convert(0.0, "c", "k")
        self.assertAlmostEqual(r["value"], 273.15)

    def test_temp_k_to_c(self):
        r = convert(273.15, "k", "c")
        self.assertAlmostEqual(r["value"], 0.0)

    def test_negative_value(self):
        r = convert(-40.0, "c", "f")
        self.assertAlmostEqual(r["value"], -40.0)

    def test_same_unit(self):
        r = convert(42.0, "m", "m")
        self.assertAlmostEqual(r["value"], 42.0)

    def test_value_too_large(self):
        with self.assertRaises(AssertionError):
            convert(MAX_VALUE * 2, "m", "cm")

    def test_non_finite_value(self):
        with self.assertRaises(AssertionError):
            convert(float("inf"), "m", "cm")

    def test_unknown_unit(self):
        with self.assertRaises(AssertionError):
            convert(1.0, "m", "furlong")

    def test_mismatched_dimensions(self):
        with self.assertRaises(AssertionError):
            convert(1.0, "m", "kg")

    def test_blank_from_unit(self):
        with self.assertRaises(AssertionError):
            convert(1.0, "", "m")

    def test_blank_to_unit(self):
        with self.assertRaises(AssertionError):
            convert(1.0, "m", "")

    def test_units_for_dimension(self):
        length_units = sorted(["m", "cm", "mm", "km", "in", "ft", "yd", "mi"])
        self.assertEqual(sorted(DIMENSIONS["length"].keys()), length_units)
        temp_units = sorted(["c", "f", "k"])
        self.assertEqual(sorted(TEMPERATURE_UNITS), temp_units)


if __name__ == "__main__":
    unittest.main()
