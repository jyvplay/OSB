#!/usr/bin/env python3
"""Differential oracle for FilePathExtensionSplitter (core/files)."""
import unittest


def split_ext(name: str):
    dot = name.rfind(".")
    if dot <= 0 or dot == len(name) - 1:
        return name, ""
    return name[:dot], name[dot + 1 :]


class FilePathExtensionSplitterTest(unittest.TestCase):
    def test_split(self):
        self.assertEqual(split_ext(".gitignore"), (".gitignore", ""))
        self.assertEqual(split_ext("archive.tar.gz"), ("archive.tar", "gz"))


if __name__ == "__main__":
    unittest.main()
