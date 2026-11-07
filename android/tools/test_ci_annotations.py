import tempfile
import unittest
from pathlib import Path
from ci_annotations import annotation, LIMIT, READ_LIMIT

class AnnotationChecks(unittest.TestCase):
    def render(self, payload):
        with tempfile.TemporaryDirectory() as root:
            path=Path(root)/'compile.log'
            path.write_bytes(payload)
            return annotation(path)

    def test_compiler_error_and_tail(self):
        text=self.render(b'e: file.kt: unresolved symbol\n* What went wrong:\n> compile failed\n')
        self.assertIn('unresolved symbol',text)
        self.assertIn('compile failed',text)
        self.assertEqual(text.count('\n'),0)

    def test_command_injection_is_escaped(self):
        text=self.render(b'e: %0A\n::warning::untrusted\r\ne: injected\xff')
        self.assertIn('%250A',text)
        self.assertNotIn('\n',text)
        self.assertNotIn('\r',text)

    def test_large_binary_log_has_bounded_output(self):
        text=self.render(b'x'*(READ_LIMIT*3)+b'\ne: final failure\n')
        self.assertIn('final failure',text)
        self.assertLessEqual(len(text),len('::error::')+LIMIT*3)

    def test_empty_log_does_not_invent_a_cause(self):
        text=self.render(b'')
        self.assertIn('not a root-cause diagnosis',text)
        self.assertNotIn('unresolved',text)

    def test_missing_log_is_an_error(self):
        with self.assertRaises(FileNotFoundError): annotation('/file-that-does-not-exist-ci.log')

    def test_github_truncation_budget_and_task_noise(self):
        text=self.render(('> Task :noise UP-TO-DATE\n'*2000+'e: actual-file.kt: REAL COMPILER ERROR\n> fatal tail\n').encode())
        self.assertIn('REAL COMPILER ERROR',text)
        self.assertIn('fatal tail',text)
        decoded=text.removeprefix('::error::').replace('%0A','\n').replace('%0D','\r').replace('%25','%')
        self.assertLessEqual(len(decoded.encode()),3000)

    def test_unicode_budget_is_bytes_not_characters(self):
        text=self.render(('e: '+'漢😀'*4000+'\n> actual tail').encode())
        decoded=text.removeprefix('::error::').replace('%0A','\n').replace('%0D','\r').replace('%25','%')
        self.assertLessEqual(len(decoded.encode()),3000)
        self.assertIn('actual tail',text)

    def test_device_exception_excerpt_is_bounded_and_not_a_diagnosis(self):
        with tempfile.TemporaryDirectory() as root:
            path=Path(root)/'device.log'
            path.write_text('com.android.ddmlib.TimeoutException: transport not ready\n' + 'noise\n'*60 + 'BUILD FAILED\n')
            text=annotation(path,'device')
            self.assertIn('com.android.ddmlib.TimeoutException',text)
            self.assertIn('not a diagnosis',text)
            self.assertIn('BUILD FAILED',text)
            self.assertNotIn('\n',text)

    def test_phase_does_not_mislabel_unit_failures_as_assembly(self):
        with tempfile.TemporaryDirectory() as root:
            path=Path(root)/'unit-tests.log';path.write_text('Execution failed for task :tests')
            text=annotation(path,'unit-tests')
            self.assertIn('unit-tests failed',text)
            self.assertIn('unit-tests.log',text)
            self.assertNotIn('Assembly failed',text)
