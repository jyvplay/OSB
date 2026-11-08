#!/usr/bin/env python3
"""One CI-only, isolated cold AVD and one connected video instrumentation run.

A bounded readiness observation reduces one possible pre-install race; it cannot
promise future system-server availability or diagnose an earlier failure.  This
script never chooses, clears, stops or runs tests on an existing device.  It
only owns the emulator process and AVD directory that it creates in RUNNER_TEMP.
"""
from __future__ import annotations

import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass

import bounded_process

AVD = 'fusion_r64_video'
SERIAL = 'emulator-5554'
IMAGE = 'system-images;android-34;google_apis;x86_64'
READY_SECONDS = 360
PROBE_SECONDS = 4
STABLE_SAMPLES = 3
SAMPLE_INTERVAL = 2
RESULT_ROOTS = (
    Path('app/build/outputs/androidTest-results/connected'),
    Path('app/build/outputs/connected_android_test_additional_output'),
)
GRADLE_COMMAND = (
    'bash', 'tools/run_gradle_ci.sh', 'device', ':app:connectedProDebugAndroidTest',
    '-Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true',
    '-Pandroid.experimental.enableAdditionalTestOutput=true',
    '-Pandroid.testInstrumentationRunnerArguments.class='
    'dev.fusion.shell.R24LocalMediaDeviceTest,dev.fusion.shell.R25LocalVideoDeviceTest',
)


class DeviceConflict(RuntimeError):
    """An unexpected device or identity must never be waited away."""


def parse_devices(output: str) -> dict[str, str]:
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if not lines or lines[0] != 'List of devices attached':
        raise ValueError('Malformed adb device list')
    devices: dict[str, str] = {}
    for line in lines[1:]:
        fields = line.split()
        if len(fields) != 2 or not fields[0] or fields[0] in devices:
            raise ValueError('Malformed or duplicate adb device entry')
        devices[fields[0]] = fields[1]
    return devices


def check_device_list(output: str) -> bool:
    devices = parse_devices(output)
    if any(serial != SERIAL for serial in devices):
        raise DeviceConflict('Another device is attached; refusing all device operations')
    return devices.get(SERIAL) == 'device'


@dataclass(frozen=True)
class Sample:
    # These fields come from read-only adb commands.  Empty/unavailable values
    # do not count as success.  system_server must stay the same throughout.
    avd: str = ''
    api: str = ''
    abi: str = ''
    boot: str = ''
    pid_before: str = ''
    package_service: str = ''
    package_path: str = ''
    activity_service: str = ''
    pid_after: str = ''

    def ready_pid(self) -> str | None:
        if self.avd and self.avd != AVD:
            raise DeviceConflict('The emulator serial belongs to a different AVD')
        if self.api and self.api != '34':
            raise DeviceConflict('The emulator is not API 34')
        if self.abi and 'x86_64' not in self.abi.split(','):
            raise DeviceConflict('The emulator is not x86_64')
        pid = self.pid_before
        if not (pid.isascii() and pid.isdecimal() and int(pid) > 0 and pid == self.pid_after):
            return None
        if not (self.avd == AVD and self.api == '34' and 'x86_64' in self.abi.split(',')
                and self.boot == '1' and self.package_service == 'Service package: found'
                and self.package_path.startswith('package:/') and '\n' not in self.package_path
                and self.activity_service == 'Service activity: found'):
            return None
        return pid


