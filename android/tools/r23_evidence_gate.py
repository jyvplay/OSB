#!/usr/bin/env python3
"""Exact-candidate R23 receipt admission, NOT receipt authentication or a general security verifier."""
import argparse
import json
import re

PREFIX = 'dev.fusion.privacy.'
OLD = {PREFIX + 'PrivacyWorkspaceTest.' + name for name in [
    'actualTypingDiscardCannotRestoreOldEditorUndoAndRecreationStartsEmpty',
    'pauseDropsBothFieldsAndDisablesSavedHierarchyAndClipboardActions',
    'externalExtrasCannotDisableProtectionOrInjectDraftAndProjectionIsOffline',
    'richImeContentIsRefusedAndIncomingExecutableUrlSpansAreStripped'
]} | {PREFIX + 'PrivacyPlatformTest.' + name for name in [
    'noInternetPermissionDeniesInetSocketsButUnixPairTransfersRealBytes',
    'secureWindowExcludesActualScreenshotMarkerWithVisiblePositiveControl'
]}
INPUT = {PREFIX + 'R23InputDefenseTest.' + name for name in [
    'realTouchEntryHasNoInputConnectionAndHardwareKeysCannotMutateCode',
    'fullAndPartialObscurationDiscardRealEditorsAndDoNotResumeOnCleanUp',
    'detachedKeyCannotMutateNewPadAndNewPadEnforcesAtomicCapacity',
    'realFocusableDialogDiscardsPadBeforePauseAndFocusReturnStartsEmpty',
    'actualMonotonicIdleDeadlineDiscardsWithoutFurtherInteraction',
    'realExposureSnapshotAndMergedManifestKeepScopeAndNewScreensPrivate'
]}
MEMORY = {PREFIX + 'R23MemoryBoundaryTest.existingWritableMapSurvivesRestrictionButUnmappedReadOnlyParcelRefusesNewWrites'}
REVEAL = {PREFIX + 'R23InputDefenseTest.actualRevealDeadlineRehidesAndNextInputMasksImmediately'}
APP = 'dev.fusion.shell.'
from r22_evidence_gate import BOUNDARY as R22_BOUNDARY, PLATFORM as R22_PLATFORM, UI as R22_UI
TRANSPORT = {APP + 'R23MemoryTransportDeviceTest.' + name for name in [
    'memoryCallsRunInDistinctUidAndLiveObserverSeesNoNamedOwnedInput',
    'exactCsvCapMalformedAndOversizeRefuseThenFreshMemoryLeaseSucceeds',
    'realWireRefusesWritableMismatchedAmbiguousUnsupportedAndEmptyPaddingInputs'
]} | {APP + 'FormatIsolationDeviceTest.' + name for name in [
    'privatePathUnavailableWhileExplicitCapabilityReadsSameFile',
    'distinctBindingsDoNotShareAnIsolatedProcess', 'idleWorkerIsKilledByActualWatchdog',
    'writablePipeOffsetAndOverBudgetInputsAreRefused', 'emptyInputIsNotMistakenForFailureAndLeasesAreSingleUse',
    'actualLoopbackConnectionIsDeniedWithReachablePositiveControl'
]} | {APP + 'R21CsvUiDeviceTest.' + name for name in [
    'actualSelectionEditInvalidatesPreviewAndSystemSaveReadsBack',
    'recreationLosesDraftAndCannotExportOldPreview', 'inFlightEditorMutationDiscardsStaleWorkerResult'
]} | {APP + 'R18OwnedSheetDeviceTest.' + name for name in [
    'actualUnlinkedReadOnlyDescriptorExecutesInChildAndLeavesNoNamedInput',
    'refusedOwnedCycleCleansNamedInputAndClosesLease'
]} | R22_BOUNDARY | R22_PLATFORM | R22_UI
RULES = {'r23_memory_transport_api34': TRANSPORT, 'r23_input_api34': OLD | INPUT, 'r23_memory_api34': MEMORY, 'r23_privacy_api34': OLD | INPUT | REVEAL | MEMORY}
UNSUPPORTED = ['global_anonymity', 'Tails_equivalence', 'lighter_than_Tails', 'hardware_keylogger_detected',
               'firmware_safe', 'host_hidden', 'all_IME_hidden', 'all_AX_blocked', 'RAM_erased', 'disk_erased',
               'all_USB_blocked', 'all_apps_offline', 'production_sharedmemory_transport', 'hostile_child_computation_verified',
               'all_parser_inputs_disk_free', 'hostile_parent_memory_immutable', 'retained_writers_revoked', 'no_swap',
               'full_browser', 'full_Office', 'physical_acceptance', 'AX_accepted', 'all_modules_complete']


def assess(receipt, candidate, claim):
    if not isinstance(claim, str) or claim not in RULES: return False, 'No evidence bridge for the broader/different claim'
    if not isinstance(receipt, dict): return False, 'Receipt must be an object'
    if not isinstance(candidate, str) or not re.fullmatch(r'[0-9a-f]{40}', candidate) or receipt.get('candidate') != candidate:
        return False, 'Wrong or invalid exact candidate'
    suites = {'focused', 'full'} if claim == 'r23_memory_transport_api34' else {'privacy'}
    suite = receipt.get('suite')
    if not isinstance(suite, str) or suite not in suites: return False, 'Wrong APK/suite'
    run = receipt.get('run')
    if type(run) is not int or run <= 0 or receipt.get('url') != f'https://github.com/jyvplay/OSB/actions/runs/{run}':
        return False, 'Invalid run locator'
    if receipt.get('conclusion') != 'success' or receipt.get('device_conclusion') != 'success':
        return False, 'Whole workflow/device gate is not successful'
    cases = receipt.get('cases'); counts = {key: 0 for key in ('PASS', 'FAIL', 'SKIP')}; seen = set()
    if not isinstance(cases, list): return False, 'Missing named cases'
    for case in cases:
        if not isinstance(case, dict) or not isinstance(case.get('case'), str) or case['case'] in seen or not isinstance(case.get('result'), str) or case['result'] not in counts:
            return False, 'Malformed or duplicate case'
        seen.add(case['case']); counts[case['result']] += 1
    actual = receipt.get('counts')
    if not isinstance(actual, dict) or any(type(v) is not int for v in actual.values()) or actual != counts:
        return False, 'Count mismatch'
    if counts['FAIL'] or counts['SKIP'] or not RULES[claim] <= seen:
        return False, 'Missing, failed or skipped required case'
    return True, 'Only named exact-candidate managed API34 experiment; not host/hardware/firmware secrecy, anonymity, all-device or full-product acceptance'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('receipt'); p.add_argument('--candidate', required=True); p.add_argument('--claim', required=True)
    a = p.parse_args()
    with open(a.receipt) as file: receipt = json.load(file)
    allowed, reason = assess(receipt, a.candidate, a.claim)
    print(json.dumps({'allowed': allowed, 'reason': reason, 'claim': a.claim, 'candidate': a.candidate}))
    raise SystemExit(0 if allowed else 1)


if __name__ == '__main__': main()
