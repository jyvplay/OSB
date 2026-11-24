#!/usr/bin/env python3
"""Independent differential oracle for MerkleTree (core/files).

Implements RFC 6962 §2.1 domain-separated SHA-256 Merkle tree with
0x00 leaf / 0x01 node separation and largest-power-of-two split, plus
O(log N) inclusion proofs. Cross-checks against the Kotlin contract in
core/files/src/main/kotlin/dev/fusion/files/MerkleTree.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import hashlib
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

HASH_BYTES = 32
MAX_LEAVES = 65_536


def leaf_hash(payload: bytes) -> bytes:
    h = hashlib.sha256()
    h.update(b"\x00")
    h.update(payload)
    return h.digest()


def node_hash(left: bytes, right: bytes) -> bytes:
    assert len(left) == HASH_BYTES and len(right) == HASH_BYTES, "node inputs must be 32 bytes"
    h = hashlib.sha256()
    h.update(b"\x01")
    h.update(left)
    h.update(right)
    return h.digest()


def root_hash(leaves: list) -> bytes:
    assert len(leaves) <= MAX_LEAVES, f"leaf count {len(leaves)} exceeds {MAX_LEAVES}"
    if not leaves:
        return hashlib.sha256().digest()
    return _mth_range(leaves, 0, len(leaves))


def _mth_range(leaves, from_idx, to_idx):
    n = to_idx - from_idx
    if n == 1:
        return leaf_hash(leaves[from_idx])
    k = _largest_power_of_two_less_than(n)
    return node_hash(_mth_range(leaves, from_idx, from_idx + k),
                     _mth_range(leaves, from_idx + k, to_idx))


def _largest_power_of_two_less_than(n):
    assert n >= 2
    k = 1
    while k * 2 < n:
        k *= 2
    return k


def inclusion_proof(leaves, leaf_index):
    assert leaves and len(leaves) <= MAX_LEAVES, f"leaves size {len(leaves)} outside 1..{MAX_LEAVES}"
    assert 0 <= leaf_index < len(leaves), f"leafIndex {leaf_index} outside 0..{len(leaves) - 1}"
    steps = []
    _build_audit_path(leaves, 0, len(leaves), leaf_index, steps)
    return steps


def _build_audit_path(leaves, from_idx, to_idx, target_abs, out):
    n = to_idx - from_idx
    if n == 1:
        return
    k = _largest_power_of_two_less_than(n)
    m = target_abs - from_idx
    if m < k:
        _build_audit_path(leaves, from_idx, from_idx + k, target_abs, out)
        out.append((_mth_range(leaves, from_idx + k, to_idx), False))
    else:
        _build_audit_path(leaves, from_idx + k, to_idx, target_abs, out)
        out.append((_mth_range(leaves, from_idx, from_idx + k), True))


def expected_sides_bottom_up(leaf_index, tree_size):
    out = []

    def walk(m, n):
        if n == 1:
            return
        k = _largest_power_of_two_less_than(n)
        if m < k:
            walk(m, k)
            out.append(False)
        else:
            walk(m - k, n - k)
            out.append(True)

    walk(leaf_index, tree_size)
    return out


def verify_inclusion(leaf_payload, leaf_index, tree_size, expected_root_hash, proof):
    assert len(expected_root_hash) == HASH_BYTES, "expectedRootHash must be 32 bytes"
    if tree_size <= 0 or not (0 <= leaf_index < tree_size):
        return False
    expected_orientation = expected_sides_bottom_up(leaf_index, tree_size)
    if len(proof) != len(expected_orientation):
        return False

    cur = leaf_hash(leaf_payload)
    for i in range(len(proof)):
        sibling_hash, sibling_on_left = proof[i]
        assert len(sibling_hash) == HASH_BYTES, "sibling hash must be 32 bytes"
        if sibling_on_left != expected_orientation[i]:
            return False
        if sibling_on_left:
            cur = node_hash(sibling_hash, cur)
        else:
            cur = node_hash(cur, sibling_hash)

    return cur == expected_root_hash


def to_hex(hash_bytes: bytes) -> str:
    return hash_bytes.hex()


class MerkleTreeTest(unittest.TestCase):
    def test_empty_tree_root(self):
        root = root_hash([])
        self.assertEqual(len(root), HASH_BYTES)
        self.assertEqual(root, hashlib.sha256().digest())

    def test_single_leaf(self):
        payload = b"hello"
        root = root_hash([payload])
        expected = leaf_hash(payload)
        self.assertEqual(root, expected)

    def test_two_leaves(self):
        leaves = [b"a", b"b"]
        root = root_hash(leaves)
        expected = node_hash(leaf_hash(leaves[0]), leaf_hash(leaves[1]))
        self.assertEqual(root, expected)

    def test_three_leaves_power_of_two_split(self):
        # n=3, k=2: root = hash(hash(a,b), hash(c))
        leaves = [b"a", b"b", b"c"]
        root = root_hash(leaves)
        expected = node_hash(node_hash(leaf_hash(leaves[0]), leaf_hash(leaves[1])), leaf_hash(leaves[2]))
        self.assertEqual(root, expected)

    def test_four_leaves(self):
        leaves = [b"a", b"b", b"c", b"d"]
        root = root_hash(leaves)
        expected = node_hash(
            node_hash(leaf_hash(leaves[0]), leaf_hash(leaves[1])),
            node_hash(leaf_hash(leaves[2]), leaf_hash(leaves[3])),
        )
        self.assertEqual(root, expected)

    def test_five_leaves(self):
        # n=5, k=4: root = hash(mth(a,b,c,d), hash(e))
        leaves = [b"a", b"b", b"c", b"d", b"e"]
        root = root_hash(leaves)
        expected = node_hash(
            node_hash(node_hash(leaf_hash(leaves[0]), leaf_hash(leaves[1])),
                      node_hash(leaf_hash(leaves[2]), leaf_hash(leaves[3]))),
            leaf_hash(leaves[4]),
        )
        self.assertEqual(root, expected)

    def test_inclusion_proof_single_leaf(self):
        leaves = [b"hello"]
        proof = inclusion_proof(leaves, 0)
        self.assertEqual(len(proof), 0)
        root = root_hash(leaves)
        self.assertTrue(verify_inclusion(b"hello", 0, 1, root, proof))

    def test_inclusion_proof_two_leaves_left(self):
        leaves = [b"a", b"b"]
        proof = inclusion_proof(leaves, 0)
        self.assertEqual(len(proof), 1)
        self.assertEqual(proof[0][1], False)  # sibling on right
        root = root_hash(leaves)
        self.assertTrue(verify_inclusion(b"a", 0, 2, root, proof))

    def test_inclusion_proof_two_leaves_right(self):
        leaves = [b"a", b"b"]
        proof = inclusion_proof(leaves, 1)
        self.assertEqual(len(proof), 1)
        self.assertEqual(proof[0][1], True)  # sibling on left
        root = root_hash(leaves)
        self.assertTrue(verify_inclusion(b"b", 1, 2, root, proof))

    def test_inclusion_proof_three_leaves(self):
        leaves = [b"a", b"b", b"c"]
        root = root_hash(leaves)
        for i in range(3):
            proof = inclusion_proof(leaves, i)
            self.assertTrue(verify_inclusion(leaves[i], i, 3, root, proof))

    def test_inclusion_proof_ten_leaves(self):
        leaves = [f"leaf{i}".encode() for i in range(10)]
        root = root_hash(leaves)
        for i in range(10):
            proof = inclusion_proof(leaves, i)
            self.assertTrue(verify_inclusion(leaves[i], i, 10, root, proof))

    def test_wrong_leaf_fails_verification(self):
        leaves = [b"a", b"b"]
        root = root_hash(leaves)
        proof = inclusion_proof(leaves, 0)
        self.assertFalse(verify_inclusion(b"x", 0, 2, root, proof))

    def test_wrong_root_fails_verification(self):
        leaves = [b"a", b"b"]
        root = root_hash(leaves)
        wrong_root = b"\x00" * HASH_BYTES
        proof = inclusion_proof(leaves, 0)
        self.assertFalse(verify_inclusion(b"a", 0, 2, wrong_root, proof))

    def test_leaf_count_bound(self):
        with self.assertRaises(AssertionError):
            root_hash([b"x"] * (MAX_LEAVES + 1))

    def test_leaf_index_out_of_bounds(self):
        with self.assertRaises(AssertionError):
            inclusion_proof([b"a"], 1)

    def test_hex_encoding(self):
        h = b"\x01\x02\xff"
        self.assertEqual(to_hex(h), "0102ff")

    def test_domain_separation_prevents_confusion(self):
        # A leaf's hash must differ from a node's hash even with identical children
        payload = b"same"
        leaf = leaf_hash(payload)
        node = node_hash(leaf, leaf)
        self.assertNotEqual(leaf, node)


if __name__ == "__main__":
    unittest.main()
