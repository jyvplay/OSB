#!/usr/bin/env python3
"""Bounded failure-only CI observations, not a diagnosis or a device retry."""
import json
import os
from pathlib import Path
import shutil


CATEGORIES = {'offline': ('device offline',), 'not_found': ("device 'emulator-5554' not found",),
              'killed': ('killed',), 'oom': ('out of memory', 'oom-kill', 'oom_kill'),
              'signal': ('sigsegv', 'sigabrt', 'signal 11', 'signal 6'),
              'incomplete': ('test run failed to complete',)}


def category_counts(data):
    # Count only categories; never print test data or arbitrary log lines.
    result = {key: 0 for key in CATEGORIES}
    for line in data.splitlines():
        lower = line.lower()
        for key, markers in CATEGORIES.items():
            if any(marker in lower for marker in markers):
                result[key] += 1
    return result


def counters(data, allowed):
    result = {}
    for line in data.splitlines():
        parts = line.split()
        if len(parts) >= 2 and parts[0] in allowed and parts[1].isdigit():
            result[parts[0]] = int(parts[1])
    return result


def bounded_file(path, limit, tail=False):
    try:
        with Path(path).open('rb') as stream:
            if tail:
                stream.seek(0, 2)
                stream.seek(max(0, stream.tell() - limit))
            return stream.read(limit).decode('utf-8', 'replace')
    except OSError:
        return None


def self_memory_events():
    membership = bounded_file('/proc/self/cgroup', 4096)
    if membership is not None:
        for line in membership.splitlines():
            if line.startswith('0::'):
                relative = Path(line[3:].lstrip('/'))
                if '..' not in relative.parts:
                    value = bounded_file(Path('/sys/fs/cgroup') / relative / 'memory.events', 4096)
                    if value is not None:
                        return value
    return bounded_file('/sys/fs/cgroup/memory.events', 4096)


def observe():
    log = bounded_file('device.log', 8 * 1024 * 1024, tail=True)
    memory = self_memory_events()
    meminfo = bounded_file('/proc/meminfo', 8192)
    try:
        disk = shutil.disk_usage('.')
        free_bytes = disk.free
    except OSError:
        free_bytes = None
    return {'source': os.environ.get('GITHUB_SHA'),
            'scope': 'Failure-time runner observations only; missing data is unknown; no root cause or retry inferred.',
            'device_log_available': log is not None,
            'device_log_last_8mib_category_counts': category_counts(log) if log is not None else None,
            'cgroup_memory_events': counters(memory, {'low', 'high', 'max', 'oom', 'oom_kill', 'oom_group_kill'}) if memory is not None else None,
            'meminfo_kib': counters(meminfo, {'MemTotal:', 'MemAvailable:', 'SwapTotal:', 'SwapFree:'}) if meminfo is not None else None,
            'disk_free_bytes': free_bytes, 'kvm_node_exists': Path('/dev/kvm').exists()}


if __name__ == '__main__':
    print('::notice::R61EMULATOROBS1 ' + json.dumps(observe(), separators=(',', ':')))
