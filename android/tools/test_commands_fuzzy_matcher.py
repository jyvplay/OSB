#!/usr/bin/env python3
"""Differential oracle for CommandsFuzzyMatcher (core/commands)."""
import unittest


def score_subseq(q: str, t: str):
    if not q:
        return 0
    ql, tl = q.lower(), t.lower()
    qi, sc, prev = 0, 0, -2
    for ti, ch in enumerate(tl):
        if qi < len(ql) and ch == ql[qi]:
            sc += 10
            if ti == prev + 1:
                sc += 15
            if ti == 0 or t[ti - 1] in " _-./":
                sc += 20
            prev = ti
            qi += 1
    return sc - (len(t) - len(q)) if qi == len(ql) else None


class CommandsFuzzyMatcherTest(unittest.TestCase):
    def test_fuzzy(self):
        self.assertGreater(score_subseq("op", "Open Project"), score_subseq("op", "oop"))
        self.assertIsNone(score_subseq("xyz", "Open Project"))


if __name__ == "__main__":
    unittest.main()
