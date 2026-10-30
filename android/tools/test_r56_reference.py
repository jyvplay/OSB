"""Adversarial tests for the independent R56 comparator, not the product evaluator."""
import unittest
from check_r56_reference import compare_edges, compare_rows, same_number


class ReferenceAttacks(unittest.TestCase):
    def test_typed_exact_comparison(self):
        self.assertTrue(same_number(1, 1.0))
        self.assertFalse(same_number(True, 1.0))
        self.assertFalse(same_number(1.0, '1'))
        self.assertFalse(same_number(1.0, float('nan')))
        self.assertFalse(same_number(float('inf'), float('inf')))
        self.assertFalse(same_number(10**1000, 1))
        self.assertFalse(same_number(5e-321, 0.0))
        self.assertFalse(same_number(1.0, 1.01))

    def test_correct_positive_and_negative_ties(self):
        rows = ['1.005\t2\t1.01', '-1.005\t2\t-1.01']
        keys = {('1.005', 2), ('-1.005', 2)}
        values = {'=ROUND(1.005,2)': 1.01, '=ROUND(-1.005,2)': -1.01}
        self.assertEqual(compare_rows(rows, keys, values.get), (2, 2))

    def test_mutated_values_and_reference_are_rejected(self):
        keys = {('1.005', 2)}
        for actual in ('1', '-1.01', 'nan', 'inf'):
            with self.subTest(actual=actual), self.assertRaises(ValueError):
                compare_rows([f'1.005\t2\t{actual}'], keys)
        with self.assertRaises(ValueError):
            compare_rows(['1.005\t2\t1.01'], keys, lambda _: True)
        with self.assertRaises(ValueError):
            compare_rows(['1.005\t2\t1.01'], keys, lambda _: 1.0)

    def test_missing_duplicate_and_wrong_input_are_rejected(self):
        row = '1.005\t2\t1.01'
        keys = {('1.005', 2), ('-1.005', 2)}
        for rows in ([], [row], [row, row], [row, '1.005\t3\t1.005'], [row, 'bad']):
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                compare_rows(rows, keys)

    def test_edge_records_reject_missing_duplicate_and_wrong_types(self):
        expected = {'=ROUND(1.005,2)': 1.01, '=ROUND(1.7e308,-308)': '#NUM!'}
        good = ['=ROUND(1.005,2)\tN\t1.01', '=ROUND(1.7e308,-308)\tE\t#NUM!']
        self.assertEqual(compare_edges(good, expected), (0, 0))
        for rows in (good[:1], good + good[:1], good[:1] + ['=ROUND(1.7e308,-308)\tN\t0'],
                     good[:1] + ['=ROUND(1.7e308,-308)\tE\t#VALUE!']):
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                compare_edges(rows, expected)
        values = {'=ROUND(1.005,2)': 1.01, '=ROUND(1.7e308,-308)': float('inf')}
        self.assertEqual(compare_edges(good, expected, values.get, {'=ROUND(1.7e308,-308)'}), (1, 1))
        with self.assertRaises(ValueError):
            compare_edges(good, expected, lambda _: 1.0, {'=ROUND(1.7e308,-308)'})

    def test_fabricated_wrong_scale_cannot_enter_expected_set(self):
        with self.assertRaises(ValueError):
            compare_rows(['1.005\t999999999\t1.005'], {('1.005', 2)})


if __name__ == '__main__':
    unittest.main()
