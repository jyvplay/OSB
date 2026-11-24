#!/usr/bin/env python3
"""Independent differential oracle for GeometryClamp (core/windowing).

Implements bounded window-geometry clamper: clamps a window rectangle (left,
top, width, height) to a work area, enforcing minimum and maximum size
constraints. Multiple work areas (displays) are supported; the window is
clamped to the work area whose center is closest to the window's center.
Cross-checks against the Kotlin contract in
core/windowing/src/main/kotlin/dev/fusion/windowing/GeometryClamp.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_DISPLAYS = 16
MAX_COORD = 1 << 16  # 65536


class Rect:
    def __init__(self, left: int, top: int, right: int, bottom: int):
        self.left = left
        self.top = top
        self.right = right
        self.bottom = bottom

    @property
    def width(self) -> int:
        return self.right - self.left

    @property
    def height(self) -> int:
        return self.bottom - self.top

    @property
    def center_x(self) -> int:
        return (self.left + self.right) // 2

    @property
    def center_y(self) -> int:
        return (self.top + self.bottom) // 2


class ClampedRect:
    def __init__(self, left: int, top: int, right: int, bottom: int):
        self.left = left
        self.top = top
        self.right = right
        self.bottom = bottom

    @property
    def width(self) -> int:
        return self.right - self.left

    @property
    def height(self) -> int:
        return self.bottom - self.top


class GeometryClamp:
    def __init__(self):
        self._work_areas = []

    def add_work_area(self, left: int, top: int, right: int, bottom: int):
        assert len(self._work_areas) < MAX_DISPLAYS, f"too many work areas: {MAX_DISPLAYS}"
        assert left >= 0 and top >= 0 and right > left and bottom > top, "invalid work area"
        assert left <= MAX_COORD and top <= MAX_COORD and right <= MAX_COORD and bottom <= MAX_COORD, f"coordinate exceeds {MAX_COORD}"
        self._work_areas.append(Rect(left, top, right, bottom))

    def clamp(self, left: int, top: int, width: int, height: int,
              min_width: int = 1, min_height: int = 1,
              max_width: int = MAX_COORD, max_height: int = MAX_COORD) -> ClampedRect:
        assert width >= 0 and height >= 0, "negative dimension"
        assert min_width >= 1 and min_height >= 1, "minimum size must be >= 1"
        assert max_width >= min_width and max_height >= min_height, "max smaller than min"
        assert max_width <= MAX_COORD and max_height <= MAX_COORD, f"max exceeds {MAX_COORD}"

        w = max(min_width, min(width, max_width))
        h = max(min_height, min(height, max_height))

        if not self._work_areas:
            target = Rect(0, 0, MAX_COORD, MAX_COORD)
        else:
            cx = left + w // 2
            cy = top + h // 2
            target = min(self._work_areas,
                         key=lambda r: abs(r.center_x - cx) + abs(r.center_y - cy))

        x = max(target.left, min(left, target.right - w))
        y = max(target.top, min(top, target.bottom - h))

        x = max(target.left, min(x, target.right - w))
        y = max(target.top, min(y, target.bottom - h))

        w = max(min_width, min(w, max(target.right - x, min_width)))
        h = max(min_height, min(h, max(target.bottom - y, min_height)))
        w = min(w, max_width)
        h = min(h, max_height)

        return ClampedRect(x, y, x + w, y + h)

    def clamp_to_work_area(self, left: int, top: int, width: int, height: int) -> ClampedRect:
        return self.clamp(left, top, width, height)


class GeometryClampTest(unittest.TestCase):
    def setUp(self):
        self.clamp = GeometryClamp()

    def test_no_work_areas(self):
        result = self.clamp.clamp(100, 100, 500, 400)
        self.assertEqual(result.left, 100)
        self.assertEqual(result.top, 100)
        self.assertEqual(result.width, 500)
        self.assertEqual(result.height, 400)

    def test_clamp_to_work_area(self):
        self.clamp.add_work_area(0, 0, 1920, 1080)
        result = self.clamp.clamp(100, 100, 800, 600)
        self.assertEqual(result.left, 100)
        self.assertEqual(result.top, 100)
        self.assertEqual(result.width, 800)
        self.assertEqual(result.height, 600)

    def test_clamp_right_edge(self):
        self.clamp.add_work_area(0, 0, 1920, 1080)
        result = self.clamp.clamp(1800, 100, 500, 400)
        self.assertLessEqual(result.right, 1920)
        self.assertEqual(result.left, 1420)  # 1920 - 500, window moved left to fit
        self.assertEqual(result.width, 500)

    def test_clamp_bottom_edge(self):
        self.clamp.add_work_area(0, 0, 1920, 1080)
        result = self.clamp.clamp(100, 900, 400, 400)
        self.assertLessEqual(result.bottom, 1080)
        self.assertEqual(result.top, 680)  # 1080 - 400, window moved up to fit
        self.assertEqual(result.height, 400)

    def test_min_size_enforced(self):
        self.clamp.add_work_area(0, 0, 1920, 1080)
        result = self.clamp.clamp(1800, 1000, 500, 400, min_width=100, min_height=100)
        self.assertGreaterEqual(result.width, 100)
        self.assertGreaterEqual(result.height, 100)

    def test_max_size_enforced(self):
        self.clamp.add_work_area(0, 0, 1920, 1080)
        result = self.clamp.clamp(100, 100, 5000, 4000, max_width=1000, max_height=800)
        self.assertEqual(result.width, 1000)
        self.assertEqual(result.height, 800)

    def test_negative_dimension_refused(self):
        with self.assertRaises(AssertionError):
            self.clamp.clamp(100, 100, -100, 400)

    def test_min_size_zero_refused(self):
        with self.assertRaises(AssertionError):
            self.clamp.clamp(100, 100, 100, 100, min_width=0, min_height=1)

    def test_max_less_than_min_refused(self):
        with self.assertRaises(AssertionError):
            self.clamp.clamp(100, 100, 100, 100, min_width=100, max_width=50)

    def test_multiple_displays_closest_center(self):
        self.clamp.add_work_area(0, 0, 1920, 1080)      # center (960, 540)
        self.clamp.add_work_area(1920, 0, 3840, 1080)   # center (2880, 540)
        result = self.clamp.clamp(1000, 500, 400, 300)
        # Window center at (1200, 650) — closer to display 1 center (960, 540)
        self.assertEqual(result.left, 1000)
        self.assertEqual(result.top, 500)

    def test_too_many_work_areas(self):
        for i in range(MAX_DISPLAYS):
            self.clamp.add_work_area(i * 1920, 0, (i + 1) * 1920, 1080)
        with self.assertRaises(AssertionError):
            self.clamp.add_work_area(MAX_DISPLAYS * 1920, 0, (MAX_DISPLAYS + 1) * 1920, 1080)

    def test_invalid_work_area(self):
        with self.assertRaises(AssertionError):
            self.clamp.add_work_area(100, 100, 50, 60)  # right < left
        with self.assertRaises(AssertionError):
            self.clamp.add_work_area(-1, 0, 100, 100)   # negative left

    def test_coordinate_exceeds_max(self):
        with self.assertRaises(AssertionError):
            self.clamp.add_work_area(0, 0, MAX_COORD + 1, 1080)


if __name__ == "__main__":
    unittest.main()
