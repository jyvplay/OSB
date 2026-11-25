#!/usr/bin/env python3
"""Differential oracle for ModelMonotonicIdGen (core/model)."""
import unittest


class ModelMonotonicIdGen:
    def __init__(self, initial: int = 1):
        self.next_id = initial

    def next(self) -> int:
        val = self.next_id
        self.next_id += 1
        return val


class ModelMonotonicIdGenTest(unittest.TestCase):
    def test_gen(self):
        g = ModelMonotonicIdGen(10)
        self.assertEqual(g.next(), 10)
        self.assertEqual(g.next(), 11)


if __name__ == "__main__":
    unittest.main()
