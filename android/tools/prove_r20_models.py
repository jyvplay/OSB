#!/usr/bin/env python3
"""Conditional integer/Boolean models only. Not Kotlin, OS, anonymity or secure-erasure proofs."""
import hashlib,json
from pathlib import Path
import z3
ROOT=Path(__file__).resolve().parents[1];results=[]
def prove(name,premises,bad):
 s=z3.Solver();s.set(timeout=5000);s.add(*premises);base=s.check();assert base==z3.sat,(name,base)
 s.add(bad);answer=s.check();assert answer==z3.unsat,(name,answer)
 results.append({'name':name,'premises':'sat','counterexample_query':'unsat'})
def witness(name,premises):
 s=z3.Solver();s.set(timeout=5000);s.add(*premises);assert s.check()==z3.sat
 results.append({'name':name,'result':'sat','witness':str(s.model())})
r,t,n=z3.Ints('issued lifetime now');m=2**63-1
p=[r>=0,t>=1,t<=60000,r<=m-t,n>=0,n<=m]
prove('admitted expiration does not overflow',p,z3.Or(r+t>m,r+t<0))
prove('half-open lease excludes deadline',p+[n>=r+t],z3.And(n>=r,n<r+t))
present=z3.Bool('present');first=z3.Bool('first');second=z3.Bool('second');after=z3.Bool('after')
prove('atomic one-shot cannot be reused',[present,first==present,after==z3.And(present,z3.Not(first)),second==after],z3.And(first,second))
a=z3.IntVector('left_counts',4);b=z3.IntVector('right_counts',4)
prove('count-vector projection noninterference',[*[v>=0 for v in a+b],z3.Sum(a)<=128,z3.Sum(b)<=128,*[a[i]==b[i] for i in range(4)]],z3.Or(*[a[i]!=b[i] for i in range(4)]))
witness('aggregates can distinguish sources',[a[0]==1,b[0]==2,*[a[i]==0 for i in range(1,4)],*[b[i]==0 for i in range(1,4)],z3.Sum(a)!=z3.Sum(b)])
pathIdentity,queryRemoved,anonymous=z3.Bools('identity_in_path query_removed anonymous')
witness('query removal does not imply anonymity',[pathIdentity,queryRemoved,anonymous==z3.Not(pathIdentity),z3.Not(anonymous)])
paths=[Path(__file__),ROOT/'feature/browser/src/main/kotlin/dev/fusion/browser/model/PermissionLeases.kt',ROOT/'feature/automation/src/main/kotlin/dev/fusion/automation/TaskCounts.kt',ROOT/'core/security/src/main/kotlin/dev/fusion/security/LinkDisclosure.kt']
data={'z3':z3.get_version_string(),'results':results,'scope':'Four nonvacuous conditional models and two countermodels, not implementation refinement, actual adversary coverage or anonymity proof. Count model does not model ZIP; actual byte noninterference separately checked.', 'sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}}
(ROOT/'docs/verification/r20-models.json').write_text(json.dumps(data,indent=2)+'\n');print(json.dumps(data))
