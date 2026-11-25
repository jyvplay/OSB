#!/usr/bin/env python3
"""Differential oracle for IndexPrefixRangeBounds (core/index)."""
import bisect
import unittest


def prefix_range(sorted_list, prefix: str):
    lo = bisect.bisect_left(sorted_list, prefix)
    hi = lo
    while hi < len(sorted_list) and sorted_list[hi].startswith(prefix):
        hi += 1
    return range(lo, hi)


class IndexPrefixRangeBoundsTest(unittest.TestCase):
    def test_range(self):
        items = ["alpha", "app", "apple", "banana"]
        self.assertEqual(list(prefix_range(items, "app")), [1, 2])


if __name__ == "__main__":
    unittest.main()
