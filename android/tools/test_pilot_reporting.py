import unittest
from pilot_device_report import observation, COMMANDS

class PilotReportTest(unittest.TestCase):
    def test_empty_property_is_unknown_not_false(self):
        self.assertEqual({'status':'unavailable','value':None}, observation(0, ' \n'))
    def test_failed_command_does_not_become_an_observation(self):
        self.assertEqual('unavailable', observation(1, 'permission denied')['status'])
    def test_observations_are_bounded(self):
        self.assertEqual(16384, len(observation(0, 'x'*20000)['value']))
        self.assertEqual('16384', observation(0, '16384\n')['value'])
    def test_default_commands_do_not_collect_identifiers_or_private_data(self):
        flat = ' '.join(' '.join(command) for command in COMMANDS.values())
        for forbidden in ['fingerprint','serialno','logcat','dumpsys','settings','contacts']:
            self.assertNotIn(forbidden, flat)

if __name__ == '__main__': unittest.main()
