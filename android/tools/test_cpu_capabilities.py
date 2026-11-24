#!/usr/bin/env python3
"""Independent differential oracle for CpuCapabilities (core/capabilities).

Parses a /proc/cpuinfo-style block and extracts vendor, model, core count,
and known feature flags. Cross-checks against the Kotlin contract in
core/capabilities/src/main/kotlin/dev/fusion/capabilities/CpuCapabilities.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_INPUT = 1 << 14
MAX_FLAGS = 64
MAX_CORES = 1024
KNOWN_FLAGS = {
    "fpu", "vme", "de", "pse", "tsc", "msr", "pae", "mce",
    "cx8", "apic", "sep", "mtrr", "pge", "mca", "cmov", "pat",
    "pse36", "clflush", "mmx", "fxsr", "sse", "sse2", "ss",
    "ht", "tm", "ia64", "sse3", "pclmulqdq", "dtes64", "monitor",
    "ds_cpl", "vmx", "smx", "est", "tm2", "sse4_1", "sse4_2",
    "x2apic", "movbe", "popcnt", "tsc_deadline_timer", "aes",
    "xsave", "osxsave", "avx", "f16c", "rdrand", "hypervisor"
}


def parse(input_str: str):
    assert len(input_str) <= MAX_INPUT, f"input exceeds {MAX_INPUT} chars"
    vendor = None
    model = None
    cores = 0
    flags = set()
    for line in input_str.splitlines():
        trimmed = line.strip()
        if not trimmed:
            continue
        colon = trimmed.find(':')
        if colon < 0:
            continue
        key = trimmed[:colon].strip()
        value = trimmed[colon + 1:].strip()
        if key == "vendor_id":
            vendor = _ascii(value)
        elif key == "model name":
            model = _ascii(value)
        elif key == "cpu cores":
            v = _to_int(value)
            cores = max(0, min(v, MAX_CORES)) if v is not None else 0
        elif key == "processor":
            cores = min(cores + 1, MAX_CORES)
        elif key == "flags":
            for f in value.split(" "):
                flag = f.strip().lower()
                if flag and flag in KNOWN_FLAGS and len(flags) < MAX_FLAGS:
                    flags.add(flag)
    return (vendor, model, cores, flags)


def _ascii(s: str) -> str:
    sb = []
    for c in s:
        if 0x20 <= ord(c) <= 0x7E:
            sb.append(c)
        else:
            break
    return "".join(sb)[:MAX_INPUT]


def _to_int(s: str):
    try:
        return int(s)
    except ValueError:
        return None


class CpuCapabilitiesTest(unittest.TestCase):
    def test_full_parse(self):
        cpuinfo = (
            "vendor_id: GenuineIntel\n"
            "model name: Intel(R) Core(TM) i7-8650U CPU @ 1.90GHz\n"
            "cpu cores: 4\n"
            "flags: fpu vme de pse tsc msr pae mce cx8 apic sse sse2 ht tm avx\n"
        )
        vendor, model, cores, flags = parse(cpuinfo)
        self.assertEqual(vendor, "GenuineIntel")
        self.assertTrue(model.startswith("Intel(R) Core"))
        self.assertEqual(cores, 4)
        self.assertIn("sse", flags)
        self.assertIn("avx", flags)
        self.assertNotIn("unknown_flag", flags)

    def test_unknown_flags_dropped(self):
        cpuinfo = "flags: fpu xyz_unknown sse\n"
        _, _, _, flags = parse(cpuinfo)
        self.assertIn("fpu", flags)
        self.assertIn("sse", flags)
        self.assertNotIn("xyz_unknown", flags)

    def test_no_flags(self):
        _, _, _, flags = parse("")
        self.assertEqual(flags, set())

    def test_too_many_cores(self):
        cpuinfo = "\n".join(f"processor: {i}\n" for i in range(MAX_CORES + 5))
        _, _, cores, _ = parse(cpuinfo)
        self.assertEqual(cores, MAX_CORES)

    def test_input_too_large(self):
        with self.assertRaises(AssertionError):
            parse("x" * (MAX_INPUT + 1))


if __name__ == "__main__":
    unittest.main()
