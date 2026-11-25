#!/usr/bin/env python3
"""Differential oracle for LauncherFolderCapacityGuard (feature/launcher)."""
import unittest


def can_add(cnt: int) -> bool:
    return cnt < 48


class LauncherFolderCapacityGuardTest(unittest.TestCase):
    def test_cap(self):
        self.assertTrue(can_add(47))
        self.assertFalse(can_add(48))


if __name__ == "__main__":
    unittest.main()
