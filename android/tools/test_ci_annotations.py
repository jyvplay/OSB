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
