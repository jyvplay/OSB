#!/usr/bin/env python3
"""Differential oracle for AutomationDagScheduler (feature/automation)."""
import unittest
from collections import deque


def topo(nodes, edges):
    indeg = {n: 0 for n in nodes}
    adj = {n: [] for n in nodes}
    for u, v in edges:
        adj[u].append(v)
        indeg[v] += 1
    q = deque(n for n, d in indeg.items() if d == 0)
    out = []
    while q:
        cur = q.popleft()
        out.append(cur)
        for nxt in adj[cur]:
            indeg[nxt] -= 1
            if indeg[nxt] == 0:
                q.append(nxt)
    assert len(out) == len(nodes)
    return out


class AutomationDagSchedulerTest(unittest.TestCase):
    def test_dag(self):
        self.assertEqual(topo(["a", "b", "c"], [("a", "b"), ("b", "c")]), ["a", "b", "c"])
        with self.assertRaises(AssertionError):
            topo(["a", "b"], [("a", "b"), ("b", "a")])


if __name__ == "__main__":
    unittest.main()
