#!/usr/bin/env python3
"""Differential oracle for CommandAliasExpander (core/commands)."""
import unittest


def expand(cmd: str, aliases: dict) -> str:
    cur = cmd
    seen = set()
    depth = 0
    while cur in aliases:
        assert cur not in seen
        assert depth < 8
        seen.add(cur)
        cur = aliases[cur]
        depth += 1
    return cur


class CommandAliasExpanderTest(unittest.TestCase):
    def test_expand(self):
        self.assertEqual(expand("ll", {"ll": "ls -la"}), "ls -la")
        with self.assertRaises(AssertionError):
            expand("a", {"a": "b", "b": "a"})


if __name__ == "__main__":
    unittest.main()
