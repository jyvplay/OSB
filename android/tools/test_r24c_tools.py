import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
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
                    'required_targets': [{'tag': 'test-input-not-runtime-evidence', 'width_dp': 48, 'height_dp': 48, 'visible_width_dp': 48, 'visible_height_dp': 48}]}
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

    def test_strict_frame_export_refuses_missing_or_mismatched_evidence(self):
        import contextlib
        import io
        from unittest.mock import patch
        import r24c_capture as capture
        from png_capture import dimensions
        # Use an existing genuine image plus synthetic metadata to test the parser only;
        # this is not a new Android execution or visual-verification receipt.
        data = (land.ROOT / 'docs/verification/r24c-native-previews/third-failed-workflow/r24c-silver.png').read_bytes()
        width, height = dimensions(data)
        report = {'schema': 1, 'origin': 'android-instrumentation', 'api': 34, 'density': 2.625,
                  'font_scale': 1, 'preview_width': width, 'preview_height': height,
                  'required_targets': [{'tag': 'synthetic-test', 'width_dp': 48, 'height_dp': 48,
                                        'visible_width_dp': 48, 'visible_height_dp': 48}]}
        name = 'r24c-silver.png'
        with tempfile.TemporaryDirectory() as directory, patch.object(capture, 'ROOT', Path(directory)):
            root = Path(directory) / 'app/build/outputs/managed_device_android_test_additional_output/device'
            root.mkdir(parents=True)
            image = root / name
            audit = root / 'r24c-silver.json'
            def export():
                with contextlib.redirect_stdout(io.StringIO()):
                    capture.export(0, name, {name}, require_complete=True)
            with self.assertRaisesRegex(ValueError, 'image missing'): export()
            image.write_bytes(data)
            with self.assertRaisesRegex(ValueError, 'audit missing or duplicated'): export()
            audit.write_text(json.dumps(report)); export()
            for kind in ('bounds', 'dimensions', 'origin'):
                changed = copy.deepcopy(report)
                if kind == 'bounds': changed['required_targets'][0]['visible_width_dp'] = 20
                if kind == 'dimensions': changed['preview_width'] += 1
                if kind == 'origin': changed['origin'] = 'reference-art'
                audit.write_text(json.dumps(changed))
                with self.assertRaises(ValueError): export()
            audit.write_text(json.dumps(report))
            other = root / 'duplicate'; other.mkdir(); (other / audit.name).write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError, 'audit missing or duplicated'): export()
            (other / audit.name).unlink(); image.write_bytes(b'not a PNG')
            with self.assertRaises(ValueError): export()

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

    def test_partly_clipped_native_control_cannot_borrow_unclipped_hitbox(self):
        row = {'tag': 'metadata-mutation-only', 'width_dp': 80, 'height_dp': 80,
               'visible_width_dp': 48, 'visible_height_dp': 48}
        report = {'schema': 1, 'origin': 'android-instrumentation', 'api': 34, 'required_targets': [row]}
        self.assertEqual(1, validate_audit(report)['measured_targets'])
        for dimension in ('visible_width_dp', 'visible_height_dp'):
            changed = copy.deepcopy(report); changed['required_targets'][0][dimension] = 40
            with self.assertRaises(ValueError): validate_audit(changed)

    def test_real_executed_failures_do_not_become_subset_acceptance(self):
        from r24c_evidence_gate import verify
        for prefix, suite, failed in [('r24c-second', 'continuation', 4), ('r24c-second-privacy', 'privacy', 2), ('r24c-second-main', 'full', 5)]:
            report = json.loads((land.ROOT / ('docs/verification/' + prefix + '-result.json')).read_text())
            self.assertEqual(failed, report['counts']['FAIL'])
            with self.assertRaises(ValueError): verify(report, report['candidate'], suite)

    def test_real_native_frame_transport_fits_step_budget_and_round_trips(self):
        from r24c_capture import transport_chunks, CHUNKS_PER_STEP, MAX_CURRENT_PARTS, CHUNK_CHARS, NAMES
        import base64
        image = land.ROOT / 'docs/verification/r24c-native-previews/third-failed-workflow/r24c-silver.png'
        data = image.read_bytes()
        self.assertEqual('98d47cea102bf7a388435f0812941eadd8664b8f1395ff349fbd9872fd86e490', hashlib.sha256(data).hexdigest())
        messages = transport_chunks(image.name, data)
        self.assertLessEqual(len(messages), MAX_CURRENT_PARTS); self.assertGreater(len(messages), 0)
        self.assertTrue(all(len(message.split(" ", 4)[4]) <= CHUNK_CHARS for message in messages))
        self.assertLessEqual(MAX_CURRENT_PARTS + 3, 50)  # One frame per real CI job: metadata + runner warning.
        pages = [messages[i:i + CHUNKS_PER_STEP] for i in range(0, len(messages), CHUNKS_PER_STEP)]
        self.assertTrue(all(len(page) <= 8 for page in pages))
        decoded = base64.b64decode(''.join(message.split(' ', 4)[4] for page in pages for message in page), validate=True)
        self.assertEqual(data, decoded)  # Real failed-workflow screenshot; NOT a new native pass.

    def test_native_capture_budget_covers_maximum_payload_without_job_overflow(self):
        from r24c_capture import MAX_CURRENT_PARTS, MAX_PREVIEW_BYTES, CHUNK_CHARS, CHUNKS_PER_STEP
        import math
        encoded = 4 * math.ceil(MAX_PREVIEW_BYTES / 3)
        self.assertLessEqual(math.ceil(encoded / CHUNK_CHARS), MAX_CURRENT_PARTS)
        self.assertLess(CHUNK_CHARS + 200, 4096)
        self.assertLessEqual(MAX_CURRENT_PARTS, 6 * CHUNKS_PER_STEP)
        self.assertLessEqual(MAX_CURRENT_PARTS + 3, 50)

    def test_engine_reuse_has_no_push_fanout_or_shared_caller_concurrency(self):
        text = (land.ROOT.parent / '.github/workflows/engine-intake-r24c.yml').read_text()
        self.assertIn('  workflow_call:', text)
        self.assertNotIn('  push:', text)
        self.assertIn('group: r24c-servo-intake-${{ github.workflow }}-${{ github.ref }}', text)
        # Workflow-configuration check only, not an executed scheduler proof.

    def test_actual_truncated_fifth_png_is_rejected_not_displayed(self):
        import base64
        rows = json.loads((land.ROOT / 'docs/verification/r24c-fifth-annotations.json').read_text())
        chunks = [row['message'].split(' ', 4) for row in rows if row['message'].startswith('R24CPNG1 r24c-wall.png ')]
        self.assertEqual(len(chunks), 1)
        self.assertEqual(len(' '.join(chunks[0])), 4096)
        raw = base64.b64decode(chunks[0][4], validate=True)
        self.assertNotEqual(hashlib.sha256(raw).hexdigest(), chunks[0][3])
        # Actual lost transport bytes cannot be recreated, padded, or promoted to an inspected frame.

    def test_privacy_readiness_flags_are_observations_not_coercible_truth(self):
        from r24c_privacy_readiness import collect
        with tempfile.TemporaryDirectory() as directory:
            # Parser-only artificial records, NOT an actual Android receipt.
            state = dict(api=37, interactive=True, keyguard_locked=False, device_locked=False, credential_secure=False)
            row = dict(origin='android-instrumentation', case='parser-only', before=dict(state, interactive=False), after=state, actions=['one-real-wake'])
            p = Path(directory) / 'r24c-privacy-preflight.jsonl'; p.write_text(json.dumps(row) + '\n')
            r = collect(directory, 37)
            self.assertEqual(r['summary'], dict(records=1, not_ready_before=1, not_ready_after=0, wake_requests=1, keyguard_dismiss_requests=0))
            with self.assertRaises(ValueError): collect(directory, 34)
            row['after']['interactive'] = 'false'; p.write_text(json.dumps(row) + '\n')
            with self.assertRaises(ValueError): collect(directory, 37)
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(collect(directory, 37)['summary']['records'], 0)

    def test_fifth_actual_privacy_failure_cannot_inherit_earlier_green(self):
        from r24c_evidence_gate import verify
        receipt = json.loads((land.ROOT / 'docs/verification/r24c-fifth-privacy37-result.json').read_text())
        self.assertEqual(receipt['counts'], {'PASS': 3, 'FAIL': 16, 'SKIP': 0})
        with self.assertRaises(ValueError): verify(receipt, receipt['candidate'], 'privacy')

    def test_staging_actual_frame_clears_stale_named_outputs(self):
        import r24c_capture as capture
        from unittest.mock import patch
        data = (land.ROOT / 'docs/verification/r24c-native-previews/third-failed-workflow/r24c-silver.png').read_bytes()
        with tempfile.TemporaryDirectory() as directory, patch.object(capture, 'ROOT', Path(directory)):
            root = Path(directory)
            source = root / 'app/build/outputs/managed_device_android_test_additional_output/device'
            target = root / 'testing/build/r24c/native-frames'
            source.mkdir(parents=True); target.mkdir(parents=True)
            (source / 'r24c-silver.png').write_bytes(data)  # Existing real frame; not new Android execution.
            (target / 'r24c-wall.png').write_bytes(b'stale marker, not a real frame')
            capture.stage()
            self.assertEqual((target / 'r24c-silver.png').read_bytes(), data)
            self.assertFalse((target / 'r24c-wall.png').exists())
            (source / 'r24c-silver.png').unlink(); capture.stage()
            self.assertEqual(list(target.iterdir()), [])

    def test_omitted_clipped_bounds_are_not_visual_acceptance(self):
        # Actual retained native observations, with fields removed as an explicit parser attack.
        record = json.loads((land.ROOT / 'docs/verification/r24c-native-previews/fourth-failed-workflow/provenance.json').read_text())
        report = record['actual_target_observations']['r24c-silver.json']
        self.assertEqual(validate_audit(report)['measured_targets'], 7)
        for key in ('visible_width_dp', 'visible_height_dp'):
            changed = copy.deepcopy(report)
            changed['required_targets'][0].pop(key)
            with self.assertRaises(ValueError): validate_audit(changed)

    def test_native_frame_capture_orders_semantics_before_committed_surface_copy(self):
        # Source ordering contract, not a substitute for Android execution / actual image inspection.
        source = (land.ROOT / 'app/src/androidTest/java/dev/fusion/shell/NativeVisualEvidence.kt').read_text()
        body = source[source.index('    fun capture('):]
        self.assertLess(body.index('node.assertIsDisplayed()'), body.index('val raw = committedWindow(activity)'))
        self.assertLess(body.index('auditFile.delete()'), body.index('val geometry'))
        self.assertIn('registerFrameCommitCallback', source)
        self.assertIn('PixelCopy.request(activity.window', source)
        self.assertNotIn('uiAutomation.takeScreenshot()', body)
        self.assertIn('application surface, not whole-display capture', source)

    def test_eighth_actual_failures_are_not_erased_by_the_focused_green(self):
        from r24c_evidence_gate import verify
        for prefix, suite, counts in [('r24c-eighth-main', 'full', {'PASS': 141, 'FAIL': 1, 'SKIP': 0}),
                                      ('r24c-eighth-privacy', 'privacy', {'PASS': 18, 'FAIL': 1, 'SKIP': 0})]:
            receipt = json.loads((land.ROOT / ('docs/verification/' + prefix + '-result.json')).read_text())
            self.assertEqual(receipt['counts'], counts)
            with self.assertRaises(ValueError): verify(receipt, receipt['candidate'], suite)
