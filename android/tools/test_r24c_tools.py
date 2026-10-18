import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import unittest
from r24c_capture import validate_audit
import natural_earth_asset as land


class ContinuationToolsTests(unittest.TestCase):
    def test_empty_or_non_native_target_claim_refused(self):
        for report in ({}, {'schema': 1, 'origin': 'reference-art', 'api': 34, 'required_targets': []},
                       {'schema': 1, 'origin': 'android-instrumentation', 'api': 34, 'required_targets': []}):
            with self.assertRaises((ValueError, KeyError)):
                validate_audit(report)

    def test_target_mutations_do_not_pass(self):
        baseline = {'schema': 1, 'origin': 'android-instrumentation', 'api': 34,
                    'required_targets': [{'tag': 'test-input-not-runtime-evidence', 'width_dp': 48, 'height_dp': 48}]}
        self.assertEqual(1, validate_audit(baseline)['measured_targets'])
        for value in (0, 20, float('nan'), float('inf'), True, '48'):
            altered = copy.deepcopy(baseline)
            altered['required_targets'][0]['width_dp'] = value
            with self.assertRaises(ValueError):
                validate_audit(altered)
        altered = copy.deepcopy(baseline)
        altered['required_targets'] *= 2
        with self.assertRaises(ValueError):
            validate_audit(altered)

    def test_actual_land_asset_receipt(self):
        record = json.loads(land.RECEIPT.read_text())
        data = land.ASSET.read_bytes()
        self.assertEqual(record['asset_sha256'], hashlib.sha256(data).hexdigest())
        self.assertEqual(record['asset_bytes'], len(data))
        self.assertEqual(128, data.count(b'\n>\n'))
        self.assertEqual(5143, len(data.decode().splitlines()) - 1 - 128)

    def test_geographic_import_refuses_bad_input(self):
        for source in ({'type': 'Nothing'}, {'type': 'FeatureCollection', 'features': []},
                       {'type': 'FeatureCollection', 'features': [{'geometry': {'type': 'Point', 'coordinates': [0, 0]}}]}):
            with self.assertRaises((ValueError, KeyError)):
                land.convert(json.dumps(source).encode())

    def test_native_runner_refuses_missing_jdk_instead_of_substituting_host(self):
        import os
        env = os.environ.copy()
        env.pop('JAVA_HOME', None); env.pop('ANDROID_HOME', None); env.pop('ANDROID_SDK_ROOT', None)
        p = subprocess.run([sys.executable, str(land.ROOT / 'tools/r24c_lab.py'), 'android'], capture_output=True, text=True, env=env)
        self.assertNotEqual(0, p.returncode)
        self.assertIn('Actual maintained full JDK17', p.stderr)

    def test_local_player_and_offline_map_do_not_claim_provider_streaming(self):
        root = land.ROOT
        manifest = (root / 'feature/media/src/main/AndroidManifest.xml').read_text()
        self.assertIn('android:exported="false"', manifest)
        source = (root / 'feature/media/src/main/java/dev/fusion/media/MediaHub.kt').read_text()
        self.assertIn('NOT built-in or universally free streaming', source)
        self.assertNotIn('addJavascriptInterface', source)
        privacy = (root / 'privacy/src/main/AndroidManifest.xml').read_text()
        self.assertNotIn('android.permission.INTERNET', privacy)

    def test_host_success_marker_removed_before_compilation(self):
        # Exercise a real missing executable. A stale result must not survive this failure.
        import os
        result = land.ROOT / 'testing/build/r24c/result.json'
        text = land.ROOT / 'testing/build/r24c/result.txt'
        saved = {p: p.read_bytes() if p.exists() else None for p in (result, text)}
        try:
            result.parent.mkdir(parents=True, exist_ok=True)
            result.write_text('{"false_stale_pass":true}')
            env = os.environ.copy(); env['KOTLINC'] = '/missing-fusion-r24c-compiler'
            p = subprocess.run([sys.executable, str(land.ROOT / 'tools/run_r24c_contracts.py')], env=env, capture_output=True)
            self.assertNotEqual(0, p.returncode); self.assertFalse(result.exists())
        finally:
            for path, data in saved.items():
                if data is not None: path.write_bytes(data)
                else: path.unlink(missing_ok=True)
