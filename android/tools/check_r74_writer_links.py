#!/usr/bin/env python3
"""Independent verification of the R74 Writer hyperlink and page-break artifacts.

Uses python-docx 1.2 (a separate implementation) plus direct ZIP/XML inspection. This is not
Word rendering evidence: python-docx parses the package, it does not lay out pages.
"""
from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path

EXPECTED_LINKS = [
    ("Arena", "https://example.org/a?x=1&y=2"),
    ("mail", "mailto:someone@example.org"),
    ("link", "https://example.org/plain"),
]
LITERAL_FRAGMENTS = [
    "[bad](javascript:alert(1))",
    "[ftp](ftp://example.org)",
    "Unterminated [label](https://example.org stays literal too.",
    "Escaped \\[label](https://example.org) stays literal.",
]
HYPERLINK_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"


def fail(message: str) -> None:
    print("R74 WRITER FAIL: " + message)
    raise SystemExit(1)


def read_docx(path: Path) -> tuple[zipfile.ZipFile, str, str]:
    if not path.is_file():
        fail("missing artifact " + path.name)
    archive = zipfile.ZipFile(path)
    names = archive.namelist()
    if any(name.lower().endswith("vbaproject.bin") for name in names):
        fail("macro part present in " + path.name)
    if len(names) > 32:
        fail("unexpected part count " + str(len(names)))
    document = archive.read("word/document.xml").decode("utf-8")
    rels = archive.read("word/_rels/document.xml.rels").decode("utf-8")
    return archive, document, rels


def check_links(artifacts: Path) -> int:
    import docx  # python-docx, an independent OPC/WordprocessingML reader

    checks = 0
    archive, document, rels = read_docx(artifacts / "links.docx")
    parsed = docx.Document(str(artifacts / "links.docx"))
    found = [(link.text, link.address) for paragraph in parsed.paragraphs for link in paragraph.hyperlinks]
    if found != EXPECTED_LINKS:
        fail("python-docx hyperlinks differ: " + repr(found))
    checks += 1

    body_text = "\n".join(paragraph.text for paragraph in parsed.paragraphs)
    for fragment in LITERAL_FRAGMENTS:
        if fragment not in body_text:
            fail("literal text lost: " + fragment)
        checks += 1

    # A rejected scheme may appear as *literal visible text*, but never as a relationship
    # target or a hyperlink attribute: that is the difference between text and a live link.
    live_targets = re.findall(r'Target="([^"]*)"', rels) + re.findall(r'<w:hyperlink[^>]*>', document)
    if any("javascript:" in target.lower() or "ftp://" in target.lower() for target in live_targets):
        fail("rejected scheme leaked into a live relationship or hyperlink element")
    checks += 1

    targets = re.findall(r'Target="([^"]+)" TargetMode="External"', rels)
    expected_targets = [target.replace("&", "&amp;") for _, target in EXPECTED_LINKS]
    if targets != expected_targets:
        fail("relationship targets differ: " + repr(targets))
    checks += 1

    if rels.count('Type="' + HYPERLINK_TYPE + '"') != len(EXPECTED_LINKS):
        fail("hyperlink relationship count is not " + str(len(EXPECTED_LINKS)))
    checks += 1

    if '<w:br w:type="page"/>' not in document:
        fail("page break element missing from document.xml")
    checks += 1

    if document.count("<w:hyperlink ") != len(EXPECTED_LINKS):
        fail("hyperlink element count differs from the relationship count")
    checks += 1

    # The page-break paragraph must sit between the link paragraph and the trailing paragraph.
    order = [match.start() for match in re.finditer(r'<w:br w:type="page"/>', document)]
    tail = document.find("After the break.")
    if not order or tail < order[0]:
        fail("page break is not positioned before the following paragraph")
    checks += 1
    return checks


def check_blocks(artifacts: Path) -> int:
    import docx

    checks = 0
    archive, document, rels = read_docx(artifacts / "blocks.docx")
    parsed = docx.Document(str(artifacts / "blocks.docx"))
    addresses = [link.address for paragraph in parsed.paragraphs for link in paragraph.hyperlinks]
    if addresses != ["https://example.org/same", "https://example.org/same", "mailto:x@example.org"]:
        fail("block-api link order differs: " + repr(addresses))
    checks += 1
    shared = re.findall(r'Target="https://example\.org/same" TargetMode="External"', rels)
    if len(shared) != 1:
        fail("a repeated target must reuse one relationship, found " + str(len(shared)))
    checks += 1
    if document.count("<w:hyperlink ") != 3:
        # Three link runs in two paragraphs; the repeated target reuses one relationship.
        fail("expected three hyperlink elements for the three link runs")
    checks += 1
    if rels.count('TargetMode="External"') != 2:
        fail("a repeated target must not create a second relationship")
    checks += 1
    if '<w:br w:type="page"/>' not in document:
        fail("block-api page break missing")
    checks += 1
    return checks


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    try:
        import docx  # noqa: F401
    except ImportError:
        print("python-docx unavailable: R74 Writer differential was NOT run.")
        return 0
    try:
        checks = check_links(args.directory) + check_blocks(args.directory)
    except SystemExit:
        raise
    except Exception as error:  # a malformed package must fail cleanly, not crash the lane
        print(f"R74 WRITER FAIL: {type(error).__name__}: {error}")
        return 1
    print(f"R74 writer links: {checks} independent checks passed (python-docx + raw OPC XML). "
          "Not Word rendering or Android evidence.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
