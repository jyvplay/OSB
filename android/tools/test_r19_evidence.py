"""Offline policy tests on retained actual receipts and explicitly corrupted copies. No new native execution."""
import copy,json,unittest
from pathlib import Path
from r19_evidence_gate import assess
ROOT=Path(__file__).resolve().parents[1]
class R19EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.receipt=json.loads((ROOT/'docs/verification/r19-spike-result.json').read_text())
        self.candidate=self.receipt['candidate']
    def test_actual_spike_is_only_its_own_named_experiment(self):
        self.assertTrue(assess(self.receipt,self.candidate,'shared_memory_spike')[0])
        self.assertFalse(assess(self.receipt,self.candidate,'task_transfer_native')[0])
        self.assertFalse(assess(self.receipt,self.candidate,'task_edit_undo_native')[0])
    def test_modal_shortcuts_refused(self):
        for claim in ['full_modules','all_providers','persisted_undo','undo_revision_rollback','all_tasks_to_xlsx',
                      'Office_parity','SharedMemory_seals','UI_stability_from_retry','reminder_delivery',
                      'merge_overwrites_conflicts','downgrade_writer','all_IO_cancellable','power_loss_proof',
                      'all_devices','complete_AX','checksum_authentication','formal_code_proof','complete_browser']:
            self.assertFalse(assess(self.receipt,self.candidate,claim)[0],claim)
    def test_repaired_candidate_inherits_no_pass(self):
        self.assertFalse(assess(self.receipt,'b'*40,'shared_memory_spike')[0])
    def test_corrupted_receipt_refused(self):
        r=copy.deepcopy(self.receipt);r['counts']['PASS']+=1
        self.assertFalse(assess(r,self.candidate,'shared_memory_spike')[0])
        r=copy.deepcopy(self.receipt);r['device_conclusion']='failure'
        self.assertFalse(assess(r,self.candidate,'shared_memory_spike')[0])

    def test_actual_95_case_candidate_cannot_inherit_new_picker_workflow(self):
        r=json.loads((ROOT/'docs/verification/r19-native-result.json').read_text())
        self.assertTrue(assess(r,r['candidate'],'task_edit_undo_native')[0])
        self.assertTrue(assess(r,r['candidate'],'shared_memory_spike')[0])
        self.assertFalse(assess(r,r['candidate'],'task_transfer_native')[0])
