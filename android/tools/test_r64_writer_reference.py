import csv
from pathlib import Path
import shutil
import tempfile
import unittest
import warnings
from zipfile import BadZipFile, ZipFile
import xml.etree.ElementTree as ET

import check_r64_writer_reference as reference


class WriterTableReferenceAttacks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (reference.ARTIFACTS / 'cases.csv').is_file():
            raise unittest.SkipTest('Run real R64 Writer table corpus first')
        import docx  # noqa: F401

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self.tmp.name) / 'corpus'
        shutil.copytree(reference.ARTIFACTS, self.folder)
        with (self.folder / 'cases.csv').open(encoding='utf-8', newline='') as f:
            self.rows = list(csv.DictReader(f))

    def tearDown(self): self.tmp.cleanup()

    def rejected(self):
        with self.assertRaises((ValueError, KeyError, OSError, BadZipFile, ET.ParseError)):
            reference.check(self.folder)

    def save_manifest(self):
        with (self.folder / 'cases.csv').open('w', encoding='utf-8', newline='') as f:
            w = csv.DictWriter(f, fieldnames=reference.FIELDS); w.writeheader(); w.writerows(self.rows)

    def mutate(self, name, part, old, new):
        path = self.folder / name
        with ZipFile(path) as z: entries = [(item, z.read(item)) for item in z.infolist()]
        with ZipFile(path, 'w') as z:
            for item, data in entries:
                if item.filename == part:
                    self.assertIn(old, data)
                    data = data.replace(old, new, 1)
                z.writestr(item, data)

    def test_actual(self): self.assertGreater(reference.check(self.folder), 3800)
    def test_cell_change(self):
        self.mutate('doc-016.docx', 'word/document.xml', b'>Name<', b'>Fake<'); self.rejected()
    def test_escaped_cell_change(self):
        self.mutate('doc-016.docx', 'word/document.xml', b'&lt;&amp;&gt;', b'&lt;&gt;'); self.rejected()
    def test_last_body_cell_changed(self):
        self.mutate('doc-016.docx', 'word/document.xml', b'>007<', b'>008<'); self.rejected()
    def test_blank_cell_filled(self):
        self.mutate('doc-016.docx', 'word/document.xml', b'<w:r></w:r>', b'<w:r><w:t>injected</w:t></w:r>'); self.rejected()
    def test_cell_added(self):
        self.mutate('doc-016.docx', 'word/document.xml', b'</w:tr>', b'<w:tc><w:p/></w:tc></w:tr>'); self.rejected()
    def test_non_table_output_cannot_gain_table(self):
        self.mutate('doc-000.docx', 'word/document.xml', b'<w:sectPr/>', b'<w:tbl/><w:sectPr/>'); self.rejected()
    def test_no_option_forgery(self):
        self.rows[16]['tables'] = 'false'; self.save_manifest(); self.rejected()
    def test_malformed_markers_cannot_be_interpreted(self):
        self.mutate('malformed-17.docx', 'word/document.xml', b'<w:r><w:t xml:space="preserve">| ~~strike~~ |',
                    b'<w:r><w:rPr><w:strike/></w:rPr><w:t xml:space="preserve">| ~~strike~~ |'); self.rejected()
    def test_boundary_table_cell_is_checked(self):
        self.mutate('boundary-256.docx', 'word/document.xml', b'>x<', b'>wrong<'); self.rejected()
    def test_missing_malformed_artifact(self):
        (self.folder / 'malformed-00.docx').unlink(); self.rejected()
    def test_restart_instance_must_remain_separate(self):
        self.mutate('doc-112.docx', 'word/document.xml', b'<w:numId w:val="3"/>', b'<w:numId w:val="2"/>'); self.rejected()
    def test_unexpected_numbering(self):
        self.mutate('doc-016.docx', 'word/numbering.xml', b'</w:numbering>', b'<w:num w:numId="4"/></w:numbering>'); self.rejected()
    def test_duplicate_zip_entry(self):
        path = self.folder / 'doc-016.docx'
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with ZipFile(path, 'a') as z: z.writestr('word/document.xml', b'<fake/>')
        self.rejected()
    def test_missing_docx(self):
        (self.folder / 'doc-016.docx').unlink(); self.rejected()
    def test_extra_artifact(self):
        (self.folder / 'extra.docx').write_bytes(b'extra'); self.rejected()
    def test_symlinked_docx(self):
        path = self.folder / 'doc-016.docx'; path.unlink(); path.symlink_to(self.folder / 'doc-017.docx'); self.rejected()
    def test_reordered_manifest(self):
        self.rows[0], self.rows[1] = self.rows[1], self.rows[0]; self.save_manifest(); self.rejected()


if __name__ == '__main__': unittest.main()
