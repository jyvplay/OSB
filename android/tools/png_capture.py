"""Bounded integrity validation of native capture PNGs; NOT image provenance or visual QA.
Admits non-interlaced 8-bit RGB/RGBA (the lab capture contract), not every legal PNG.
Compressed metadata, text/EXIF, APNG and other ancillary extensions are refused.
Input is already in memory; the caller must separately bound transport acquisition.
"""
import struct
import zlib

MAX_FILE_BYTES = 32 * 1024 * 1024
MAX_PIXELS = 16 * 1024 * 1024
MAX_CHUNKS = 4096
SIGNATURE = b'\x89PNG\r\n\x1a\n'


def dimensions(data):
    if not 45 <= len(data) <= MAX_FILE_BYTES or data[:8] != SIGNATURE:
        raise ValueError('Missing, oversized or truncated PNG capture')
    offset, chunks, width, height, channels = 8, 0, 0, 0, 0
    compressed = bytearray()
    seen_idat = seen_palette = ended = False
    metadata = set()
    while offset < len(data):
        chunks += 1
        if chunks > MAX_CHUNKS or len(data) - offset < 12:
            raise ValueError('Invalid PNG chunk framing/count')
        size, kind = struct.unpack_from('>I4s', data, offset)
        if size > len(data) - offset - 12 or any(not (65 <= x <= 90 or 97 <= x <= 122) for x in kind) or kind[2] & 32:
            raise ValueError('Invalid PNG chunk size/type/reserved bit')
        body = memoryview(data)[offset+8:offset+8+size]
        crc = struct.unpack_from('>I', data, offset+8+size)[0]
        if zlib.crc32(body, zlib.crc32(kind)) & 0xffffffff != crc:
            raise ValueError('PNG chunk checksum mismatch')
        offset += 12 + size
        if chunks == 1 and kind != b'IHDR':
            raise ValueError('PNG must start with IHDR')
        if kind == b'IHDR':
            if chunks != 1 or size != 13:
                raise ValueError('Invalid/duplicate IHDR')
            width, height, depth, color, compression, filtering, interlace = struct.unpack('>IIBBBBB', body)
            if not (0 < width <= 16384 and 0 < height <= 16384 and width * height <= MAX_PIXELS):
                raise ValueError('Capture dimensions exceed bounds')
            if depth != 8 or color not in (2, 6) or (compression, filtering, interlace) != (0, 0, 0):
                raise ValueError('Capture requires non-interlaced 8-bit RGB/RGBA')
            channels = 3 if color == 2 else 4
        elif kind == b'PLTE':
            if seen_palette or seen_idat or not 3 <= size <= 768 or size % 3:
                raise ValueError('Invalid PNG palette')
            seen_palette = True
        elif kind == b'IDAT':
            seen_idat = True
            compressed.extend(body)
        elif kind == b'IEND':
            if size or not seen_idat or offset != len(data):
                raise ValueError('Invalid IEND, missing pixels or trailing bytes')
            ended = True
            break
        else:
            # Deliberately narrow capture subset. Do not forward unbounded compressed ICC/text
            # or EXIF/animation payloads to a later viewer while only validating pixel IDAT.
            sizes = {b'sRGB': 1, b'gAMA': 4, b'cHRM': 32, b'pHYs': 9, b'sBIT': channels}
            if kind not in sizes or size != sizes[kind] or kind in metadata or seen_idat or seen_palette:
                raise ValueError('Unsupported, duplicate or misplaced PNG metadata')
            if kind == b'sRGB' and body[0] > 3:
                raise ValueError('Invalid rendering intent')
            if kind == b'gAMA' and bytes(body) == b'\0'*4:
                raise ValueError('Invalid zero gamma')
            if kind == b'pHYs' and body[-1] > 1:
                raise ValueError('Invalid physical pixel units')
            if kind == b'sBIT' and any(not 1 <= value <= 8 for value in body):
                raise ValueError('Invalid significant bits')
            metadata.add(kind)
    if not ended or not compressed:
        raise ValueError('PNG is incomplete or has no pixel stream')
    stride = 1 + width * channels
    expected = height * stride
    decoder = zlib.decompressobj()
    try:
        # One extra byte detects overlong streams without unbounded expansion.
        pixels = decoder.decompress(compressed, expected + 1)
    except zlib.error as error:
        raise ValueError('Invalid PNG deflate stream') from error
    if len(pixels) != expected or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
        raise ValueError('PNG pixel count/deflate termination mismatch')
    if any(pixels[row] > 4 for row in range(0, expected, stride)):
        raise ValueError('Invalid PNG row filter')
    return width, height
