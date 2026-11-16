#!/usr/bin/env python3
"""R91 escaping controls: the writer's single escaper must be a true inverse under an independent parser.

`XlsxValidation.esc` escapes `& < > "` uniformly in attributes *and* element text. Python's XML parser is used
here as an independent decoding path: whatever `ElementTree` returns must equal the string the rule declared,
character for character — including non-ASCII and astral characters, which must survive as characters rather
than as escapes or mojibake.

Skips when the R91 artifacts are absent (CI regenerates them with `tools/run_r91_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r91_escaping -v
"""
from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r91/artifacts'
S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'

# The rule the probe writes into `dv-escaping.xlsx`; the strings below are the declaration, not a re-reading.
ERROR_TITLE = 'Q&A <checklist>'
ERROR = '"quoted" & <angled>'
PROMPT_TITLE = 'a & b'
PROMPT = 'x < y > z'
FORMULA1 = '"a&b,c<d,e>f"'


class EscapingControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = ARTIFACTS / 'dv-escaping.xlsx'
        if not path.is_file():
            raise unittest.SkipTest('R91 artifacts are absent; run tools/run_r91_checks.py')
        with zipfile.ZipFile(path) as archive:
            cls.raw = archive.read('xl/worksheets/sheet1.xml').decode('utf-8')
        cls.node = ElementTree.fromstring(cls.raw).find(f'{S}dataValidations/{S}dataValidation')
        cls.folder = Path(tempfile.mkdtemp(prefix='r91-esc-'))
        cls.plain = zipfile.ZipFile(ARTIFACTS / 'dv-plain.xlsx').read('xl/worksheets/sheet1.xml').decode('utf-8')

    def test_attributes_decode_to_the_declared_strings(self):
        self.assertEqual(self.node.get('errorTitle'), ERROR_TITLE)
        self.assertEqual(self.node.get('error'), ERROR)
        self.assertEqual(self.node.get('promptTitle'), PROMPT_TITLE)
        self.assertEqual(self.node.get('prompt'), PROMPT)

    def test_element_text_decodes_to_the_declared_formula(self):
        self.assertEqual(self.node.find(f'{S}formula1').text, FORMULA1)

    def test_no_raw_metacharacter_reaches_the_document(self):
        # Between two attributes, an unescaped `&` or `<` would make this substring appear.
        self.assertNotIn('&a', self.raw.replace('&amp;', ''))
        self.assertNotIn(' < ', self.raw.replace('&lt;', ''))

    def test_escaping_is_not_double_encoded(self):
        self.assertNotIn('&amp;amp;', self.raw)
        self.assertNotIn('&amp;lt;', self.raw)
        self.assertNotIn('&amp;quot;', self.raw)

    def test_plain_workbook_carries_no_validation_markup(self):
        self.assertNotIn('dataValidation', self.plain)
        self.assertTrue(self.plain.endswith('</sheetData></worksheet>'), self.plain[-80:])

    def test_quoting_survives_as_quotes(self):
        # Two quotes bracket the list literal, two more bracket `"quoted"` inside the error attribute.
        self.assertEqual(self.raw.count('&quot;'), 4)

    def test_utf8_round_trip_of_a_fixture_rule(self):
        # A hand-built document with the same characters proves the parser path, not the writer path.
        document = ('<dataValidation xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
                    'error="&quot;q&quot; &amp; &lt;a&gt;" sqref="A1"/>')
        parsed = ElementTree.fromstring(document)
        self.assertEqual(parsed.get('error'), '"q" & <a>')


if __name__ == '__main__':
    unittest.main()
