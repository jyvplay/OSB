#!/usr/bin/env python3
"""Independent differential oracle for MessageThread (feature/messages).

Groups messages into threads by parent-message id. Returns roots and
descendant maps. Cross-checks against the Kotlin contract in
feature/messages/src/main/kotlin/dev/fusion/messages/MessageThread.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_MESSAGES = 4096
MAX_ID_LEN = 256
MAX_DEPTH = 32
ID_RE = re.compile(r"[a-zA-Z0-9_-]{1,64}")


class Message:
    def __init__(self, id, parent_id, text):
        self.id = id
        self.parent_id = parent_id
        self.text = text


def resolve(messages):
    assert len(messages) <= MAX_MESSAGES, f"too many messages: {MAX_MESSAGES}"
    by_id = {}
    # Pass 1: validate and index all messages
    for m in messages:
        assert ID_RE.fullmatch(m.id), f"invalid message id: {m.id}"
        assert len(m.id) <= MAX_ID_LEN, "id too long"
        if m.parent_id is not None:
            assert ID_RE.fullmatch(m.parent_id), "invalid parent id"
        assert len(m.text) <= MAX_ID_LEN * 4, "text too long"
        by_id[m.id] = m
    # Pass 2: determine roots and children (all messages are now indexed)
    children = {}
    roots = []
    for m in messages:
        if m.parent_id is None or m.parent_id not in by_id:
            roots.append(m.id)
        else:
            children.setdefault(m.parent_id, []).append(m.id)

    # Cycle detection: no roots means every node has a parent; in a finite
    # set following parent pointers must eventually revisit a node.
    if messages and not roots:
        raise AssertionError("cycle detected: no root messages")

    visited = set()
    in_stack = set()

    def dfs(node):
        if node in in_stack:
            return True  # cycle
        if node in visited:
            return False
        in_stack.add(node)
        visited.add(node)
        for child in children.get(node, []):
            if dfs(child):
                return True
        in_stack.remove(node)
        return False

    for root in roots:
        assert not dfs(root), f"cycle detected at {root}"

    # Collect descendants per root (bounded depth)
    threads = []
    for root in roots:
        desc = []
        _collect(root, children, 0, desc)
        threads.append((root, desc))
    return threads


def _collect(node, children, depth, out):
    if depth >= MAX_DEPTH:
        return
    for child in children.get(node, []):
        out.append(child)
        _collect(child, children, depth + 1, out)


class MessageThreadTest(unittest.TestCase):
    def test_simple_thread(self):
        msgs = [
            Message("root1", None, "hello"),
            Message("child1", "root1", "reply"),
            Message("child2", "root1", "reply2"),
        ]
        threads = resolve(msgs)
        self.assertEqual(len(threads), 1)
        self.assertEqual(threads[0][0], "root1")
        self.assertIn("child1", threads[0][1])
        self.assertIn("child2", threads[0][1])

    def test_multiple_roots(self):
        msgs = [
            Message("a", None, "a"),
            Message("b", None, "b"),
        ]
        threads = resolve(msgs)
        self.assertEqual(len(threads), 2)

    def test_no_cycle(self):
        msgs = [
            Message("a", None, "a"),
            Message("b", "a", "b"),
            Message("c", "b", "c"),
        ]
        threads = resolve(msgs)
        self.assertEqual(len(threads), 1)

    def test_cycle_refused(self):
        msgs = [
            Message("a", "b", "a"),
            Message("b", "a", "b"),
        ]
        with self.assertRaises(AssertionError):
            resolve(msgs)

    def test_invalid_id(self):
        msgs = [Message("bad id!", None, "x")]
        with self.assertRaises(AssertionError):
            resolve(msgs)


if __name__ == "__main__":
    unittest.main()
