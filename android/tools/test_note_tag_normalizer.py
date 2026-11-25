#!/usr/bin/env python3
"""Differential oracle for NoteTagNormalizer (feature/notes)."""
import re
import unittest

TAG_RE = re.compile(r"^[a-z0-9_-]{1,64}$")


def normalize_all(raw_tags):
    out = []
    seen = set()
    for raw in raw_tags:
        norm = raw.strip().lstrip("#").lower()
        assert TAG_RE.match(norm)
        if norm not in seen:
            seen.add(norm)
            out.append(norm)
    return out


class NoteTagNormalizerTest(unittest.TestCase):
    def test_normalize(self):
        self.assertEqual(normalize_all(["#Work", "work", "  #TODO "]), ["work", "todo"])


if __name__ == "__main__":
    unittest.main()
