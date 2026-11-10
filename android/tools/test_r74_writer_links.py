#!/usr/bin/env python3
"""Negative controls for the R74 Writer verifier.

These tests build WordprocessingML packages by hand and hand them to
tools/check_r74_writer_links.py. They prove the verifier rejects tampered packages; they do
NOT prove the Kotlin writer is correct — that is done by R74WriterLinkChecks plus the same
verifier running over its real output in the office lane.
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
CHECKER = TOOLS / 'check_r74_writer_links.py'
REL_NS = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
HYPERLINK = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink'
CONTENT_TYPES = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
    '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
    '<Default Extension="xml" ContentType="application/xml"/>'
    '<Override PartName="/word/document.xml" '
    'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
    '</Types>'
)
ROOT_RELS = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
    '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/'
    'officeDocument" Target="word/document.xml"/></Relationships>'
)


def rels_xml(*targets: str) -> str:
    entries = ''.join(
        f'<Relationship Id="rId{index + 1}" Type="{HYPERLINK}" Target="{target}" TargetMode="External"/>'
        for index, target in enumerate(targets)
    )
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        + entries + '</Relationships>'
    )


def document(items: str) -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="{REL_NS}">'
        f'<w:body>{items}<w:sectPr/></w:body></w:document>'
    )


def paragraph(text: str) -> str:
    return f'<w:p><w:r><w:t xml:space="preserve">{text}</w:t></w:r></w:p>'


def link_paragraph(label: str, rid: str) -> str:
    return (f'<w:p><w:hyperlink r:id="{rid}" w:history="1"><w:r>'
            f'<w:t xml:space="preserve">{label}</w:t></w:r></w:hyperlink></w:p>')


PAGE_BREAK = '<w:p><w:r><w:br w:type="page"/></w:r></w:p>'
LITERALS = [
    '[bad](javascript:alert(1))',
    '[ftp](ftp://example.org)',
    'Unterminated [label](https://example.org stays literal too.',
    'Escaped \\[label](https://example.org) stays literal.',
]


def write_package(path: Path, document_xml: str, rels: str) -> None:
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('[Content_Types].xml', CONTENT_TYPES)
        archive.writestr('_rels/.rels', ROOT_RELS)
        archive.writestr('word/_rels/document.xml.rels', rels)
        archive.writestr('word/document.xml', document_xml)


def build_links(directory: Path, *, drop_target: bool = False, drop_break: bool = False,
                inject_scheme: bool = False, drop_literal: bool = False) -> None:
    targets = ['https://example.org/a?x=1&amp;y=2', 'mailto:someone@example.org',
               'https://example.org/plain']
    if drop_target:
        targets = targets[:2]
    if inject_scheme:
        targets = targets + ['javascript:alert(1)']
    items = [link_paragraph('Arena', 'rId1'), link_paragraph('mail', 'rId2')]
    literal_text = '' if drop_literal else ' '.join(LITERALS)
    items.append(paragraph(literal_text))
    items.append(link_paragraph('link', 'rId3'))
    if not drop_break:
        items.append(PAGE_BREAK)
    items.append(paragraph('After the break.'))
    write_package(directory / 'links.docx', document(''.join(items)), rels_xml(*targets))


def build_blocks(directory: Path, *, duplicate_relationship: bool = False) -> None:
    targets = ['https://example.org/same', 'mailto:x@example.org']
    if duplicate_relationship:
        # An extra, unused relationship duplicating an existing target must be rejected.
        targets = ['https://example.org/same', 'mailto:x@example.org', 'https://example.org/same']
    items = (paragraph('one') + link_paragraph('two', 'rId1') + PAGE_BREAK
             + link_paragraph('three', 'rId1') + link_paragraph('four', 'rId2'))
    write_package(directory / 'blocks.docx', document(items), rels_xml(*targets))


def run_checker(directory: Path) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, str(CHECKER), str(directory)],
                          capture_output=True, text=True)


class R74WriterVerifierChecks(unittest.TestCase):
    def test_untampered_package_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            build_links(target)
            build_blocks(target)
            result = run_checker(target)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('independent checks passed', result.stdout)

    def test_missing_relationship_target_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            build_links(target, drop_target=True)
            build_blocks(target)
            result = run_checker(target)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('FAIL', result.stdout)

    def test_missing_page_break_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            build_links(target, drop_break=True)
            build_blocks(target)
            self.assertNotEqual(run_checker(target).returncode, 0)

    def test_live_javascript_target_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            build_links(target, inject_scheme=True)
            build_blocks(target)
            result = run_checker(target)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('rejected scheme', result.stdout)

    def test_lost_literal_text_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            build_links(target, drop_literal=True)
            build_blocks(target)
            result = run_checker(target)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('literal text lost', result.stdout)

    def test_duplicate_relationship_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            build_links(target)
            build_blocks(target, duplicate_relationship=True)
            result = run_checker(target)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('reuse one relationship', result.stdout)

    def test_macro_part_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            build_links(target)
            build_blocks(target)
            with zipfile.ZipFile(target / 'links.docx', 'a') as archive:
                archive.writestr('word/vbaProject.bin', b'\x00')
            self.assertIn('macro part', run_checker(target).stdout)


if __name__ == '__main__':
    unittest.main()
