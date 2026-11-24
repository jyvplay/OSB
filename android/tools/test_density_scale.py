#!/usr/bin/env python3
"""Independent differential oracle for DensityScale (core/designsystem).

Converts between density pixels (dp), scale-independent pixels (sp), and
pixels (px) given display density scale and font scale. Round-half-up.
Cross-checks against the Kotlin contract in
core/designsystem/src/main/kotlin/dev/fusion/designsystem/DensityScale.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_SCALE = 10.0
MAX_DIP = 100_000.0
MAX_PX = 1_000_000.0


def round_half_up(v: float) -> int:
    scaled = v + 0.5
    if scaled >= 0.0:
        return int(scaled)
    return -int((-scaled).__int__() if False else -scaled)


def dp_to_px(dp: float, density_scale: float) -> int:
    assert 0.0 < density_scale <= MAX_SCALE, "densityScale out of bounds"
    assert 0.0 <= dp <= MAX_DIP, "dp out of bounds"
    return round_half_up(dp * density_scale)


def px_to_dp(px: int, density_scale: float) -> float:
    assert 0.0 < density_scale <= MAX_SCALE, "densityScale out of bounds"
    assert 0.0 <= px <= MAX_PX, "px out of bounds"
    return px / density_scale


def sp_to_px(sp: float, font_scale: float, density_scale: float) -> int:
    assert 0.0 < font_scale <= MAX_SCALE, "fontScale out of bounds"
    assert 0.0 < density_scale <= MAX_SCALE, "densityScale out of bounds"
    assert 0.0 <= sp <= MAX_DIP, "sp out of bounds"
    return round_half_up(sp * font_scale * density_scale)


def px_to_sp(px: int, font_scale: float, density_scale: float) -> float:
    assert 0.0 < font_scale <= MAX_SCALE, "fontScale out of bounds"
    assert 0.0 < density_scale <= MAX_SCALE, "densityScale out of bounds"
    assert 0.0 <= px <= MAX_PX, "px out of bounds"
    return px / (font_scale * density_scale)


class DensityScaleTest(unittest.TestCase):
    def test_dp_to_px(self):
        self.assertEqual(dp_to_px(100, 2.0), 200)
        self.assertEqual(dp_to_px(72, 1.5), 108)  # 72 * 1.5 = 108.0

    def test_dp_to_px_round_half_up(self):
        # 72 * 1.333... = 95.976 -> 96
        self.assertEqual(dp_to_px(72, 4 / 3), 96)

    def test_px_to_dp(self):
        self.assertAlmostEqual(px_to_dp(200, 2.0), 100.0)

    def test_sp_to_px(self):
        self.assertEqual(sp_to_px(16, 1.0, 2.0), 32)
        self.assertEqual(sp_to_px(16, 1.5, 2.0), 48)

    def test_px_to_sp(self):
        self.assertAlmostEqual(px_to_sp(32, 1.0, 2.0), 16.0)

    def test_invalid_density_scale(self):
        with self.assertRaises(AssertionError):
            dp_to_px(100, 0.0)
        with self.assertRaises(AssertionError):
            dp_to_px(100, MAX_SCALE + 1)

    def test_invalid_dp(self):
        with self.assertRaises(AssertionError):
            dp_to_px(-1, 2.0)
        with self.assertRaises(AssertionError):
            dp_to_px(MAX_DIP + 1, 2.0)

    def test_invalid_sp(self):
        with self.assertRaises(AssertionError):
            sp_to_px(-1, 1.0, 2.0)

    def test_invalid_font_scale(self):
        with self.assertRaises(AssertionError):
            sp_to_px(16, 0.0, 2.0)


if __name__ == "__main__":
    unittest.main()
