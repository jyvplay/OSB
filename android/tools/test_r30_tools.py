#!/usr/bin/env python3
"""Pure-Python R30 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r30'
KERNELS = [
    'cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspMessage.kt',
    'feature/capture/src/main/kotlin/dev/fusion/capture/Timecode.kt',
    'feature/continuity/src/main/kotlin/dev/fusion/continuity/VEventParse.kt',
    'feature/together/src/main/kotlin/dev/fusion/together/LamportClock.kt',
    'format/image/src/main/kotlin/dev/fusion/format/image/GifInfo.kt',
    'feature/settings/src/main/kotlin/dev/fusion/settingsui/PatchScope.kt',
    'format/archive/src/main/kotlin/dev/fusion/archive/TarHeader.kt',
    'feature/lens/src/main/kotlin/dev/fusion/lens/ZoomLadder.kt',
    'feature/messages/src/main/kotlin/dev/fusion/messages/SmsSegments.kt',
    'core/model/src/main/kotlin/dev/fusion/model/DensitySelect.kt',
]

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r30/kotlin/dev/fusion/testing/R30Checks.kt').is_file())

class RtspFixture(unittest.TestCase):
    def test_framing(self):
        raw = (FIX / 'rtsp_announce.txt').read_bytes()
        self.assertNotIn(b'\n', raw.replace(b'\r\n', b''))
        head, body = raw.split(b'\r\n\r\n')
        headers = dict(l.split(': ', 1) for l in head.decode().split('\r\n')[1:])
        self.assertEqual(int(headers['Content-Length']), len(body))

class IcsFixture(unittest.TestCase):
    def test_shape(self):
        raw = (FIX / 'event.ics').read_bytes()
        self.assertTrue(raw.endswith(b'\r\n'))
        text = raw.decode()
        self.assertIn('BEGIN:VEVENT', text)
        self.assertTrue(any(l.startswith(' ') for l in text.split('\r\n')), 'must contain a folded line')

class GifFixture(unittest.TestCase):
    def test_magic(self):
        g = (FIX / 'frames2.gif').read_bytes()
        self.assertIn(g[:6], (b'GIF87a', b'GIF89a'))
        self.assertEqual(g[-1:], b'\x3b')

class TarFixture(unittest.TestCase):
    def test_headers(self):
        t = (FIX / 'archive.tar').read_bytes()
        self.assertEqual(len(t) % 512, 0)
        self.assertEqual(t[257:263], b'ustar\x00')
        s = sum(0x20 if 148 <= i < 156 else t[i] for i in range(512))
        stored = int(t[148:156].split(b'\0')[0].strip() or b'0', 8)
        self.assertEqual(s, stored)

class TimecodeCrossCheck(unittest.TestCase):
    def test_vectors(self):
        self.assertEqual(1040 * 25 // 1000 % 25, 1)
        self.assertEqual((86399999 * 30 // 1000) // 30, 86399)

class TarOctalCrossCheck(unittest.TestCase):
    def test_octal(self):
        self.assertEqual(int('0000644', 8), 420)
        self.assertEqual(int('0000006', 8), 6)

class SmsCrossCheck(unittest.TestCase):
    def test_gsm_membership(self):
        text = (ROOT / KERNELS[8]).read_text()
        m = re.search(r'private const val BASIC =\n\s*"((?:[^"\\]|\\.)*)"', text)
        raw = m.group(1)
        basic = re.sub(r'\\(.)', lambda e: {'n': '\n', 'r': '\r', '"': '"', '\\': '\\', '$': '$'}[e.group(1)], raw)
        self.assertIn('£', basic)
        self.assertIn('$', basic)
        self.assertNotIn('—', basic)
        self.assertEqual(len(set(basic)), len(basic), 'BASIC must not repeat codepoints')

class ZoomCrossCheck(unittest.TestCase):
    def test_ladder(self):
        import math
        self.assertAlmostEqual(2.0 ** 0.5, 1.41421356, places=7)
        self.assertEqual(round(math.log2(16.0) * 2), 8)

if __name__ == '__main__':
    unittest.main()
