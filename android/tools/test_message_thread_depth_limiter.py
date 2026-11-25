#!/usr/bin/env python3
"""Differential oracle for MessageThreadDepthLimiter (feature/messages)."""
import unittest


def depth_of(msg_id: str, parent_of: dict) -> int:
    cur = msg_id
    depth = 0
    seen = set()
    while cur is not None:
        assert cur not in seen
        seen.add(cur)
        parent = parent_of.get(cur)
        if parent is None:
            break
        depth += 1
        assert depth <= 64
        cur = parent
    return depth


class MessageThreadDepthLimiterTest(unittest.TestCase):
    def test_depth(self):
        self.assertEqual(depth_of("m3", {"m1": None, "m2": "m1", "m3": "m2"}), 2)
        with self.assertRaises(AssertionError):
            depth_of("a", {"a": "b", "b": "a"})


if __name__ == "__main__":
    unittest.main()
