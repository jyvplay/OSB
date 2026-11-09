#!/usr/bin/env python3
"""Host guards for the plain Writer early resource bound and its comparators."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def verify_order(source):
    body = source.split('fun write(text: String): ByteArray {', 1)[1]
    early = 'require(text.length <= MAX_TEXT_BYTES)'
    xml = 'OfficeSheetDraft.validateXml(text)'
    bytes_check = 'OfficeSheetDraft.utf8(text).size <= MAX_TEXT_BYTES'
    if not all(part in body for part in (early, xml, bytes_check)):
        raise ValueError('Missing early, XML or UTF-8 bound')
    if not body.index(early) < body.index(xml) < body.index(bytes_check):
        raise ValueError('Plain Writer validates/encodes before its inexpensive early bound')
    if body.index(bytes_check) > body.index('DocxPackage.write('):
        raise ValueError('Byte budget must be checked before packaging')


class PlainWriterGuard(unittest.TestCase):
    def test_real_source_uses_early_and_strict_bounds(self):
        source = (ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/PlainWriterDocx.kt').read_text(encoding='utf-8')
        verify_order(source)
        self.assertEqual(source.count('require(text.length <= MAX_TEXT_BYTES)'), 1)
        self.assertIn('const val MAX_TEXT_BYTES = 128 * 1024', source)

    def test_missing_or_reordered_guards_are_rejected(self):
        source = (ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/PlainWriterDocx.kt').read_text(encoding='utf-8')
        early = 'require(text.length <= MAX_TEXT_BYTES)'
        xml = 'OfficeSheetDraft.validateXml(text)'
        with self.assertRaises(ValueError):
            verify_order(source.replace(early, 'true', 1))
        with self.assertRaises(ValueError):
            verify_order(source.replace(xml, '/* omitted */', 1))
        swapped = source.replace(early, '__EARLY__', 1).replace(xml, early, 1).replace('__EARLY__', xml, 1)
        with self.assertRaises(ValueError):
            verify_order(swapped)

    def test_probe_and_runner_keep_real_execution_and_classification(self):
        probe = (ROOT / 'testing/src/r72writer/kotlin/dev/fusion/testing/R72WriterResourceProbe.kt').read_text(encoding='utf-8')
        for required in ('PlainWriterDocx.write("a".repeat(limit))',
                         'checkRefused("one UTF-8 byte over the limit")',
                         'OUT_OF_MEMORY phase=probe-input',
                         'OUT_OF_MEMORY phase=plain-writer',
                         'REFUSED phase=plain-writer'):
            self.assertIn(required, probe)
        runner = (ROOT / 'tools/run_r72_writer_guard.py').read_text(encoding='utf-8')
        for required in ("BASE = 'aacae2396ca3af6363e973999a2115132efa272c'",
                         "['git', 'show', f'{BASE}:{rel}']",
                         '(artifacts[\'pinned\'] / name).read_bytes() != (artifacts[\'candidate\'] / name).read_bytes()',
                         "'REFUSED phase=plain-writer' not in candidate.stdout",
                         "HEAP = '80m'"):
            self.assertIn(required, runner)
        self.assertIn("if pinned.returncode == 13:", runner)
        self.assertNotIn('shell=True', runner)


if __name__ == '__main__':
    unittest.main()
