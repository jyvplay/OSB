#!/usr/bin/env python3
"""Differential oracle for TestFixtureSeedRegistry (testing)."""
import re
import unittest

ID_RE = re.compile(r"[a-zA-Z0-9_.-]{1,128}")


class TestFixtureSeedRegistry:
    def __init__(self):
        self.seeds = {}

    def register(self, test_id: str, seed: int):
        assert len(test_id) <= 128 and ID_RE.fullmatch(test_id)
        assert len(self.seeds) < 2048 or test_id in self.seeds
        self.seeds[test_id] = seed


class TestFixtureSeedRegistryTest(unittest.TestCase):
    def test_register_and_get(self):
        reg = TestFixtureSeedRegistry()
        reg.register("fuzz.case.1", 42)
        self.assertEqual(reg.seeds["fuzz.case.1"], 42)

    def test_invalid_id(self):
        reg = TestFixtureSeedRegistry()
        with self.assertRaises(AssertionError):
            reg.register("bad id!", 1)


if __name__ == "__main__":
    unittest.main()
