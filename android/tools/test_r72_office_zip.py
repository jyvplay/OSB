#!/usr/bin/env python3
"""Host guards for the R72 Office ZIP accounting repair and its independent comparators."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import zipfile

import check_r72_office_zip_reference as reference
import r72_office_zip_cases as fixtures

ROOT = Path(__file__).resolve().parents[1]
READERS = {
    'DocxPackage': ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxPackage.kt',
    'FormulaXlsx': ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaXlsx.kt',
}


def read_loop(source):
    body = source.split('ZipInputStream(ByteArrayInputStream', 1)[1].split('return out', 1)[0]
    markers = ('require(names.add(name))',
               'val want = minOf(buf.size.toLong(), MAX_PART_BYTES - partBytes + 1L',
               'MAX_ZIP_BYTES - totalExpanded + 1L',
               'val n = zis.read(buf, 0, want)',
               'require(n > 0)',
               'require(partBytes <= MAX_PART_BYTES && totalExpanded <= MAX_ZIP_BYTES)',
               'payload?.write(buf, 0, n)',
               'if (payload != null) out[name] = payload.toByteArray()',
               'zis.closeEntry()')
    if not all(marker in body for marker in markers):
        raise ValueError('Missing bounded per-entry accounting marker')
    if [body.index(m) for m in markers] != sorted(body.index(m) for m in markers):
        raise ValueError('ZIP accounting occurs in an unsafe order')
    if 'if (!entry.isDirectory)' in body:
        raise ValueError('Directory branch reintroduced an uncounted entry path')
    return body


class OfficeZipGuard(unittest.TestCase):
    def test_both_real_readers_count_every_entry_before_closing(self):
        for name, path in READERS.items():
            with self.subTest(name):
                read_loop(path.read_text(encoding='utf-8'))

    def test_missing_or_reverted_accounting_is_rejected(self):
        for name, path in READERS.items():
            with self.subTest(name):
                source = path.read_text(encoding='utf-8')
                for old, new in (('require(names.add(name))', 'require(name !in out)'),
                                 ('val n = zis.read(buf, 0, want)', 'val n = zis.read(buf)'),
                                 ('require(n > 0)', 'require(n >= 0)')):
                    with self.assertRaises(ValueError):
                        read_loop(source.replace(old, new, 1))
                with self.assertRaises(ValueError):
                    read_loop(source.replace(
                        'val payload = if (entry.isDirectory) null else ByteArrayOutputStream()',
                        'if (!entry.isDirectory) { val payload = ByteArrayOutputStream()', 1))

    def test_limits_and_runner_are_not_weakened(self):
        for name, path in READERS.items():
            source = path.read_text(encoding='utf-8')
            self.assertIn('const val MAX_ZIP_BYTES = 16 * 1024 * 1024', source)
            self.assertIn('const val MAX_PART_BYTES = 1024 * 1024', source)
            self.assertIn('entries <= 256', source)
        runner = (ROOT / 'tools/run_r72_office_zip.py').read_text(encoding='utf-8')
        self.assertIn("BASE = 'aacae2396ca3af6363e973999a2115132efa272c'", runner)
        self.assertIn("'--baseline'", runner)
        self.assertIn("'-Xmx256m'", runner)
        self.assertIn('Refusing to overwrite existing evidence', runner)
        self.assertNotIn('shell=True', runner)
        gradle = (ROOT / 'testing/build.gradle.kts').read_text(encoding='utf-8')
        workflow = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text(encoding='utf-8')
        for required in ('r72OfficeZipSeed', 'r72OfficeZipContracts', 'src/r72zip/kotlin', 'maxHeapSize = "256m"'):
            self.assertIn(required, gradle)
        for required in (':testing:r72OfficeZipSeed', 'tools/r72_office_zip_cases.py testing/build/r72zip',
                         ':testing:r72OfficeZipContracts',
                         'tools/check_r72_office_zip_reference.py testing/build/r72zip', 'test_r72_office_zip'):
            self.assertIn(required, workflow)
        self.assertNotIn('continue-on-error', workflow)

    def test_fixture_builder_rejects_forged_boundaries(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            with mock.patch.object(fixtures, 'PART', 4_096), mock.patch.object(fixtures, 'TOTAL', 65_536):
                for kind in ('docx', 'xlsx'):
                    _seed(directory, kind)
                fixtures.build(directory)
            rows = reference.read_tsv(directory / 'cases.tsv',
                                      ('id', 'kind', 'file', 'expected', 'reason', 'sha256'))
            indexed = reference.index_cases(rows)
            self.assertEqual(len(indexed), 2 * len(reference.SPECS))
            with self.assertRaises(ValueError):
                reference.read_tsv(directory / 'cases.tsv', ('id', 'kind', 'file'))
            forged = [dict(row) for row in rows]
            forged[0]['expected'] = 'REFUSE'
            with self.assertRaises(ValueError):
                reference.index_cases(forged)
            with self.assertRaises(ValueError):
                reference.index_cases(rows + [dict(rows[0])])
            self.assertEqual(hashlib.sha256((directory / 'docx-normal.zip').read_bytes()).hexdigest(),
                             indexed['docx-normal']['sha256'])

    def test_observation_comparator_detects_every_baseline_failure(self):
        rows = [dict(id=f'{kind}-{name}', kind=kind, file=f'{kind}-{name}.zip', expected=status, reason=reason,
                     sha256='0' * 64)
                for kind in ('docx', 'xlsx') for name, (status, reason) in reference.SPECS.items()]
        baseline = [dict(id=row['id'], status=row['expected'], reason=row['reason']) for row in rows]
        for row in baseline:
            if row['id'] in reference.BASELINE_FAILURES:
                row['status'], row['reason'] = 'ACCEPT', '-'
        self.assertEqual(reference.compare_observations(rows, baseline, baseline=True), len(rows))
        with self.assertRaises(ValueError):
            reference.compare_observations(rows, baseline, baseline=False)
        repaired = [dict(id=row['id'], status=row['expected'], reason=row['reason']) for row in rows]
        self.assertEqual(reference.compare_observations(rows, repaired, baseline=False), len(rows))
        bad = [dict(row) for row in repaired]
        bad[0]['status'] = 'REFUSE' if bad[0]['status'] == 'ACCEPT' else 'ACCEPT'
        with self.assertRaises(ValueError):
            reference.compare_observations(rows, bad, baseline=False)
        with self.assertRaises(ValueError):
            reference.compare_observations(rows, repaired[:-1], baseline=False)


def _seed(directory, kind):
    parts = ({'[Content_Types].xml': b'<t/>', 'word/document.xml': b'<d/>'} if kind == 'docx'
             else {'[Content_Types].xml': b'<t/>', 'xl/workbook.xml': b'<w/>'})
    with zipfile.ZipFile(directory / f'seed.{kind}', 'w', zipfile.ZIP_DEFLATED) as out:
        for name, data in parts.items():
            out.writestr(name, data)


if __name__ == '__main__':
    unittest.main()
