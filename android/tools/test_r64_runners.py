"""Host-runner cleanup adversaries; never a native or Office application result."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import run_r64_sheet_copy as sheet
import run_r64_sheet_csv as csv
import run_r64_writer_tables as writer

CASES = ((sheet, 'r64sheet', 'result-000.xlsx', 'cases.tsv', 'r64-sheet.jar'),
         (csv, 'r64csv', 'sheet-000.csv', 'cases.csv', 'r64-csv.jar'),
         (writer, 'r64writer', 'doc-000.docx', 'cases.csv', 'r64-writer.jar'))
ENV = {'KOTLINC': '/unused/kotlinc', 'JAVA_HOME': '/unused'}


class RunnerHygiene(unittest.TestCase):
    def test_symlinked_artifact_directory_is_never_cleaned(self):
        for module, name, owned, _, _ in CASES:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp) / 'workspace'; out = root / 'testing/build' / name
                out.mkdir(parents=True); target = Path(tmp) / 'keep'; target.mkdir()
                sentinel = target / owned; sentinel.write_bytes(b'important')
                (out / 'artifacts').symlink_to(target, target_is_directory=True)
                with patch.object(module, 'ROOT', root), patch.object(module.bootstrap_host_tools, 'environment', return_value=ENV), \
                     patch.object(module.subprocess, 'run') as run:
                    with self.assertRaisesRegex(SystemExit, 'Symlink build'): module.main()
                    run.assert_not_called()
                self.assertEqual(sentinel.read_bytes(), b'important')

    def test_symlinked_owned_file_fails_before_cleanup(self):
        for module, name, owned, _, _ in CASES:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp) / 'workspace'; art = root / 'testing/build' / name / 'artifacts'
                art.mkdir(parents=True); target = Path(tmp) / 'keep'; target.write_bytes(b'important')
                (art / owned).symlink_to(target)
                with patch.object(module, 'ROOT', root), patch.object(module.bootstrap_host_tools, 'environment', return_value=ENV), \
                     patch.object(module.subprocess, 'run') as run:
                    with self.assertRaisesRegex(SystemExit, 'Symlink artifact'): module.main()
                    run.assert_not_called()
                self.assertEqual(target.read_bytes(), b'important')

    def test_compiler_failure_removes_only_owned_stale_artifacts(self):
        for module, name, owned, manifest, jar in CASES:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp) / 'workspace'; art = root / 'testing/build' / name / 'artifacts'
                art.mkdir(parents=True)
                stale = [art / owned, art / manifest, art.parent / jar]
                for path in stale: path.write_bytes(b'stale')
                unrelated = art / 'important.keep'; unrelated.write_bytes(b'important')
                with patch.object(module, 'ROOT', root), patch.object(module.bootstrap_host_tools, 'environment', return_value=ENV), \
                     patch.object(module.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, 'compiler')) as run:
                    with self.assertRaises(subprocess.CalledProcessError): module.main()
                    self.assertEqual(run.call_count, 1)
                self.assertTrue(all(not path.exists() for path in stale))
                self.assertEqual(unrelated.read_bytes(), b'important')


if __name__ == '__main__': unittest.main()
