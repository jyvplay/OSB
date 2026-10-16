#!/usr/bin/env python3
"""Exact-candidate named-experiment admission. Offline metadata checks are NOT authentication or semantic sufficiency proofs."""
import argparse,json,re
P='dev.fusion.privacy.';A='dev.fusion.shell.'
BOUNDARY={A+'R21BoundaryDeviceTest.'+n for n in ['csvGrammarRunsInDistinctUidAndOwnedInputIsUnlinked','malformedCsvRefusesAndCleansUpThenFreshChildSucceeds','consentCannotSurviveSameUrlOrUnsupportedNavigationOrReplay','exactByteCapAndDenseEmptyTableCrossRealBinder','literalWorksheetBeyondTaskBudgetWritesAndReopensRealProvider']}
UI={A+'R21CsvUiDeviceTest.'+n for n in ['actualSelectionEditInvalidatesPreviewAndSystemSaveReadsBack','recreationLosesDraftAndCannotExportOldPreview','inFlightEditorMutationDiscardsStaleWorkerResult']}
RULES={
 'csv_worker_spike_api34':('focused',{A+'R21BoundaryDeviceTest.csvGrammarRunsInDistinctUidAndOwnedInputIsUnlinked',A+'R21BoundaryDeviceTest.malformedCsvRefusesAndCleansUpThenFreshChildSucceeds'}),
 'r21_workbench_api34':('focused',BOUNDARY|UI),
 'r21_full_api34':('full',BOUNDARY|UI)}
UNSUPPORTED=['global_anonymity','Tails_equivalence','lighter_than_Tails','host_hidden','IME_hidden','RAM_erasure','disk_erasure','Tor_installed','VPN_is_anonymity','all_apps_offline','full_Office','full_browser','LOK_license_cleared','browser_engine_wired','formula_execution_tested_in_Excel','digest_proves_honest_worker','provider_durable','physical_acceptance','AX_accepted','all_modules_complete']

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
 return True,'Only named exact-candidate R21 managed API34 experiment; not anonymity, full Office/browser, provider durability, physical/AX or product acceptance'
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('receipt');p.add_argument('--candidate',required=True);p.add_argument('--claim',required=True);a=p.parse_args()
 allowed,reason=assess(json.load(open(a.receipt)),a.candidate,a.claim);print(json.dumps(dict(allowed=allowed,reason=reason,claim=a.claim,candidate=a.candidate)));raise SystemExit(0 if allowed else 1)
if __name__=='__main__':main()
