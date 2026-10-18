"""Mutate real wrapper configuration, not a fake Gradle installation/build result."""
from pathlib import Path
import unittest
from gradle_distribution import EXPECTED, verify


class DistributionPinTests(unittest.TestCase):
    def setUp(self):
        self.text = (Path(__file__).resolve().parents[1]/'gradle/wrapper/gradle-wrapper.properties').read_text()

    def test_actual_canonical_distribution_pin(self):
        self.assertEqual(EXPECTED, verify(self.text))

    def test_presence_is_not_configuration_duplicate_last_value_is_refused(self):
        # A substring-only checker would see the good earlier pin, while Java Properties uses the LAST key.
        for tail in ['distributionSha256Sum=' + '0'*64, 'distributionUrl=https://example.invalid/evil.zip',
                     'zipStoreBase=PROJECT']:
            self.assertIn(EXPECTED['distributionSha256Sum'], self.text)
            with self.assertRaises(ValueError): verify(self.text + tail + '\n')

    def test_wrong_distribution_hash_alias_or_noncanonical_escape_refused(self):
        for text in [self.text.replace('9.3.1-bin', '0.0.0-bin'),
                     self.text.replace(EXPECTED['distributionSha256Sum'], '0'*64),
                     self.text.replace('distributionUrl=', 'distributionUrl:'),
                     self.text + 'unknown=1\n', self.text.replace('networkTimeout=10000', 'networkTimeout=10000 '),
                     self.text.replace('distributionSha256Sum=', r'distributionSha256S\u0075m='), 'x'*8193]:
            with self.assertRaises(ValueError): verify(text)


if __name__ == '__main__': unittest.main()
