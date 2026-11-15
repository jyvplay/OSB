#!/usr/bin/env python3
"""One command for the R87 third-party lanes: install the pinned Node tools, then verify through them.

* `docx-preview` renders the R84, R86 and R87 `.docx` artifacts and must reproduce the declared paragraph
  text, fills, headings, tab spans and indents (as points derived from our twips);
* `exceljs` reads the R86 `.xlsx` artifacts and must reproduce the declared sheet state, tab colours,
  zooms and `firstSheet`.

Both tools are pinned in `tools/r87node/package.json` and installed with `npm ci`, so the run is
reproducible from the lockfile. Nothing from `tools/r87node` ships in the APK, and no Office application,
emulator or device is involved.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NODE_TOOLS = ROOT / 'tools/r87node'
LANES = {
    'r87': ROOT / 'testing/build/r87writer/artifacts',
    'r84': ROOT / 'testing/build/r84writer/artifacts',
    'r86': ROOT / 'testing/build/r86writer/artifacts',
    'sheet': ROOT / 'testing/build/r86sheet/artifacts',
}


def installed() -> bool:
    return (NODE_TOOLS / 'node_modules').is_dir()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', action='store_true', help='run `npm ci` when node_modules is absent')
    parser.add_argument('--require-all', action='store_true',
                        help='fail when an artifact directory is missing instead of skipping that lane')
    args = parser.parse_args(argv)

    if shutil.which('node') is None:
        raise SystemExit('node is required for the R87 renderer/reader lanes')
    if not installed():
        if not args.install:
            raise SystemExit('tools/r87node/node_modules is absent; rerun with --install or run '
                             '`npm ci --prefix tools/r87node`')
        subprocess.run(['npm', 'ci', '--no-audit', '--no-fund'], cwd=NODE_TOOLS, check=True)

    missing = [name for name, folder in LANES.items() if not folder.is_dir()]
    if missing and args.require_all:
        raise SystemExit('missing artifact directories: ' + ', '.join(missing))
    for name in missing:
        print(f'R87 lane skipped: {LANES[name]} is absent (run its runner first)')

    render_dirs = [name for name in ('r87', 'r84', 'r86') if name not in missing]
    if render_dirs:
        command = [sys.executable, str(ROOT / 'tools/check_r87_docx_render.py')]
        for name in render_dirs:
            command += [f'--{name}', str(LANES[name])]
        subprocess.run(command, check=True)
    if 'sheet' not in missing:
        subprocess.run([sys.executable, str(ROOT / 'tools/check_r87_sheet_exceljs.py'), str(LANES['sheet'])],
                       check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
