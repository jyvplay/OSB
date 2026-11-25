#!/usr/bin/env python3
"""Differential oracle for AirPlayRtspStatusCode (cast/airplay)."""
import unittest


def classify(code: int) -> str:
    if 200 <= code <= 299:
        return "SUCCESS"
    if code in (401, 403):
        return "AUTH_REQUIRED"
    if 400 <= code <= 499:
        return "CLIENT_ERROR"
    if 500 <= code <= 599:
        return "SERVER_ERROR"
    return "OTHER"


class AirPlayRtspStatusCodeTest(unittest.TestCase):
    def test_classify(self):
        self.assertEqual(classify(200), "SUCCESS")
        self.assertEqual(classify(401), "AUTH_REQUIRED")
        self.assertEqual(classify(503), "SERVER_ERROR")


if __name__ == "__main__":
    unittest.main()
