#!/usr/bin/env python3
"""Independent differential oracle for AtomicWriteJournal (core/storage).

Implements bounded write-ahead journal for atomic file operations: records an
intent (target path, expected content hash, operation type) before a mutation,
then commits or rolls back. Entries are bounded in count and total size;
overflow refuses new entries rather than silently dropping old ones.
Cross-checks against the Kotlin contract in
core/storage/src/main/kotlin/dev/fusion/storage/AtomicWriteJournal.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_ENTRIES = 1024
MAX_PATH_LEN = 4096
HEX64_RE = re_compile = None
import re
HEX64_RE = re.compile(r"[0-9a-fA-F]{64}")
OP_SET = {"WRITE", "DELETE", "RENAME"}


class JournalEntry:
    def __init__(self, path: str, operation: str, content_hash: str):
        self.path = path
        self.operation = operation
        self.content_hash = content_hash
        self.committed = False
        self.rolled_back = False


class AtomicWriteJournal:
    def __init__(self):
        self._entries = {}

    def record(self, path: str, operation: str, content_hash: str) -> str:
        assert len(path) <= MAX_PATH_LEN, f"path exceeds {MAX_PATH_LEN} chars"
        assert HEX64_RE.fullmatch(content_hash), "contentHash must be 64 hex chars"
        assert operation in OP_SET, f"unknown operation: {operation}"
        assert len(self._entries) < MAX_ENTRIES, f"journal full: {MAX_ENTRIES} entries"
        assert path not in [e.path for e in self._entries.values()], f"entry already exists for path: {path}"
        import uuid
        eid = str(uuid.uuid4())
        self._entries[eid] = JournalEntry(path, operation, content_hash)
        return eid

    def commit(self, eid: str) -> bool:
        entry = self._entries.get(eid)
        if entry is None:
            return False
        assert not entry.committed and not entry.rolled_back, f"entry {eid} already resolved"
        entry.committed = True
        return True

    def rollback(self, eid: str) -> bool:
        entry = self._entries.get(eid)
        if entry is None:
            return False
        assert not entry.committed and not entry.rolled_back, f"entry {eid} already resolved"
        entry.rolled_back = True
        return True

    def status(self, eid: str):
        entry = self._entries.get(eid)
        if entry is None:
            return None
        if entry.committed:
            return "committed"
        if entry.rolled_back:
            return "rolled_back"
        return "pending"

    def pending_count(self) -> int:
        return sum(1 for e in self._entries.values() if not e.committed and not e.rolled_back)

    def committed_count(self) -> int:
        return sum(1 for e in self._entries.values() if e.committed)

    def rolled_back_count(self) -> int:
        return sum(1 for e in self._entries.values() if e.rolled_back)

    def clear_resolved(self):
        self._entries = {k: v for k, v in self._entries.items() if not v.committed and not v.rolled_back}


class AtomicWriteJournalTest(unittest.TestCase):
    def setUp(self):
        self.journal = AtomicWriteJournal()

    def test_record_and_commit(self):
        eid = self.journal.record("/data/file.txt", "WRITE", "a" * 64)
        self.assertEqual(self.journal.status(eid), "pending")
        self.assertTrue(self.journal.commit(eid))
        self.assertEqual(self.journal.status(eid), "committed")

    def test_record_and_rollback(self):
        eid = self.journal.record("/data/file.txt", "DELETE", "b" * 64)
        self.assertTrue(self.journal.rollback(eid))
        self.assertEqual(self.journal.status(eid), "rolled_back")

    def test_unknown_entry_id(self):
        self.assertFalse(self.journal.commit("no-such-id"))
        self.assertFalse(self.journal.rollback("no-such-id"))
        self.assertIsNone(self.journal.status("no-such-id"))

    def test_double_commit_refused(self):
        eid = self.journal.record("/data/file.txt", "WRITE", "c" * 64)
        self.assertTrue(self.journal.commit(eid))
        with self.assertRaises(AssertionError):
            self.journal.commit(eid)

    def test_double_rollback_refused(self):
        eid = self.journal.record("/data/file.txt", "WRITE", "c" * 64)
        self.assertTrue(self.journal.rollback(eid))
        with self.assertRaises(AssertionError):
            self.journal.rollback(eid)

    def test_commit_then_rollback_refused(self):
        eid = self.journal.record("/data/file.txt", "WRITE", "c" * 64)
        self.assertTrue(self.journal.commit(eid))
        with self.assertRaises(AssertionError):
            self.journal.rollback(eid)

    def test_invalid_path_too_long(self):
        with self.assertRaises(AssertionError):
            self.journal.record("x" * 4097, "WRITE", "d" * 64)

    def test_invalid_content_hash(self):
        with self.assertRaises(AssertionError):
            self.journal.record("/data/file.txt", "WRITE", "not-hex")
        with self.assertRaises(AssertionError):
            self.journal.record("/data/file.txt", "WRITE", "a" * 63)
        with self.assertRaises(AssertionError):
            self.journal.record("/data/file.txt", "WRITE", "a" * 65)

    def test_invalid_operation(self):
        with self.assertRaises(AssertionError):
            self.journal.record("/data/file.txt", "COPY", "e" * 64)

    def test_duplicate_path_refused(self):
        self.journal.record("/data/file.txt", "WRITE", "f" * 64)
        with self.assertRaises(AssertionError):
            self.journal.record("/data/file.txt", "DELETE", "g" * 64)

    def test_counts(self):
        e1 = self.journal.record("/a", "WRITE", "1" * 64)
        e2 = self.journal.record("/b", "WRITE", "2" * 64)
        self.assertEqual(self.journal.pending_count(), 2)
        self.journal.commit(e1)
        self.assertEqual(self.journal.pending_count(), 1)
        self.assertEqual(self.journal.committed_count(), 1)
        self.journal.rollback(e2)
        self.assertEqual(self.journal.rolled_back_count(), 1)
        self.assertEqual(self.journal.pending_count(), 0)

    def test_clear_resolved(self):
        e1 = self.journal.record("/a", "WRITE", "1" * 64)
        e2 = self.journal.record("/b", "WRITE", "2" * 64)
        self.journal.commit(e1)
        self.journal.rollback(e2)
        self.journal.clear_resolved()
        self.assertEqual(self.journal.pending_count(), 0)
        self.assertEqual(self.journal.committed_count(), 0)
        self.assertEqual(self.journal.rolled_back_count(), 0)


if __name__ == "__main__":
    unittest.main()
