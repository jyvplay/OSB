#!/usr/bin/env python3
"""Executable closed-vocabulary promotion policy; not prose understanding or receipt authentication."""
import argparse,json
from r18_evidence_gate import assess as baseline
STORE='dev.fusion.shell.R19TaskStoreDeviceTest.'
TRANSFER='dev.fusion.shell.R19TaskTransferDeviceTest.'
UI='dev.fusion.shell.R19TaskUiDeviceTest.'
REQUIRED={
 'task_edit_undo_native':{STORE+'editUndoReopensAtNewRevisionAndReplayDoesNotWrite',STORE+'undoHeadroomFailureLeavesBytesAndLegacyRestoreBoundaryUsable',UI+'editUndoUsesRealTouchAndRecreationDoesNotInventAnUndoToken'},
 'task_transfer_native':{STORE+'staleMergeAndSameRevisionDifferentDataRefuseWithoutWriting',STORE+'fullArchivedImportIgnoresForeignRevisionAndCanBeUndone',TRANSFER+'actualProviderSnapshotWriteReadbackImportAndCorruptionRefusal',TRANSFER+'actualProviderTwoSheetReportIsLiteralAndNotATaskImport',UI+'actualSystemCreateDocumentPickerCanBeCancelledWithoutTaskMutation',UI+'actualPickerImportConfirmExportAndUndoPreserveSource'},
 'shared_memory_spike':{'dev.fusion.shell.R19SharedMemoryDeviceTest.protectionRestrictsFutureMapsButDoesNotRevokeExistingWriter'}
}
def assess(receipt,candidate,claim):
    if claim not in REQUIRED:return False,'No R19 evidence bridge for this broader/different claim'
    valid,reason=baseline(receipt,candidate,'tasks_native_subset')
    if not valid:return valid,reason
    cases={c['case'] for c in receipt['cases']}
    if not REQUIRED[claim]<=cases:return False,'Missing required R19 executed cases; earlier native passes do not substitute'
    return True,'Named exact-candidate API34 experiment only; not all inputs/providers/devices, UI stability, full Office, authenticated data or sealed-memory proof'
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('receipt');p.add_argument('--candidate',required=True);p.add_argument('--claim',required=True);a=p.parse_args()
    allowed,reason=assess(json.load(open(a.receipt)),a.candidate,a.claim)
    print(json.dumps(dict(claim=a.claim,candidate=a.candidate,allowed=allowed,reason=reason)))
    raise SystemExit(0 if allowed else 1)
if __name__=='__main__':main()
