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
    """R146 repaired contract: two-pass indexing, iterative cycle walks."""
    assert len(messages) <= MAX_MESSAGES, f"too many messages: {MAX_MESSAGES}"
    by_id = {}
    # Pass 1: validate and index ALL messages before any parent is read, so a
    # parent id that appears later in the list still resolves.
    for m in messages:
        assert ID_RE.fullmatch(m.id), f"invalid message id: {m.id}"
        assert len(m.id) <= MAX_ID_LEN, "id too long"
        if m.parent_id is not None:
            assert ID_RE.fullmatch(m.parent_id), "invalid parent id"
        assert len(m.text) <= MAX_ID_LEN * 4, "text too long"
        by_id[m.id] = m
    # Pass 2: determine roots and children against the complete index.
    children = {}
    roots = []
    for m in messages:
        if m.parent_id is None or m.parent_id not in by_id:
            roots.append(m.id)
        else:
            children.setdefault(m.parent_id, []).append(m.id)

    # No roots in a non-empty set means every parent pointer stays inside the
    # set; following them in a finite set must revisit a node.
    if messages and not roots:
        raise AssertionError("cycle detected: no root messages")

    # Iterative DFS from every root (explicit stack: chains up to MAX_MESSAGES
    # must not depend on interpreter recursion limits).
    visited = set()
    in_stack = set()
    for root in roots:
        if root in visited:
            continue
        stack = [(root, -1)]
        while stack:
            node, phase = stack.pop()
            if phase == -1:
                if node in in_stack:
                    raise AssertionError(f"cycle detected at {node}")
                if node in visited:
                    continue
                in_stack.add(node)
                visited.add(node)
                stack.append((node, 0))
                for child in reversed(children.get(node, [])):
                    stack.append((child, -1))
            else:
                in_stack.discard(node)

    # Every non-root has a parent inside the set, so any message never reached
    # from a root belongs to a rootless component whose parent walk loops.
    if len(visited) != len(by_id):
        raise AssertionError("cycle detected: unreachable cyclic component")

    # Collect descendants per root (bounded depth, iterative pre-order).
    threads = []
    for root in roots:
        desc = []
        _collect(root, children, 0, desc)
        threads.append((root, desc))
    return threads


def _collect(node, children, depth, out):
    """Emit descendants of `node` pre-order up to MAX_DEPTH (root excluded)."""
    if depth >= MAX_DEPTH:
        return
    stack = [(node, depth)]
    first = True
    while stack:
        cur, cur_depth = stack.pop()
        if not first:
            out.append(cur)
        first = False
        if cur_depth < MAX_DEPTH:
            kids = children.get(cur, [])
            for child in reversed(kids):
                stack.append((child, cur_depth + 1))


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

    # --- R146 adversarial vectors against the repaired two-pass contract ---

    def test_forward_reference_parent_is_not_a_root(self):
        # Child listed BEFORE its parent: single-pass indexing used to mark
        # the child a root as well, producing two threads for one thread.
        msgs = [
            Message("child", "root", "reply"),
            Message("root", None, "hello"),
        ]
        threads = resolve(msgs)
        self.assertEqual(len(threads), 1)
        self.assertEqual(threads[0], ("root", ["child"]))

    def test_forward_reference_two_cycle_refused(self):
        # The exact R145-documented defect: a->b listed first, b->a second.
        # Old single-pass Kotlin classified `a` a root and never saw b->a.
        msgs = [
            Message("a", "b", "a"),
            Message("b", "a", "b"),
        ]
        with self.assertRaises(AssertionError) as ctx:
            resolve(msgs)
        self.assertIn("no root messages", str(ctx.exception))

    def test_self_parent_alongside_valid_root_refused(self):
        # `loop` parents itself while `root` is a genuine thread: the loop
        # component has no root, so the root DFS cannot reach it.
        msgs = [
            Message("root", None, "ok"),
            Message("loop", "loop", "self"),
        ]
        with self.assertRaises(AssertionError) as ctx:
            resolve(msgs)
        self.assertIn("unreachable cyclic component", str(ctx.exception))

    def test_unreachable_cycle_component_refused(self):
        # Valid thread a->b plus a detached c<->d cycle.
        msgs = [
            Message("a", None, "a"),
            Message("b", "a", "b"),
            Message("c", "d", "c"),
            Message("d", "c", "d"),
        ]
        with self.assertRaises(AssertionError) as ctx:
            resolve(msgs)
        self.assertIn("unreachable cyclic component", str(ctx.exception))

    def test_long_chain_uses_iterative_walk(self):
        # 1500-deep parent chain: the recursive DFS/oracle would hit the
        # interpreter recursion limit (~1000) before this contract did.
        n = 1500
        msgs = [Message("m0", None, "root")]
        msgs += [Message(f"m{i}", f"m{i-1}", "x") for i in range(1, n)]
        threads = resolve(msgs)
        self.assertEqual(len(threads), 1)
        self.assertEqual(threads[0][0], "m0")
        # Descendants are bounded at MAX_DEPTH=32.
        self.assertEqual(len(threads[0][1]), 32)

    def test_three_cycle_with_reachable_entry_refused(self):
        # Valid thread r->x plus a detached a->b->c->a cycle whose members
        # are all non-roots: the in-stack walk or the unreachable-component
        # check must refuse the set.
        msgs = [
            Message("r", None, "r"),
            Message("x", "r", "x"),
            Message("a", "c", "a"),
            Message("b", "a", "b"),
            Message("c", "b", "c"),
        ]
        with self.assertRaises(AssertionError) as ctx:
            resolve(msgs)
        self.assertIn("unreachable cyclic component", str(ctx.exception))

    def test_text_over_limit_refused(self):
        msgs = [Message("a", None, "x" * (MAX_ID_LEN * 4 + 1))]
        with self.assertRaises(AssertionError):
            resolve(msgs)

    def test_duplicate_parent_links_keep_list_order(self):
        msgs = [
            Message("root", None, "r"),
            Message("c1", "root", "1"),
            Message("c2", "root", "2"),
            Message("c3", "root", "3"),
        ]
        threads = resolve(msgs)
        self.assertEqual(threads[0], ("root", ["c1", "c2", "c3"]))


if __name__ == "__main__":
    unittest.main()
