#!/usr/bin/env python3
"""Guards for the shared bounded ZIP reader and the four core readers that use it."""
from pathlib import Path
import hashlib
import tempfile
import unittest
from unittest import mock

import r72_core_zip_cases as cases

try:
    import openpyxl  # noqa: F401
    HAS_OPENPYXL = True
except ImportError:  # The CI discovery step runs before the reference libraries are installed.
    HAS_OPENPYXL = False

ROOT = Path(__file__).resolve().parents[1]
OOXML = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml'
READERS = ('MultiSheetBook', 'XlsxStyledBook', 'PptxSlideDeck', 'DocxRichDocument')


def bounded_markers(source):
    body = source.split('fun read(', 1)[1].split('return out', 1)[0]
    compact = ''.join(body.split())
    markers = ('require(names.add(name))', 'valpayload=if(entry.isDirectory)null',
               'minOf(buffer.size.toLong(),partBytes-part+1L,expandedBytes-total+1L)',
               'require(want>0L)', 'require(read>0)', 'require(part<=partBytes&&total<=expandedBytes)',
               'payload?.write(buffer,0,read)')
    if not all(marker in compact for marker in markers):
        raise ValueError('Missing bounded per-entry accounting')
    order = [compact.index(marker) for marker in markers]
    if order != sorted(order):
        raise ValueError('ZIP accounting occurs in an unsafe order')
    if 'if(entry.isDirectory)continue' in compact:
        raise ValueError('Directory entries bypass the budget')
    return compact


class CoreZipGuards(unittest.TestCase):
    def test_shared_reader_counts_every_entry_once(self):
        source = (OOXML / 'BoundedZip.kt').read_text(encoding='utf-8')
        bounded_markers(source)
        self.assertIn('internal object BoundedZip', source)
        self.assertNotIn('readBytes()', source)

    def test_reverting_the_shared_reader_is_rejected(self):
        source = (OOXML / 'BoundedZip.kt').read_text(encoding='utf-8')
        for old, new in (('require(names.add(name))', 'require(name !in out)'),
                         ('require(read > 0)', 'require(read >= 0)'),
                         ('val payload = if (entry.isDirectory) null else ByteArrayOutputStream()',
                          'if (entry.isDirectory) continue\n                val payload = ByteArrayOutputStream()')):
            with self.subTest(old), self.assertRaises(ValueError):
                bounded_markers(source.replace(old, new, 1))

    def test_all_four_readers_delegate_and_keep_budgets(self):
        for name in READERS:
            with self.subTest(name):
                text = (OOXML / f'{name}.kt').read_text(encoding='utf-8')
                self.assertIn('BoundedZip.read(', text)
                self.assertIn('expandedBytes =', text)
                self.assertIn('entries =', text)
                self.assertNotIn('.readBytes()', text)
                self.assertNotIn('ZipInputStream(', text)
        self.assertIn('const val MAX_PART_BYTES = 8 * 1024 * 1024',
                      (OOXML / 'XlsxStyledBook.kt').read_text(encoding='utf-8'))
        self.assertIn('const val MAX_ENTRIES = 2_048', (OOXML / 'PptxSlideDeck.kt').read_text(encoding='utf-8'))

    @unittest.skipUnless(HAS_OPENPYXL, 'openpyxl seeds the fixtures; the CI reader step installs it')
    def test_fixtures_are_real_and_tamper_visible(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            cases.build(directory)
            rows = cases.read_tsv(directory / 'cases.tsv', cases.HEADER)
            cases.index(rows)
            self.assertEqual(len(rows), len({row['id'] for row in rows}))
            if not any(row['pinned'] != row['expected'] for row in rows):
                self.fail('no counterexample declared')
            payload = (directory / rows[0]['file']).read_bytes()
            self.assertTrue(payload.startswith(b'PK'))
            observed = {row['id']: row['expected'] for row in rows}
            self.assertEqual(cases.compare(rows, observed), [])
            forged = dict(observed)
            forged[rows[0]['id']] = 'REFUSE' if rows[0]['expected'] == 'ACCEPT' else 'ACCEPT'
            self.assertEqual(cases.compare(rows, forged), [rows[0]['id']])
            with self.assertRaises(ValueError):
                cases.compare(rows, dict(list(observed.items())[:-1]))
            (directory / rows[0]['file']).write_bytes(payload + b'\0')
            with self.assertRaises(ValueError):
                self._hash_check(directory, rows)

    def _hash_check(self, directory, rows):
        for row in rows:
            if hashlib.sha256((directory / row['file']).read_bytes()).hexdigest() != row['sha256']:
                raise ValueError('fixture changed: ' + row['file'])

    def test_runner_and_ci_execute_the_real_readers(self):
        runner = (ROOT / 'tools/run_r72_core_zip.py').read_text(encoding='utf-8')
        for required in ("BASE = 'aacae2396ca3af6363e973999a2115132efa272c'", "'cat-file', '-e'",
                         "compile_sources(env, directory, 'pinned'", 'dev.fusion.testing.R72CoreZipProbe',
                         "cases.compare(rows, outcomes['candidate'])", "'-Xmx512m'"):
            self.assertIn(required, runner)
        self.assertNotIn('shell=True', runner)
        gradle = (ROOT / 'testing/build.gradle.kts').read_text(encoding='utf-8')
        workflow = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text(encoding='utf-8')
        self.assertIn('r72CoreZipContracts', gradle)
        self.assertIn('src/r72core/kotlin', gradle)
        for required in ('tools/r72_core_zip_cases.py build testing/build/r72core',
                         ':testing:r72CoreZipContracts',
                         'tools/r72_core_zip_cases.py verify testing/build/r72core',
                         'test_r72_core_zip'):
            self.assertIn(required, workflow)
        self.assertNotIn('continue-on-error', workflow)


if __name__ == '__main__':
    unittest.main()
