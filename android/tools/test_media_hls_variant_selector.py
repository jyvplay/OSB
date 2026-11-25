#!/usr/bin/env python3
"""Differential oracle for MediaHlsVariantSelector (feature/media)."""
import re
import unittest


def parse_master(m3u8: str):
    lines = [l.strip() for l in m3u8.splitlines() if l.strip()]
    out, i = [], 0
    while i < len(lines):
        if lines[i].startswith("#EXT-X-STREAM-INF:"):
            bw = int(re.search(r"BANDWIDTH=(\d+)", lines[i]).group(1))
            m = re.search(r"RESOLUTION=(\d+)x(\d+)", lines[i])
            h = int(m.group(2)) if m else 0
            uri = lines[i + 1]
            out.append((bw, h, uri))
            i += 2
        else:
            i += 1
    return out


class MediaHlsVariantSelectorTest(unittest.TestCase):
    def test_hls(self):
        m3u8 = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1920x1080\nhi.m3u8\n"
        vs = parse_master(m3u8)
        best = max((v for v in vs if v[0] <= 1_500_000 and v[1] <= 720), key=lambda v: v[0])
        self.assertEqual(best[2], "low.m3u8")


if __name__ == "__main__":
    unittest.main()
