"""Adversarial local JUnit fixtures only; no Android result is claimed."""
import os
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

import r64_office_case_gate as gate

SHA = 'a' * 40  # Local parser fixture, not a real commit or run.
NAMES = sorted(gate.EXPECTED)


def write_cases(path, names=NAMES, statuses=None):
    statuses = statuses or {}
    root = ET.Element('testsuite')
    for identity in names:
        cls, _, method = identity.rpartition('.')
        node = ET.SubElement(root, 'testcase', classname=cls, name=method)
        if identity in statuses: ET.SubElement(node, statuses[identity])
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(path, encoding='utf-8', xml_declaration=True)


class OfficeCaseGateTests(unittest.TestCase):
    def setUp(self):
        self.previous = os.environ.get('GITHUB_SHA'); os.environ['GITHUB_SHA'] = SHA
    def tearDown(self):
        if self.previous is None: os.environ.pop('GITHUB_SHA', None)
        else: os.environ['GITHUB_SHA'] = self.previous

    def test_exact_fixture(self):
        self.assertEqual(len(NAMES), 37)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); write_cases(root / 'pixel/TEST-local.xml')
            self.assertEqual(gate.verify(root, SHA)['counts'], {'PASS': 37, 'FAIL': 0, 'SKIP': 0})

    def test_missing_extra_and_duplicate_are_not_passes(self):
        for names in (NAMES[:-1], NAMES + [NAMES[0]], NAMES + ['dev.fusion.shell.Bad.fake'],
                      [*NAMES[:-1], NAMES[0]]):
            with self.subTest(last=names[-1]), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); write_cases(root / 'TEST-a.xml', names)
                with self.assertRaisesRegex(ValueError, '37 unique'): gate.verify(root, SHA)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); write_cases(root / 'TEST-a.xml'); write_cases(root / 'TEST-b.xml', [NAMES[0]])
            with self.assertRaisesRegex(ValueError, '37 unique'): gate.verify(root, SHA)

    def test_error_failure_and_skip_are_not_passes(self):
        for status in ('failure', 'error', 'skipped'):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); write_cases(root / 'TEST-a.xml', statuses={NAMES[0]: status})
                with self.assertRaisesRegex(ValueError, 'actually PASS'): gate.verify(root, SHA)

    def test_sha_absent_oversize_and_malformed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for source in (None, '', 'abc', 'g' * 40):
                with self.subTest(source=source), self.assertRaises(ValueError): gate.verify(root, source)
            with self.assertRaisesRegex(ValueError, 'Missing'): gate.verify(root, SHA)
            result = root / 'TEST-a.xml'; write_cases(result)
            with self.assertRaisesRegex(ValueError, 'candidate SHA'): gate.verify(root, 'b' * 40)
            result.write_text('<not-closed', encoding='utf-8')
            with self.assertRaises(ET.ParseError): gate.verify(root, SHA)
            result.write_bytes(b'x' * (16 * 1024 * 1024 + 1))
            with self.assertRaisesRegex(ValueError, 'oversized'): gate.verify(root, SHA)

    def test_symlink_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); target = root / 'target.xml'; write_cases(target)
            (root / 'TEST-a.xml').symlink_to(target)
            with self.assertRaisesRegex(ValueError, 'Unsafe'): gate.verify(root, SHA)


if __name__ == '__main__': unittest.main()
