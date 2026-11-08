#!/usr/bin/env python3
"""JUnit-gate adversaries using local XML fixtures; never a native-device receipt."""
import os
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

import r25_evidence_gate
import r64_media_case_gate as gate

SHA = 'a' * 40  # Parser fixture only; not a GitHub commit or run.
NAMES = sorted(r25_evidence_gate.expected('media'))


def write_cases(path, names=NAMES, statuses=None):
    statuses = statuses or {}
    root = ET.Element('testsuite')
    for identity in names:
        cls, _, method = identity.rpartition('.')
        case = ET.SubElement(root, 'testcase', classname=cls, name=method)
        if identity in statuses: ET.SubElement(case, statuses[identity])
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(path, encoding='utf-8', xml_declaration=True)


class MediaCaseGateTests(unittest.TestCase):
    def setUp(self):
        self.previous = os.environ.get('GITHUB_SHA')
        os.environ['GITHUB_SHA'] = SHA

    def tearDown(self):
        if self.previous is None: os.environ.pop('GITHUB_SHA', None)
        else: os.environ['GITHUB_SHA'] = self.previous

    def test_all_exact_cases_pass(self):
        self.assertEqual(len(NAMES), 3)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); write_cases(root / 'pixel/TEST-local.xml')
            self.assertEqual(gate.verify(root, SHA)['counts'], {'PASS': 3, 'FAIL': 0, 'SKIP': 0})

    def test_missing_extra_or_duplicate_cannot_pass(self):
        mutations = (NAMES[:-1], NAMES + [NAMES[0]], NAMES + ['dev.fusion.shell.Bad.injected'],
                     [NAMES[0], NAMES[0], NAMES[2]])
        for names in mutations:
            with self.subTest(names=names), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); write_cases(root / 'TEST-a.xml', names)
                with self.assertRaisesRegex(ValueError, 'three unique'): gate.verify(root, SHA)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); write_cases(root / 'TEST-a.xml'); write_cases(root / 'TEST-b.xml', [NAMES[0]])
            # The existing collector would coalesce identical rows; this gate must not.
            with self.assertRaisesRegex(ValueError, 'three unique'): gate.verify(root, SHA)

    def test_failure_error_or_skip_never_passes(self):
        for status in ('failure', 'error', 'skipped'):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); write_cases(root / 'TEST-a.xml', statuses={NAMES[1]: status})
                with self.assertRaisesRegex(ValueError, 'actually PASS'): gate.verify(root, SHA)

    def test_invalid_source_missing_and_oversized_results_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for source in (None, '', 'abc', 'g' * 40):
                with self.subTest(source=source), self.assertRaises(ValueError): gate.verify(root, source)
            with self.assertRaisesRegex(ValueError, 'Missing'): gate.verify(root, SHA)
            write_cases(root / 'TEST-a.xml')
            with self.assertRaisesRegex(ValueError, 'candidate SHA'): gate.verify(root, 'b' * 40)
            (root / 'TEST-a.xml').write_bytes(b'x' * (16 * 1024 * 1024 + 1))
            with self.assertRaisesRegex(ValueError, 'oversized'): gate.verify(root, SHA)

    def test_symlink_and_malformed_xml_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); result = root / 'TEST-a.xml'
            result.write_text('<not-closed', encoding='utf-8')
            with self.assertRaises(ET.ParseError): gate.verify(root, SHA)
            result.unlink(); target = root / 'target.xml'; write_cases(target)
            result.symlink_to(target)
            with self.assertRaisesRegex(ValueError, 'Unsafe'): gate.verify(root, SHA)


if __name__ == '__main__': unittest.main()
