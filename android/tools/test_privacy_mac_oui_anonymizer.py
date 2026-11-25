#!/usr/bin/env python3
"""Differential oracle for PrivacyMacOuiAnonymizer (privacy)."""
import re
import unittest

MAC_RE = re.compile(r"^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")


def anonymize(mac: str) -> str:
    assert MAC_RE.match(mac)
    p = mac.upper().split(":")
    return f"{p[0]}:{p[1]}:{p[2]}:00:00:00"


class PrivacyMacOuiAnonymizerTest(unittest.TestCase):
    def test_anon(self):
        self.assertEqual(anonymize("aa:bb:cc:dd:ee:ff"), "AA:BB:CC:00:00:00")


if __name__ == "__main__":
    unittest.main()
