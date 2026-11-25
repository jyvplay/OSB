#!/usr/bin/env python3
"""Differential oracle for CapabilityScopeHierarchy (core/capabilities)."""
import unittest


def implies(granted: str, required: str) -> bool:
    return granted == required or required.startswith(f"{granted}.")


class CapabilityScopeHierarchyTest(unittest.TestCase):
    def test_implies(self):
        self.assertTrue(implies("fs.read", "fs.read"))
        self.assertTrue(implies("fs.read", "fs.read.metadata"))
        self.assertFalse(implies("fs.read", "fs.readonly"))


if __name__ == "__main__":
    unittest.main()
