"""Host release-record adversaries. No Android/device/license acceptance inferred."""
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import requirements
import release_gate
import module_audit


class ReleaseGateChecks(unittest.TestCase):
    def test_p1_only_flags_do_not_authorize_release(self):
        root = requirements.ROOT
        rows = json.loads((root/'docs/feature-register.json').read_text())
        spikes = json.loads((root/'docs/spikes.json').read_text())
        audit = json.loads((root/'docs/module-audit.json').read_text())
        for row in rows:
            if 'P1' in row['phase']:
                row['status'] = 'verified'
        for spike in spikes:
            spike['status'] = 'passed'
            spike['evidence'] = ['nonexistent-evidence']
        with self.assertRaisesRegex(ValueError, 'Every in-scope requirement'):
            release_gate.verify(root, rows, spikes, audit, module_audit.included_modules(root))
        for row in rows:
            row['status'] = release_gate.EXCEPTIONS.get(row['id'], 'verified')
        with self.assertRaisesRegex(ValueError, 'common gates'):
            release_gate.verify(root, rows, spikes, audit, module_audit.included_modules(root))

    def test_truthy_evidence_missing_and_outside_paths_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            for value in (True, 'approved', {}, {'path':'../outside', 'sha256':'0'*64},
                          {'path':'docs/release-artifacts/missing.txt', 'sha256':'0'*64}):
                with self.assertRaises(ValueError):
                    release_gate.artifact(Path(temporary), value)

    def test_real_file_digest_tampering_and_symlink(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root/'docs/release-artifacts'
            directory.mkdir(parents=True)
            file = directory/'input.txt'
            data = b'Parser test input only; not a release report.'
            file.write_bytes(data)
            reference = {'path': 'docs/release-artifacts/input.txt', 'sha256': hashlib.sha256(data).hexdigest()}
            self.assertEqual(data, release_gate.artifact(root, reference))
            file.write_bytes(data+b'changed')
            with self.assertRaisesRegex(ValueError, 'digest mismatch'):
                release_gate.artifact(root, reference)
            link = directory/'link.txt'
            try:
                link.symlink_to(file)
            except (OSError, NotImplementedError):
                return  # Digest checks executed; this host did not support the symlink subcase.
            with self.assertRaisesRegex(ValueError, 'Symlink'):
                release_gate.artifact(root, {**reference, 'path':'docs/release-artifacts/link.txt'})

    def test_source_binding_without_attestation_hash_cycle(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root/'docs').mkdir()
            (root/'source.kt').write_text('original source')
            audit = {'modules':[{'status':'incomplete','reviewed_acceptance_record':{'sha256':'a'}}]}
            path = root/'docs/module-audit.json'
            path.write_text(json.dumps(audit))
            before = release_gate.source_fingerprint(root)
            audit['modules'][0]['reviewed_acceptance_record'] = {'sha256':'b'}
            path.write_text(json.dumps(audit))
            self.assertEqual(before, release_gate.source_fingerprint(root))
            changed = copy.deepcopy(audit)
            changed['modules'][0]['status'] = 'complete'
            path.write_text(json.dumps(changed))
            self.assertNotEqual(before, release_gate.source_fingerprint(root))
            path.write_text(json.dumps(audit))
            (root/'source.kt').write_text('changed source')
            self.assertNotEqual(before, release_gate.source_fingerprint(root))

    def test_traceability_validation_survives_python_optimization(self):
        result = subprocess.run([sys.executable, '-O', '-c', 'import requirements; requirements.validate([], [])'],
                                cwd=Path(__file__).resolve().parent, capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Register differs from normalized owner tables', result.stderr)
