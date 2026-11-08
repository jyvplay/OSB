#!/usr/bin/env python3
"""Expose bounded failed-build excerpts through Checks API when artifact downloads fail.
Not a classifier of root cause, and never changes the build command's exit status.
"""
from pathlib import Path
import sys

LIMIT = 3000
READ_LIMIT = 1024 * 1024

def annotation(path, phase="Assembly"):
    with Path(path).open('rb') as log:
        head = log.read(READ_LIMIT)
        log.seek(0, 2)
        size = log.tell()
        log.seek(max(0, size - READ_LIMIT))
        tail = log.read(READ_LIMIT)
    text = head.decode('utf-8', 'replace')
    if size > READ_LIMIT:
        text += '\n[bounded tail; middle omitted; overlap possible]\n' + tail.decode('utf-8', 'replace')
    lines = text.splitlines()
    errors = []
    for index, line in enumerate(lines):
        if line.startswith(('e: ', 'error: ', '* What went wrong:', 'Execution failed for task')) or line.endswith(' FAILED'):
            errors.extend(lines[index:index+4] if line.endswith(' FAILED') else [line])
    # Reserve room for failure context even if compiler lines fill their budget.
    # A managed-device run can fail at the UTP/controller layer after all *named*
    # JUnit records passed; surface actual exception lines without deciding the cause.
    clip = lambda value, size: value.encode('utf-8')[:size].decode('utf-8', 'ignore')
    clues = []
    if phase == 'device':
        needles = ('Exception', 'Caused by:', 'FATAL', 'device disconnected', 'Device disconnected', 'Test execution failed')
        for line in lines:
            if any(needle in line for needle in needles) and line not in clues:
                clues.append(line)
                if len(clues) >= 12: break
    detail = '\nObserved device/error lines (not a diagnosis):\n' + clip('\n'.join(clues), 600) if clues else ''
    excerpt = clip('\n'.join(errors), 1000) + detail + '\nBuild tail (not a root-cause diagnosis):\n' + '\n'.join(lines[-35:]).encode('utf-8')[-1250:].decode('utf-8', 'ignore')
    value = clip(f'{phase[:80]} failed. Full output is in {Path(path).name[:120]}.\n' + excerpt, LIMIT)
    # Workflow command escaping: untrusted compiler paths/messages cannot create another command.
    return '::error::' + value.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')

if __name__ == '__main__':
    print(annotation(sys.argv[1],sys.argv[2] if len(sys.argv)>2 else "Assembly"))
