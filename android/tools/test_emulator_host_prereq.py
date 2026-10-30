"""Static workflow ordering checks; they do not install a library or run an emulator."""
from pathlib import Path
import unittest

REPO = Path(__file__).resolve().parents[2]
WORKFLOWS = REPO / '.github/workflows'
PREREQ = "- name: Install the emulator's Linux host audio library"
TARGETS = {
    'android-r54-office.yml': '- name: Install and check Android platform and managed-emulator components',
    'android-r24c.yml': '- name: Install real compilation and emulator components',
    'android-r25-png.yml': '- name: Real isolated UID, native decoder and system-picker copy/readback',
    'android.yml': '- name: Reproduce prior-source child-focus failure on real Android',
}


class EmulatorHostPrerequisiteChecks(unittest.TestCase):
    def test_target_workflows_install_and_check_library_before_emulator(self):
        for name, target in TARGETS.items():
            with self.subTest(workflow=name):
                text = (WORKFLOWS / name).read_text()
                self.assertEqual(text.count(PREREQ), 1)
                start = text.index(PREREQ)
                end = text.index('\n      - ', start + len(PREREQ))
                step = text[start:end]
                self.assertLess(start, text.index(target))
                self.assertIn('sudo apt-get update', step)
                self.assertIn('sudo apt-get install -y --no-install-recommends libpulse0', step)
                self.assertIn("ldconfig -p | grep -F 'libpulse.so.0'", step)
                self.assertNotIn('|| true', step)
                self.assertNotIn('continue-on-error', step)


if __name__ == '__main__':
    unittest.main()
