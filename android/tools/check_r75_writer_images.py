#!/usr/bin/env python3
"""Independent verification of the R75 Writer image artifacts.

Two layers, deliberately separate:

* ``inspect_package`` is a **generic OPC/DrawingML consistency validator**. It knows nothing about
  the Kotlin writer: it re-derives every fact from the ZIP bytes (relationships, content types,
  media magic, IHDR/SOF dimensions, extents, alt text) and reports the first contradiction.
* ``check_artifacts`` adds the specific expectations of the R75 corpus (which pictures, which alt
  text, which dedupe).

python-docx 1.2 is used as a *second reader* when it is installed. It is never the only reader:
the raw checks above always run, so a missing dependency cannot turn this into a rubber stamp.
This is not Word rendering evidence and says nothing about Android.
"""
from __future__ import annotations

import argparse
import struct
import sys
import zipfile
from pathlib import Path

W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
R = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
WP = 'http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing'
A = 'http://schemas.openxmlformats.org/drawingml/2006/main'
PIC = 'http://schemas.openxmlformats.org/drawingml/2006/picture'
NS = {'w': W, 'r': R, 'wp': WP, 'a': A, 'pic': PIC}
EMU_PER_PIXEL = 9525
MAX_IMAGE_BYTES = 512 * 1024


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def png_size(data: bytes) -> tuple[int, int]:
    require(len(data) >= 24, 'PNG shorter than the IHDR chunk')
    require(data[:8] == b'\x89PNG\r\n\x1a\n', 'PNG signature missing')
    require(data[12:16] == b'IHDR', 'PNG does not start with IHDR')
    width, height = struct.unpack('>II', data[16:24])
    return width, height


def jpeg_size(data: bytes) -> tuple[int, int]:
    require(data[:3] == b'\xff\xd8\xff', 'JPEG signature missing')
    i = 2
    while i + 9 < len(data):
        require(data[i] == 0xFF, f'JPEG marker expected at {i}')
        marker = data[i + 1]
        if marker in (0xD8, 0x01) or 0xD0 <= marker <= 0xD7:
            i += 2
            continue
        if marker in (0xD9, 0xDA):
            break
        length = struct.unpack('>H', data[i + 2:i + 4])[0]
        require(length >= 2, 'JPEG segment length below 2')
        if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
            height, width = struct.unpack('>HH', data[i + 5:i + 9])
            return width, height
        i += 2 + length
    raise Fail('JPEG has no start-of-frame segment')


def measure(data: bytes, extension: str) -> tuple[int, int]:
    if extension == 'png':
        return png_size(data)
    if extension in ('jpg', 'jpeg'):
        return jpeg_size(data)
    raise Fail('unexpected media extension ' + extension)


def content_type_defaults(archive: zipfile.ZipFile) -> dict[str, str]:
    import xml.etree.ElementTree as ET

    root = ET.fromstring(archive.read('[Content_Types].xml'))
    out = {}
    for node in root:
        if node.tag.endswith('Default'):
            out[node.attrib['Extension'].lower()] = node.attrib['ContentType']
    return out


def document_relationships(archive: zipfile.ZipFile) -> dict[str, tuple[str, str]]:
    import xml.etree.ElementTree as ET

    root = ET.fromstring(archive.read('word/_rels/document.xml.rels'))
    out = {}
    for node in root:
        if node.tag.endswith('Relationship'):
            out[node.attrib['Id']] = (node.attrib['Type'], node.attrib['Target'],
                                      node.attrib.get('TargetMode', ''))
    return out


