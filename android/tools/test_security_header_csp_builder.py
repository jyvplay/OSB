#!/usr/bin/env python3
"""Differential oracle for SecurityHeaderCspBuilder (core/security)."""
import unittest


def build_csp(directives: dict) -> str:
    return "; ".join(f"{k} {' '.join(v)}" if v else k for k, v in directives.items())


class SecurityHeaderCspBuilderTest(unittest.TestCase):
    def test_build(self):
        self.assertEqual(build_csp({"default-src": ["'self'"], "upgrade-insecure-requests": []}), "default-src 'self'; upgrade-insecure-requests")


if __name__ == "__main__":
    unittest.main()
