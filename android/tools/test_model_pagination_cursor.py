#!/usr/bin/env python3
"""Differential oracle for ModelPaginationCursor (core/model)."""
import unittest


def next_offset(offset: int, limit: int, total: int):
    nxt = offset + limit
    return nxt if nxt < total else None


class ModelPaginationCursorTest(unittest.TestCase):
    def test_next(self):
        self.assertEqual(next_offset(0, 10, 25), 10)
        self.assertIsNone(next_offset(20, 10, 25))


if __name__ == "__main__":
    unittest.main()
