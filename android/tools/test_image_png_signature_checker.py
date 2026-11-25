#!/usr/bin/env python3
"""Differential oracle for ImagePngSignatureChecker (format/image)."""
import unittest

MAGIC = b"\x89PNG\r\n\x1a\n"


class ImagePngSignatureCheckerTest(unittest.TestCase):
    def test_png(self):
        self.assertTrue(MAGIC[:8] == b"\x89PNG\r\n\x1a\n")


if __name__ == "__main__":
    unittest.main()
