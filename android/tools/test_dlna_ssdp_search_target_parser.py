#!/usr/bin/env python3
"""Differential oracle for DlnaSsdpSearchTargetParser (cast/dlna)."""
import re
import unittest

URN_RE = re.compile(r"^urn:[a-zA-Z0-9._-]+:(device|service):[a-zA-Z0-9._-]+:[1-9][0-9]*$")
UUID_RE = re.compile(r"^uuid:[a-fA-F0-9-]{8,64}$")


def is_valid(st: str) -> bool:
    if not st or len(st) > 256:
        return False
    if st in ("ssdp:all", "upnp:rootdevice"):
        return True
    return bool(URN_RE.match(st) or UUID_RE.match(st))


class DlnaSsdpSearchTargetParserTest(unittest.TestCase):
    def test_valid(self):
        self.assertTrue(is_valid("ssdp:all"))
        self.assertTrue(is_valid("urn:schemas-upnp-org:device:MediaRenderer:1"))
        self.assertTrue(is_valid("uuid:12345678-1234-1234-1234-1234567890ab"))

    def test_invalid(self):
        self.assertFalse(is_valid(""))
        self.assertFalse(is_valid("urn:bad:0"))


if __name__ == "__main__":
    unittest.main()
