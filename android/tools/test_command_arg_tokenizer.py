#!/usr/bin/env python3
"""Differential oracle for CommandArgTokenizer (core/commands)."""
import shlex
import unittest


class CommandArgTokenizerTest(unittest.TestCase):
    def test_tokenize(self):
        self.assertEqual(shlex.split('echo "hello world" foo'), ["echo", "hello world", "foo"])


if __name__ == "__main__":
    unittest.main()