def inspect_package(path: Path) -> dict:
    """Validate one .docx as a self-consistent OPC package; return the observed pictures."""
    require(path.is_file(), 'missing artifact ' + path.name)
    archive = zipfile.ZipFile(path)
    names = archive.namelist()
    require(len(names) == len(set(names)), 'duplicate part names in ' + path.name)
    require(not any(n.lower().endswith('vbaproject.bin') for n in names), 'macro part present')
    require(len(names) <= 64, 'unexpected part count ' + str(len(names)))
    for name in names:
        require(not name.startswith('/') and '..' not in name.split('/') and '\\' not in name,
                'unsafe part name ' + name)

    import xml.etree.ElementTree as ET

    document = ET.fromstring(archive.read('word/document.xml'))
    rels = document_relationships(archive)
    defaults = content_type_defaults(archive)

    drawings = document.findall('.//w:drawing', NS)
    anchors = document.findall('.//wp:anchor', NS)
    require(not anchors, 'floating picture (wp:anchor) is out of contract')

    pictures = []
    for index, drawing in enumerate(drawings):
        inline = drawing.find('wp:inline', NS)
        require(inline is not None, f'drawing {index} has no wp:inline')
        extent = inline.find('wp:extent', NS)
        require(extent is not None, f'drawing {index} has no wp:extent')
        cx, cy = int(extent.attrib['cx']), int(extent.attrib['cy'])
        require(0 < cx <= 40_000_000 and 0 < cy <= 40_000_000, f'drawing {index} extent out of range')
        doc_pr = inline.find('wp:docPr', NS)
        require(doc_pr is not None, f'drawing {index} has no wp:docPr')
        require('descr' in doc_pr.attrib, f'drawing {index} has no alt text attribute')

        blip = inline.find('.//a:blip', NS)
        require(blip is not None, f'drawing {index} has no a:blip')
        rel_id = blip.get(f'{{{R}}}embed')
        require(rel_id is not None, f'drawing {index} blip has no r:embed')
        require(rel_id in rels, f'drawing {index} references undeclared relationship {rel_id}')
        rel_type, target, mode = rels[rel_id]
        require(rel_type.endswith('/image'), f'{rel_id} is not an image relationship')
        require(mode == '', f'{rel_id} must not be an external image link')
        require(not target.startswith('/') and '..' not in target.split('/') and ':' not in target,
                f'{rel_id} target escapes the package: {target}')

        part_name = target if target.startswith('word/') else 'word/' + target
        require(part_name in names, f'media part {part_name} is missing')
        data = archive.read(part_name)
        require(0 < len(data) <= MAX_IMAGE_BYTES, f'media {part_name} outside 1..{MAX_IMAGE_BYTES} bytes')
        extension = part_name.rsplit('.', 1)[-1].lower()
        require(extension in defaults, f'no content type for .{extension} ({part_name})')
        expected_type = {'png': 'image/png', 'jpeg': 'image/jpeg', 'jpg': 'image/jpeg'}[extension]
        require(defaults[extension] == expected_type,
                f'content type for .{extension} is {defaults[extension]}, not {expected_type}')
        width, height = measure(data, extension)
        # The picture may legitimately be scaled: the caller can set wp:extent independently of the
        # pixel count. What must agree is the inline extent and the shape's own a:ext.
        shape_ext = inline.find('.//pic:spPr/a:xfrm/a:ext', NS)
        require(shape_ext is not None, f'drawing {index} has no a:ext')
        require((int(shape_ext.attrib['cx']), int(shape_ext.attrib['cy'])) == (cx, cy),
                f'drawing {index} wp:extent {cx}x{cy} disagrees with a:ext '
                f'{shape_ext.attrib["cx"]}x{shape_ext.attrib["cy"]}')

        pictures.append({
            'rel_id': rel_id,
            'part': part_name,
            'alt': doc_pr.attrib['descr'],
            'cx': cx,
            'cy': cy,
            'scaled': (cx, cy) != (width * EMU_PER_PIXEL, height * EMU_PER_PIXEL),
            'width_px': width,
            'height_px': height,
            'bytes': len(data),
            'sha': __import__('hashlib').sha256(data).hexdigest(),
        })

    media = sorted(n for n in names if n.startswith('word/media/'))
    referenced = {p['part'] for p in pictures}
    require(set(media) == referenced,
            f'media parts {media} do not match the referenced parts {sorted(referenced)}')
    require(len(pictures) == len(drawings), 'picture count mismatch')
    return {'document': document, 'rels': rels, 'pictures': pictures, 'media': media,
            'names': names, 'archive': archive}


