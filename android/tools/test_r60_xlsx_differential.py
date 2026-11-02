#!/usr/bin/env python3
import struct
import unittest

import run_r60_xlsx_differential as subject


class DifferentialComparatorTests(unittest.TestCase):
    def test_frame_preserves_shape_holes_and_utf8(self):
        actual = subject.frame([(1, 2, 'α'), (2, 1, '=1')])
        expected = (struct.pack('>iii', 0x46435431, 2, 2) + struct.pack('>i', 0) +
                    struct.pack('>i', 2) + 'α'.encode() + struct.pack('>i', 2) + b'=1' + struct.pack('>i', 0))
        self.assertEqual(expected, actual)

    def test_matching_valid_and_refused(self):
        cases = [('ok', b'x', 'OK\thash'), ('bad', b'y', 'REFUSED')]
        self.assertEqual(2, subject.verify_results(cases, ['OK\thash', 'REFUSED'], ['OK\thash', 'REFUSED']))

    def test_identical_but_wrong_accepted_value_is_not_a_pass(self):
        cases = [('ok', b'x', 'OK\tcorrect')]
        with self.assertRaises(AssertionError):
            subject.verify_results(cases, ['OK\twrong'], ['OK\twrong'])

    def test_identical_but_wrong_refusal_is_not_a_pass(self):
        cases = [('ok', b'x', 'OK\tcorrect')]
        with self.assertRaises(AssertionError):
            subject.verify_results(cases, ['REFUSED'], ['REFUSED'])

    def test_forbidden_case_cannot_be_accepted(self):
        cases = [('bad', b'x', 'REFUSED')]
        with self.assertRaises(AssertionError):
            subject.verify_results(cases, ['REFUSED'], ['OK\tbad'])

    def test_missing_or_extra_result_fails(self):
        cases = [('ok', b'x', 'OK\thash')]
        with self.assertRaises(AssertionError):
            subject.verify_results(cases, [], [])
        with self.assertRaises(AssertionError):
            subject.verify_results(cases, ['OK\thash'], ['OK\thash', 'REFUSED'])

    def test_column_boundary(self):
        self.assertEqual(['A', 'Z', 'AA', 'AF', 'AG'], [subject.column(n) for n in (1, 26, 27, 32, 33)])


if __name__ == '__main__':
    unittest.main()
