import copy
import json
import tempfile
import unittest
from pathlib import Path
from r25_ui_lab import collect
from r25_evidence_gate import expected, verify

ROOT = Path(__file__).resolve().parents[1]

class R25ToolTests(unittest.TestCase):
    def test_preflight_unknown_is_not_ready_and_repeat_actions_refused(self):
        # Artificial parser inputs only, never native execution evidence.
        state = dict(api=34, interactive=True, keyguard_locked=False, device_locked=False, credential_secure=False)
        row = dict(origin='android-instrumentation', case='parser-only', phase='candidate', before=dict(state, interactive=False), after=state, actions=['one-real-wake'])
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'r25-ui-preflight.jsonl'
            p.write_text(json.dumps(row)+'\n')
            self.assertEqual(collect(tmp)['summary']['not_ready_before'], 1)
            self.assertEqual(collect(tmp)['summary']['not_ready_after'], 0)
            for bad in (None, 'false', 1):
                changed = copy.deepcopy(row); changed['after']['interactive'] = bad
                p.write_text(json.dumps(changed)+'\n')
                with self.assertRaises(ValueError): collect(tmp)
            changed = copy.deepcopy(row); changed['actions'] *= 2
            p.write_text(json.dumps(changed)+'\n')
            with self.assertRaises(ValueError): collect(tmp)
            changed = copy.deepcopy(row); changed['before']['api'] = 37
            p.write_text(json.dumps(changed)+'\n')
            with self.assertRaises(ValueError): collect(tmp)

    def test_historical_counterexample_preflight_cannot_count_as_candidate(self):
        state = dict(api=34, interactive=True, keyguard_locked=False, device_locked=False, credential_secure=False)
        row = dict(origin='android-instrumentation', case='parser-only', phase='prior-source-counterexample', before=state, after=state, actions=[])
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp)/'r25-ui-preflight.jsonl').write_text(json.dumps(row)+'\n')
            report = collect(tmp)
            self.assertEqual(report['summary']['observations'], 0)
            self.assertEqual(report['summary']['other_phase_observations'], 1)
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(collect(tmp)['summary']['observations'], 0)

    def test_exact_named_native_contract_and_bad_receipt_mutations(self):
        self.assertEqual(len(expected('full')), 156)
        self.assertEqual(len(expected('web')), 1)
        self.assertEqual(len(expected('media')), 3)
        self.assertEqual(len(expected('png')), 3)
        self.assertEqual(len(expected('readiness')), 6)
        candidate='a'*40  # Parser-only value; never sent to GitHub or recorded as a native run.
        report=dict(candidate=candidate, suite='focused', platform_api=34, conclusion='success', device_conclusion='success',
                    cases=[dict(case=name,result='PASS') for name in sorted(expected('readiness'))], counts=dict(PASS=6,FAIL=0,SKIP=0))
        self.assertEqual(verify(report,candidate,'readiness')['passed_cases'],6)
        for mutation in ('missing','skip','failure','wrong-source','wrong-api','duplicate'):
            r=copy.deepcopy(report)
            if mutation=='missing': r['cases'].pop()
            elif mutation=='skip': r['cases'][0]['result']='SKIP'
            elif mutation=='failure': r['conclusion']='failure'
            elif mutation=='wrong-source': r['candidate']='b'*40
            elif mutation=='wrong-api': r['platform_api']=37
            else: r['cases'].append(r['cases'][0])
            with self.assertRaises(ValueError): verify(r,candidate,'readiness')

    def test_real_failed_r24c_full_run_cannot_be_promoted(self):
        r=json.loads((ROOT/'docs/verification/r24c-tenth-main-result.json').read_text())
        self.assertEqual(r['counts'],dict(PASS=137,FAIL=5,SKIP=0))
        with self.assertRaises(ValueError): verify(r,r['candidate'],'full')

    def test_png_frame_group_does_not_relabel_the_legacy_desktop_group(self):
        import r24c_capture as capture
        self.assertEqual(capture.FRAME_GROUPS['png-copy'], {'r25-png-copy.png'})
        self.assertEqual(len(capture.NAMES), 4)
        self.assertTrue(capture.NAMES.isdisjoint(capture.FRAME_GROUPS['png-copy']))
        with self.assertRaises(ValueError): capture.export(0, 'r25-png-copy.png')

    def test_office_capture_group_covers_every_audit_in_the_actual_workflow(self):
        import r24c_capture as capture
        self.assertEqual(capture.FRAME_GROUPS['r57-office'],
                         {'r24c-structured.png', 'r24c-sheet.png', 'r24c-writer.png', 'r24c-round.png'})
        self.assertTrue(capture.NAMES.isdisjoint(capture.FRAME_GROUPS['r57-office']))
        with self.assertRaises(ValueError): capture.export(0, 'r24c-structured.png')

    def test_png_source_owner_is_existing_image_module_not_denominator_inflation(self):
        import module_audit
        included = module_audit.included_modules()
        self.assertIn(':format:image', included)
        self.assertNotIn(':format:png', included)
        self.assertTrue((ROOT/'format/image/src/main/kotlin/dev/fusion/format/image/PngMetadata.kt').is_file())

    def test_r58_capture_group_includes_all_office_workflow_audits(self):
        import r24c_capture as capture
        self.assertEqual(capture.FRAME_GROUPS['r58-office'], capture.FRAME_GROUPS['r57-office'] |
                         {'r58-text-grid.png', 'r58-service-review.png'})
        self.assertTrue(capture.NAMES.isdisjoint(capture.FRAME_GROUPS['r58-office']))

    def test_r60_capture_group_and_office_workflow_include_both_new_frames(self):
        import r24c_capture as capture
        added = {'r60-writer-numbered.png', 'r60-sheet-transpose.png'}
        self.assertEqual(capture.FRAME_GROUPS['r60-office'], capture.FRAME_GROUPS['r59-office'] | added)
        self.assertTrue(capture.NAMES.isdisjoint(capture.FRAME_GROUPS['r60-office']))
        workflow = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text()
        for name in added:
            self.assertIn(name, workflow)
            with self.assertRaises(ValueError):
                capture.export(0, name)  # The unrelated legacy group cannot relabel these captures.

    def test_r61_capture_group_contains_protection_frame(self):
        import r24c_capture as capture
        added = {'r61-sheet-protection.png'}
        self.assertEqual(capture.FRAME_GROUPS['r61-office'], capture.FRAME_GROUPS['r60-office'] | added)
        self.assertIn(next(iter(added)), (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text())
        with self.assertRaises(ValueError):
            capture.export(0, next(iter(added)))

    def test_r63_capture_group_adds_both_office_frames(self):
        import r24c_capture as capture
        added = {'r63-sheet-formula.png', 'r63-writer-restart.png'}
        self.assertEqual(capture.FRAME_GROUPS['r63-office'], capture.FRAME_GROUPS['r62-office'] | added)
        workflow = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text()
        for name in added:
            self.assertIn(name, workflow)

    def test_r64_capture_group_keeps_existing_and_adds_three_real_frames(self):
        import r24c_capture as capture
        added = {'r64-sheet-copy.png', 'r64-sheet-csv.png', 'r64-writer-table.png'}
        self.assertEqual(capture.FRAME_GROUPS['r64-office'], capture.FRAME_GROUPS['r63-office'] | added)
        workflow = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text()
        for name in added:
            self.assertIn(name, workflow)
            with self.assertRaises(ValueError): capture.export(0, name)

    def test_r62_capture_group_preserves_all_previous_and_new_frames(self):
        import r24c_capture as capture
        added = {'r62-writer-strike.png', 'r62-sheet-range.png'}
        self.assertEqual(capture.FRAME_GROUPS['r62-office'], capture.FRAME_GROUPS['r61-office'] | added)
        workflow = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text()
        for name in added:
            self.assertIn(name, workflow)
            with self.assertRaises(ValueError): capture.export(0, name)

    def test_original_encoded_video_fixture_matches_its_actual_generation_receipt(self):
        import hashlib
        receipt=json.loads((ROOT/'docs/research/r25-video-fixture.json').read_text())
        file=ROOT.parent/receipt['file']
        self.assertEqual(file.stat().st_size,receipt['bytes'])
        self.assertEqual(hashlib.sha256(file.read_bytes()).hexdigest(),receipt['sha256'])
        self.assertFalse(receipt['native_decoded'])  # Generation receipt is deliberately NOT a decoder pass.

    def test_lok_testability_is_bound_to_actual_executed_production_adapter(self):
        import hashlib
        record=json.loads((ROOT/'docs/verification/r25/lok-production-first/verified.json').read_text())
        production=ROOT/'format/lok/src/main/assets/osb-lok/OfficeSession.js'
        consumer=ROOT/'testing/src/r25js/LokWasmChecks.js'
        self.assertEqual(hashlib.sha256(production.read_bytes()).hexdigest(),record['production_adapter_sha256'])
        self.assertEqual(hashlib.sha256(consumer.read_bytes()).hexdigest(),record['consumer_script_sha256'])
        self.assertEqual(record['actual_protocol_checks'],10)
        self.assertTrue(record['source_alias_attack'])
        self.assertFalse(record['integrated_android']);self.assertFalse(record['full_office_accepted']);self.assertFalse(record['legal_accepted'])
