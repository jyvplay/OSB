"""Host-side adversarial checks of the reference comparator; not a device test."""
import unittest
from check_r55_reference import same


class ReferenceComparatorChecks(unittest.TestCase):
    def test_numbers_are_not_booleans(self):
        self.assertTrue(same('N', '1', 1.0))
        self.assertFalse(same('N', '1', True))
        self.assertFalse(same('B', 'TRUE', 1))
        self.assertTrue(same('B', 'TRUE', True))

    def test_wrong_kind_or_value_is_not_a_match(self):
        self.assertFalse(same('N', '0', '0'))
        self.assertFalse(same('T', '', None))
        self.assertFalse(same('Z', '', ''))
        self.assertFalse(same('E', '#DIV/0!', {'type': 'Error', 'kind': 'Num'}))
        self.assertTrue(same('E', '#DIV/0!', {'type': 'Error', 'kind': 'Div'}))
        self.assertFalse(same('R', 'refused', {'type': 'Error', 'kind': 'Num'}))

    def test_subnormal_is_not_accepted_as_zero(self):
        self.assertFalse(same('N', '5e-321', 0.0))
        self.assertTrue(same('N', '5e-321', 5e-321))

    def test_small_rounding_tolerance_does_not_hide_obvious_mutants(self):
        self.assertTrue(same('N', '0.3', 0.30000000000000004))
        self.assertFalse(same('N', '0.3', 0.31))
        self.assertFalse(same('N', 'not-number', 1.0))


if __name__ == '__main__':
    unittest.main()
