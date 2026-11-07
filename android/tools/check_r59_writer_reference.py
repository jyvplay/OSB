#!/usr/bin/env python3
"""Independent python-docx/ZIP/XML checks over actual R59 Writer corpus; not rendering."""
import base64
from pathlib import Path
import re
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r59writer/artifacts'
REL = '{http://schemas.openxmlformats.org/package/2006/relationships}'


def validate(directory: Path = ARTIFACTS) -> int:
    from docx import Document
    directory = Path(directory)
    count = 0
    def require(ok, message):
        nonlocal count
        count += 1
        if not ok:
            raise ValueError(message)

    records = (directory / 'manifest.tsv').read_text(encoding='utf-8').splitlines()
    require(len(records) == 100, 'missing or extra Writer corpus records')
    seen = set()
    for expected_index, record in enumerate(records):
        fields = record.split('\t')
        require(len(fields) == 2 and fields[0] == f'{expected_index:03d}' and fields[0] not in seen, 'invalid Writer corpus identity')
        seen.add(fields[0])
        source = base64.b64decode(fields[1], validate=True).decode('utf-8')
        lines = re.sub(r'\r\n?', '\n', source).split('\n')
        require(len(lines) == 6 and lines[0].startswith('# H') and lines[-1] == '', 'invalid source shape')
        payload = lines[0][3:]
        require(lines == [f'# H{payload}', f'- {payload} **strong** and *soft*', f'> {payload} `mono`',
                          f'  {payload}  ', f'**unclosed {payload}', ''], 'input violates generated grammar')
        plain_path = directory / f'doc-{fields[0]}-plain.docx'
        structured_path = directory / f'doc-{fields[0]}-structured.docx'
        plain = Document(plain_path)
        structured = Document(structured_path)
        require([p.text for p in plain.paragraphs] == lines, f'plain text/line loss at {fields[0]}')
        require(all(p.style.name == 'Normal' for p in plain.paragraphs), f'plain acquired styling at {fields[0]}')
        expected = [f'H{payload}', f'{payload} strong and soft', f'{payload} mono', f'  {payload}  ', f'**unclosed {payload}', '']
        require([p.text for p in structured.paragraphs] == expected, f'structured text/line loss at {fields[0]}')
        require([p.style.name for p in structured.paragraphs] == ['Heading 1', 'List Paragraph', 'Quote', 'Normal', 'Normal', 'Normal'],
                f'structured block styles differ at {fields[0]}')
        bullet = structured.paragraphs[1]
        require(bullet._p.pPr.numPr is not None and bullet._p.pPr.numPr.numId.val == 1 and bullet._p.pPr.numPr.ilvl.val == 0,
                f'bullet numbering missing at {fields[0]}')
        require([r.text for r in bullet.runs] == [payload + ' ', 'strong', ' and ', 'soft'], f'inline run boundaries differ at {fields[0]}')
        require(bullet.runs[1].bold is True and bullet.runs[1].italic is not True and
                bullet.runs[3].italic is True and bullet.runs[3].bold is not True, f'bold/italic mismatch at {fields[0]}')
        quote = structured.paragraphs[2]
        require([r.text for r in quote.runs] == [payload + ' ', 'mono'] and quote.runs[-1].font.name == 'monospace',
                f'monospace missing at {fields[0]}')
        require(len(structured.paragraphs[4].runs) == 1 and structured.paragraphs[4].runs[0].bold is not True,
                f'ambiguous inline marker was interpreted at {fields[0]}')
        for path in (plain_path, structured_path):
            with zipfile.ZipFile(path) as package:
                names = package.namelist()
                require(len(names) == len(set(names)) and not any('vbaproject' in n.lower() for n in names), 'duplicate/macro member')
                for rel_name in (n for n in names if n.endswith('.rels')):
                    rels = ET.fromstring(package.read(rel_name))
                    require(all(r.attrib.get('TargetMode') != 'External' for r in rels.findall(REL + 'Relationship')),
                            f'external relationship in {path.name}')
    return count


if __name__ == '__main__':
    print(f'R59 independent Writer python-docx/ZIP/XML corpus: {validate()} predicates across 200 actual files; no Word/LibreOffice rendering')
