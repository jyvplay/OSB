"""Actual PNG parsing/deflate tests. Bytes are parser fixtures, never Android screenshots."""
import struct
import unittest
import zlib
from png_capture import dimensions, SIGNATURE, MAX_PIXELS, MAX_CHUNKS


def chunk(kind, body):
    return struct.pack('>I', len(body))+kind+body+struct.pack('>I', zlib.crc32(kind+body)&0xffffffff)


def header(w=2, h=2, color=6, depth=8, interlace=0):
    return chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, depth, color, 0, 0, interlace))


def png(raw=b'\0'+b'\x12'*8+b'\0'+b'\x34'*8):
    return SIGNATURE+header()+chunk(b'IDAT', zlib.compress(raw))+chunk(b'IEND', b'')


class PngCaptureChecks(unittest.TestCase):
    def test_supported_rgb_rgba_and_all_row_filters(self):
        for color, channels in ((2, 3), (6, 4)):
            for filter_type in range(5):
                raw=(bytes([filter_type])+bytes(range(channels*2)))*2
                data=SIGNATURE+header(color=color)+chunk(b'IDAT', zlib.compress(raw))+chunk(b'IEND', b'')
                self.assertEqual((2, 2), dimensions(data))

    def test_recorded_header_only_counterexample(self):
        data=bytes.fromhex('89504e470d0a1a0a0000000d494844520000000a0000000a0000000000000000000000000049454e44ae426082')
        with self.assertRaises(ValueError): dimensions(data)
        # Patch-targeted attack: valid IHDR CRC still does not provide any pixels.
        with self.assertRaises(ValueError): dimensions(SIGNATURE+header()+chunk(b'IEND', b''))

    def test_every_single_byte_corruption_and_truncation_refused(self):
        data=png()
        for i in range(len(data)):
            changed=bytearray(data);changed[i]^=1
            with self.assertRaises(ValueError): dimensions(changed)
            with self.assertRaises(ValueError): dimensions(data[:i])
        with self.assertRaises(ValueError): dimensions(data+b'x')

    def test_deflate_length_end_extra_stream_and_filter_attacks(self):
        for raw in (b'', b'\0'*17, b'\0'*19, b'\x05'+b'\0'*17, b'\0'*1000000):
            with self.assertRaises(ValueError): dimensions(png(raw))
        for payload in (b'invalid', zlib.compress(b'\0'*18)+b'extra', zlib.compress(b'\0'*18)*2,
                        zlib.compress(b'\0'*18)[:-1]):
            with self.assertRaises(ValueError):
                dimensions(SIGNATURE+header()+chunk(b'IDAT', payload)+chunk(b'IEND', b''))

    def test_idat_order_unknown_critical_and_animation(self):
        stream=zlib.compress(b'\0'*18)
        split=SIGNATURE+header()+chunk(b'IDAT',stream[:4])+chunk(b'IDAT',stream[4:])+chunk(b'IEND',b'')
        self.assertEqual((2,2),dimensions(split))
        for middle in (chunk(b'tEXt',b'key\0value'),chunk(b'PLTE',b'\0'*3),chunk(b'IHDR',b'')):
            with self.assertRaises(ValueError):
                dimensions(SIGNATURE+header()+chunk(b'IDAT',stream[:4])+middle+chunk(b'IDAT',stream[4:])+chunk(b'IEND',b''))
        for kind in (b'XXXX',b'acTL',b'fcTL',b'fdAT',b'text',b't1Xt',b'iCCP',b'zTXt',b'iTXt',b'eXIf'):
            with self.assertRaises(ValueError): dimensions(SIGNATURE+header()+chunk(kind,b'')+chunk(b'IDAT',stream)+chunk(b'IEND',b''))

    def test_parameter_and_chunk_count_bounds(self):
        for hdr in (header(0,2),header(16385,1),header(8192,8192),header(depth=16),header(color=3),header(interlace=1)):
            with self.assertRaises(ValueError): dimensions(SIGNATURE+hdr+chunk(b'IDAT',zlib.compress(b'\0'*18))+chunk(b'IEND',b''))
        with self.assertRaises(ValueError):
            dimensions(SIGNATURE+header()+chunk(b'tEXt',b'')*MAX_CHUNKS+chunk(b'IDAT',zlib.compress(b'\0'*18))+chunk(b'IEND',b''))

    def test_small_color_metadata_and_profile_refusal(self):
        image=SIGNATURE+header()+chunk(b'sRGB',b'\0')+chunk(b'gAMA',struct.pack('>I',45455))+chunk(b'IDAT',zlib.compress(b'\0'*18))+chunk(b'IEND',b'')
        self.assertEqual((2,2),dimensions(image))
        for kind,body in ((b'iCCP',b'profile\0\0'+zlib.compress(b'x'*1000000)),
                          (b'gAMA',b'\0'*4),(b'sRGB',b'\x04'),(b'pHYs',b'\0'*8+b'\x02'),(b'sBIT',b'\0'*4)):
            with self.assertRaises(ValueError):
                dimensions(SIGNATURE+header()+chunk(kind,body)+chunk(b'IDAT',zlib.compress(b'\0'*18))+chunk(b'IEND',b''))
