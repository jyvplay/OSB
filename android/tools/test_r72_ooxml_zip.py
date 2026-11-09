#!/usr/bin/env python3
"""Host guards around the production import-budget fixtures; not Android or provider evidence."""
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import r72_ooxml_zip_cases as cases

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/Ooxml.kt'


class OoxmlBudgetGuards(unittest.TestCase):
    def test_source_declares_the_measured_limits(self):
        source = SOURCE.read_text(encoding='utf-8')
        for declaration in ('const val MAX_PART_BYTES = 32 * 1024 * 1024',
                            'const val MAX_ENTRIES = 5_000',
                            'const val MAX_EXPANDED_BYTES = 64L * 1024 * 1024',
                            'const val MAX_XML_BYTES = 2 * 1024 * 1024'):
            self.assertIn(declaration, source)
        self.assertEqual((cases.PART, cases.ENTRIES, cases.TOTAL, cases.XML),
                         (32 * 1024 * 1024, 5_000, 64 * 1024 * 1024, 2 * 1024 * 1024))
        read = source.split('private fun readParts(', 1)[1].split('return out', 1)[0]
        compact = ''.join(read.split())
        for marker in ('require(compressed<=MAX_INPUT_BYTES)', 'compressed+=n', 'count+=n;total+=n',
                       'require(count<=limit&&total<=MAX_EXPANDED_BYTES)',
                       'require(names.add(name))', 'valselected=!entry.isDirectory&&want(name)'):
            self.assertIn(marker, compact)
        self.assertNotIn('if(entry.isDirectory)continue', compact)
        self.assertLess(compact.index('count+=n;total+=n'), compact.index('if(bytes!=null)'))
        self.assertLess(compact.index('require(count<=limit&&total<=MAX_EXPANDED_BYTES)'),
                        compact.index('zip.closeEntry()'))

    def test_ci_and_runner_execute_real_extractor_contracts(self):
        gradle = (ROOT / 'testing/build.gradle.kts').read_text(encoding='utf-8')
        workflow = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text(encoding='utf-8')
        for required in ('r72OoxmlZipSeed', 'r72OoxmlZipContracts', 'maxHeapSize = "512m"'):
            self.assertIn(required, gradle)
        for required in (':testing:r72OoxmlZipSeed', 'tools/r72_ooxml_zip_cases.py build testing/build/r72ooxml',
                         ':testing:r72OoxmlZipContracts',
                         'tools/r72_ooxml_zip_cases.py verify testing/build/r72ooxml', 'test_r72_ooxml_zip'):
            self.assertIn(required, workflow)
        self.assertNotIn('continue-on-error', workflow)
        runner = (ROOT / 'tools/run_r72_ooxml_zip.py').read_text(encoding='utf-8')
        self.assertIn('Refusing to overwrite existing evidence', runner)
        self.assertIn("'verify'", runner)
        self.assertIn("'-Xmx512m'", runner)
        self.assertNotIn('shell=True', runner)

    def test_forged_manifest_and_observations_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            with mock.patch.object(cases, 'PART', 131_072), mock.patch.object(cases, 'TOTAL', 262_144), \
                    mock.patch.object(cases, 'ENTRIES', 60), mock.patch.object(cases, 'XML', 4_096):
                cases.build(directory)
                rows = cases.read_tsv(directory / 'cases.tsv', cases.HEADER)
                self.assertEqual(len(rows), sum(len(v) for v in cases.KINDS.values()))
                with self.assertRaises(ValueError):
                    cases.read_tsv(directory / 'cases.tsv', ('id', 'kind'))
                forged = [dict(row) for row in rows]
                forged[0]['expected'] = 'REFUSE'
                with self.assertRaises(ValueError):
                    cases.index(forged)
                with self.assertRaises(ValueError):
                    cases.index(rows + [dict(rows[0])])
                good = [dict(id=row['id'], status=row['expected'], reason=row['reason']) for row in rows]
                self.assertEqual(cases.compare(rows, good), len(rows))
                bad = [dict(row) for row in good]
                bad[0]['status'] = 'REFUSE'
                with self.assertRaises(ValueError):
                    cases.compare(rows, bad)
                with self.assertRaises(ValueError):
                    cases.compare(rows, good[:-1])
                self.assertGreater(cases.inspect(directory, rows), len(rows))
                tampered = directory / rows[0]['file']
                original = tampered.read_bytes()
                tampered.write_bytes(original + b'\0')
                with self.assertRaises(ValueError):
                    cases.inspect(directory, rows)
                tampered.write_bytes(original)
                self.assertGreater(cases.inspect(directory, rows), 0)

    def test_scenarios_cover_every_declared_boundary(self):
        for kind, scenarios in cases.KINDS.items():
            for required in ('entries-at-limit', 'entries-over', 'dir-at-limit', 'dir-over',
                             'total-at-limit', 'total-over', 'xml-over', 'duplicate-dir'):
                self.assertIn(required, scenarios, kind)
            for name in cases.UNSAFE:
                self.assertIn(name, scenarios, kind)
            with mock.patch.object(cases, 'PART', 131_072), mock.patch.object(cases, 'TOTAL', 262_144), \
                    mock.patch.object(cases, 'ENTRIES', 60), mock.patch.object(cases, 'XML', 4_096):
                base = cases.seed_parts(kind)
                at_limit = sum(len(c) for _, c in base) + sum(
                    len(c) for _, c in cases.scenario_parts(kind, 'total-at-limit', base))
                self.assertEqual(at_limit, cases.TOTAL)
                over = sum(len(c) for _, c in base) + sum(
                    len(c) for _, c in cases.scenario_parts(kind, 'total-over', base))
                self.assertEqual(over, cases.TOTAL + 1)
                selected = cases.scenario_parts(kind, 'xml-over', base)[0][1]
                self.assertEqual(len(selected), cases.XML + 1)


if __name__ == '__main__':
    unittest.main()
