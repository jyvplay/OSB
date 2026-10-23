#!/usr/bin/env python3
"""Pure-Python R38 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import hashlib
import re
import struct
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r38'
KERNELS = [
    'feature/beam/src/main/kotlin/dev/fusion/beam/MagnetParse.kt',
    'feature/beam/src/main/kotlin/dev/fusion/beam/Bencode.kt',
    'feature/media/src/main/kotlin/dev/fusion/media/FlacHead.kt',
    'format/image/src/main/kotlin/dev/fusion/format/image/WebpHead.kt',
    'core/model/src/main/kotlin/dev/fusion/model/SemVer.kt',
    'core/storage/src/main/kotlin/dev/fusion/storage/UuidInfo.kt',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/HttpRange.kt',
    'feature/messages/src/main/kotlin/dev/fusion/messages/QuotedPrint.kt',
    'core/security/src/main/kotlin/dev/fusion/security/PemBound.kt',
    'feature/automation/src/main/kotlin/dev/fusion/automation/IsoWeek.kt',
    'feature/accessibility/src/main/kotlin/dev/fusion/a11y/HslColor.kt',
    'core/hw/src/main/kotlin/dev/fusion/hw/EdidHead.kt',
    'feature/capture/src/main/kotlin/dev/fusion/capture/VttTime.kt',
    'privacy/src/main/java/dev/fusion/privacy/LuhnCheck.kt',
    'core/index/src/main/kotlin/dev/fusion/index/Soundex.kt',
    'format/pdf/src/main/java/dev/fusion/format/pdf/PdfName.kt',
    'core/security/src/main/kotlin/dev/fusion/security/KeyCadence.kt',
]


class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)

    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r38/kotlin/dev/fusion/testing/R38Checks.kt').is_file())

    def test_runner_covers_every_kernel(self):
        runner = (ROOT / 'tools/run_r38_contracts.py').read_text()
        for rel in KERNELS:
            self.assertIn(rel, runner, f'{rel} missing from run_r38_contracts.py')

    def test_seventeen_kernels(self):
        self.assertEqual(len(KERNELS), 17)
        self.assertEqual(len(set(KERNELS)), 17)


class NoPackageCollisions(unittest.TestCase):
    def test_no_top_level_redeclaration(self):
        decl = re.compile(r'^(?:data class|class|object|interface|typealias|enum class|sealed class|sealed interface) (\w+)', re.M)
        for rel in KERNELS:
            here = ROOT / rel
            names = set(decl.findall(here.read_text()))
            self.assertTrue(names, rel)
            for sib in here.parent.glob('*.kt'):
                if sib == here:
                    continue
                clash = names & set(decl.findall(sib.read_text()))
                self.assertEqual(clash, set(), f'{rel} redeclares {clash} from {sib.name}')


class FlacFixture(unittest.TestCase):
    def setUp(self):
        self.raw = (FIX / 'flac.bin').read_bytes()

    def test_marker_and_block_header(self):
        self.assertEqual(self.raw[:4], b'fLaC')
        self.assertEqual(self.raw[4], 0x80)  # last=1, type=0
        self.assertEqual(int.from_bytes(self.raw[5:8], 'big'), 34)
        self.assertEqual(len(self.raw), 42)

    def test_streaminfo_fields(self):
        body = self.raw[8:]
        self.assertEqual(struct.unpack('>HH', body[:4]), (4096, 4096))
        acc = int.from_bytes(body[10:18], 'big')
        self.assertEqual(acc >> 44, 44100)
        self.assertEqual(((acc >> 41) & 7) + 1, 2)
        self.assertEqual(((acc >> 36) & 0x1F) + 1, 16)
        self.assertEqual(acc & ((1 << 36) - 1), 88200)

    def test_md5_unknown(self):
        self.assertEqual(self.raw[26:42], bytes(16))


class WebpFixture(unittest.TestCase):
    def setUp(self):
        self.raw = (FIX / 'webp.bin').read_bytes()

    def test_riff_envelope(self):
        self.assertEqual(self.raw[:4], b'RIFF')
        self.assertEqual(struct.unpack('<I', self.raw[4:8])[0], len(self.raw) - 8)
        self.assertEqual(self.raw[8:12], b'WEBP')

    def test_vp8x_payload(self):
        self.assertEqual(self.raw[12:16], b'VP8X')
        self.assertEqual(struct.unpack('<I', self.raw[16:20])[0], 10)
        self.assertEqual(self.raw[20], 0x30)  # ICC | ALPHA, libwebp flag values
        self.assertEqual(int.from_bytes(self.raw[24:27], 'little'), 1023)
        self.assertEqual(int.from_bytes(self.raw[27:30], 'little'), 767)


class EdidFixture(unittest.TestCase):
    def setUp(self):
        self.raw = (FIX / 'edid.bin').read_bytes()

    def test_header_and_checksum(self):
        self.assertEqual(len(self.raw), 128)
        self.assertEqual(self.raw[:8], bytes([0, 255, 255, 255, 255, 255, 255, 0]))
        self.assertEqual(sum(self.raw) % 256, 0)

    def test_dell_pnp_id(self):
        # 0x10AC is the registered Dell PnP id; 5-bit letters decode to DEL
        self.assertEqual(self.raw[8:10], bytes([0x10, 0xAC]))
        mfr = int.from_bytes(self.raw[8:10], 'big')
        letters = ''.join(chr(ord('A') + ((mfr >> s) & 0x1F) - 1) for s in (10, 5, 0))
        self.assertEqual(letters, 'DEL')

    def test_vendor_block(self):
        self.assertEqual(int.from_bytes(self.raw[10:12], 'little'), 0xA0C5)
        self.assertEqual(self.raw[16], 2)
        self.assertEqual(1990 + self.raw[17], 2020)
        self.assertEqual((self.raw[18], self.raw[19]), (1, 4))


class SpecPins(unittest.TestCase):
    """String-level pins that tie kernels to the fetched primary sources."""

    def test_bencode_pins_bep3_rules(self):
        text = (ROOT / KERNELS[1]).read_text()
        self.assertIn('i-0e', text)
        self.assertIn('sorted as raw strings', text)

    def test_magnet_pins_bep9_grammar(self):
        text = (ROOT / KERNELS[0]).read_text()
        self.assertIn('urn:btih:', text)
        self.assertIn('urn:btmh:', text)
        self.assertIn('x.pe', text)

    def test_flac_pins_rfc9639(self):
        text = (ROOT / KERNELS[2]).read_text()
        self.assertIn('RFC 9639', text)
        self.assertIn('STREAMINFO_LENGTH = 34', text)
        self.assertIn('16..65535', text)

    def test_webp_pins_rfc9649_flags(self):
        text = (ROOT / KERNELS[3]).read_text()
        for flag in ('FLAG_ICC = 0x20', 'FLAG_ALPHA = 0x10', 'FLAG_EXIF = 0x08',
                     'FLAG_XMP = 0x04', 'FLAG_ANIM = 0x02'):
            self.assertIn(flag, text)

    def test_uuid_pins_rfc9562_vector(self):
        text = (ROOT / KERNELS[5]).read_text()
        self.assertIn('017F22E2-79B0-7CC3-98C4-DC0C0C07398F', text)
        self.assertIn('1645557742000', text)

    def test_cadence_pins_published_thresholds_and_limits(self):
        text = (ROOT / KERNELS[16]).read_text()
        self.assertIn('MACHINE_RATE_PER_S = 200.0', text)
        self.assertIn('MACHINE_SD_MS = 1.5', text)
        self.assertIn('EVADABLE', text)
        self.assertIn('inline hardware keylogger', text)

    def test_soundex_pins_nara_vectors(self):
        text = (ROOT / KERNELS[14]).read_text()
        self.assertIn('T522', text)
        self.assertIn('A261', text)
        self.assertIn('P236', text)

    def test_pdfname_pins_sharp_zero_rule(self):
        text = (ROOT / KERNELS[15]).read_text()
        self.assertIn('#00', text)
        self.assertIn('7.3.5', text)


class ReferenceMirrorsAgree(unittest.TestCase):
    def test_reference_runs_clean(self):
        sys.path.insert(0, str(ROOT / 'tools'))
        import check_r38_reference as ref
        self.assertEqual(ref.soundex_mirror('Tymczak'), 'T522')
        self.assertEqual(ref.soundex_mirror('Ashcraft'), 'A261')
        self.assertTrue(ref.luhn_valid('4111111111111111'))
        self.assertFalse(ref.luhn_valid('4111111111111112'))
        key = ref.semver_key
        self.assertLess(key('1.0.0-alpha'), key('1.0.0-alpha.1'))
        self.assertLess(key('1.0.0-rc.1'), key('1.0.0'))

    def test_bdecode_mirror_strictness(self):
        sys.path.insert(0, str(ROOT / 'tools'))
        import check_r38_reference as ref
        self.assertEqual(ref.bdecode(b'd3:cow3:moo4:spam4:eggse'), {b'cow': b'moo', b'spam': b'eggs'})
        with self.assertRaises((AssertionError, ValueError)):
            ref.bdecode(b'd1:b0:1:a0:e')


class FixtureHashesStable(unittest.TestCase):
    PINNED = {
        'flac.bin': 'e5a397ce78f7e95e0eba692b95e990003de53b371817b166d68a827b554709a9',
        'webp.bin': 'e291d05f5c1a32f670b1e660ba22907d877725cd6b963e2f7cc5f4966bbacbdf',
        'edid.bin': '9d57aa0a77fab6133ef867a4bcc67159cfe7398965d79a1737467533cdfe49a0',
    }

    def test_sha256_pins(self):
        for name, want in self.PINNED.items():
            got = hashlib.sha256((FIX / name).read_bytes()).hexdigest()
            self.assertEqual(got, want, name)


if __name__ == '__main__':
    unittest.main(verbosity=1)
