#!/usr/bin/env python3
"""Real Android laptop lab. No downloads, synthetic screenshots, or fake device results.
Requires JDK17, SDK36/35+36 build tools, and (for visual) an already-running disposable AVD.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
from png_capture import dimensions as png_dimensions
from bounded_process import capture as capture_binary
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
SHOTS = ('01-desktop', '02-calculator', '03-editor', '04-editor-tools', '05-window-actions', '06-find', '07-notes')


def serial_checked(serial, consent):
    if not consent or not re.fullmatch(r'emulator-[0-9]{1,5}', serial or ''):
        raise ValueError('Requires --allow-disposable-reset and an exact emulator-NNNN --serial; physical/network devices refused.')
    return serial


def font_checked(value):
    value = value.strip()
    if value != 'null' and not re.fullmatch(r'(?:[0-9](?:\.[0-9]{1,8})?|10(?:\.0{1,8})?)', value):
        raise ValueError('Unexpected original font_scale; refusing to mutate it.')
    if value != 'null' and not 0.1 <= float(value) <= 10:
        raise ValueError('Original font_scale is outside the restorable bounds.')
    return value


def instrumentation_count(output, expected):
    if any(marker in output for marker in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed', 'INSTRUMENTATION_ABORTED')):
        raise ValueError('Instrumentation reported failure; inspect the saved log.')
    counts = re.findall(r'^OK \((\d+) tests?\)\s*$', output, re.M)
    if counts != [str(expected)] or not re.search(r'^INSTRUMENTATION_CODE: -1\s*$', output, re.M):
        raise ValueError(f'Instrumentation did not prove completion of exactly {expected} tests.')
    return expected


def apk_from_metadata(directory, package):
    directory = Path(directory).resolve()
    metadata = json.loads((directory/'output-metadata.json').read_text())
    elements = metadata.get('elements', [])
    if metadata.get('applicationId') != package or len(elements) != 1:
        raise ValueError('Unexpected APK applicationId or split outputs; refusing to guess.')
    name = elements[0].get('outputFile', '')
    if not name or Path(name).name != name or '\\' in name or not name.endswith('.apk'):
        raise ValueError('Unsafe APK output name.')
    apk = directory/name
    if apk.resolve().parent != directory or not apk.is_file():
        raise ValueError('Missing or out-of-directory APK.')
    return apk


def run(command, *, env=None, timeout=120, binary=False):
    if binary:
        return capture_binary(command, cwd=ROOT, env=env, timeout=timeout)
    result = subprocess.run(list(map(str, command)), cwd=ROOT, env=env, capture_output=True,
                            text=not binary, timeout=timeout, check=False)
    if result.returncode:
        error = result.stderr.decode(errors='replace') if binary else result.stdout+'\n'+result.stderr
        raise RuntimeError(f'Command failed ({result.returncode}): {command[0]}\n{error}')
    return result.stdout if binary else result.stdout+result.stderr


def doctor(sdk_arg=None):
    sdk = Path(sdk_arg or os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '/SDK-NOT-CONFIGURED').expanduser().resolve()
    suffix = '.exe' if os.name == 'nt' else ''
    tools, versions = {}, {}
    for name in ('java', 'javac'):
        path = str(Path(os.environ['JAVA_HOME'])/'bin'/(name+suffix)) if os.environ.get('JAVA_HOME') else shutil.which(name)
        tools[name] = path
        try:
            output = run([path, '-version']) if path else 'not found'
        except (OSError, RuntimeError, subprocess.SubprocessError) as error:
            output = str(error)
        versions[name] = output.strip()
    checks = {
        'java17': bool(re.search(r'version "17\.', versions['java'])),
        'javac17': bool(re.search(r'javac 17\.', versions['javac'])),
        'platform36': (sdk/'platforms/android-36/android.jar').is_file(),
        'buildTools35': (sdk/'build-tools/35.0.0').is_dir(),
        'buildTools36': (sdk/'build-tools/36.0.0').is_dir(),
        'wrapper': (ROOT/'gradle/wrapper/gradle-wrapper.jar').is_file(),
    }
    adb = sdk/'platform-tools'/('adb'+suffix)
    report = {'checks': checks, 'versions': versions, 'sdk': str(sdk), 'adb': str(adb),
              'adbPresent': adb.is_file(), 'buildPrerequisitesPresent': all(checks.values()),
              'limitations': 'No dependency resolution, Gradle build, emulator, UI or network check performed by doctor. Distribution SHA-256 remains unpinned; release supply-chain gate open.'}
    return report


def build(report, output):
    if not report['buildPrerequisitesPresent']:
        raise RuntimeError('Missing build prerequisites. Run doctor and configure JDK17 plus ANDROID_HOME or --sdk.')
    wrapper = ROOT/('gradlew.bat' if os.name == 'nt' else 'gradlew')
    tasks = [':app:assembleProDebug', ':app:assemblePlayDebug', ':app:assembleProDebugAndroidTest',
             ':app:assemblePlayDebugAndroidTest', ':app:lintProDebug', ':app:lintPlayDebug',
             ':testing:pilotContracts', ':testing:calculatorContracts', ':testing:editorContracts', ':testing:copyContracts', ':testing:searchAndGateContracts', ':testing:windowRestoreContracts']
    # Notes contracts exercise POSIX symlinks/directory fsync. Do not pretend Windows
    # host filesystem results establish Android behavior; run the native device test there.
    if sys.platform.startswith('linux'):
        tasks.extend([':testing:notesContracts', ':testing:encryptedIndexContracts'])
    # Stream into a file; never label a timeout/partial build as passed.
    with (output/'build.log').open('w') as log:
        result = subprocess.run([str(wrapper), '--no-daemon', '--console=plain', *tasks], cwd=ROOT,
                                env={**os.environ, 'ANDROID_HOME': report['sdk'], 'ANDROID_SDK_ROOT': report['sdk']},
                                stdout=log, stderr=subprocess.STDOUT, timeout=3600)
    if result.returncode:
        raise RuntimeError(f'Gradle failed ({result.returncode}); see {output}/build.log')
    return tasks


def visual(args, report, output, evidence):
    serial = serial_checked(args.serial, args.allow_disposable_reset)
    adb = report['adb']
    if not report['adbPresent']:
        raise RuntimeError('SDK platform-tools/adb is missing.')

    def device(*command, binary=False):
        return run([adb, '-s', serial, *command], timeout=600, binary=binary)

    if device('get-state').strip() != 'device':
        raise RuntimeError('Selected emulator is not ready.')
    if device('shell', 'getprop', 'ro.kernel.qemu').strip() != '1' or device('shell', 'getprop', 'ro.hardware').strip() not in ('ranchu', 'goldfish'):
        raise RuntimeError('Refusing destructive test reset on an unrecognized/non-emulator target.')
    if device('shell', 'getprop', 'sys.boot_completed').strip() != '1':
        raise RuntimeError('Emulator must already be fully booted and unlocked.')
    api = int(device('shell', 'getprop', 'ro.build.version.sdk').strip())
    if api < 34:
        raise RuntimeError('API34+ required.')
    original_font = font_checked(device('shell', 'settings', 'get', 'system', 'font_scale'))
    evidence['device'] = {'serial': serial, 'api': api, 'fingerprint': device('shell', 'getprop', 'ro.build.fingerprint').strip(),
                          'originalFontScale': original_font, 'wmSize': device('shell', 'wm', 'size').strip(),
                          'wmDensity': device('shell', 'wm', 'density').strip()}
    # Survives interruption before the final report; recovery value is recorded before mutation.
    (output/'device-before.json').write_text(json.dumps(evidence['device'], indent=2)+'\n')
    evidence['buildTasks'] = build(report, output)
    package = 'dev.fusion.shell.'+args.flavor
    app = apk_from_metadata(ROOT/f'app/build/outputs/apk/{args.flavor}/debug', package)
    test = apk_from_metadata(ROOT/f'app/build/outputs/apk/androidTest/{args.flavor}/debug', package+'.test')
    evidence['apks'] = [{'file': p.name, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in (app,test)]
    for apk in (app, test):
        installed = device('install', '-r', str(apk))
        if not re.search(r'^Success\s*$', installed, re.M):
            raise RuntimeError('Install did not confirm success.')
    runner = package+'.test/androidx.test.runner.AndroidJUnitRunner'
    classes = ('ShellSmokeTest', 'CopyContractDeviceTest', 'CheckpointDeviceTest', 'StorageBoundaryDeviceTest', 'EncryptedIndexDeviceTest')
    expected = sum(len(re.findall(r'@Test\s+fun ', (ROOT/f'app/src/androidTest/java/dev/fusion/shell/{name}.kt').read_text())) for name in classes)
    evidence['captures'] = []
    evidence['fontRestored'] = False
    try:
        for scale in ('1.0', '2.0'):
            destination = output/('font-'+scale)
            destination.mkdir()
            device('shell', 'settings', 'put', 'system', 'font_scale', scale)
            device('shell', 'am', 'force-stop', package)
            if float(device('shell', 'settings', 'get', 'system', 'font_scale').strip()) != float(scale):
                raise RuntimeError('Font scale write not observed.')
            smoke = device('shell', 'am', 'instrument', '-w', '-r', '-e', 'fusionDisposable', 'true', '-e', 'class',
                           ','.join('dev.fusion.shell.'+name for name in classes), runner)
            (destination/'instrumentation.txt').write_text(smoke)
            instrumentation_count(smoke, expected)
            token = uuid.uuid4().hex
            capture = device('shell', 'am', 'instrument', '-w', '-r', '-e', 'fusionDisposable', 'true', '-e', 'fusionCaptureRun', token,
                             '-e', 'fusionFontScale', scale, '-e', 'class', 'dev.fusion.shell.VisualCaptureTest', runner)
            (destination/'capture-instrumentation.txt').write_text(capture)
            instrumentation_count(capture, 1)
            prefix = 'files/visual-evidence/'+token+'/'
            config = device('exec-out', 'run-as', package, 'cat', prefix+'configuration.txt')
            (destination/'configuration.txt').write_text(config)
            if f'actualFontScale={scale}\n' not in config:
                raise RuntimeError('Captured Activity did not report the requested font scale.')
            for name in SHOTS:
                data = device('exec-out', 'run-as', package, 'cat', prefix+name+'.png', binary=True)
                dimensions = png_dimensions(data)
                path = destination/(name+'.png')
                path.write_bytes(data)
                evidence['captures'].append({'file': str(path.relative_to(output)), 'sha256': hashlib.sha256(data).hexdigest(),
                                             'dimensions': dimensions, 'fontScale': scale, 'humanReviewed': False})
    except BaseException:
        try:
            data = device('exec-out', 'screencap', '-p', binary=True)
            png_dimensions(data)
            (output/'failure-screen.png').write_bytes(data)
        except (OSError, RuntimeError, ValueError, subprocess.SubprocessError):
            pass  # A failed capture never replaces the primary test failure.
        raise
    finally:
        # A timed-out instrumentation command may still be running on the AVD.
        # Stop this test app, but attempt font restoration even if stopping fails.
        try:
            device('shell', 'am', 'force-stop', package)
        finally:
            if original_font == 'null':
                device('shell', 'settings', 'delete', 'system', 'font_scale')
            else:
                device('shell', 'settings', 'put', 'system', 'font_scale', original_font)
            restored = device('shell', 'settings', 'get', 'system', 'font_scale').strip()
            if restored != original_font:
                raise RuntimeError(f'Font restore unconfirmed; manually restore font_scale={original_font}.')
            evidence['fontRestored'] = True



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('doctor', 'build', 'visual'))
    parser.add_argument('--sdk', help='SDK root (or set ANDROID_HOME)')
    parser.add_argument('--serial')
    parser.add_argument('--flavor', choices=('pro', 'play'), default='pro')
    parser.add_argument('--allow-disposable-reset', action='store_true', help='Consent to install test APKs, erase this app\'s drafts/preferences, and temporarily change emulator font scale.')
    args = parser.parse_args()
    if args.command == 'visual':
        serial_checked(args.serial, args.allow_disposable_reset)  # Before any subprocess or file mutation.
    report = doctor(args.sdk)
    if args.command == 'doctor':
        print(json.dumps(report, indent=2))
        return 0 if report['buildPrerequisitesPresent'] else 1
    output = ROOT/'pilot-reports'/('lab-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8])
    output.mkdir(parents=True)
    evidence = {'status': 'started', 'toolchain': report, 'humanVisualReview': 'NOT PERFORMED', 'physicalDeviceAcceptance': False}
    try:
        if args.command == 'build':
            evidence['buildTasks'] = build(report, output)
        else:
            visual(args, report, output, evidence)
        evidence['status'] = 'executed_checks_passed_NOT_release_acceptance'
        return 0
    except KeyboardInterrupt:
        evidence['status'] = 'interrupted_NOT_passed'
        return 130
    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as error:
        evidence['status'] = 'failed'
        evidence['error'] = str(error)
        print(str(error), file=sys.stderr)
        return 1
    finally:
        (output/'result.json').write_text(json.dumps(evidence, indent=2)+'\n')
        print(f'Actual run report: {output}')


if __name__ == '__main__':
    try:
        sys.exit(main())
    except ValueError as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