def wait_ready(probe, alive, *, timeout=READY_SECONDS, clock=time.monotonic, pause=time.sleep):
    """Read-only probes can repeat; the test/install action is never retried."""
    if timeout <= 0:
        raise ValueError('Readiness timeout must be positive')
    deadline = clock() + timeout
    previous = None
    stable = 0
    attempts = 0
    while clock() < deadline:
        if not alive():
            raise RuntimeError('The owned emulator exited before readiness')
        attempts += 1
        sample = probe(deadline)
        # A slow probe completing after the deadline cannot establish readiness.
        if clock() >= deadline:
            break
        pid = sample.ready_pid() if sample is not None else None
        if pid is not None:
            stable = stable + 1 if pid == previous else 1
            previous = pid
            if stable >= STABLE_SAMPLES:
                if not alive():
                    raise RuntimeError('The owned emulator exited at the readiness boundary')
                print(f'Read-only preflight: {attempts} samples; {stable} stable service observations; no install yet.', flush=True)
                return
        else:
            previous = None
            stable = 0
        remaining = deadline - clock()
        if remaining > 0:
            pause(min(SAMPLE_INTERVAL, remaining))
    raise TimeoutError(f'Package/activity/system-server readiness not established within {timeout} seconds')


def _adb(adb: Path, env: dict[str, str], deadline: float, *args: str) -> str | None:
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        return None
    try:
        output = bounded_process.capture((str(adb), *args), env=env,
            timeout=min(PROBE_SECONDS, remaining), max_output=8192, max_error=8192)
        return output.decode('utf-8', errors='strict').strip()
    except (OSError, RuntimeError, UnicodeError, subprocess.TimeoutExpired):
        return None


def probe_device(adb: Path, env: dict[str, str], deadline: float) -> Sample | None:
    devices = _adb(adb, env, deadline, 'devices')
    if devices is None:
        return None
    try:
        if not check_device_list(devices):
            return None
    except ValueError:
        return None

    def command(*args):
        return _adb(adb, env, deadline, '-s', SERIAL, *args) or ''

    avd_output = command('emu', 'avd', 'name')
    avd_lines = avd_output.splitlines()
    avd = avd_lines[0].strip() if len(avd_lines) == 2 and avd_lines[1].strip() == 'OK' else ''
    api = command('shell', 'getprop', 'ro.build.version.sdk')
    abi = command('shell', 'getprop', 'ro.product.cpu.abilist')
    boot = command('shell', 'getprop', 'sys.boot_completed')
    before = command('shell', 'pidof', 'system_server')
    package = command('shell', 'service', 'check', 'package')
    package_path = command('shell', 'pm', 'path', 'android')
    activity = command('shell', 'service', 'check', 'activity')
    after = command('shell', 'pidof', 'system_server')
    return Sample(avd, api, abi, boot, before, package, package_path, activity, after)


def assert_no_stale_results() -> None:
    for root in RESULT_ROOTS:
        if root.is_symlink() or (root.exists() and (not root.is_dir() or next(root.iterdir(), None) is not None)):
            raise RuntimeError(f'Prior connected-test output exists at {root}; refusing to reuse it')


def _assert_ci_environment() -> tuple[Path, Path]:
    if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('CI') != 'true':
        raise RuntimeError('Refusing to create or run a disposable AVD outside GitHub Actions CI')
    sdk = Path(os.environ.get('ANDROID_HOME', '')).resolve()
    runner_temp = Path(os.environ.get('RUNNER_TEMP', '')).resolve()
    if not os.environ.get('ANDROID_HOME') or not os.environ.get('RUNNER_TEMP') or not runner_temp.is_dir():
        raise RuntimeError('ANDROID_HOME and an existing RUNNER_TEMP are required')
    for executable in (sdk / 'platform-tools/adb', sdk / 'emulator/emulator',
                       sdk / 'cmdline-tools/latest/bin/avdmanager'):
        if not executable.is_file() or not os.access(executable, os.X_OK):
            raise RuntimeError(f'Required Android tool is unavailable: {executable.name}')
    if not os.access('/dev/kvm', os.R_OK | os.W_OK):
        raise RuntimeError('Usable KVM is required; software fallback is not evidence for this gate')
    return sdk, runner_temp


def _terminate_group(process: subprocess.Popen) -> None:
    # Never signal a process group after its leader has been reaped: the PID
    # could have been reused. While it is alive its group is the one we own.
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait(timeout=10)


