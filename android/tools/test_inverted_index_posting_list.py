#!/usr/bin/env python3
"""Differential oracle for InvertedIndexPostingList (core/index)."""
import unittest


def intersect(a, b):
    res = []
    i = j = 0
    while i < len(a) and j < len(b):
        if a[i] == b[j]:
            if not res or res[-1] != a[i]:
                res.append(a[i])
            i += 1
            j += 1
        elif a[i] < b[j]:
            i += 1
        else:
            j += 1
    return res


class InvertedIndexPostingListTest(unittest.TestCase):
    def test_intersect(self):
        self.assertEqual(intersect([1, 2, 2, 5, 9], [2, 3, 5, 10]), [2, 5])
        self.assertEqual(intersect([], [1, 2]), [])


if __name__ == "__main__":
    unittest.main()
