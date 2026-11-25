#!/usr/bin/env python3
"""Differential oracle for CastV2NamespaceValidator (cast/castv2)."""
import re
import unittest

NS_RE = re.compile(r"^urn:x-cast:[a-zA-Z0-9._-]+$")


def is_valid(ns: str) -> bool:
    return bool(ns and len(ns) <= 128 and NS_RE.match(ns))


class CastV2NamespaceValidatorTest(unittest.TestCase):
    def test_valid(self):
        self.assertTrue(is_valid("urn:x-cast:com.google.cast.media"))
        self.assertFalse(is_valid("urn:other:foo"))


if __name__ == "__main__":
    unittest.main()
