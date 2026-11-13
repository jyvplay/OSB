#!/usr/bin/env python3
"""Negative controls for the R75 Writer image validator.

Each test builds a WordprocessingML package *by hand* — no Kotlin, no production writer — and hands
it to ``check_r75_writer_images.py --package``. A verifier that accepts any of these is worthless,
so the first test proves the untampered package passes and the rest prove each tampering is caught.
These are host-only checks of a verifier; they prove nothing about Word, LibreOffice or Android.
"""
from __future__ import annotations

import base64
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib
import zipfile
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
CHECKER = TOOLS / 'check_r75_writer_images.py'

REL_NS = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
HYPERLINK = REL_NS + '/hyperlink'
IMAGE = REL_NS + '/image'
W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
WP = 'http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing'
A = 'http://schemas.openxmlformats.org/drawingml/2006/main'
PIC = 'http://schemas.openxmlformats.org/drawingml/2006/picture'

CONTENT_TYPES = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
    '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
    '<Default Extension="xml" ContentType="application/xml"/>'
    '<Default Extension="png" ContentType="image/png"/>'
    '<Override PartName="/word/document.xml" '
    'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
    '</Types>'
)
ROOT_RELS = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
    '<Relationship Id="rId1" Type="' + REL_NS + '/officeDocument" Target="word/document.xml"/>'
    '</Relationships>'
)

# A real 16x16 baseline JPEG (303 bytes): `convert -size 16x16 gradient:red-blue -quality 85`.
TINY_JPEG = base64.b64decode(
    '/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAUDBAQEAwUEBAQFBQUGBwwIBwcHBw8LCwkMEQ8SEhEP'
    'ERETFhwXExQaFRERGCEYGh0dHx8fExciJCIeJBweHx7/2wBDAQUFBQcGBw4ICA4eFBEUHh4eHh4e'
    'Hh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh7/wAARCAAQABADASIA'
    'AhEBAxEB/8QAFgABAQEAAAAAAAAAAAAAAAAAAAYH/8QAFxAAAwEAAAAAAAAAAAAAAAAAABRhYv/E'
    'ABQBAQAAAAAAAAAAAAAAAAAAAAf/xAAWEQADAAAAAAAAAAAAAAAAAAAAF2L/2gAMAwEAAhEDEQA/AMbe'
    '0HtEs/Q/REVkCqyb'
)


def png(width: int, height: int, seed: int = 3) -> bytes:
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw += bytes(((x * 37 + seed) & 0xFF, (y * 53 + seed) & 0xFF, ((x + y) * 29) & 0xFF))
    deflated = zlib.compress(bytes(raw), 9)

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return (struct.pack('>I', len(payload)) + kind + payload
                + struct.pack('>I', zlib.crc32(kind + payload) & 0xFFFFFFFF))

    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', deflated) + chunk(b'IEND', b''))


def document(cx: int, cy: int, rel_id: str, alt: str = 'figure', shape_cx: int | None = None,
             shape_cy: int | None = None) -> str:
    shape_cx = cx if shape_cx is None else shape_cx
    shape_cy = cy if shape_cy is None else shape_cy
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:document xmlns:w="{W}" xmlns:r="{REL_NS}" xmlns:wp="{WP}" xmlns:a="{A}" xmlns:pic="{PIC}">'
        '<w:body><w:p><w:r><w:t>before</w:t></w:r></w:p>'
        '<w:p><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">'
        f'<wp:extent cx="{cx}" cy="{cy}"/><wp:effectExtent l="0" t="0" r="0" b="0"/>'
        f'<wp:docPr id="1" name="Picture 1" descr="{alt}"/>'
        '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        '<pic:pic><pic:nvPicPr><pic:cNvPr id="1" name="image1.png"/><pic:cNvPicPr/></pic:nvPicPr>'
        f'<pic:blipFill><a:blip r:embed="{rel_id}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
        f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{shape_cx}" cy="{shape_cy}"/></a:xfrm>'
        '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>'
        '</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>'
        '<w:sectPr/></w:body></w:document>'
    )


def rels(target: str = 'media/image1.png', rel_type: str = IMAGE, mode: str = '') -> str:
    external = f' TargetMode="{mode}"' if mode else ''
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" Type="' + REL_NS + '/styles" Target="styles.xml"/>'
        f'<Relationship Id="rId5" Type="{rel_type}" Target="{target}"{external}/>'
        '</Relationships>'
    )


