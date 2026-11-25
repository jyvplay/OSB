#!/usr/bin/env python3
"""Differential oracle for FilesConflictRenameSuffix (feature/files)."""
import unittest


def resolve(base: str, ext: str, existing: set) -> str:
    init = base if not ext else f"{base}.{ext}"
    if init not in existing:
        return init
    for i in range(1, 10001):
        cand = f"{base} ({i})" if not ext else f"{base} ({i}).{ext}"
        if cand not in existing:
            return cand
    raise RuntimeError("too many")


class FilesConflictRenameSuffixTest(unittest.TestCase):
    def test_resolve(self):
        self.assertEqual(resolve("doc", "txt", {"doc.txt", "doc (1).txt"}), "doc (2).txt")


if __name__ == "__main__":
    unittest.main()