def _interrupt(signum, _frame):
    raise InterruptedError(f'Interrupted by signal {signum}; cleaning up owned processes')


def _one_gradle_invocation(env: dict[str, str]) -> int:
    # Its output and result are real, not substituted. A timeout is a failure,
    # and the owned process group is stopped rather than starting another run.
    process = subprocess.Popen(GRADLE_COMMAND, env=env, start_new_session=True)
    try:
        return process.wait(timeout=1800)
    finally:
        _terminate_group(process)


def run() -> int:
    sdk, runner_temp = _assert_ci_environment()
    assert_no_stale_results()
    env = os.environ.copy()
    adb = sdk / 'platform-tools/adb'
    # Fail closed before creating anything if another ADB target is present.
    initial = _adb(adb, env, time.monotonic() + 15, 'devices')
    if initial is None or parse_devices(initial):
        raise DeviceConflict('ADB must report no existing devices before the disposable run')

    with tempfile.TemporaryDirectory(prefix='fusion-r64-video-', dir=runner_temp) as private:
        private_dir = Path(private)
        user_home = private_dir / 'android-user'
        avd_home = private_dir / 'avd'
        user_home.mkdir(); avd_home.mkdir()
        env['ANDROID_USER_HOME'] = str(user_home)
        env['ANDROID_AVD_HOME'] = str(avd_home)
        env['ANDROID_SERIAL'] = SERIAL
        manager = sdk / 'cmdline-tools/latest/bin/avdmanager'
        # No --force and a fresh private home: existing user AVDs are untouched.
        create = [str(manager), 'create', 'avd', '-n', AVD, '-k', IMAGE, '--device', 'pixel_6']
        with Path('r64-avd-create.log').open('wb') as log:
            result = subprocess.run(create, input=b'no\n', stdout=log, stderr=subprocess.STDOUT,
                                    env=env, timeout=120, check=False)
        if result.returncode:
            raise RuntimeError(f'AVD creation failed ({result.returncode}); see r64-avd-create.log')
        launch = [str(sdk / 'emulator/emulator'), '-avd', AVD, '-port', '5554', '-no-window',
                  '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'swiftshader_indirect',
                  '-memory', '2048', '-cores', '2']
        with Path('r64-emulator.log').open('wb') as log:
            process = subprocess.Popen(launch, stdout=log, stderr=subprocess.STDOUT,
                                       env=env, start_new_session=True)
            previous_handlers = {}
            try:
                for sig in (signal.SIGINT, signal.SIGTERM):
                    previous_handlers[sig] = signal.signal(sig, _interrupt)
                wait_ready(lambda deadline: probe_device(adb, env, deadline),
                           lambda: process.poll() is None)
                # A final device-list check immediately precedes the one Gradle
                # invocation. A local hostile process attaching later is out of scope.
                final_devices = _adb(adb, env, time.monotonic() + 15, 'devices')
                if final_devices is None or not check_device_list(final_devices):
                    raise DeviceConflict('The disposable device is not uniquely connected at test start')
                assert_no_stale_results()
                print('Starting one connected instrumentation invocation; no device test retries.', flush=True)
                return _one_gradle_invocation(env)
            finally:
                # A second interruption cannot abandon the still-owned emulator
                # halfway through the bounded cleanup.
                for sig in previous_handlers:
                    signal.signal(sig, signal.SIG_IGN)
                try:
                    _terminate_group(process)
                finally:
                    for sig, previous in previous_handlers.items():
                        signal.signal(sig, previous)


if __name__ == '__main__':
    try:
        sys.exit(run())
    except (DeviceConflict, OSError, RuntimeError, TimeoutError, ValueError, subprocess.TimeoutExpired) as error:
        print(f'Disposable video AVD failed before/at execution: {error}', file=sys.stderr, flush=True)
        sys.exit(1)
