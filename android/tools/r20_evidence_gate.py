#!/usr/bin/env python3
"""Exact-candidate named-experiment admission. Offline metadata checks are NOT authentication or semantic sufficiency proofs."""
import argparse,json,re
P='dev.fusion.privacy.';A='dev.fusion.shell.'
RULES={
 'offline_workspace_api34':('privacy',{P+'PrivacyPlatformTest.noInternetPermissionDeniesInetSocketsButUnixPairTransfersRealBytes',P+'PrivacyPlatformTest.secureWindowExcludesActualScreenshotMarkerWithVisiblePositiveControl',P+'PrivacyWorkspaceTest.actualTypingDiscardCannotRestoreOldEditorUndoAndRecreationStartsEmpty',P+'PrivacyWorkspaceTest.pauseDropsBothFieldsAndDisablesSavedHierarchyAndClipboardActions',P+'PrivacyWorkspaceTest.externalExtrasCannotDisableProtectionOrInjectDraftAndProjectionIsOffline',P+'PrivacyWorkspaceTest.richImeContentIsRefusedAndIncomingExecutableUrlSpansAreStripped'}),
 'counts_export_api34':('full',{A+'R20DisclosureDeviceTest.countsProjectionWritesAndReopensRealProviderWithoutIdentifiers',A+'R20CountsUiDeviceTest.actualCountsPreviewAndSystemSaveDoNotMutatePrivateTasks',A+'R19TaskUiDeviceTest.actualPickerImportConfirmExportAndUndoPreserveSource'}),
 'origin_lease_api34':('full',{A+'R20DisclosureDeviceTest.permissionLeaseUsesActualElapsedClockAndBurnsWrongContext',A+'R20DisclosureDeviceTest.linkProjectionAgreesWithAndroidUriAndRetainsPathIdentityCounterexample'})}
UNSUPPORTED=['Tails_equivalence','lighter_than_Tails','global_anonymity','all_apps_offline','IME_cannot_read','accessibility_cannot_read','no_OS_traces','RAM_secure_erase','flash_secure_erase','Tor_installed','VPN_is_anonymity','all_IPC_blocked','all_screenshots_blocked','counts_are_anonymous','link_path_is_anonymous','full_Office','complete_browser','browser_permission_UI_wired','physical_acceptance','full_modules']
def assess(r,candidate,claim):
 if claim not in RULES:return False,'No evidence bridge for this broader/different claim'
 if not re.fullmatch('[0-9a-f]{40}',candidate) or r.get('candidate')!=candidate:return False,'Wrong or invalid exact candidate'
 suite,required=RULES[claim]
 if r.get('suite')!=suite:return False,'Wrong suite; companion and main app are different APKs'
 run=r.get('run')
 if type(run) is not int or run<=0 or r.get('url')!=f'https://github.com/jyvplay/OSB/actions/runs/{run}':return False,'Invalid run locator'
 if r.get('conclusion')!='success' or r.get('device_conclusion')!='success':return False,'Whole gate not successful'
 cases=r.get('cases');counts={k:0 for k in ['PASS','FAIL','SKIP']};seen=set()
 if not isinstance(cases,list):return False,'Missing cases'
 for c in cases:
  if not isinstance(c,dict) or not isinstance(c.get('case'),str) or c['case'] in seen or c.get('result') not in counts:return False,'Malformed/duplicate cases'
  seen.add(c['case']);counts[c['result']]+=1
 if not isinstance(r.get('counts'),dict) or any(type(v) is not int for v in r['counts'].values()) or r['counts']!=counts:return False,'Count mismatch'
 if counts['FAIL'] or counts['SKIP'] or not required<=seen:return False,'Missing, failed or skipped cases'
 return True,'Only named exact-candidate managed API34 experiment; not anonymity, OS/IME secrecy, all providers/devices or full product acceptance'
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('receipt');p.add_argument('--candidate',required=True);p.add_argument('--claim',required=True);a=p.parse_args()
 allowed,reason=assess(json.load(open(a.receipt)),a.candidate,a.claim);print(json.dumps(dict(allowed=allowed,reason=reason,claim=a.claim,candidate=a.candidate)));raise SystemExit(0 if allowed else 1)
if __name__=='__main__':main()