def check_artifacts(directory: Path) -> tuple[int, list[str]]:
    checks = 0
    notes: list[str] = []

    import xml.etree.ElementTree as ET

    # --- structured conversion ---------------------------------------------------------------
    structured = inspect_package(directory / 'images.docx')
    require(len(structured['pictures']) == 2, 'images.docx must carry two pictures')
    first, second = structured['pictures']
    require(first['alt'] == 'a gradient square', 'first alt text: ' + first['alt'])
    require(second['alt'] == 'second', 'second alt text: ' + second['alt'])
    require((first['width_px'], first['height_px']) == (4, 3), 'first picture is not 4x3 px')
    require((second['width_px'], second['height_px']) == (8, 2), 'second picture is not 8x2 px')
    require((first['cx'], first['cy']) == (4 * EMU_PER_PIXEL, 3 * EMU_PER_PIXEL), 'first extent is not pixel-derived')
    require((second['cx'], second['cy']) == (8 * EMU_PER_PIXEL, 2 * EMU_PER_PIXEL), 'second extent is not pixel-derived')
    require(not first['scaled'] and not second['scaled'], 'default extents must be pixel-derived')
    checks += 1

    # --- escaped alt text and caller-chosen extents -------------------------------------------
    escaped = inspect_package(directory / 'escaped-alt.docx')
    require(len(escaped['pictures']) == 2, 'escaped-alt.docx must carry two pictures')
    require(escaped['pictures'][0]['alt'] == 'A & B "quoted" <tag>',
            'alt text was not preserved through XML escaping: ' + escaped['pictures'][0]['alt'])
    require((escaped['pictures'][1]['cx'], escaped['pictures'][1]['cy']) == (952_500, 476_250),
            'caller-chosen extent was not written verbatim')
    require(escaped['pictures'][1]['scaled'], 'the scaled picture must be marked as scaled')
    checks += 1

    body_text = ''.join(node.text or '' for node in structured['document'].iter(f'{{{W}}}t'))
    require('Above the figure.' in body_text, 'text before the picture was lost')
    require('![missing key](absent)' in body_text, 'unknown image key was not preserved as literal text')
    require('After the break.' in body_text, 'text after the page break was lost')
    checks += 1
    breaks = structured['document'].findall(f'.//{{{W}}}br')
    require(any(node.get(f'{{{W}}}type') == 'page' for node in breaks), 'page break lost next to pictures')
    checks += 1
    require(not any(t.endswith('/hyperlink') for t, _, _ in structured['rels'].values()),
            'images.docx must not declare hyperlink relationships')
    checks += 1

    # --- mixed document: jpeg + png, identical bytes stored once ---------------------------------
    mixed = inspect_package(directory / 'mixed.docx')
    require(len(mixed['pictures']) == 3, 'mixed.docx must carry three picture references')
    require(len(mixed['media']) == 2, 'mixed.docx must store two distinct media parts: ' + str(mixed['media']))
    require(mixed['media'] == ['word/media/image1.png', 'word/media/image2.jpeg'],
            'media part order follows first use: ' + str(mixed['media']))
    by_part = {p['part']: p for p in mixed['pictures']}
    require(set(by_part) == set(mixed['media']), 'every media part must be referenced')
    jpeg = by_part['word/media/image2.jpeg']
    require((jpeg['width_px'], jpeg['height_px']) == (16, 16), 'jpeg is not 16x16')
    require(jpeg['alt'] == 'a jpeg', 'jpeg alt text: ' + jpeg['alt'])
    png = by_part['word/media/image1.png']
    require(png['alt'] in ('same bytes again', 'third reference to the same png'),
            'png alt text lost: ' + png['alt'])
    require(len({p['rel_id'] for p in mixed['pictures'] if p['part'] == png['part']}) == 1,
            'identical bytes must reuse one relationship')
    checks += 1

    # --- links and pictures coexist -------------------------------------------------------------
    both = inspect_package(directory / 'links-and-images.docx')
    require(len(both['pictures']) == 1, 'links-and-images.docx must carry one picture')
    hyper = [(i, t, g) for i, (t, g, m) in both['rels'].items() if t.endswith('/hyperlink')]
    require(len(hyper) == 1, 'links-and-images.docx must declare exactly one hyperlink')
    require(hyper[0][2] == 'https://example.org/docs', 'hyperlink target changed: ' + hyper[0][2])
    require(hyper[0][0] == 'rId3', 'hyperlink should keep rId3, got ' + hyper[0][0])
    require(both['pictures'][0]['rel_id'] == 'rId4', 'picture should take rId4')
    checks += 1

    # --- python-docx as an independent second reader ---------------------------------------------
    try:
        import docx  # python-docx
    except ImportError:
        docx = None
    if docx is None:
        notes.append('python-docx unavailable: the second-reader differential is NOT run; raw OPC checks only.')
    else:
        parsed = docx.Document(str(directory / 'images.docx'))
        shapes = parsed.inline_shapes
        require(len(shapes) == 2, f'python-docx sees {len(shapes)} inline shapes')
        require((shapes[0].width, shapes[0].height) == (4 * EMU_PER_PIXEL, 3 * EMU_PER_PIXEL),
                f'python-docx shape 0 extent {shapes[0].width}x{shapes[0].height}')
        require((shapes[1].width, shapes[1].height) == (8 * EMU_PER_PIXEL, 2 * EMU_PER_PIXEL),
                f'python-docx shape 1 extent {shapes[1].width}x{shapes[1].height}')
        descriptors = [s._inline.docPr.get('descr') for s in shapes]
        require(descriptors == ['a gradient square', 'second'], f'python-docx alt text {descriptors}')
        mixed_shapes = docx.Document(str(directory / 'mixed.docx')).inline_shapes
        require(len(mixed_shapes) == 3, f'python-docx sees {len(mixed_shapes)} shapes in mixed.docx')
        require((mixed_shapes[1].width, mixed_shapes[1].height) == (16 * EMU_PER_PIXEL, 16 * EMU_PER_PIXEL),
                'python-docx jpeg extent')
        checks += 1

    return checks, notes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path, nargs='?')
    parser.add_argument('--package', type=Path, help='validate one package for internal consistency')
    parser.add_argument('--require-python-docx', action='store_true')
    args = parser.parse_args()

    try:
        if args.package is not None:
            result = inspect_package(args.package)
            print(f'R75 package OK: {len(result["pictures"])} picture(s), media={result["media"]}')
            return 0
        if args.directory is None:
            raise Fail('a directory or --package is required')
        if args.require_python_docx:
            try:
                import docx  # noqa: F401
            except ImportError:
                raise Fail('python-docx is required in this lane and is not importable')
        checks, notes = check_artifacts(args.directory)
    except Fail as error:
        print('R75 WRITER IMAGE FAIL: ' + str(error))
        return 1
    except Exception as error:  # a malformed package must fail cleanly, never crash the lane
        print(f'R75 WRITER IMAGE FAIL: {type(error).__name__}: {error}')
        return 1
    for note in notes:
        print(note)
    readers = 'raw OPC XML only, python-docx absent' if notes else 'raw OPC XML, python-docx 1.2'
    print(f'R75 writer images: {checks} independent checks passed ({readers}). '
          'Not Word rendering or Android evidence.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
