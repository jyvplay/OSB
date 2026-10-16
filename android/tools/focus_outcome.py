"""Classify a historical experiment, not the candidate regression gate."""
def classify(exit_code, selected):
    if len(selected)!=1: return 'INVALID_EXPERIMENT','Expected exactly one selected case'
    c=selected[0]
    if c.find('skipped') is not None or c.find('error') is not None:return 'INVALID_EXPERIMENT','Skipped/error case'
    failures=c.findall('failure')
    detail='\n'.join(f.get('message','')+'\n'+(f.text or '') for f in failures)
    if exit_code==0 and not failures:return 'NOT_REPRODUCED','Prior-source test passed; no stable causal counterexample established'
    if exit_code!=0 and len(failures)==1 and 'Focused' in detail:return 'REPRODUCED',detail
    return 'INVALID_EXPERIMENT',detail or 'Build/instrumentation failure without the expected assertion'
