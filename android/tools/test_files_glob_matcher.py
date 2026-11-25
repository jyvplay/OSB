#!/usr/bin/env python3
"""Differential oracle for FilesGlobMatcher (feature/files)."""
import unittest


def match_seg(pat: str, seg: str) -> bool:
    pi = si = 0
    star, match = -1, 0
    while si < len(seg):
        if pi < len(pat) and pat[pi] in ("?", seg[si]):
            pi += 1
            si += 1
        elif pi < len(pat) and pat[pi] == "*":
            star, match = pi, si
            pi += 1
        elif star != -1:
            pi = star + 1
            match += 1
            si = match
        else:
            return False
    while pi < len(pat) and pat[pi] == "*":
        pi += 1
    return pi == len(pat)


def matches(pattern: str, path: str) -> bool:
    pat_s = [s for s in pattern.split("/") if s]
    path_s = [s for s in path.split("/") if s]

    def rec(pi, si):
        if pi == len(pat_s) and si == len(path_s):
            return True
        if pi == len(pat_s):
            return False
        if pat_s[pi] == "**":
            return any(rec(pi + 1, k) for k in range(si, len(path_s) + 1))
        if si == len(path_s):
            return False
        return match_seg(pat_s[pi], path_s[si]) and rec(pi + 1, si + 1)

    return rec(0, 0)


class FilesGlobMatcherTest(unittest.TestCase):
    def test_glob(self):
        self.assertTrue(matches("src/**/*.kt", "src/main/kotlin/App.kt"))
        self.assertFalse(matches("src/*.kt", "src/main/App.kt"))


if __name__ == "__main__":
    unittest.main()
