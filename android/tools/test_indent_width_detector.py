#!/usr/bin/env python3
"""Differential oracle for IndentWidthDetector (feature/editor)."""
import unittest


def detect(lines):
    tabs = s2 = s4 = s8 = 0
    for line in lines:
        if not line:
            continue
        if line[0] == "\t":
            tabs += 1
        elif line[0] == " ":
            sp = len(line) - len(line.lstrip(" "))
            if sp == len(line):
                continue
            if sp % 8 == 0:
                s8 += 1
            elif sp % 4 == 0:
                s4 += 1
            elif sp % 2 == 0:
                s2 += 1
    mx = max(tabs, s2, s4, s8)
    if mx == 0:
        return "UNKNOWN"
    if mx == tabs:
        return "TABS"
    if mx == s4:
        return "SPACES_4"
    if mx == s2:
        return "SPACES_2"
    return "SPACES_8"


class IndentWidthDetectorTest(unittest.TestCase):
    def test_detect(self):
        self.assertEqual(detect(["    a", "    b", "  c"]), "SPACES_4")
        self.assertEqual(detect(["\ta", "\tb"]), "TABS")


if __name__ == "__main__":
    unittest.main()
