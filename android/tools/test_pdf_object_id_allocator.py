#!/usr/bin/env python3
"""Independent differential oracle for PdfObjectIdAllocator (format/pdf).

Allocates PDF object IDs (object number, generation number) within bounded
limits, with free-list reuse. Cross-checks against the Kotlin contract in
format/pdf/src/main/kotlin/dev/fusion/pdf/PdfObjectIdAllocator.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_OBJECTS = 100_000
MAX_OBJ_NUM = 10_000_000
MAX_GEN = 65535


class PdfObjectIdAllocator:
    def __init__(self):
        self._live = {}
        self._free_list = []
        self._next_obj_num = 1

    def allocate(self, obj_num, gen):
        assert 1 <= obj_num <= MAX_OBJ_NUM, "objNum out of bounds"
        assert 0 <= gen <= MAX_GEN, "gen out of bounds"
        assert len(self._live) < MAX_OBJECTS or (obj_num, gen) in self._live, f"too many live objects: {MAX_OBJECTS}"
        self._live[(obj_num, gen)] = True
        return (obj_num, gen)

    def allocate_next(self):
        assert len(self._live) < MAX_OBJECTS, f"too many live objects: {MAX_OBJECTS}"
        if self._free_list:
            obj_num, gen = self._free_list.pop(0)
            self._live[(obj_num, gen)] = True
            return (obj_num, gen)
        while self._next_obj_num <= MAX_OBJ_NUM:
            candidate = (self._next_obj_num, 0)
            self._next_obj_num += 1
            if candidate not in self._live:
                self._live[candidate] = True
                return candidate
        raise RuntimeError("object number space exhausted")

    def free(self, obj_num, gen):
        if (obj_num, gen) not in self._live:
            return False
        del self._live[(obj_num, gen)]
        self._free_list.append((obj_num, gen))
        return True

    def is_allocated(self, obj_num, gen):
        return (obj_num, gen) in self._live

    def live_count(self):
        return len(self._live)

    def free_count(self):
        return len(self._free_list)

    def clear(self):
        self._live.clear()
        self._free_list.clear()
        self._next_obj_num = 1


class PdfObjectIdAllocatorTest(unittest.TestCase):
    def setUp(self):
        self.a = PdfObjectIdAllocator()

    def test_allocate_and_free(self):
        self.a.allocate(1, 0)
        self.assertTrue(self.a.is_allocated(1, 0))
        self.assertTrue(self.a.free(1, 0))
        self.assertFalse(self.a.is_allocated(1, 0))
        self.assertFalse(self.a.free(1, 0))  # already freed

    def test_allocate_next_sequential(self):
        id1 = self.a.allocate_next()
        id2 = self.a.allocate_next()
        self.assertEqual(id1, (1, 0))
        self.assertEqual(id2, (2, 0))

    def test_free_list_reuse(self):
        id1 = self.a.allocate_next()  # (1, 0)
        id2 = self.a.allocate_next()  # (2, 0)
        self.a.free(1, 0)
        id3 = self.a.allocate_next()  # should reuse (1, 0)
        self.assertEqual(id3, (1, 0))

    def test_invalid_obj_num(self):
        with self.assertRaises(AssertionError):
            self.a.allocate(0, 0)
        with self.assertRaises(AssertionError):
            self.a.allocate(MAX_OBJ_NUM + 1, 0)

    def test_invalid_gen(self):
        with self.assertRaises(AssertionError):
            self.a.allocate(1, -1)
        with self.assertRaises(AssertionError):
            self.a.allocate(1, MAX_GEN + 1)

    def test_clear(self):
        self.a.allocate_next()
        self.a.clear()
        self.assertEqual(self.a.live_count(), 0)
        self.assertEqual(self.a.free_count(), 0)
        self.assertFalse(self.a.is_allocated(1, 0))


if __name__ == "__main__":
    unittest.main()
