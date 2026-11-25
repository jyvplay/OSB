#!/usr/bin/env python3
"""Differential oracle for FileSizeFormatter (core/files)."""
import unittest

UNITS = ["B", "KiB", "MiB", "GiB", "TiB"]


def format_iec(num_bytes: int) -> str:
    assert num_bytes >= 0
    if num_bytes < 1024:
        return f"{num_bytes} B"
    val = float(num_bytes)
    idx = 0
    while val >= 1024.0 and idx < len(UNITS) - 1:
        val /= 1024.0
        idx += 1
    return f"{val:.1f} {UNITS[idx]}"


class FileSizeFormatterTest(unittest.TestCase):
    def test_format(self):
        self.assertEqual(format_iec(512), "512 B")
        self.assertEqual(format_iec(1536), "1.5 KiB")
        self.assertEqual(format_iec(1048576), "1.0 MiB")


if __name__ == "__main__":
    unittest.main()
