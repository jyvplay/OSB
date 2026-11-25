#!/usr/bin/env python3
"""Differential oracle for PrivacyQueryParamStripper (privacy)."""
import unittest


def strip_query(q: str) -> str:
    if not q:
        return ""
    return "&".join(p for p in q.split("&") if p and not (p.split("=")[0].lower().startswith("utm_") or p.split("=")[0].lower() in {"fbclid", "gclid", "mc_cid", "mc_eid"}))


class PrivacyQueryParamStripperTest(unittest.TestCase):
    def test_strip(self):
        self.assertEqual(strip_query("q=osb&utm_source=x&fbclid=y"), "q=osb")


if __name__ == "__main__":
    unittest.main()
