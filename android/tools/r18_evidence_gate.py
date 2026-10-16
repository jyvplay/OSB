#!/usr/bin/env python3
"""Fail-closed typed promotion policy, NOT a natural-language truth/proof/authentication oracle.
Only exact-candidate, wholly green, explicitly named API34 subsets are admissible.
Receipts must come from collect_native_evidence.py; this offline tool cannot authenticate them.
"""
import argparse,json,re
TASKS={
 'dev.fusion.shell.R18TaskDeviceTest.'+n for n in [
 'legacyLoadAndNoOpPreserveBytesButExplicitEditWritesVersionTwo',
 'archiveSurvivesReopenRestorePreservesIdentityAndStaleEditCannotWin',
 'persistedRevisionReserveCannotBeConsumedByAnUnrelatedEdit',
 'allArchivedStillCountTowardCapacityAndRestoreNeedsNoFreeSlot']
}|{'dev.fusion.shell.R18TaskUiDeviceTest.editCancelArchiveRecreateRestoreAndQueryThroughRealShell'}
OWNED={'dev.fusion.shell.R18OwnedSheetDeviceTest.'+n for n in [
 'actualUnlinkedReadOnlyDescriptorExecutesInChildAndLeavesNoNamedInput',
 'refusedOwnedCycleCleansNamedInputAndClosesLease']}
SUPPORTED={'tasks_native_subset':TASKS,'owned_sheet_native_subset':OWNED}
def assess(receipt,candidate,claim):
    if claim not in SUPPORTED:return False,'No admissible bridge for this claim in R18'
    if not re.fullmatch('[0-9a-f]{40}',candidate) or receipt.get('candidate')!=candidate:
        return False,'Different or invalid source candidate; a repaired candidate inherits no pass'
    run=receipt.get('run')
    if type(run) is not int or run<=0 or receipt.get('url')!=f'https://github.com/jyvplay/OSB/actions/runs/{run}':
        return False,'Missing/inconsistent run locator (locator is not authentication)'
    if receipt.get('conclusion')!='success' or receipt.get('device_conclusion')!='success':
        return False,'Queued, partial, failed or absent whole native gate'
    cases=receipt.get('cases')
    if not isinstance(cases,list):return False,'No named executed cases'
    seen=set();counts={k:0 for k in ('PASS','FAIL','SKIP')}
    for item in cases:
        if not isinstance(item,dict):return False,'Malformed case'
        name=item.get('case');result=item.get('result')
        if not isinstance(name,str) or name in seen or result not in counts:return False,'Malformed/duplicate case'
        seen.add(name);counts[result]+=1
    reported=receipt.get('counts')
    if not isinstance(reported,dict) or any(type(v) is not int or v<0 for v in reported.values()) or reported!=counts:
        return False,'Counts do not match the named outcomes'
    if counts['FAIL'] or counts['SKIP']:return False,'Failed/skipped cases cannot promote a green gate'
    if not SUPPORTED[claim]<=seen:return False,'Required executed cases missing; source/build/other tests are not a substitute'
    return True,'Only this named API34 subset at this exact candidate; no full-product, physical, legal, AX or universal claim'
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('receipt');p.add_argument('--candidate',required=True);p.add_argument('--claim',required=True);a=p.parse_args()
    allowed,reason=assess(json.load(open(a.receipt)),a.candidate,a.claim)
    print(json.dumps(dict(claim=a.claim,candidate=a.candidate,allowed=allowed,reason=reason)))
    raise SystemExit(0 if allowed else 1)
if __name__=='__main__':main()
