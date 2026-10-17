"""Real missing-executable/actual filesystem refusal; no fake compiler/Android SDK."""
import os
from pathlib import Path
import tempfile
import unittest
import run_r24_contracts as runner
class RunnerFreshnessTests(unittest.TestCase):
    def test_real_missing_compiler_cannot_leave_earlier_success_marker(self):
        old=runner.ROOT;compiler=os.environ.get('KOTLINC')
        try:
            with tempfile.TemporaryDirectory() as tmp:
                runner.ROOT=Path(tmp);out=runner.ROOT/'testing/build/r24';out.mkdir(parents=True)
                (out/'result.txt').write_text('OLD SUCCESS MUST NOT SURVIVE')
                os.environ['KOTLINC']=str(Path(tmp)/'compiler-does-not-exist')
                with self.assertRaises(FileNotFoundError):runner.main()
                self.assertFalse((out/'result.txt').exists())
        finally:
            runner.ROOT=old
            if compiler is None:os.environ.pop('KOTLINC',None)
            else:os.environ['KOTLINC']=compiler
