#!/usr/bin/env python3
"""Differential oracle for PreferenceKeyNamespace (core/settings)."""
import re
import unittest

KEY_RE = re.compile(r"^([a-z][a-z0-9_-]{0,31})\.([a-z][a-z0-9_.-]{0,94})$")


def extract_domain(key: str) -> str:
    m = KEY_RE.match(key)
    if not m:
        raise ValueError(key)
    return m.group(1)


class PreferenceKeyNamespaceTest(unittest.TestCase):
    def test_domain(self):
        self.assertEqual(extract_domain("editor.tab_size"), "editor")
        with self.assertRaises(ValueError):
            extract_domain("nodomain")


if __name__ == "__main__":
    unittest.main()
