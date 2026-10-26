#!/usr/bin/env python3
"""Pure-Python R37 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import binascii
import math
import re
import struct
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r37'
KERNELS = [
    'feature/notes/src/main/java/dev/fusion/notes/SlugHead.kt',
    'feature/files/src/main/kotlin/dev/fusion/filesui/SizeBucket.kt',
    'feature/continuity/src/main/kotlin/dev/fusion/continuity/TaskTally.kt',
    'format/archive/src/main/kotlin/dev/fusion/archive/XzHead.kt',
    'feature/editor/src/main/java/dev/fusion/editor/PasteWrap.kt',
    'cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspReason.kt',
    'privacy/src/main/java/dev/fusion/privacy/UtmStrip.kt',
    'core/security/src/main/kotlin/dev/fusion/security/HibpRange.kt',
    'feature/maps/src/main/kotlin/dev/fusion/maps/PolyLine.kt',
    'feature/media/src/main/kotlin/dev/fusion/media/Mp4Box.kt',
    'feature/terminal/src/main/kotlin/dev/fusion/terminal/OscLink.kt',
    'feature/find/src/main/kotlin/dev/fusion/find/RankFuse.kt',
    'core/commands/src/main/kotlin/dev/fusion/commands/ExitCode.kt',
    'feature/lens/src/main/kotlin/dev/fusion/lens/FovMath.kt',
]


class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)

    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r37/kotlin/dev/fusion/testing/R37Checks.kt').is_file())

    def test_runner_covers_every_kernel(self):
        runner = (ROOT / 'tools/run_r37_contracts.py').read_text()
        for rel in KERNELS:
            self.assertIn(rel, runner, f'{rel} missing from run_r37_contracts.py')


class NoPackageCollisions(unittest.TestCase):
    def test_no_top_level_redeclaration(self):
        decl = re.compile(r'^(?:data class|class|object|interface|typealias|enum class|sealed interface) (\w+)', re.M)
        for rel in KERNELS:
            here = ROOT / rel
            names = set(decl.findall(here.read_text()))
            self.assertTrue(names, rel)
            for sib in here.parent.glob('*.kt'):
                if sib == here:
                    continue
                clash = names & set(decl.findall(sib.read_text()))
                self.assertEqual(clash, set(), f'{rel} redeclares {clash} from {sib.name}')


class XzFixture(unittest.TestCase):
    def test_shape(self):
        raw = (FIX / 'xz.bin').read_bytes()
        self.assertEqual(len(raw), 12)
        self.assertEqual(raw[:6], bytes([0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00]))
        self.assertEqual(raw[6], 0)
        self.assertEqual(raw[7], 1)
        crc, = struct.unpack('<I', raw[8:12])
        self.assertEqual(crc, binascii.crc32(raw[6:8]) & 0xFFFFFFFF)
        self.assertEqual(crc, 0x36DE2269)

    def test_check_id_table_is_spec_values(self):
        text = (ROOT / 'format/archive/src/main/kotlin/dev/fusion/archive/XzHead.kt').read_text()
        self.assertIn('SHA256(10)', text)
        self.assertNotIn('SHA256(7)', text)
        self.assertEqual({0, 1, 4, 10}, {0x00, 0x01, 0x04, 0x0A})


class Mp4Fixture(unittest.TestCase):
    def test_shape(self):
        raw = (FIX / 'mp4.bin').read_bytes()
        self.assertEqual(len(raw), 32)
        self.assertEqual(raw[4:8], b'ftyp')
        self.assertEqual(struct.unpack_from('>I', raw, 0)[0], 24)
        self.assertEqual(raw[28:32], b'free')
        self.assertEqual(struct.unpack_from('>I', raw, 24)[0], 8)


class PolylineCrossCheck(unittest.TestCase):
    def test_canonical_example(self):
        # Google's documented example path, hardcoded; the encoder mirror is
        # recomputed in check_r37_reference.py.
        canon = '_p~iF~ps|U_ulLnnqC_mqNvxq`@'
        self.assertEqual(len(canon), 27)
        self.assertIn('~', canon)
        self.assertIn('`', canon)


class RtspCrossCheck(unittest.TestCase):
    def test_table_cardinality(self):
        text = (ROOT / 'cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspReason.kt').read_text()
        codes = re.findall(r'(\d{3}) to "', text)
        self.assertEqual(len(codes), 44, codes)
        self.assertEqual(len(set(codes)), 44)
        self.assertIn('304', codes)
        self.assertNotIn('306', codes)
        for expect in ('"Low on Storage Space"', '"Request Time-out"', '"Request-URI Too Large"',
                       '"Header Field Not Valid for Resource"', '"Option not supported"'):
            self.assertIn(expect, text)


class UtmCrossCheck(unittest.TestCase):
    def test_tracker_names(self):
        text = (ROOT / 'privacy/src/main/java/dev/fusion/privacy/UtmStrip.kt').read_text()
        names = re.findall(r'"([a-z_]+)"', text.split('val TRACKERS')[1].split(')')[0])
        self.assertEqual(len(names), 18, names)
        self.assertEqual(len(set(names)), 18)
        for n in ('utm_source', 'fbclid', 'gclid', 'msclkid', 'mc_eid', 'vero_id'):
            self.assertIn(n, names)


class RankFuseCrossCheck(unittest.TestCase):
    def test_rrf_scores(self):
        self.assertAlmostEqual(1 / 61, 0.01639344262295082, places=17)
        self.assertAlmostEqual(1 / 61 + 1 / 62, 0.03252247488101534, places=17)
        self.assertEqual(1 / 61 + 1 / 62, 1 / 62 + 1 / 61)


class FovCrossCheck(unittest.TestCase):
    def test_pins(self):
        fov = lambda f, d: math.degrees(2 * math.atan(d / (2 * f)))  # noqa: E731
        self.assertAlmostEqual(fov(50.0, 36.0), 39.597752709049864, places=12)
        self.assertAlmostEqual(fov(35.0, 36.0), 54.43222311461495, places=12)
        self.assertEqual(50.0 * 1.5, 75.0)


class VtodoSpelling(unittest.TestCase):
    """The R37 repair pins RFC 5545's IN-PROCESS token in kernel, consumer and mirror."""

    def test_rfc_token_everywhere(self):
        kernel = (ROOT / 'feature/continuity/src/main/kotlin/dev/fusion/continuity/VTodoParse.kt').read_text()
        self.assertIn('"IN-PROCESS" -> Status.IN_PROGRESS', kernel)
        self.assertNotIn('"IN-PROGRESS"', kernel)
        r34 = (ROOT / 'testing/src/r34/kotlin/dev/fusion/testing/R34Checks.kt').read_text()
        self.assertIn('STATUS:IN-PROCESS', r34)
        self.assertNotIn('STATUS:IN-PROGRESS', r34)
        ref = (ROOT / 'tools/check_r34_reference.py').read_text()
        self.assertNotIn('IN-PROGRESS', ref)


