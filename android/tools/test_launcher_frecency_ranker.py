#!/usr/bin/env python3
"""Differential oracle for LauncherFrecencyRanker (feature/launcher)."""
import math
import unittest


def rank_apps(apps, half_life=24.0):
    lam = math.log(2.0) / half_life
    return [
        a[0]
        for a in sorted(
            apps,
            key=lambda x: (not x[3], -(x[1] * math.exp(-lam * x[2])), x[0]),
        )
    ]


class LauncherFrecencyRankerTest(unittest.TestCase):
    def test_rank(self):
        apps = [("a.pkg", 10, 1.0, False), ("b.pkg", 1, 100.0, True), ("c.pkg", 5, 0.0, False)]
        self.assertEqual(rank_apps(apps), ["b.pkg", "a.pkg", "c.pkg"])


if __name__ == "__main__":
    unittest.main()
