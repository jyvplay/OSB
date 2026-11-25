#!/usr/bin/env python3
"""Differential oracle for MessagesDraftLengthGuard (feature/messages)."""
import unittest


def is_sendable(draft: str) -> bool:
    return len(draft) <= 4096 and bool(draft.strip())


class MessagesDraftLengthGuardTest(unittest.TestCase):
    def test_sendable(self):
        self.assertTrue(is_sendable("hi"))
        self.assertFalse(is_sendable("   "))


if __name__ == "__main__":
    unittest.main()
