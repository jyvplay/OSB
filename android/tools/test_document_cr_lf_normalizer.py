#!/usr/bin/env python3
"""Differential oracle for DocumentCrLfNormalizer (core/documents)."""
import unittest


def normalize(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n")


class DocumentCrLfNormalizerTest(unittest.TestCase):
    def test_norm(self):
        self.assertEqual(normalize("a\r\nb\rc\n"), "a\nb\nc\n")


if __name__ == "__main__":
    unittest.main()
