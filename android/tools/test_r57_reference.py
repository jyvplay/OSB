#!/usr/bin/env python3
"""Attack the independent R57 comparator; these are not Android device tests."""
from pathlib import Path
import shutil
import tempfile
import unittest
import zipfile

import check_r57_reference as reference


class WriterReferenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = reference.ROOT / 'testing/build/r57/artifacts'
        try:
            import docx  # noqa: F401 - host-only independent reader
        except ImportError:
            raise unittest.SkipTest('Install python-docx==1.2.0 for this host-only comparator gate')
        if not (cls.original / 'structured.docx').is_file():
            raise unittest.SkipTest('Run tools/run_r57_contracts.py first to create actual production artifacts')

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        for name in ('structured.docx', 'literal.docx', 'plain.docx'):
            shutil.copyfile(self.original / name, self.directory / name)

    def tearDown(self):
        self.temp.cleanup()

    def mutate_part(self, part, before, after, filename='structured.docx'):
        path = self.directory / filename
        with zipfile.ZipFile(path) as source:
            contents = {item.filename: source.read(item) for item in source.infolist()}
        self.assertIn(before, contents[part])
        contents[part] = contents[part].replace(before, after, 1)
        with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_STORED) as result:
            for name, data in contents.items():
                result.writestr(name, data)

    def test_actual_output_is_checked(self):
        self.assertGreaterEqual(reference.inspect(self.directory), 34)

    def test_missing_output_cannot_count_as_success(self):
        (self.directory / 'structured.docx').unlink()
        with self.assertRaises(Exception):
            reference.inspect(self.directory)

    def test_changed_text_is_rejected(self):
        self.mutate_part('word/document.xml', b'Heading &amp; &lt;title&gt;', b'Incorrect title')
        with self.assertRaises(Exception):
            reference.inspect(self.directory)

    def test_lost_bold_run_is_rejected(self):
        self.mutate_part('word/document.xml', b'<w:b/>', b'<w:i/>')
        with self.assertRaises(Exception):
            reference.inspect(self.directory)

    def test_heading_definition_not_just_heading_name(self):
        self.mutate_part('word/styles.xml', b'<w:sz w:val="32"/>', b'<w:sz w:val="36"/>')
        with self.assertRaises(Exception):
            reference.inspect(self.directory)

    def test_bullet_must_resolve_to_bullet_numbering(self):
        self.mutate_part('word/numbering.xml', b'<w:numFmt w:val="bullet"/>', b'<w:numFmt w:val="decimal"/>')
        with self.assertRaises(Exception):
            reference.inspect(self.directory)

    def test_literal_ambiguity_and_plain_mode_cannot_be_silently_changed(self):
        self.mutate_part('word/document.xml', b'**unmatched', b'unmatched', filename='literal.docx')
        with self.assertRaises(Exception):
            reference.inspect(self.directory)
        shutil.copyfile(self.original / 'literal.docx', self.directory / 'literal.docx')
        self.mutate_part('word/document.xml', b'# Heading', b'Heading', filename='plain.docx')
        with self.assertRaises(Exception):
            reference.inspect(self.directory)


if __name__ == '__main__':
    unittest.main()
