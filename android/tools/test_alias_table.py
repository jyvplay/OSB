#!/usr/bin/env python3
"""Independent differential oracle for AliasTable (core/commands).

Implements bounded shell-alias table with cycle-safe expansion: only argv[0]
is resolved repeatedly up to MAX_DEPTH hops; a revisit (a->b->a) or depth
overflow refuses instead of looping. Cross-checks against the Kotlin contract
in core/commands/src/main/kotlin/dev/fusion/commands/AliasTable.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_ALIASES = 64
MAX_DEPTH = 8
MAX_TOKENS = 8
MAX_TOKEN = 64
NAME_RE = re.compile(r"[a-z][a-z0-9-]{0,31}")


def valid_name(s):
    return NAME_RE.fullmatch(s) is not None


def valid_token(s):
    return bool(s) and len(s) <= MAX_TOKEN and "\u0000" not in s


class AliasTable:
    def __init__(self):
        self._map = {}

    def names(self):
        return set(self._map.keys())

    def get(self, name):
        if name in self._map:
            return list(self._map[name])
        return None

    def define(self, name, expansion):
        assert valid_name(name), f"invalid alias name: {name}"
        assert expansion and len(expansion) <= MAX_TOKENS, "expansion size out of bounds"
        assert all(valid_token(t) for t in expansion), "invalid token in expansion"
        if name not in self._map:
            assert len(self._map) < MAX_ALIASES, "too many aliases"
        self._map[name] = list(expansion)

    def undefine(self, name):
        return self._map.pop(name, None) is not None

    def clear(self):
        self._map.clear()

    def expand(self, argv):
        assert argv and all(valid_token(t) for t in argv), "invalid argv"
        seen = set()
        head = argv[0]
        tail = list(argv[1:])
        hops = 0
        while True:
            exp = self._map.get(head)
            if exp is None:
                return [head] + tail
            assert head not in seen, f"alias cycle at {head}"
            assert hops < MAX_DEPTH, "alias depth exceeded"
            seen.add(head)
            hops += 1
            head = exp[0]
            tail = list(exp[1:]) + tail


class AliasTableTest(unittest.TestCase):
    def setUp(self):
        self.table = AliasTable()

    def test_define_and_expand(self):
        self.table.define("ll", ["ls", "-l"])
        result = self.table.expand(["ll", "/home"])
        self.assertEqual(result, ["ls", "-l", "/home"])

    def test_expand_no_alias(self):
        result = self.table.expand(["ls", "/home"])
        self.assertEqual(result, ["ls", "/home"])

    def test_expand_chain(self):
        self.table.define("ll", ["ls", "-l"])
        self.table.define("l", ["ll", "-a"])
        result = self.table.expand(["l", "/home"])
        self.assertEqual(result, ["ls", "-l", "-a", "/home"])

    def test_alias_cycle_detected(self):
        self.table.define("a", ["b"])
        self.table.define("b", ["a"])
        with self.assertRaises(AssertionError):
            self.table.expand(["a"])

    def test_alias_depth_exceeded(self):
        # Build a chain longer than MAX_DEPTH
        current = "z0"
        for i in range(MAX_DEPTH + 1):
            next_name = f"z{i + 1}"
            self.table.define(current, [next_name])
            current = next_name
        with self.assertRaises(AssertionError):
            self.table.expand(["z0"])

    def test_invalid_alias_name(self):
        with self.assertRaises(AssertionError):
            self.table.define("1bad", ["ls"])
        with self.assertRaises(AssertionError):
            self.table.define("BAD", ["ls"])
        with self.assertRaises(AssertionError):
            self.table.define("a" * 33, ["ls"])

    def test_invalid_token(self):
        with self.assertRaises(AssertionError):
            self.table.define("a", [""])
        with self.assertRaises(AssertionError):
            self.table.define("a", ["x" * 65])

    def test_too_many_aliases(self):
        for i in range(MAX_ALIASES):
            self.table.define(f"alias{i}", ["ls"])
        with self.assertRaises(AssertionError):
            self.table.define("alias64", ["ls"])

    def test_undefine(self):
        self.table.define("a", ["ls"])
        self.assertTrue(self.table.undefine("a"))
        self.assertFalse(self.table.undefine("a"))
        self.assertIsNone(self.table.get("a"))

    def test_clear(self):
        self.table.define("a", ["ls"])
        self.table.clear()
        self.assertEqual(self.table.names(), set())

    def test_names(self):
        self.table.define("a", ["ls"])
        self.table.define("b", ["cat"])
        self.assertEqual(self.table.names(), {"a", "b"})

    def test_empty_argv_rejected(self):
        with self.assertRaises(AssertionError):
            self.table.expand([])

    def test_expansion_preserves_tail(self):
        self.table.define("cmd", ["realcmd"])
        result = self.table.expand(["cmd", "arg1", "arg2"])
        self.assertEqual(result, ["realcmd", "arg1", "arg2"])


if __name__ == "__main__":
    unittest.main()
