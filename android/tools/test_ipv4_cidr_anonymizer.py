#!/usr/bin/env python3
"""Differential oracle for Ipv4CidrAnonymizer (privacy)."""
import unittest


def anonymize(ipv4: str, prefix_len: int = 24) -> str:
    parts = [int(p) for p in ipv4.split(".")]
    assert len(parts) == 4 and all(0 <= o <= 255 for o in parts)
    ip = (parts[0] << 24) | (parts[1] << 16) | (parts[2] << 8) | parts[3]
    mask = 0 if prefix_len == 0 else ((0xFFFFFFFF << (32 - prefix_len)) & 0xFFFFFFFF)
    m = ip & mask
    return f"{(m >> 24) & 255}.{(m >> 16) & 255}.{(m >> 8) & 255}.{m & 255}"


class Ipv4CidrAnonymizerTest(unittest.TestCase):
    def test_anonymize(self):
        self.assertEqual(anonymize("192.168.1.42", 24), "192.168.1.0")
        self.assertEqual(anonymize("10.20.30.40", 16), "10.20.0.0")


if __name__ == "__main__":
    unittest.main()