def build(path: Path, *, media: bytes | None = None, target: str = 'media/image1.png',
          cx: int = 2 * 9525, cy: int = 2 * 9525, content_types: str = CONTENT_TYPES,
          include_media: bool = True, extra: dict[str, bytes] | None = None,
          rel_type: str = IMAGE, mode: str = '', alt: str = 'figure',
          duplicate_media: bool = False, shape_cx: int | None = None, shape_cy: int | None = None) -> None:
    """`cx`/`cy` default to a 2x2 PNG at 96 dpi; `media` may replace the bytes."""
    payload = media if media is not None else png(2, 2)
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('[Content_Types].xml', content_types)
        archive.writestr('_rels/.rels', ROOT_RELS)
        archive.writestr('word/_rels/document.xml.rels', rels(target, rel_type, mode))
        archive.writestr('word/document.xml', document(cx, cy, 'rId5', alt, shape_cx, shape_cy))
        if include_media:
            # An escaping *relationship target* is the thing under test, so the part itself is stored
            # at a legal name and only the rels entry points outside the package.
            stored = 'word/media/image1.png' if target.startswith('..') else 'word/' + target
            archive.writestr(stored, payload)
            if duplicate_media:
                archive.writestr('word/' + target, payload)
        for name, data in (extra or {}).items():
            archive.writestr(name, data)


def run_checker(path: Path) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, str(CHECKER), '--package', str(path)],
                          capture_output=True, text=True)


class R75ImageVerifierChecks(unittest.TestCase):
    def test_untampered_package_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'ok.docx'
            build(path)
            result = run_checker(path)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('R75 package OK', result.stdout)

    def test_jpeg_package_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'jpeg.docx'
            with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('[Content_Types].xml', CONTENT_TYPES.replace(
                    '<Default Extension="png" ContentType="image/png"/>',
                    '<Default Extension="jpeg" ContentType="image/jpeg"/>'))
                archive.writestr('_rels/.rels', ROOT_RELS)
                archive.writestr('word/_rels/document.xml.rels', rels('media/image1.jpeg'))
                archive.writestr('word/document.xml', document(16 * 9525, 16 * 9525, 'rId5'))
                archive.writestr('word/media/image1.jpeg', TINY_JPEG)
            result = run_checker(path)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_missing_media_part_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'missing.docx'
            build(path, include_media=False)
            result = run_checker(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('media part', result.stdout)

    def test_scaled_extent_is_accepted(self):
        # A caller may scale a picture; only wp:extent and a:ext must agree.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'scaled.docx'
            build(path, cx=100 * 9525, cy=100 * 9525)
            result = run_checker(path)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_extent_disagreeing_with_the_shape_extent_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'extent.docx'
            build(path, cx=100 * 9525, shape_cx=50 * 9525)
            result = run_checker(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('disagrees with a:ext', result.stdout)

    def test_escaping_target_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'escape.docx'
            build(path, target='../media/image1.png')
            result = run_checker(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('escapes the package', result.stdout)

    def test_missing_content_type_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'noctype.docx'
            build(path, content_types=CONTENT_TYPES.replace(
                '<Default Extension="png" ContentType="image/png"/>', ''))
            result = run_checker(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('no content type', result.stdout)

    def test_wrong_content_type_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'wrongtype.docx'
            build(path, content_types=CONTENT_TYPES.replace('ContentType="image/png"',
                                                            'ContentType="image/jpeg"'))
            result = run_checker(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('not image/png', result.stdout)

    def test_jpeg_bytes_behind_a_png_name_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'disguised.docx'
            build(path, media=TINY_JPEG, cx=16 * 9525, cy=16 * 9525)
            result = run_checker(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('PNG signature missing', result.stdout)

    def test_external_image_relationship_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'external.docx'
            build(path, mode='External')
            result = run_checker(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('external image link', result.stdout)

    def test_duplicate_part_names_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'duplicate.docx'
            build(path, duplicate_media=True)
            result = run_checker(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('duplicate part names', result.stdout)

    def test_macro_part_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'macro.docx'
            build(path, extra={'word/vbaProject.bin': b'\x00'})
            result = run_checker(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('macro part', result.stdout)

    def test_floating_picture_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'anchor.docx'
            build(path)
            with zipfile.ZipFile(path) as source:
                parts = {item.filename: source.read(item) for item in source.infolist()}
            parts['word/document.xml'] = parts['word/document.xml'].replace(
                b'<wp:inline', b'<wp:anchor').replace(b'</wp:inline>', b'</wp:anchor>')
            with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as target:
                for name, data in parts.items():
                    target.writestr(name, data)
            result = run_checker(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('floating picture', result.stdout)


if __name__ == '__main__':
    unittest.main()
