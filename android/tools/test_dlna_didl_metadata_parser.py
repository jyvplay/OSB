#!/usr/bin/env python3
"""Differential oracle for DlnaDidlMetadataParser (cast/dlna)."""
import re
import unittest


def extract_first(xml: str):
    t = re.search(r"<dc:title>([^<]+)</dc:title>", xml)
    if not t:
        return None
    u = re.search(r"<res[^>]*>([^<]+)</res>", xml)
    return t.group(1).strip(), (u.group(1).strip() if u else "")


class DlnaDidlMetadataParserTest(unittest.TestCase):
    def test_didl(self):
        xml = '<DIDL-Lite><item><dc:title>Song</dc:title><res protocolInfo="http-get:*:audio/mpeg:*">http://1.2.3.4/a.mp3</res></item></DIDL-Lite>'
        self.assertEqual(extract_first(xml), ("Song", "http://1.2.3.4/a.mp3"))


if __name__ == "__main__":
    unittest.main()
