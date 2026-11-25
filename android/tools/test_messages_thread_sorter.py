#!/usr/bin/env python3
"""Differential oracle for MessagesThreadSorter (feature/messages)."""
import unittest


def build_thread_order(messages):
    by_id = {m[0]: m for m in messages}
    children = {}
    for m in sorted(messages, key=lambda x: x[2]):
        mid, parent, ts = m
        p = parent if (parent is not None and parent in by_id and parent != mid) else None
        children.setdefault(p, []).append(m)
    res, vis = [], set()

    def dfs(pid, depth):
        for ch in children.get(pid, []):
            if ch[0] not in vis:
                vis.add(ch[0])
                res.append((ch[0], depth))
                dfs(ch[0], depth + 1)

    dfs(None, 0)
    return res


class MessagesThreadSorterTest(unittest.TestCase):
    def test_thread(self):
        msgs = [("m1", None, 10), ("m2", "m1", 20), ("m3", "m1", 30), ("m4", "m2", 25)]
        self.assertEqual(build_thread_order(msgs), [("m1", 0), ("m2", 1), ("m4", 2), ("m3", 1)])


if __name__ == "__main__":
    unittest.main()
