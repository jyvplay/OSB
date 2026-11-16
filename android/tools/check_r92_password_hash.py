#!/usr/bin/env python3
"""R92 differential oracle for Excel's legacy sheet-protection password hash.

The probe writes `password_hashes.tsv` — one row per password with the hash this code produced. This script
recomputes every hash with **openpyxl's own implementation** (`openpyxl.utils.protection.hash_password`), which
was written independently of this repository, and compares the two numerically.

Why numerically rather than as strings: openpyxl spells the hash with `hex()` and no zero padding, while Excel
(and this writer) pad to four digits. For the corpus used here the two spellings happen to coincide, and the
comparison reports how many rows would have differed, so the padding assumption is measured rather than
assumed.

What this proves: two implementations of the published algorithm agree on 311 inputs, including the empty
password, every printable ASCII character, length-15 passwords and the top of the BMP. What it does not prove:
that Excel computes the same value — nothing here runs Excel — or that the hash protects anything, which it
does not, being a 16-bit non-cryptographic checksum.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

FIELDS = 2
MIN_ROWS = 300


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def unescape(value: str) -> str:
    return value.replace('\\t', '\t').replace('\\n', '\n')


def verify(folder: Path) -> dict[str, int]:
    try:
        from openpyxl.utils.protection import hash_password  # noqa: PLC0415
    except ImportError as error:  # pragma: no cover - environment guard
        raise Fail(f'openpyxl is required for the independent hash: {error}')

    path = folder / 'password_hashes.tsv'
    require(path.is_file(), f'missing hash corpus {path}')
    rows = 0
    three_digit = 0
    distinct = set()
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == FIELDS, f'{path.name} line {number}: {len(parts)} fields, expected {FIELDS}')
        password = unescape(parts[0])
        written = parts[1]
        require(len(written) == 4 and written == written.upper() and all(c in '0123456789ABCDEF' for c in written),
                f'line {number}: {written!r} is not four upper-case hex digits')
        reference = hash_password(password)
        if len(reference) != 4:
            three_digit += 1
        require(int(written, 16) == int(reference, 16),
                f'line {number} (password {password!r}): this code wrote {written}, openpyxl computes {reference}')
        rows += 1
        distinct.add(written)
    require(rows >= MIN_ROWS, f'only {rows} hash rows; the corpus is too small to be evidence')
    return {'passwords': rows, 'distinct_hashes': len(distinct), 'unpadded_reference': three_digit}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder)
    except Fail as failure:
        print(f'R92 password-hash verification FAILED: {failure}')
        return 1
    print(f'R92 password hashes: {totals["passwords"]} passwords ({totals["distinct_hashes"]} distinct hashes) '
          f'recomputed with openpyxl\'s independent implementation and identical. '
          f'{totals["unpadded_reference"]} reference value(s) would have needed zero padding. '
          'Not Excel evidence, and the hash is not cryptography.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
