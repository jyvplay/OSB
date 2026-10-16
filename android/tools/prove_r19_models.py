#!/usr/bin/env python3
"""Conditional models, not a proof of Kotlin/Android/provider execution."""
import hashlib,json
from pathlib import Path
import z3
ROOT=Path(__file__).resolve().parents[1];M=2**63-1;results=[]
def prove(name,assumptions,bad,hypotheses):
    s=z3.Solver();s.set(timeout=10000);s.add(*assumptions);feasible=s.check();assert feasible==z3.sat
    witness=str(s.model());s.add(bad);result=s.check();assert result==z3.unsat
    results.append(dict(name=name,assumptions_result=str(feasible),witness=witness,result=str(result),hypotheses=hypotheses,smt2=s.to_smt2()))
r,a,b=z3.Ints('revision archived_before archived_after')
valid=[r>=0,a>=0,a<=128,b>=0,b<=128,r+a<=M,r+1+b<=M,r+2+a<=M]
prove('admitted undoable transition has representable immediate undo',valid,
      z3.Or(r+2>M,r+2+a>M,r+2<=r+1),
      'Exact +1 commit and +1 undo, exact before/after data binding, archive counts unchanged by serialization. Does not verify code, IO or concurrency.')
expected=z3.Int('expected')
prove('serialized successful transition invalidates an old expected revision',[r>=0,r<M,expected==r],expected==r+1,
      'First publication completes r+1 under the same process lock before second comparison; no rollback/external writer. Content equality is separately tested.')
limit,total,n=z3.Ints('limit consumed read_count')
prove('bounded stream consumes at most limit plus one',[limit>=1,limit<=512*1024,total>=0,total<=limit,n>=1,n<=8192,n<=limit-total+1],total+n>limit+1,
      'InputStream obeys requested read length; each non-EOF read returns. Zero progress is refused. No wall-clock bound or guarantee a provider returns.')
old,new,old_a,new_a=z3.Ints('old_count added_count old_archived added_archived')
prove('append-only admitted merge keeps archive count within total capacity',
      [old>=0,new>=0,old+new<=128,old_a>=0,old_a<=old,new_a>=0,new_a<=new],
      z3.Or(old_a+new_a>128,old_a+new_a>old+new),
      'Unique IDs, identical IDs not added, conflicts refused; the model assumes the implementation computes these counts correctly.')
negative=[]
for name,conditions,scope in [
 ('forward-valid is not always undoable',[r==M-1,a==1,b==0,r+a<=M,r+1+b<=M,r+2+a>M],
  'Restore at the old reserved boundary remains available through the old API, but cannot promise an additional revision-consuming undo.'),
 ('per-sheet cell bounds do not imply a workbook cell bound',[old==15000,new==15000,old<=20000,new<=20000,old+new>20000],
  'Two independently valid sheet counts violate the aggregate workbook cap; new implementation checks the sum before copying.')]:
    s=z3.Solver();s.set(timeout=10000);s.add(*conditions);assert s.check()==z3.sat
    negative.append(dict(name=name,result='sat',witness=str(s.model()),smt2=s.to_smt2(),scope=scope))
sources=[Path(__file__)]+[ROOT/p for p in ['feature/automation/src/main/kotlin/dev/fusion/automation/TaskChanges.kt','feature/automation/src/main/kotlin/dev/fusion/automation/TaskStreams.kt','format/ooxml/src/main/kotlin/dev/fusion/ooxml/LiteralWorkbook.kt']]
artifact={'solver':z3.get_version_string(),'scope':'Four nonvacuous conditional UNSAT counterexample checks and two SAT boundary models. Not Kotlin, persistence, Office or full-system proof.',
 'sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},'positive':results,'negative':negative}
(ROOT/'docs/verification/r19-models.json').write_text(json.dumps(artifact,indent=2)+'\n')
print('R19:4 conditional models UNSAT with SAT premises;2 boundary models SAT. Z3 '+z3.get_version_string())
