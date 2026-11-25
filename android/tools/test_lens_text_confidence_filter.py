#!/usr/bin/env python3
"""Differential oracle for LensTextConfidenceFilter (feature/lens)."""
import unittest


def filter_blocks(blocks, min_conf: float):
    return [(t, c) for t, c in blocks if c >= min_conf and t.strip()]


class LensTextConfidenceFilterTest(unittest.TestCase):
    def test_filter(self):
        self.assertEqual(filter_blocks([("hi", 0.9), (" ", 0.99), ("lo", 0.2)], 0.5), [("hi", 0.9)])


if __name__ == "__main__":
    unittest.main()
