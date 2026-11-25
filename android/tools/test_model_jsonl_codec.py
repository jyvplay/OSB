#!/usr/bin/env python3
"""Differential oracle for ModelJsonlCodec (core/model)."""
import json
import unittest


class ModelJsonlCodecTest(unittest.TestCase):
    def test_encode(self):
        obj = {"msg": 'hi "osb"\nline2'}
        s = json.dumps(obj, separators=(",", ":"))
        self.assertEqual(json.loads(s), obj)


if __name__ == "__main__":
    unittest.main()
