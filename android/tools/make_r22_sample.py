#!/usr/bin/env python3
"""Create disposable real XLSX test data using openpyxl. Never overwrite an existing file."""
import argparse,io
from pathlib import Path
from openpyxl import Workbook
p=argparse.ArgumentParser(description=__doc__);p.add_argument('destination',type=Path);a=p.parse_args()
w=Workbook();s=w.active;s.title='Example'
for row in [('Name','Value','Private'),('Alice','=1+1','Do not share'),('Bob','000123','Omit this too')]:s.append(row)
for row in s:
 for cell in row:cell.data_type='s' # Deliberately literal source strings, not formulas.
b=io.BytesIO();w.save(b)
with a.destination.open('xb') as out:out.write(b.getvalue())
print(f'Created {a.destination}: {len(b.getvalue())} bytes. Disposable test data, not an Office fidelity claim.')
