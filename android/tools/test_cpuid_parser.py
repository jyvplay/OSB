#!/usr/bin/env python3
"""Independent differential oracle for CpuidParser (core/hw).

Implements bounded CPUID leaf parser: decodes EAX/EBX/ECX/EDX registers for a
given leaf/sub-leaf, extracts vendor string (leaf 0) and processor brand string
(leaves 0x80000002–0x80000004), and tests feature flags by bit index. Does not
issue the CPUID instruction — raw register values are supplied by the caller.
Cross-checks against the Kotlin contract in
core/hw/src/main/kotlin/dev/fusion/hw/CpuidParser.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_LEAVES = 64
MAX_SUB_LEAVES = 16
MAX_STRING_LEN = 64


class CpuidLeaf:
    def __init__(self, eax: int, ebx: int, ecx: int, edx: int):
        self.eax = eax
        self.ebx = ebx
        self.ecx = ecx
        self.edx = edx


class CpuidParser:
    def __init__(self):
        self._leaves = {}

    def record(self, leaf: int, sub_leaf: int, eax: int, ebx: int, ecx: int, edx: int):
        assert len(self._leaves) < MAX_LEAVES or leaf in self._leaves, "too many leaves"
        sub = self._leaves.setdefault(leaf, {})
        assert len(sub) < MAX_SUB_LEAVES or sub_leaf in sub, f"too many sub-leaves for leaf {leaf}"
        sub[sub_leaf] = CpuidLeaf(eax, ebx, ecx, edx)

    def vendor_string(self):
        leaf0 = self._leaves.get(0)
        if leaf0 is None:
            return None
        regs = leaf0.get(0)
        if regs is None:
            return None
        return _ascii_string(_int_to_bytes(regs.ebx) + _int_to_bytes(regs.edx) + _int_to_bytes(regs.ecx))

    def brand_string(self):
        parts = []
        for leaf in (0x80000002, 0x80000003, 0x80000004):
            sub = self._leaves.get(leaf)
            if sub is None:
                return "".join(parts) if parts else None
            regs = sub.get(0)
            if regs is None:
                return "".join(parts) if parts else None
            parts.append(_ascii_string(
                _int_to_bytes(regs.eax) + _int_to_bytes(regs.ebx) +
                _int_to_bytes(regs.ecx) + _int_to_bytes(regs.edx)
            ))
        return "".join(parts).strip() or None

    def feature_flag(self, leaf: int, sub_leaf: int, bit: int) -> bool:
        sub = self._leaves.get(leaf)
        if sub is None:
            return False
        regs = sub.get(sub_leaf)
        if regs is None:
            return False
        word = {0: regs.eax, 1: regs.ebx, 2: regs.ecx, 3: regs.edx}[bit // 32]
        return (word >> (bit % 32)) & 1 == 1

    def max_leaf(self):
        if not self._leaves:
            return None
        return max(self._leaves.keys())

    def leaf_count(self) -> int:
        return len(self._leaves)


def _int_to_bytes(v: int) -> bytes:
    return bytes([(v >> i) & 0xFF for i in (0, 8, 16, 24)])


def _ascii_string(data: bytes) -> str:
    sb = []
    for b in data:
        if 0x20 <= b <= 0x7E:
            sb.append(chr(b))
        else:
            break
    return "".join(sb)


class CpuidParserTest(unittest.TestCase):
    def setUp(self):
        self.parser = CpuidParser()

    def test_vendor_string(self):
        # GenuineIntel: EBX=0x756e6547, EDX=0x49656e69, ECX=0x6c65746e
        self.parser.record(0, 0, 0x00000004, 0x756e6547, 0x6c65746e, 0x49656e69)
        self.assertEqual(self.parser.vendor_string(), "GenuineIntel")

    def test_authentic_amd(self):
        # AuthenticAMD: EBX="Auth"(0x68747541), EDX="enti"(0x69746e65), ECX="cAMD"(0x444d4163)
        self.parser.record(0, 0, 0x00000004, 0x68747541, 0x444d4163, 0x69746e65)
        self.assertEqual(self.parser.vendor_string(), "AuthenticAMD")

    def test_brand_string(self):
        # Three brand leaves concatenated
        self.parser.record(0x80000002, 0, 0x20202020, 0x20202020, 0x20202020, 0x20202020)
        self.parser.record(0x80000003, 0, 0x20202020, 0x20202020, 0x20202020, 0x20202020)
        self.parser.record(0x80000004, 0, 0x20202020, 0x20202020, 0x20202020, 0x20202020)
        # All spaces -> trimmed to empty -> None
        self.assertIsNone(self.parser.brand_string())

    def test_feature_flag(self):
        # Leaf 1, EAX bit 25 (bit 25 maps to EAX in 0..31 range)
        self.parser.record(1, 0, 0x02000000, 0x00000000, 0x00000000, 0x00000000)
        self.assertTrue(self.parser.feature_flag(1, 0, 25))  # EAX bit 25
        self.assertFalse(self.parser.feature_flag(1, 0, 24))  # EAX bit 24

    def test_feature_flag_eax_bit0(self):
        # Leaf 1, EAX bit 0 = FPU
        self.parser.record(1, 0, 0x00000001, 0x00000000, 0x00000000, 0x00000000)
        self.assertTrue(self.parser.feature_flag(1, 0, 0))
        self.assertFalse(self.parser.feature_flag(1, 0, 1))

    def test_feature_flag_unknown_leaf(self):
        self.assertFalse(self.parser.feature_flag(999, 0, 0))

    def test_feature_flag_unknown_subleaf(self):
        self.parser.record(1, 0, 0x00000001, 0, 0, 0)
        self.assertFalse(self.parser.feature_flag(1, 1, 0))

    def test_max_leaf(self):
        self.assertIsNone(self.parser.max_leaf())
        self.parser.record(0, 0, 0, 0, 0, 0)
        self.parser.record(1, 0, 0, 0, 0, 0)
        self.parser.record(0x80000000, 0, 0, 0, 0, 0)
        self.assertEqual(self.parser.max_leaf(), 0x80000000)

    def test_leaf_count(self):
        self.assertEqual(self.parser.leaf_count(), 0)
        self.parser.record(0, 0, 0, 0, 0, 0)
        self.assertEqual(self.parser.leaf_count(), 1)
        self.parser.record(0, 1, 0, 0, 0, 0)  # same leaf, different sub-leaf
        self.assertEqual(self.parser.leaf_count(), 1)

    def test_too_many_leaves(self):
        for i in range(MAX_LEAVES):
            self.parser.record(i, 0, 0, 0, 0, 0)
        with self.assertRaises(AssertionError):
            self.parser.record(MAX_LEAVES, 0, 0, 0, 0, 0)

    def test_too_many_sub_leaves(self):
        for i in range(MAX_SUB_LEAVES):
            self.parser.record(0, i, 0, 0, 0, 0)
        with self.assertRaises(AssertionError):
            self.parser.record(0, MAX_SUB_LEAVES, 0, 0, 0, 0)


if __name__ == "__main__":
    unittest.main()
