#!/usr/bin/env python3
"""Exact named R25 native gates. Metadata consistency, not an attestation or full-spec verdict."""
import argparse
import json
import re
from pathlib import Path
from r24c_evidence_gate import expected as retained

READINESS_CASE = 'dev.fusion.shell.R25UiReadinessDeviceTest.actualAsleepAvdIsPreparedBeforeNativeFindTouch'
READINESS = {READINESS_CASE,
    'dev.fusion.shell.RealImeDeviceTest.realImeAndLongEditorTextAtTwoHundredPercentWithoutComposeInputInjection',
    'dev.fusion.shell.ShellSmokeTest.editorActionsRemainVisibleAfterLongInputAtTwoHundredPercentFont',
    *{'dev.fusion.shell.R24TaskInputDeviceTest.' + n for n in (
        'oneActualImeVisibleAddInBusinessWritesExactlyOneRevision',
        'oneActualImeVisibleAddInArtWritesExactlyOneRevision',
        'oneNativeImeVisibleNewNoteTouchRequestsDiscardWithoutErasingDraft')}}


PNG_CASES = {'dev.fusion.shell.R25PngCopyDeviceTest.' + name for name in ('isolatedCopyRejectsCorruptionThenPreservesActualDecodedPixels', 'actualPngPickerNewCopyReadbackKeepsSourceAndRemovesMetadata', 'recreationCannotExportOldPreparedCopyAndPickerCancelDoesNotRead')}

VIDEO_CASE = 'dev.fusion.shell.R25LocalVideoDeviceTest.actualMp4DecoderProducesOrderedChangingSurfacePixelsAndReleasesOnHome'
MEDIA_CASES = {VIDEO_CASE} | {name for name in retained('continuation') if '.R24LocalMediaDeviceTest.' in name}

WEB_CASE = 'dev.fusion.shell.R25WebCapabilityDeviceTest.actualControlledWebViewReportsThreadedWasmPrerequisitesWithoutClaimingOffice'

def expected(suite):
    if suite == 'readiness': return READINESS
    if suite == 'full': return retained('full') | {READINESS_CASE, VIDEO_CASE, WEB_CASE} | PNG_CASES
    if suite == 'png': return PNG_CASES
    if suite == 'media': return MEDIA_CASES
    if suite == 'web': return {WEB_CASE}
    return retained(suite)


def verify(receipt, candidate, suite):
    if not re.fullmatch('[0-9a-f]{40}', candidate) or receipt.get('candidate') != candidate: raise ValueError('Wrong exact candidate')
    if receipt.get('conclusion') != 'success' or receipt.get('device_conclusion') != 'success': raise ValueError('Workflow/device did not succeed')
    if receipt.get('suite') != ('focused' if suite in ('readiness','png','media','web') else suite): raise ValueError('Wrong actual suite')
    if receipt.get('platform_api') not in ((34, 37) if suite == 'privacy' else (34,)): raise ValueError('Wrong actual runtime')
    rows = receipt.get('cases', []); names = [r['case'] for r in rows]
    if len(set(names)) != len(names) or set(names) != expected(suite): raise ValueError('Missing/duplicate/undeclared named method')
    if any(r.get('result') != 'PASS' for r in rows): raise ValueError('Failure/skip is not acceptance')
    if receipt.get('counts') != {'PASS': len(rows), 'FAIL': 0, 'SKIP': 0}: raise ValueError('Counts disagree')
    return {'candidate': candidate, 'suite': suite, 'api': receipt['platform_api'], 'passed_cases': len(rows),
            'scope': 'Exact named native emulator subset ONLY. Not full original spec, physical, all-page visual, hardware privacy or legal acceptance.'}


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('receipt', type=Path)
    p.add_argument('--candidate', required=True); p.add_argument('--suite', choices=('full','readiness','png','media','web','continuation','privacy'), required=True)
    a = p.parse_args(); print(json.dumps(verify(json.loads(a.receipt.read_text()), a.candidate, a.suite), indent=2))


if __name__ == '__main__': main()
