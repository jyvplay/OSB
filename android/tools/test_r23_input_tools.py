"""Real parser/file IO unit tests, not emulated USB devices or physical-host security evidence."""
import json
from pathlib import Path
import tempfile
import unittest
from inspect_usb_posture import inspect, parse_authorization, read_attribute


class UsbPostureTests(unittest.TestCase):
    def test_explicit_byte_values_and_interface_boundary(self):
        for value in range(3):
            self.assertEqual(value, parse_authorization(str(value).encode() + b'\n'))
        for value in (b'2', b'3', b'-1', b'', b'1\x00', b'0x01', b'1' * 33, b'\xff', b'1\n0'):
            with self.assertRaises(ValueError):
                parse_authorization(value, interface=True)
        with self.assertRaises(ValueError):
            parse_authorization('1')

    def test_missing_is_unknown_not_zero_or_all_clear(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.assertEqual('UNKNOWN', inspect(root)['inventory'])
            bus = root / 'usb1'; bus.mkdir()
            result = inspect(root)
            self.assertIsNone(result['buses'][0]['new_device_default']['value'])
            self.assertEqual('UNKNOWN_UNREADABLE', result['buses'][0]['new_device_default']['observation'])
            self.assertEqual('UNKNOWN', inspect(root / 'missing')['inventory'])

    def test_bounded_read_does_not_truncate_invalid_attribute_or_change_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, device, interface in [('usb10', b'0\n', b'1\n'), ('usb2', b'2\n', b'2\n')]:
                bus = root / name; bus.mkdir()
                (bus / 'authorized_default').write_bytes(device)
                (bus / 'interface_authorized_default').write_bytes(interface)
                (bus / 'serial').write_text('MUST_NOT_APPEAR')
            (root / 'not-a-bus').mkdir()
            before = {str(p): p.read_bytes() for p in root.rglob('*') if p.is_file()}
            report = inspect(root)
            self.assertEqual(['usb2', 'usb10'], [row['bus'] for row in report['buses']])
            self.assertEqual('UNKNOWN_FORMAT', report['buses'][0]['new_interface_default']['observation'])
            self.assertEqual(2, report['buses'][0]['new_device_default']['value'])
            self.assertNotIn('MUST_NOT_APPEAR', json.dumps(report))
            self.assertEqual(before, {str(p): p.read_bytes() for p in root.rglob('*') if p.is_file()})
            huge = root / 'usb2' / 'authorized_default'; huge.write_bytes(b'1' + b' ' * 33)
            self.assertEqual('UNKNOWN_FORMAT', read_attribute(huge)['observation'])
            self.assertEqual(b'1' + b' ' * 33, huge.read_bytes())

    def test_overflow_refuses_inventory_instead_of_clean_truncated_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for i in range(257):
                (root / f'usb{i}').mkdir()
            report = inspect(root)
            self.assertEqual('UNKNOWN_BUS_LIMIT', report['inventory'])
            self.assertEqual([], report['buses'])

    def test_actual_sysfs_observation_remains_scoped(self):
        # An actual read in this runtime; a container can legitimately expose no root hubs.
        report = inspect()
        self.assertEqual('READ_ONLY_USB_DEFAULTS_NOT_A_SECURITY_VERDICT', report['kind'])
        self.assertIn(report['inventory'], ('UNKNOWN', 'UNKNOWN_ENTRY_LIMIT', 'UNKNOWN_BUS_LIMIT', 'OBSERVED_ROOT_HUB_DEFAULTS_ONLY'))
        self.assertTrue(all(set(row) == {'bus', 'new_device_default', 'new_interface_default'} for row in report['buses']))


if __name__ == '__main__':
    unittest.main()
