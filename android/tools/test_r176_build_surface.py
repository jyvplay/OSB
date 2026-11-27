#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile complete model/windowing/index modules plus 21 repaired source files.

These are real compiler/runtime checks, not Kotlin-looking Python kernels.
Compiling standalone islands could conceal package collisions; compile the union.
"""
import json
import os
from pathlib import Path
import random
import re
import subprocess
import tempfile
import unittest
from bootstrap_host_tools import environment

ROOT = Path(__file__).resolve().parents[1]
LIMITS = json.loads(Path(__file__).with_name('r176_build_surface.json').read_text())


def geometry_reference(areas, p):
    left, top, width, height, minw, minh, maxw, maxh = p
    if len(areas) > 16 or width < 0 or height < 0 or minw < 1 or minh < 1 or maxw < minw or maxh < minh or maxw > 65536 or maxh > 65536:
        return 'REF'
    if any(not (0 <= l < r <= 65536 and 0 <= t < b <= 65536) for l, t, r, b in areas):
        return 'REF'
    fitting = [a for a in (areas or [(0, 0, 65536, 65536)]) if a[2] - a[0] >= minw and a[3] - a[1] >= minh]
    if not fitting:
        return 'REF'
    w, h = max(minw, min(width, maxw)), max(minh, min(height, maxh))
    cx, cy = left + w // 2, top + h // 2
    l, t, r, b = min(fitting, key=lambda a: abs((a[0] + a[2]) // 2 - cx) + abs((a[1] + a[3]) // 2 - cy))
    w, h = min(w, r - l), min(h, b - t)
    x, y = max(l, min(left, r - w)), max(t, min(top, b - h))
    return '%d,%d,%d,%d' % (x, y, x + w, y + h)


class BuildSurfaceExecution(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cache = Path.home() / '.cache/fusion-host-r23'
        if not (cache / 'host-tools.json').is_file():
            raise unittest.SkipTest('pinned JVM toolchain absent; no module compile execution claimed')
        cls.env = environment(cache)
        cls.temp = tempfile.TemporaryDirectory(prefix='r176-build-surface-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.dir = Path(cls.temp.name)
        sources = {ROOT / r['path'] for r in LIMITS}
        for module in ['core/model', 'core/windowing', 'core/index']:
            sources.update((ROOT / module / 'src/main').rglob('*.kt'))
        sources.add(ROOT / 'testing/src/r176/kotlin/dev/fusion/testing/R176BuildSurfaceChecks.kt')
        cls.source_count = len(sources)
        proc = subprocess.run([cls.env['KOTLINC'], '-jvm-target', '17', '-Werror', '-d', str(cls.dir / 'classes'),
                               *map(str, sorted(sources))], env=cls.env, capture_output=True, text=True, timeout=120)
        if proc.returncode:
            raise AssertionError(proc.stdout + proc.stderr)
        cls.java = [cls.env['JAVA_HOME'] + '/bin/java', '-cp', str(cls.dir / 'classes') + os.pathsep +
                    str(cache / 'node_modules/kotlin-compiler/lib/kotlin-stdlib.jar'),
                    'dev.fusion.testing.R176BuildSurfaceChecks', str(cls.dir)]
        (cls.dir / 'limits.tsv').write_text(''.join('%s\t%s\t%d\n' % (r['class'], r['constant'], r['value']) for r in LIMITS))

    def run_probe(self, *args):
        proc = subprocess.run(self.java + list(args), env=self.env, capture_output=True, text=True, timeout=20)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        return proc.stdout

    def test_runtime_caps_and_cross_module_behavior(self):
        text = self.run_probe()
        self.assertEqual(text.count('CONST\t'), 24)
        self.assertIn('R176_BUILD_SURFACE_OK behavior_assertions=26', text)
        self.assertGreaterEqual(self.source_count, 90)

    def test_registered_work_areas_oversized_windows_and_extreme_positions(self):
        rng = random.Random(17680)
        cases = []
        for _ in range(256):
            areas = [(0, 0, 1920, 1080), (1920, 0, 3840, 1440)]
            p = [rng.randrange(-10000, 10000), rng.randrange(-10000, 10000),
                 rng.randrange(10000), rng.randrange(10000), 100, 100, 5000, 5000]
            cases.append((areas, p))
        cases += [
            ([(0, 0, 100, 80)], [0, 0, 200, 200, 1, 1, 65536, 65536]),
            ([(0, 0, 100, 80)], [0, 0, 200, 200, 101, 1, 65536, 65536]),
            ([(0, 0, 100, 80), (100, 0, 600, 500)], [0, 0, 200, 200, 101, 1, 65536, 65536]),
            ([], [2**31 - 1, -2**31, 2**31 - 1, 2**31 - 1, 1, 1, 65536, 65536]),
            ([(0, 0, 100, 80)] * 17, [0, 0, 1, 1, 1, 1, 100, 80]),
            ([(0, 0, 65537, 80)], [0, 0, 1, 1, 1, 1, 100, 80]),
        ]
        (self.dir / 'geometry.tsv').write_text(''.join(';'.join(','.join(map(str, a)) for a in areas) + '\t' +
            ','.join(map(str, p)) + '\n' for areas, p in cases))
        lines = self.run_probe('geometry').splitlines()
        self.assertEqual(len(lines), 262)
        for i, ((areas, p), line) in enumerate(zip(cases, lines)):
            with self.subTest(areas=areas, p=p):
                self.assertEqual(line, '%d\t%s' % (i, geometry_reference(areas, p)))

    def test_horspool_search_against_native_bytes_find(self):
        rng = random.Random(17681)
        cases = []
        for _ in range(400):
            haystack = bytes(rng.randrange(256) for _ in range(rng.randrange(1, 300)))
            needle = (haystack[rng.randrange(len(haystack)):] if rng.randrange(2) else
                      bytes(rng.randrange(256) for _ in range(rng.randrange(15))))
            cases.append((haystack, needle))
        cases.extend([(b'', b''), (b'ababa', b'aba'), (b'aaa', b'aaaa'), (b'\x00\xff', b'\xff')])
        (self.dir / 'search.tsv').write_text(''.join(a.hex() + '\t' + b.hex() + '\n' for a, b in cases))
        lines = self.run_probe('search').splitlines()
        self.assertEqual(len(lines), 404)
        for i, ((haystack, needle), line) in enumerate(zip(cases, lines)):
            with self.subTest(i=i):
                self.assertEqual(line, '%d\t%d' % (i, haystack.find(needle)))


class InvalidKotlinSyntaxGate(unittest.TestCase):
    def test_no_c_style_numeric_constant_shifts_remain(self):
        invalid = []
        for path in ROOT.glob('**/src/main/**/*.kt'):
            for match in re.finditer(r'\bconst\s+val\s+\w+\s*=\s*\d+L?\s*<<', path.read_text()):
                invalid.append(str(path.relative_to(ROOT)))
        self.assertEqual(invalid, [])


if __name__ == '__main__':
    unittest.main()
