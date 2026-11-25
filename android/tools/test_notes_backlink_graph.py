#!/usr/bin/env python3
"""Differential oracle for NotesBacklinkGraph (feature/notes)."""
import re
import unittest
from collections import deque

LINK_RE = re.compile(r"\[\[([^\]|]+)(?:\|[^\]]*)?\]\]")


def norm_slug(s: str) -> str:
    return re.sub(r"\s+", "-", s.strip().lower())


class NotesBacklinkGraph:
    def __init__(self):
        self.fwd, self.bwd = {}, {}

    def index(self, slug: str, md: str):
        n = norm_slug(slug)
        for old in self.fwd.get(n, ()):
            self.bwd.get(old, set()).discard(n)
        targets = {norm_slug(m.group(1)) for m in LINK_RE.finditer(md)}
        self.fwd[n] = targets
        self.bwd.setdefault(n, set())
        for t in targets:
            self.bwd.setdefault(t, set()).add(n)

    def reachable(self, a: str, b: str) -> bool:
        s, g = norm_slug(a), norm_slug(b)
        if s == g:
            return s in self.fwd
        vis, q = {s}, deque([s])
        while q:
            cur = q.popleft()
            for nxt in self.fwd.get(cur, ()):
                if nxt == g:
                    return True
                if nxt not in vis:
                    vis.add(nxt)
                    q.append(nxt)
        return False


class NotesBacklinkGraphTest(unittest.TestCase):
    def test_graph(self):
        g = NotesBacklinkGraph()
        g.index("A", "See [[B|b]]")
        g.index("B", "See [[C]]")
        g.index("C", "Cycle [[A]]")
        self.assertTrue(g.reachable("a", "c"))
        self.assertEqual(g.bwd["b"], {"a"})


if __name__ == "__main__":
    unittest.main()
