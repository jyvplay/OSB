#!/usr/bin/env python3
"""Host adversaries for process control and readiness logic; not an Android run."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import r64_video_avd as avd


def valid(**changes):
    values = dict(avd=avd.AVD, api='34', abi='x86_64,x86', boot='1', pid_before='817',
                  package_service='Service package: found', package_path='package:/system/framework/framework-res.apk',
                  activity_service='Service activity: found', pid_after='817')
    values.update(changes)
    return avd.Sample(**values)


class Clock:
    def __init__(self): self.value = 0.0
    def now(self): return self.value
    def sleep(self, seconds): self.value += seconds


class VideoAvdTests(unittest.TestCase):
    def test_only_one_target_and_strict_device_list(self):
        self.assertFalse(avd.check_device_list('List of devices attached\n'))
        self.assertFalse(avd.check_device_list('List of devices attached\nemulator-5554\toffline\n'))
        self.assertTrue(avd.check_device_list('List of devices attached\nemulator-5554\tdevice\n'))
        for text in ('List of devices attached\nemulator-5556\tdevice\n',
                     'List of devices attached\nemulator-5554\tdevice\nphone\toffline\n'):
            with self.assertRaises(avd.DeviceConflict): avd.check_device_list(text)
        for text in ('', 'List of devices attached\nwrong\n', 'List of devices attached\nemulator-5554 device extra\n',
                     'List of devices attached\nemulator-5554 device\nemulator-5554 device\n'):
            with self.assertRaises(ValueError): avd.check_device_list(text)

    def test_missing_or_changed_observations_cannot_be_ready(self):
        self.assertEqual(valid().ready_pid(), '817')
        for patch in (
            {'avd': ''}, {'api': ''}, {'abi': ''}, {'boot': ''}, {'boot': '0'},
            {'pid_before': ''}, {'pid_before': '0'}, {'pid_before': '8 9'}, {'pid_after': '818'},
            {'package_service': ''}, {'package_service': 'Service package: not found'},
            {'package_path': ''}, {'package_path': 'package:relative'}, {'package_path': 'package:/one\npackage:/two'},
            {'activity_service': ''}, {'activity_service': 'Service activity: not found'},
        ):
            with self.subTest(patch=patch): self.assertIsNone(valid(**patch).ready_pid())
        for patch in ({'avd': 'personal'}, {'api': '37'}, {'abi': 'arm64-v8a'}):
            with self.subTest(patch=patch), self.assertRaises(avd.DeviceConflict): valid(**patch).ready_pid()

    def test_boot_and_transport_without_package_never_start(self):
        clock = Clock(); calls = []
        def probe(deadline):
            calls.append(deadline)
            return valid(package_service='', package_path='')
        with self.assertRaises(TimeoutError):
            avd.wait_ready(probe, lambda: True, timeout=6, clock=clock.now, pause=clock.sleep)
        self.assertEqual(len(calls), 3)
        self.assertEqual(clock.value, 6)

    def test_pid_restart_or_missing_sample_resets_stability(self):
        clock = Clock(); calls = []
        samples = iter((valid(), valid(), valid(pid_before='818', pid_after='818'), None,
                        valid(pid_before='818', pid_after='818'), valid(pid_before='818', pid_after='818'),
                        valid(pid_before='818', pid_after='818')))
        def probe(deadline): calls.append(deadline); return next(samples)
        avd.wait_ready(probe, lambda: True, timeout=30, clock=clock.now, pause=clock.sleep)
        self.assertEqual(len(calls), 7)
        self.assertEqual(clock.value, 12)

    def test_late_probe_and_exited_emulator_fail_closed(self):
        clock = Clock()
        def late(deadline): clock.value += 10; return valid()
        with self.assertRaises(TimeoutError):
            avd.wait_ready(late, lambda: True, timeout=6, clock=clock.now, pause=clock.sleep)
        with self.assertRaisesRegex(RuntimeError, 'exited'):
            avd.wait_ready(lambda _: valid(), lambda: False, timeout=5, clock=clock.now, pause=clock.sleep)

    def test_actual_subprocess_probe_obeys_serial_and_missing_service(self):
        # A local executable returns deliberately synthetic ADB-shaped strings.
        # This executes the process/capture code, not an Android emulator.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); fake = root / 'adb'; log = root / 'calls'
            fake.write_text('#!' + sys.executable + '\n' + '''
import os, sys
args=sys.argv[1:]
with open(os.environ['CALLS'],'a') as f: f.write(' '.join(args)+'\\n')
if args==['devices']: print('List of devices attached\\nemulator-5554\\tdevice'); sys.exit(0)
if args[:2]!=['-s','emulator-5554']: sys.exit(4)
args=args[2:]
values={
 'emu avd name':'fusion_r64_video\\nOK',
 'shell getprop ro.build.version.sdk':'34',
 'shell getprop ro.product.cpu.abilist':'x86_64,x86',
 'shell getprop sys.boot_completed':'1',
 'shell pidof system_server':'817',
 'shell service check package':'Service package: found',
 'shell pm path android':'package:/system/framework/framework-res.apk',
 'shell service check activity':'Service activity: found',
}
key=' '.join(args)
if key=='shell pm path android' and os.environ.get('MISSING')=='1': sys.exit(1)
if key not in values: sys.exit(5)
print(values[key])
''', encoding='utf-8')
            fake.chmod(0o755)
            env = os.environ.copy(); env['CALLS'] = str(log)
            import time
            sample = avd.probe_device(fake, env, time.monotonic() + 20)
            self.assertEqual(sample.ready_pid(), '817')
            lines = log.read_text().splitlines()
            self.assertEqual(lines[0], 'devices')
            self.assertTrue(all(line.startswith('-s emulator-5554 ') for line in lines[1:]))
            self.assertEqual(sum(line.endswith('shell pidof system_server') for line in lines), 2)
            env['MISSING'] = '1'
            sample = avd.probe_device(fake, env, time.monotonic() + 20)
            self.assertIsNone(sample.ready_pid())

    def test_actual_child_command_is_invoked_once_even_on_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            counter = Path(tmp) / 'calls'
            original = avd.GRADLE_COMMAND
            try:
                avd.GRADLE_COMMAND = (sys.executable, '-c',
                    'from pathlib import Path; import sys; p=Path(sys.argv[1]); p.open("a").write("one\\n"); sys.exit(7)', str(counter))
                self.assertEqual(avd._one_gradle_invocation(os.environ.copy()), 7)
                self.assertEqual(counter.read_text().splitlines(), ['one'])
            finally:
                avd.GRADLE_COMMAND = original

    def test_owned_subprocess_group_is_terminated(self):
        process = subprocess.Popen([sys.executable, '-u', '-c', 'import time; print("ready",flush=True); time.sleep(60)'],
                                   stdout=subprocess.PIPE, start_new_session=True, text=True)
        try:
            self.assertEqual(process.stdout.readline().strip(), 'ready')
            avd._terminate_group(process)
            self.assertIsNotNone(process.poll())
        finally:
            if process.poll() is None: avd._terminate_group(process)
            process.stdout.close()

    def test_stale_result_is_refused_without_removal(self):
        old = Path.cwd()
        with tempfile.TemporaryDirectory() as tmp:
            try:
                os.chdir(tmp)
                avd.assert_no_stale_results()
                path = avd.RESULT_ROOTS[0] / 'stale.xml'
                path.parent.mkdir(parents=True); path.write_text('not current')
                with self.assertRaisesRegex(RuntimeError, 'Prior connected-test output'): avd.assert_no_stale_results()
                self.assertEqual(path.read_text(), 'not current')
            finally:
                os.chdir(old)

    def test_refuses_non_ci_before_device_mutation(self):
        previous = os.environ.pop('GITHUB_ACTIONS', None)
        try:
            with self.assertRaisesRegex(RuntimeError, 'outside GitHub Actions'): avd._assert_ci_environment()
        finally:
            if previous is not None: os.environ['GITHUB_ACTIONS'] = previous


if __name__ == '__main__': unittest.main()
