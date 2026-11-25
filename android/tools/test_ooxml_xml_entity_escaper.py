#!/usr/bin/env python3
"""Differential oracle for OoxmlXmlEntityEscaper (format/ooxml)."""
import unittest


def escape_xml(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;").replace("'", "&apos;")


class OoxmlXmlEntityEscaperTest(unittest.TestCase):
    def test_escape(self):
        self.assertEqual(escape_xml('<a & "b">'), "&lt;a &amp; &quot;b&quot;&gt;")


if __name__ == "__main__":
    unittest.main()
