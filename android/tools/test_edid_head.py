#!/usr/bin/env python3
"""Independent differential oracle for EdidHead (core/hw).

Implements VESA E-EDID base-block parser (structure version 1, rev 2)
over a 128-byte array: header check, checksum mod 256, manufacturer ID
(3×5-bit letters big-endian), product code (little-endian), serial
(little-endian), week/year, EDID version/revision, extension block count.
Cross-checks against the Kotlin contract in
core/hw/src/main/kotlin/dev/fusion/hw/EdidHead.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

HEADER = bytes([0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00])


def parse_edid(block):
    assert len(block) == 128, f"EDID base block must be 128 bytes, got {len(block)}"
    for i in range(8):
        assert block[i] == HEADER[i], f"bad header byte at {i}"

    total = sum(b & 0xFF for b in block) & 0xFF
    assert total == 0, f"checksum: 128-byte sum is {total}, must be 0 mod 256"

    mfr = ((block[8] & 0xFF) << 8) | (block[9] & 0xFF)
    assert mfr & 0x8000 == 0, "manufacturer ID reserved bit set"
    letters = []
    for i in range(3):
        v = (mfr >> (10 - 5 * i)) & 0x1F
        assert 1 <= v <= 26, f"manufacturer letter {i + 1} out of 1..26: {v}"
        letters.append(chr(ord("A") + v - 1))
    manufacturer = "".join(letters)

    product_code = (block[10] & 0xFF) | ((block[11] & 0xFF) << 8)

    serial = 0
    for i in range(4):
        serial |= (block[12 + i] & 0xFF) << (8 * i)

    week_byte = block[16] & 0xFF
    model_year = week_byte == 0xFF
    assert model_year or week_byte <= 54, f"week of manufacture out of 0..54: {week_byte}"
    week = None if (model_year or week_byte == 0) else week_byte
    year = 1990 + (block[17] & 0xFF)

    version = block[18] & 0xFF
    revision = block[19] & 0xFF
    assert version == 1, f"unsupported EDID structure version {version}"

    extension_blocks = block[126] & 0xFF

    return {
        "manufacturer": manufacturer,
        "productCode": product_code,
        "serial": serial,
        "week": week,
        "year": year,
        "modelYearFlag": model_year,
        "version": version,
        "revision": revision,
        "extensionBlocks": extension_blocks,
    }


def make_edid_block(manufacturer="DEL", product_code=0x1234, serial=0xDEADBEEF,
                    week=None, year=2024, model_year=False, version=1, revision=3,
                    extension_blocks=0):
    """Build a valid 128-byte EDID block for testing."""
    block = bytearray(128)
    block[0:8] = HEADER

    # Manufacturer ID: 3×5-bit letters, big-endian 16-bit
    mfr = 0
    for i, c in enumerate(manufacturer):
        v = ord(c) - ord("A") + 1
        mfr |= v << (10 - 5 * i)
    block[8] = (mfr >> 8) & 0xFF
    block[9] = mfr & 0xFF

    # Product code, little-endian
    block[10] = product_code & 0xFF
    block[11] = (product_code >> 8) & 0xFF

    # Serial, little-endian
    for i in range(4):
        block[12 + i] = (serial >> (8 * i)) & 0xFF

    # Week/year
    if model_year:
        block[16] = 0xFF
    elif week is None:
        block[16] = 0
    else:
        block[16] = week
    block[17] = year - 1990

    # Version/revision
    block[18] = version
    block[19] = revision

    # Extension blocks
    block[126] = extension_blocks

    # Checksum: sum of all 128 bytes = 0 mod 256
    checksum = (256 - (sum(block) & 0xFF)) & 0xFF
    block[127] = checksum

    return bytes(block)


class EdidHeadTest(unittest.TestCase):
    def test_valid_block(self):
        block = make_edid_block()
        info = parse_edid(block)
        self.assertEqual(info["manufacturer"], "DEL")
        self.assertEqual(info["productCode"], 0x1234)
        self.assertEqual(info["serial"], 0xDEADBEEF)
        self.assertIsNone(info["week"])
        self.assertEqual(info["year"], 2024)
        self.assertFalse(info["modelYearFlag"])
        self.assertEqual(info["version"], 1)
        self.assertEqual(info["revision"], 3)
        self.assertEqual(info["extensionBlocks"], 0)

    def test_bad_header(self):
        block = bytearray(make_edid_block())
        block[0] = 0xAA
        with self.assertRaises(AssertionError):
            parse_edid(bytes(block))

    def test_wrong_size(self):
        with self.assertRaises(AssertionError):
            parse_edid(b"\x00" * 127)
        with self.assertRaises(AssertionError):
            parse_edid(b"\x00" * 129)

    def test_bad_checksum(self):
        block = bytearray(make_edid_block())
        block[50] = (block[50] + 1) & 0xFF  # break checksum
        with self.assertRaises(AssertionError):
            parse_edid(bytes(block))

    def test_manufacturer_reserved_bit(self):
        block = bytearray(make_edid_block())
        # Set reserved bit in manufacturer ID
        block[8] |= 0x80
        with self.assertRaises(AssertionError):
            parse_edid(bytes(block))

    def test_manufacturer_letters(self):
        # "ABC" = letters 1,2,3 -> mfr = 1<<10 | 2<<5 | 3 = 1024 + 64 + 3 = 1091
        block = make_edid_block(manufacturer="ABC")
        info = parse_edid(block)
        self.assertEqual(info["manufacturer"], "ABC")

    def test_week_and_year(self):
        block = make_edid_block(week=24, year=2023)
        info = parse_edid(block)
        self.assertEqual(info["week"], 24)
        self.assertEqual(info["year"], 2023)
        self.assertFalse(info["modelYearFlag"])

    def test_model_year_flag(self):
        block = make_edid_block(model_year=True, year=2025)
        info = parse_edid(block)
        self.assertIsNone(info["week"])
        self.assertEqual(info["year"], 2025)
        self.assertTrue(info["modelYearFlag"])

    def test_week_0_is_unspecified(self):
        block = make_edid_block(week=0, year=2022)
        info = parse_edid(block)
        self.assertIsNone(info["week"])
        self.assertEqual(info["year"], 2022)

    def test_week_54_valid(self):
        block = make_edid_block(week=54, year=2021)
        info = parse_edid(block)
        self.assertEqual(info["week"], 54)

    def test_week_55_invalid(self):
        block = bytearray(make_edid_block(week=55, year=2021))
        with self.assertRaises(AssertionError):
            parse_edid(bytes(block))

    def test_version_not_1(self):
        block = bytearray(make_edid_block(version=2))
        with self.assertRaises(AssertionError):
            parse_edid(bytes(block))

    def test_extension_blocks(self):
        block = make_edid_block(extension_blocks=2)
        info = parse_edid(block)
        self.assertEqual(info["extensionBlocks"], 2)

    def test_checksum_with_extension_blocks(self):
        # Verify checksum works with non-zero extension blocks
        block = make_edid_block(extension_blocks=3)
        info = parse_edid(block)
        self.assertEqual(info["extensionBlocks"], 3)


if __name__ == "__main__":
    unittest.main()
