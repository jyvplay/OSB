#!/usr/bin/env python3
"""Differential oracle for ElfSectionAlignmentVerifier (format/native)."""
import unittest


def is_pow2_or_0_1(align: int) -> bool:
    if align < 0:
        return False
    if align in (0, 1):
        return True
    return (align & (align - 1)) == 0


def verify_section(vaddr: int, offset: int, align: int) -> bool:
    assert vaddr >= 0 and offset >= 0
    if not is_pow2_or_0_1(align):
        return False
    if align <= 1:
        return True
    return (vaddr % align) == (offset % align)


class ElfSectionAlignmentVerifierTest(unittest.TestCase):
    def test_valid_alignments(self):
        self.assertTrue(verify_section(0x1000, 0x1000, 4096))
        self.assertTrue(verify_section(0x1040, 0x2040, 4096))
        self.assertTrue(verify_section(0x123, 0x456, 1))
        self.assertTrue(verify_section(0x123, 0x456, 0))

    def test_misaligned(self):
        self.assertFalse(verify_section(0x1000, 0x1004, 4096))

    def test_non_power_of_two(self):
        self.assertFalse(verify_section(0x1000, 0x1000, 3))


if __name__ == "__main__":
    unittest.main()
