#!/usr/bin/env python3
"""Reproduce the offline coastline from a pinned real upstream GeoJSON blob. No tile scraping.
--fetch explicitly uses authenticated gh against GitHub, never stores credentials.
--check performs a local source/asset digest and grammar review only, not an Android render.
"""
import argparse
import base64
import hashlib
import json
import math
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / 'feature/maps/src/main/assets/natural-earth-land-v1.txt'
RECEIPT = ROOT / 'docs/research/r24c-natural-earth.json'
REPO = 'nvkelso/natural-earth-vector'
BLOB = '04811d72fff2701ec67587e30ad8942675b511e3'


def convert(raw):
    if len(raw) > 256 * 1024:
        raise ValueError('Source byte cap')
    doc = json.loads(raw)
    if doc['type'] != 'FeatureCollection':
        raise ValueError('Not a feature collection')
    rings = []
    for feature in doc['features']:
        geometry = feature['geometry']
        if geometry['type'] == 'Polygon':
            rings.extend(geometry['coordinates'])
        elif geometry['type'] == 'MultiPolygon':
            for polygon in geometry['coordinates']:
                rings.extend(polygon)
        else:
            raise ValueError('Unexpected geometry')
    if not 1 <= len(rings) <= 512 or sum(map(len, rings)) > 12000:
        raise ValueError('Geometry budget')
    lines = ['FUSION-LAND-1']
    for ring in rings:
        if len(ring) < 4 or ring[0] != ring[-1]:
            raise ValueError('Unclosed ring')
        lines.append('>')
        for lon, lat in ring:
            if not (math.isfinite(lon) and math.isfinite(lat) and -180 <= lon <= 180 and -90 <= lat <= 90):
                raise ValueError('Non-geographic vertex')
            lines.append(f'{lon:.5f},{lat:.5f}')
    result = ('\n'.join(lines) + '\n').encode('ascii')
    if len(result) > 256 * 1024:
        raise ValueError('Asset byte cap')
    return result, len(rings), sum(map(len, rings))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument('--fetch', action='store_true')
    g.add_argument('--source', type=Path)
    g.add_argument('--check', action='store_true')
    a = p.parse_args()
    if a.check:
        record = json.loads(RECEIPT.read_text())
        data = ASSET.read_bytes()
        if hashlib.sha256(data).hexdigest() != record['asset_sha256'] or len(data) != record['asset_bytes'] or record['github_blob'] != BLOB:
            raise SystemExit('Asset provenance mismatch')
        print(json.dumps(record, sort_keys=True))
        return
    if a.fetch:
        r = json.loads(subprocess.check_output(['gh', 'api', f'repos/{REPO}/git/blobs/{BLOB}']))
        if r['sha'] != BLOB or r['encoding'] != 'base64':
            raise SystemExit('Unexpected upstream blob')
        raw = base64.b64decode(r['content'])
    else:
        raw = a.source.read_bytes()
    identity = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
    if identity != BLOB:
        raise SystemExit('Not the pinned upstream source; refusing replacement')
    data, rings, vertices = convert(raw)
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    ASSET.write_bytes(data)
    record = {'retrieved_date': '2026-10-18', 'upstream_repository': 'https://github.com/' + REPO,
              'source_path': 'geojson/ne_110m_land.geojson', 'github_blob': BLOB,
              'source_bytes': len(raw), 'source_sha256': hashlib.sha256(raw).hexdigest(),
              'asset_bytes': len(data), 'asset_sha256': hashlib.sha256(data).hexdigest(),
              'rings': rings, 'vertices': vertices, 'transformation': 'all polygon rings, coordinates rounded to five decimal degrees, no invented geometry',
              'license': 'Public domain, per Natural Earth Terms of Use; no warranty',
              'terms': 'https://www.naturalearthdata.com/about/terms-of-use/',
              'scope': '1:110m land outlines only; not streets or navigation; no online tile dependency'}
    RECEIPT.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record, indent=2))


if __name__ == '__main__':
    main()