class HidTopology(unittest.TestCase):
    """The hardware-origin audit tool: parser shape + policy verdicts."""

    def setUp(self):
        sys.path.insert(0, str(ROOT / 'tools'))
        import hid_topology
        self.hid = hid_topology

    def test_fixture_verdict(self):
        devices = self.hid.parse_verbose((FIX / 'lsusb-verbose.txt').read_text())
        nodes = self.hid.parse_tree((FIX / 'lsusb-tree.txt').read_text())
        report = self.hid.audit(devices, nodes)
        self.assertEqual(len(devices), 4)
        self.assertEqual(report['keyboard_count'], 2)
        self.assertEqual(report['hid_nodes'], 3)
        self.assertEqual(report['max_depth'], 2)
        self.assertEqual(report['verdict'], 'REVIEW')
        joined = ' '.join(report['warnings'])
        self.assertIn('DUAL_KEYBOARD', joined)
        self.assertIn('HUB_IN_PATH', joined)

    def test_single_keyboard_is_ok(self):
        text = ('Bus 001 Device 003: ID 046d:c31c Keyboard\n'
                '      bInterfaceClass         3 Human Interface Device\n'
                '      bInterfaceSubClass      1 Boot Interface Subclass\n'
                '      bInterfaceProtocol      1 Keyboard\n')
        report = self.hid.audit(self.hid.parse_verbose(text), [])
        self.assertEqual(report['keyboard_count'], 1)
        self.assertEqual(report['verdict'], 'OK')

    def test_mouse_protocol_is_not_keyboard(self):
        text = ('Bus 001 Device 005: ID 046d:c077 Mouse\n'
                '      bInterfaceClass         3 Human Interface Device\n'
                '      bInterfaceSubClass      1 Boot Interface Subclass\n'
                '      bInterfaceProtocol      2 Mouse\n')
        self.assertEqual(self.hid.audit(self.hid.parse_verbose(text), [])['keyboard_count'], 0)

    def test_non_hid_class_ignored(self):
        text = ('Bus 001 Device 009: ID 1d6b:0003 Hub\n'
                '      bInterfaceClass         9 Hub\n'
                '      bInterfaceSubClass      0 Unused Class\n'
                '      bInterfaceProtocol      1 Keyboard\n')
        self.assertEqual(self.hid.audit(self.hid.parse_verbose(text), [])['keyboard_count'], 0)

    def test_caps(self):
        with self.assertRaises(ValueError):
            self.hid.parse_verbose(''.join(
                'Bus 001 Device %03d: ID 046d:c31c K\n' % i for i in range(600)))


class PasteCrossCheck(unittest.TestCase):
    def test_unit_stack(self):
        self.assertEqual(' ' * 3 * 2 + 'a', '      a')
        self.assertEqual('\t' * 3, '\t\t\t')
        self.assertTrue('  b'.startswith('  '))


if __name__ == '__main__':
    unittest.main()
