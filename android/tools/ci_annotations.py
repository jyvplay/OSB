#!/usr/bin/env python3
"""Expose bounded failed-build excerpts through Checks API when artifact downloads fail.
Not a classifier of root cause, and never changes the build command's exit status.
"""
from pathlib import Path
import sys

LIMIT = 16384
READ_LIMIT = 1024 * 1024

def annotation(path):
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
    errors = [line for line in lines if line.startswith(('e: ', 'error: ', '* What went wrong:', '> '))]
    # Reserve room for failure context even if compiler lines fill their budget.
    excerpt = '\n'.join(errors)[:LIMIT//2] + '\nBuild tail (not a root-cause diagnosis):\n' + '\n'.join(lines[-60:])[-LIMIT//2:]
    value = ('Assembly failed. Full output is in assemble.log.\n' + excerpt)[:LIMIT]
    # Workflow command escaping: untrusted compiler paths/messages cannot create another command.
    return '::error::' + value.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')

if __name__ == '__main__':
    print(annotation(sys.argv[1]))
