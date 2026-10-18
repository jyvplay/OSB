"""Actual shipped wrapper ZIP and real host ELF (when available), not a simulated browser/Office engine."""
import io
from pathlib import Path
import unittest
from engine_intake import inspect,elf_header
ROOT=Path(__file__).resolve().parents[1]
class EngineIntakeTests(unittest.TestCase):
    def test_actual_wrapper_jar_is_not_a_native_engine_or_license_clearance(self):
        result=inspect(ROOT/'gradle/wrapper/gradle-wrapper.jar')
        self.assertEqual([],result['native_libraries'])
        for key in ['integrated','runtime_verified','legal_accepted']:self.assertFalse(result[key])
    def test_truncated_or_not_elf_data_cannot_be_accepted_as_a_library(self):
        for data in [b'',b'not a native engine',b'\x7fELF'+bytes(60)]:
            with self.assertRaises(ValueError):elf_header(io.BytesIO(data),len(data))
