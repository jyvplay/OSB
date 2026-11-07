import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import r61_emulator_observation as observer


class EmulatorObservationTests(unittest.TestCase):
    def test_only_category_counts_leave_parser_no_raw_log_data(self):
        private = 'EXAMPLE_DISPOSABLE_FIXTURE_TEXT'
        counts = observer.category_counts(f'{private} device offline\nSIGSEGV {private}\n'
                                          "device 'emulator-5554' not found\nTest run failed to complete\n")
        self.assertEqual(counts['offline'], 1)
        self.assertEqual(counts['signal'], 1)
        self.assertEqual(counts['not_found'], 1)
        self.assertEqual(counts['incomplete'], 1)
        self.assertNotIn(private, json.dumps(counts))

    def test_memory_counter_allowlist_and_bad_values(self):
        values = observer.counters('oom 2\noom_kill 0\nprivate 99\nhigh -1\nmax nope\n',
                                   {'oom', 'oom_kill', 'high', 'max'})
        self.assertEqual(values, {'oom': 2, 'oom_kill': 0})

    def test_tail_is_bounded_and_missing_is_unknown(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'log'; path.write_text('a' * 1024 + 'end')
            self.assertEqual(observer.bounded_file(path, 3, tail=True), 'end')
            self.assertIsNone(observer.bounded_file(path / 'missing', 3))

    def test_memory_events_use_process_membership_and_refuse_parent_traversal(self):
        def read(path, limit, tail=False):
            path = str(path)
            if path == '/proc/self/cgroup': return '0::/job/runner\n'
            if path == '/sys/fs/cgroup/job/runner/memory.events': return 'oom_kill 3\n'
            return None
        with patch.object(observer, 'bounded_file', side_effect=read):
            self.assertEqual(observer.self_memory_events(), 'oom_kill 3\n')
        def hostile(path, limit, tail=False):
            if str(path) == '/proc/self/cgroup': return '0::/../../private\n'
            if str(path) == '/sys/fs/cgroup/memory.events': return 'oom 0\n'
            self.fail('Unexpected cgroup traversal')
        with patch.object(observer, 'bounded_file', side_effect=hostile):
            self.assertEqual(observer.self_memory_events(), 'oom 0\n')

    def test_missing_log_and_cgroup_do_not_become_zeroes(self):
        with patch.object(observer, 'bounded_file', return_value=None):
            report = observer.observe()
        self.assertFalse(report['device_log_available'])
        self.assertIsNone(report['device_log_last_8mib_category_counts'])
        self.assertIsNone(report['cgroup_memory_events'])
        self.assertIn('no root cause', report['scope'])


if __name__ == '__main__':
    unittest.main()
