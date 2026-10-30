#!/usr/bin/env python3
"""Adversarial checks for reporting real, bounded picker selector observations."""
import json
from pathlib import Path
import tempfile
import unittest

import report_r25_picker_selection as picker


class PickerSelectionReportTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.path = self.root / 'r25-picker-selection-r25-src-1234abcd.json'
        self.row = {
            'schema': 1, 'origin': 'android-instrumentation', 'api': 34,
            'fixture': 'r25-src-1234abcd.png', 'foreground': 'com.google.android.documentsui',
            'selector': 'description', 'matched_id': 'com.google.android.documentsui:id/preview_icon',
            'matched_description': 'Preview r25-src-1234abcd.png',
            'strategy': 'preview-sibling-title', 'target_id': 'android:id/title',
        }

    def tearDown(self):
        self.tmp.cleanup()

    def write(self):
        self.path.write_text(json.dumps(self.row), encoding='utf-8')

    def test_absent_observation_is_not_a_pass(self):
        self.assertEqual(picker.collect(self.root), [])

    def test_observed_preview_and_exact_title_routes(self):
        self.write()
        self.assertEqual(picker.collect(self.root)[0]['strategy'], 'preview-sibling-title')
        self.row.update(selector='text', matched_id='android:id/title', strategy='filename-text')
        self.write()
        self.assertEqual(picker.collect(self.root)[0]['target_id'], 'android:id/title')

    def test_refuses_unverified_description_as_selection(self):
        self.row.update(strategy='filename-text', target_id=self.row['matched_id'])
        self.write()
        with self.assertRaises(ValueError):
            picker.collect(self.root)

    def test_refuses_fake_preview_or_wrong_target(self):
        for key, value in (('matched_id', 'id/not-preview'), ('target_id', 'id/wrong')):
            old = self.row[key]
            self.row[key] = value
            self.write()
            with self.assertRaises(ValueError):
                picker.collect(self.root)
            self.row[key] = old

    def test_refuses_fake_origin_schema_fixture_and_api(self):
        for key, value in (('origin', 'host'), ('schema', True), ('fixture', 'private.png'), ('api', True)):
            old = self.row[key]
            self.row[key] = value
            self.write()
            with self.assertRaises(ValueError):
                picker.collect(self.root)
            self.row[key] = old

    def test_refuses_oversize_and_symlink(self):
        self.path.write_text(' ' * 4097)
        with self.assertRaises(ValueError):
            picker.collect(self.root)
        self.path.unlink()
        other = self.root / 'other'
        other.write_text('{}')
        self.path.symlink_to(other)
        with self.assertRaises(ValueError):
            picker.collect(self.root)


if __name__ == '__main__':
    unittest.main()
