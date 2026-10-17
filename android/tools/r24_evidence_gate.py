#!/usr/bin/env python3
"""Exact named R24 metadata admission. NOT execution authenticity, full spec or hardware/AX proof."""
import argparse
import json
import re
from pathlib import Path
from r23_evidence_gate import RULES as PREVIOUS
P='dev.fusion.shell.'
UI={P+'R24AppearanceDeviceTest.'+n for n in [
 'bothPalettesAndLayoutsRenderPixelsAndPersistThroughRecreation',
 'settingsAndControlPanelShareOneStoreWithoutLosingDirtyEditorOrGates',
 'realMouseMagnifiesDrawingNotHitboxAndMotionOffRestoresBaseSize',
 'invalidAppearancePatchAndCorruptPreferenceTypeCannotPartiallyApplyOrResetData','densityAndSearchRemainReachableAtActualTwoHundredPercentFont']}
UI|={P+'R24TaskInputDeviceTest.'+n for n in ['oneActualImeVisibleAddInBusinessWritesExactlyOneRevision','oneActualImeVisibleAddInArtWritesExactlyOneRevision','oneNativeImeVisibleNewNoteTouchRequestsDiscardWithoutErasingDraft']}
UI|={P+'R18TaskUiDeviceTest.editCancelArchiveRecreateRestoreAndQueryThroughRealShell'}
PDF={P+'R24PdfDeviceTest.'+n for n in ['genuinePdfRendersInDistinctUidWithReadonlyCapabilityAndBoundedRawPixels',
 'malformedOversizeWrongPageAndMemoryCarrierRefuseThenFreshPdfLeaseRecovers','actualPickerIsolatedPreviewNextPageAndRecreationNeverWriteOriginalPdf']}
SCREEN=PREVIOUS['r23_privacy_api34']|{'dev.fusion.privacy.R24ScreenOffDeviceTest.'+n for n in [
 'realScreenOffDiscardsRevealedPadAndOldButtonCannotEnterOnWake','ordinaryUidCannotSpoofProtectedScreenOffBroadcast']}
RULES={'r24_desktop_api34':UI,'r24_pdf_boundary_api34':PREVIOUS['r23_memory_transport_api34']|PDF,'r24_screenoff_api34':SCREEN}

def assess(data,candidate,claim):
    if not isinstance(data,dict) or not isinstance(candidate,str) or not re.fullmatch('[0-9a-f]{40}',candidate):return False,'Malformed receipt/candidate'
    if claim not in RULES:return False,'No bridge for this broader/different claim'
    if data.get('candidate')!=candidate:return False,'Wrong exact candidate'
    if data.get('conclusion')!='success' or data.get('device_conclusion')!='success':return False,'Whole workflow/device gate is not successful'
    allowed={'privacy'} if claim=='r24_screenoff_api34' else {'focused','full'}
    if data.get('suite') not in allowed:return False,'Wrong suite context'
    cases=data.get('cases');counts=data.get('counts')
    if not isinstance(cases,list) or not isinstance(counts,dict) or set(counts)!={'PASS','FAIL','SKIP'} or any(type(v)is not int or v<0 for v in counts.values()):return False,'Malformed cases/counts'
    names=[]
    for row in cases:
        if not isinstance(row,dict) or not isinstance(row.get('case'),str) or row.get('result') not in ['PASS','FAIL','SKIP']:return False,'Malformed named result'
        names.append(row['case'])
    if len(set(names))!=len(names):return False,'Duplicate named cases'
    actual={k:sum(r['result']==k for r in cases) for k in counts}
    if actual!=counts or counts['FAIL'] or counts['SKIP']:return False,'Missing/failed/skipped or inconsistent cases'
    if not RULES[claim]<=set(names):return False,'Required new attack/regression names missing'
    return True,'Named exact-candidate API34 subset ONLY, not all-spec/AX/physical/host/firmware/anonymity acceptance'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('receipt',type=Path);p.add_argument('--candidate',required=True);p.add_argument('--claim',required=True);a=p.parse_args()
    try:data=json.loads(a.receipt.read_text());allowed,reason=assess(data,a.candidate,a.claim)
    except (OSError,ValueError,TypeError):allowed,reason=False,'Unreadable receipt'
    print(json.dumps({'allowed':allowed,'reason':reason,'candidate':a.candidate,'claim':a.claim}));raise SystemExit(0 if allowed else 1)
if __name__=='__main__':main()
