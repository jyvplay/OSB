#!/usr/bin/env python3
"""Differential oracle for SettingsSearchMatcher (feature/settings)."""
import unittest


def score(query: str, title: str, summary: str) -> int:
    q = query.strip().lower()
    if not q:
        return 0
    t, s = title.lower(), summary.lower()
    pts = 0
    if t == q:
        pts += 100
    elif t.startswith(q):
        pts += 60
    elif q in t:
        pts += 30
    if q in s:
        pts += 10
    return pts


class SettingsSearchMatcherTest(unittest.TestCase):
    def test_score(self):
        self.assertEqual(score("wifi", "WiFi", "Configure wireless"), 100)
        self.assertEqual(score("wire", "WiFi", "Configure wireless"), 10)


if __name__ == "__main__":
    unittest.main()
