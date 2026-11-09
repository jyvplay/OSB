#!/usr/bin/env python3
"""Construct real ZIP boundary fixtures around generated DOCX/XLSX seeds.

The archives are ordinary ZIP files built with Python's `zipfile`, including deflated
payloads, non-empty directory entries, duplicate names, stored entries and streaming
data descriptors. This is a disposable host corpus, not Android or Office rendering.
"""
import argparse
import csv
import hashlib
import io
from pathlib import Path
import struct
import warnings
import zipfile

PART = 1024 * 1024
TOTAL = 16 * 1024 * 1024


class Unseekable(io.BytesIO):
    """Byte sink that forces ZIP data descriptors for streamed entries."""

    def seekable(self):
        return False

    def seek(self, *args):
        raise io.UnsupportedOperation('nonseekable output forces data descriptors')


def make_zip(base, extra=(), streamed=False, corrupt=None):
    target = Unseekable() if streamed else io.BytesIO()
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore', message='Duplicate name:.*', category=UserWarning)
        with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as out:
            for name, data in base:
                out.writestr(name, data)
            for name, data, method in extra:
                out.writestr(name, data, compress_type=method)
    data = bytearray(target.getvalue())
    if corrupt:
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            info = z.getinfo(corrupt)
            assert info.compress_type == zipfile.ZIP_STORED and info.file_size
            offset = info.header_offset
            assert data[offset:offset + 4] == b'PK\x03\x04'
            name_len, extra_len = struct.unpack_from('<HH', data, offset + 26)
            data[offset + 30 + name_len + extra_len] ^= 1
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            assert z.testzip() == corrupt
    if streamed:
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            assert all(info.flag_bits & 8 for info in z.infolist()), 'data descriptor not produced'
    return bytes(data)


def fill_to(base_bytes, target):
    """Decompressed directory payloads that sum exactly to `target` minus the seeds."""
    remaining = target - base_bytes
    assert remaining > 0
    out = []
    index = 0
    while remaining:
        count = min(remaining, PART)
        out.append((f'padding-aggregate-{index:02d}/', b'Z' * count, zipfile.ZIP_DEFLATED))
        remaining -= count
        index += 1
    return out


def scenarios(base_size, base):
    def extra(name, data, method=zipfile.ZIP_DEFLATED):
        return (name, data, method)

    return [
        ('normal', [], 'ACCEPT', '-', False, None, False),
        ('empty-dir', [extra('empty/', b'')], 'ACCEPT', '-', False, None, False),
        ('small-dir', [extra('small/', b'directory bytes')], 'ACCEPT', '-', False, None, False),
        ('dir-at-limit', [extra('limit/', b'P' * PART)], 'ACCEPT', '-', False, None, False),
        ('dir-over', [extra('overflow/', b'P' * (PART + 1))], 'REFUSE', 'EXPANDED', False, None, False),
        ('file-at-limit', [extra('limit.bin', b'Q' * PART)], 'ACCEPT', '-', False, None, False),
        ('file-over', [extra('overflow.bin', b'Q' * (PART + 1))], 'REFUSE', 'EXPANDED', False, None, False),
        ('aggregate-below', fill_to(base_size, TOTAL - 1), 'ACCEPT', '-', False, None, False),
        ('aggregate-at-limit', fill_to(base_size, TOTAL) + [extra('after-full/', b'')],
         'ACCEPT', '-', False, None, False),
        ('aggregate-over', fill_to(base_size, TOTAL + 1), 'REFUSE', 'EXPANDED', False, None, False),
        ('aggregate-suffix-over', fill_to(base_size, TOTAL) + [extra('after-full/', b'x')],
         'REFUSE', 'EXPANDED', False, None, False),
        ('duplicate-dir', [extra('twice/', b''), extra('twice/', b'')], 'REFUSE', 'DUPLICATE', False, None, False),
        ('duplicate-file', [extra(base[0][0], base[0][1])], 'REFUSE', 'DUPLICATE', False, None, False),
        ('entries-at-limit', [extra(f'count-{i:03d}/', b'') for i in range(256 - len(base))],
         'ACCEPT', '-', False, None, False),
        ('entries-over', [extra(f'count-{i:03d}/', b'') for i in range(257 - len(base))],
         'REFUSE', 'COUNT', False, None, False),
        ('descriptor-dir-over', [extra('descriptor/', b'D' * (PART + 1))], 'REFUSE', 'EXPANDED', True, None, False),
        ('unsafe-path', [extra('../escape.bin', b'x')], 'REFUSE', 'UNSAFE', False, None, False),
        ('macro', [extra('extra/vBaPrOjEcT.bIn', b'x')], 'REFUSE', 'MACRO', False, None, False),
        ('bad-crc', [extra('bad-crc.bin', b'real-checksum-data', zipfile.ZIP_STORED)],
         'REFUSE', 'CORRUPT_OR_OTHER', False, 'bad-crc.bin', False),
        ('input-over', [], 'REFUSE', 'INPUT', False, None, True),
    ]


SPECS = {name: (status, reason) for name, _, status, reason, *_ in scenarios(0, (('a', b''),))}


def build(directory):
    directory = Path(directory)
    if not directory.is_dir() or directory.is_symlink():
        raise ValueError('Fixture directory must already exist and not be a symlink')
    if (directory / 'cases.tsv').exists() or any(directory.glob('*.zip')):
        raise ValueError('Refusing to overwrite fixture evidence')
    rows = []
    for kind in ('docx', 'xlsx'):
        seed = directory / f'seed.{kind}'
        if not seed.is_file() or seed.is_symlink():
            raise ValueError(f'Missing or symlink seed: {seed}')
        with zipfile.ZipFile(seed) as archive:
            infos = archive.infolist()
            if len({i.filename for i in infos}) != len(infos):
                raise ValueError('Seed already contains duplicate names')
            base = [(info.filename, archive.read(info)) for info in infos]
        base_size = sum(len(content) for _, content in base)
        if not (0 < base_size < PART and 0 < len(base) < 240):
            raise ValueError('Unexpected seed size or entry count')
        for name, extras, expected, reason, streamed, corrupt, input_over in scenarios(base_size, base):
            case_id = f'{kind}-{name}'
            payload = make_zip(base, extras, streamed, corrupt)
            if input_over:
                payload += bytes(TOTAL + 1 - len(payload))
            file = directory / f'{case_id}.zip'
            file.write_bytes(payload)
            rows.append((case_id, kind, file.name, expected, reason, hashlib.sha256(payload).hexdigest()))
    with (directory / 'cases.tsv').open('x', encoding='utf-8', newline='') as output:
        writer = csv.writer(output, delimiter='\t', lineterminator='\n')
        writer.writerow(('id', 'kind', 'file', 'expected', 'reason', 'sha256'))
        writer.writerows(rows)
    print(f'R72 Office ZIP fixtures: {len(rows)} actual archives; not Android or full-import acceptance')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    build(parser.parse_args().directory)


if __name__ == '__main__':
    main()
