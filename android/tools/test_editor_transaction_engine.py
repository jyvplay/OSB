#!/usr/bin/env python3
"""Differential oracle for EditorTransactionEngine (feature/editor)."""
import unittest


class EditorTransactionEngine:
    def __init__(self, initial: str = "", max_history: int = 256):
        self.buf = initial
        self.max_history = max_history
        self.undo_stack = []
        self.redo_stack = []

    def _apply_single(self, op):
        kind = op[0]
        if kind == "ins":
            _, off, txt = op
            assert 0 <= off <= len(self.buf)
            self.buf = self.buf[:off] + txt + self.buf[off:]
            return ("del", off, len(txt))
        else:
            _, off, length = op
            assert 0 <= off and length >= 0 and off + length <= len(self.buf)
            removed = self.buf[off : off + length]
            self.buf = self.buf[:off] + self.buf[off + length :]
            return ("ins", off, removed)

    def apply_tx(self, label: str, ops):
        snap = self.buf
        invs = []
        try:
            for op in ops:
                invs.append(self._apply_single(op))
        except AssertionError:
            self.buf = snap
            raise
        if len(self.undo_stack) == self.max_history:
            self.undo_stack.pop(0)
        self.undo_stack.append((label, list(reversed(invs)), ops))
        self.redo_stack.clear()

    def undo(self):
        if not self.undo_stack:
            return None
        label, invs, fwd = self.undo_stack.pop()
        for inv in invs:
            self._apply_single(inv)
        self.redo_stack.append((label, invs, fwd))
        return label

    def redo(self):
        if not self.redo_stack:
            return None
        label, _, fwd = self.redo_stack.pop()
        invs = [self._apply_single(op) for op in fwd]
        self.undo_stack.append((label, list(reversed(invs)), fwd))
        return label


class EditorTransactionEngineTest(unittest.TestCase):
    def test_tx_undo_redo_and_rollback(self):
        ed = EditorTransactionEngine("hello world")
        ed.apply_tx("replace", [("del", 6, 5), ("ins", 6, "osb")])
        self.assertEqual(ed.buf, "hello osb")
        self.assertEqual(ed.undo(), "replace")
        self.assertEqual(ed.buf, "hello world")
        self.assertEqual(ed.redo(), "replace")
        self.assertEqual(ed.buf, "hello osb")
        with self.assertRaises(AssertionError):
            ed.apply_tx("bad", [("ins", 0, "X"), ("del", 999, 1)])
        self.assertEqual(ed.buf, "hello osb")  # atomic rollback


if __name__ == "__main__":
    unittest.main()
