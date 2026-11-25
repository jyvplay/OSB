#!/usr/bin/env python3
"""Differential oracle for NotesLinkSlugNormalizer (feature/notes)."""
import re
import unittest


def to_slug(title: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", title.strip().lower()).strip("-")


class NotesLinkSlugNormalizerTest(unittest.TestCase):
    def test_slug(self):
        self.assertEqual(to_slug("  Hello, World!  "), "hello-world")


if __name__ == "__main__":
    unittest.main()
