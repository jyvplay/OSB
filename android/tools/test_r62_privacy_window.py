#!/usr/bin/env python3
"""Static guardrails for failure-only window observation; not Android execution evidence."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class PrivacyWindowIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ready = (ROOT / 'privacy/src/androidTest/java/dev/fusion/privacy/PrivacyDeviceReady.kt').read_text(encoding='utf-8')
        cls.classifier = (ROOT / 'privacy/src/androidTest/java/dev/fusion/privacy/PrivacyWindowClassifier.kt').read_text(encoding='utf-8')

    def test_window_observation_runs_only_inside_failure_expressions(self):
        self.assertEqual(self.ready.count('windowSummary()'), 3)  # Definition and two failure messages.
        self.assertIn('if (!gained) fail(', self.ready)
        self.assertIn('if (observed != expected) fail(', self.ready)
        for line in self.ready.splitlines():
            if 'windowSummary()' in line and 'fun windowSummary()' not in line:
                self.assertIn('fail(', line)

    def test_window_observation_is_read_only_bounded_and_emits_categories_only(self):
        block = self.ready.split('private fun windowSummary()', 1)[1].split('/** Before launching any tested UI', 1)[0]
        self.assertIn('windows.take(8)', block)
        self.assertIn('PrivacyWindowClassifier.classify(pkg, title)', block)
        self.assertIn('"root_kind", kind.root', block)
        self.assertIn('"title_kind", kind.title', block)
        self.assertIn('"empty-or-unavailable"', block)
        for forbidden in ('executeShellCommand', '.click(', 'performAction', 'serviceInfo', 'setServiceInfo', 'put("title",', 'put("package",'):
            self.assertNotIn(forbidden, block)

    def test_classifier_uses_exact_system_packages_and_bounded_no_raw_categories(self):
        self.assertIn('"android" -> "android-system"', self.classifier)
        self.assertIn('"com.android.systemui" -> "system-ui"', self.classifier)
        self.assertIn('title?.take(512)', self.classifier)
        self.assertIn('else -> "other"', self.classifier)
        self.assertIn('"not-inspected"', self.classifier)
        self.assertIn('return Kind(root, category)', self.classifier)


if __name__ == '__main__': unittest.main()
