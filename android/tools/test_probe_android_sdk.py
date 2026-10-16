"""Catalog parser/refusal tests, NOT simulated SDK installation or availability evidence."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from probe_android_sdk import platforms, probe


class SdkCatalogTests(unittest.TestCase):
    def test_exact_platform_tokens_and_lookalikes(self):
        text = 'Installed packages:\n platforms;android-36 | 2 | SDK36\n platforms;android-37 | 1 | SDK37\n platforms;android-370 | 1 | unrelated\n platforms;android-Codename | 1 | named preview\n platforms;android-37 | 1 | duplicate\n'
        names = platforms(text)
        self.assertEqual(4, len(names)); self.assertIn('platforms;android-37', names)
        self.assertNotIn('platforms;android-37', platforms('platforms;android-370 | 1'))
        for bad in ['platforms;../android-37 | 1', 'platforms;android-37;execute | 1', 'platforms;android-37 extra | 1']:
            with self.assertRaises(ValueError): platforms(bad)

    def test_catalog_consumed_output_and_count_caps(self):
        with self.assertRaises(ValueError): platforms('x' * (4*1024*1024 + 1))
        with self.assertRaises(ValueError): platforms('\n'.join(f'platforms;android-{n} | 1' for n in range(129)))
        self.assertEqual(128, len(platforms('\n'.join(f'platforms;android-{n} | 1' for n in range(128)))))
        self.assertEqual([], platforms('repository warning only')) # Parsing empty text is NOT a real availability result.

    def test_real_absent_manager_is_unknown_and_cli_nonzero(self):
        with tempfile.TemporaryDirectory() as temporary:
            missing = Path(temporary)/'sdkmanager-not-present'; report = Path(temporary)/'observation.json'
            self.assertIsNone(probe(missing)['sdk37_in_any_observed_catalog'])
            command = [sys.executable, str(Path(__file__).with_name('probe_android_sdk.py')), '--sdkmanager', str(missing), '--report', str(report)]
            result = subprocess.run(command, text=True, capture_output=True, check=False)
            self.assertEqual(1, result.returncode)
            observation = json.loads(report.read_text())
            self.assertIsNone(observation['sdk37_in_any_observed_catalog'])
            self.assertEqual([], observation['catalogs'])
            self.assertFalse(missing.exists())


if __name__ == '__main__': unittest.main()
