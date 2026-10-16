#!/usr/bin/env python3
"""Small conditional models, not Kotlin/CSV/Android refinement or anonymity proofs."""
import hashlib,json
from pathlib import Path
import z3
ROOT=Path(__file__).resolve().parents[1];results=[]
def prove(name,premises,bad):
 s=z3.Solver();s.set(timeout=5000);s.add(*premises);assert s.check()==z3.sat
 s.add(bad);assert s.check()==z3.unsat;results.append({'name':name,'premises':'sat','counterexample':'unsat'})
def witness(name,premises):
 s=z3.Solver();s.set(timeout=5000);s.add(*premises);assert s.check()==z3.sat
 results.append({'name':name,'result':'sat','witness':str(s.model())})
old,current=z3.Ints('old_document current_document');allow,present=z3.Bools('allow pending')
prove('Changed document cannot accept old prompt',[old!=current],z3.And(allow,present,old==current))
n,c=z3.Ints('source_utf8_bytes cells')
prove('Conditional decoded bytes and frame overhead fit cap',[n>=0,n<=65536,c>=0,c<=8192],n+4*c+12>131072)
a,b=[z3.Array(name,z3.IntSort(),z3.IntSort()) for name in ['left','right']];i,j=z3.Ints('first_column second_column')
prove('Two-column projection equality',[i>=0,i<32,j>=0,j<32,i!=j,a[i]==b[i],a[j]==b[j]],z3.Or(a[i]!=b[i],a[j]!=b[j]))
witness('Equal selected values do not imply equal source',[i==0,j==1,a[i]==b[i],a[j]!=b[j]])
identity,selected=z3.Bools('identity_in_selected_cell column_selected')
witness('Column selection is not anonymity',[identity,selected])
paths=[Path(__file__),ROOT/'format/ooxml/src/main/kotlin/dev/fusion/ooxml/CsvTable.kt',ROOT/'feature/browser/src/main/kotlin/dev/fusion/browser/model/BrowserConsentSession.kt']
r={'z3':z3.get_version_string(),'results':results,'scope':__doc__+' Frame bound assumes decoded payload bytes <= source bytes; solver does not prove parser lemma. Projection model has two symbolic columns, not ZIP encoding.','sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}}
(ROOT/'docs/verification/r21-models.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r))
