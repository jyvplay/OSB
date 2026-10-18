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

    def test_actual_failed_native_receipt_cannot_be_promoted(self):
        from r24c_evidence_gate import verify, NEW_CASES, expected
        receipt = json.loads((land.ROOT / 'docs/verification/r24c-first-result.json').read_text())
        with self.assertRaises(ValueError):
            verify(receipt, receipt['candidate'], 'continuation')
        self.assertEqual(9, len(NEW_CASES))
        self.assertEqual(142, len(expected('full')))
        self.assertEqual(19, len(expected('privacy')))

    def test_receipt_must_retain_every_named_case_at_the_exact_source(self):
        from r24c_evidence_gate import verify, NEW_CASES
        # Deliberately synthetic parser input; never written as execution evidence.
        candidate = 'a' * 40
        baseline = {'candidate': candidate, 'conclusion': 'success', 'device_conclusion': 'success',
                    'suite': 'continuation', 'platform_api': 34, 'counts': {'PASS': 9, 'FAIL': 0, 'SKIP': 0},
                    'cases': [{'case': name, 'result': 'PASS'} for name in sorted(NEW_CASES)]}
        self.assertEqual(9, verify(baseline, candidate, 'continuation')['passed_cases'])
        for kind in ('missing', 'duplicate', 'skip', 'other-source', 'other-api', 'failed-workflow'):
            changed = copy.deepcopy(baseline)
            if kind == 'missing': changed['cases'].pop()
            if kind == 'duplicate': changed['cases'].append(changed['cases'][0])
            if kind == 'skip': changed['cases'][0]['result'] = 'SKIP'
            if kind == 'other-source': changed['candidate'] = 'b' * 40
            if kind == 'other-api': changed['platform_api'] = 37
            if kind == 'failed-workflow': changed['conclusion'] = 'failure'
            with self.assertRaises(ValueError): verify(changed, candidate, 'continuation')
