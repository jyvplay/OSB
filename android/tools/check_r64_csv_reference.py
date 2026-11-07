#!/usr/bin/env python3
"""Verify actual CSV files with Python's independent csv reader/writer, not Excel."""
import csv
import io
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r64csv/artifacts'
FIELDS = ['id', 'text', 'formula', 'negative', 'shape', 'status']
TEXTS = ('hello', ' spaced ', 'a,b"c\nx', '=1+1', '\tword', '  +1', '＝evil', "'-7")
SHAPES = ((3, 3), (6, 3), (3, 8), (6, 8))  # row, column of final cell


def check(folder=ARTIFACTS):
    folder = Path(folder); predicates = 0
    def require(value, why):
        nonlocal predicates
        predicates += 1
        if not value: raise ValueError(why)
    require(folder.is_dir() and not folder.is_symlink(), 'Missing/symlink root')
    manifest = folder / 'cases.csv'
    require(manifest.is_file() and not manifest.is_symlink(), 'Missing/symlink manifest')
    with manifest.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream)
        require(reader.fieldnames == FIELDS, 'Manifest schema')
        cases = list(reader)
    require(len(cases) == 256 and [r['id'] for r in cases] == [f'{i:03}' for i in range(256)], 'Case IDs')
    matrix = [(text, formula, negative, shape) for text in range(8) for formula in range(4)
              for negative in (False, True) for shape in range(4)]
    expected_files = {'cases.csv'}; accepted = refused = 0
    for case, (text, formula, negative, shape) in zip(cases, matrix):
        require((case['text'], case['formula'], case['negative'], case['shape']) ==
                (str(text), str(formula), str(negative).lower(), str(shape)), 'Input matrix')
        bad = text >= 3 or formula == 2
        path = folder / f"sheet-{case['id']}.csv"
        if bad:
            refused += 1
            require(case['status'] == 'refused' and not path.exists(), 'Dangerous output was not refused')
            continue
        accepted += 1
        require(case['status'] == 'accepted' and path.is_file() and not path.is_symlink(), 'Missing accepted CSV')
        expected_files.add(path.name)
        raw = path.read_bytes()
        require(len(raw) <= 1024 * 1024 and not raw.startswith(b'\xef\xbb\xbf'), 'Output budget/BOM')
        decoded = raw.decode('utf-8', errors='strict')
        actual = list(csv.reader(io.StringIO(decoded, newline=''), strict=True))
        rows, columns = SHAPES[shape]
        expected = [[''] * columns for _ in range(rows)]
        expected[0][0] = '-12.5' if negative else '12.5'
        expected[0][1] = TEXTS[text]
        expected[0][2] = ('-25' if negative else '25') if formula == 0 else ('safe' if formula == 1 else '#DIV/0!')
        expected[1][0] = 'TRUE'; expected[-1][-1] = 'end,' + str(int(case['id']))
        require(actual == expected, 'CSV field content, values, or shape')
        encoded = io.StringIO(newline='')
        csv.writer(encoded, quoting=csv.QUOTE_ALL, lineterminator='\r\n').writerows(expected)
        require(decoded == encoded.getvalue(), 'Noncanonical quoting, field breakout, or line endings')
    require({p.name for p in folder.iterdir()} == expected_files and
            all(p.is_file() and not p.is_symlink() for p in folder.iterdir()), 'Unexpected or symlink artifacts')
    require(accepted == 72 and refused == 184, 'Positive/negative corpus coverage')
    print(f'R64 independent Python CSV: {predicates} predicates; {accepted} accepted/{refused} refused; no Excel import claim')
    return predicates


if __name__ == '__main__': check()
