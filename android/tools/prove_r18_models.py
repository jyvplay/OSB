#!/usr/bin/env python3
"""Conditional integer-model checks. Not verification of Kotlin, IO or Android execution."""
import hashlib,json,os,tempfile
from pathlib import Path
import z3
ROOT=Path(__file__).resolve().parents[1]
M=2**63-1
results=[]
def prove(name,assumptions,bad,hypotheses):
    s=z3.Solver();s.set(timeout=10000);s.add(*assumptions)
    feasible=s.check();assert feasible==z3.sat,'Vacuous or inconclusive premises'
    witness=str(s.model());s.add(bad);answer=s.check()
    results.append(dict(model=name,assumptions_result=str(feasible),assumption_witness=witness,result=str(answer),hypotheses=hypotheses,smt2=s.to_smt2()))
    assert answer==z3.unsat,(name,answer)
r,a,k=z3.Ints('revision archived restorations')
valid=[r>=0,a>=0,a<=128,r+a<=M]
prove('one restore is representable and preserves reserve',valid+[a>=1],
      z3.Or(r+1>M,r+1+(a-1)>M,a-1<0),
      'Existing archived ID; exact update (r,a)->(r+1,a-1). No failed IO, external writer, rollback or miscount.')
prove('every prefix of restoring all archived records remains valid',valid+[k>=0,k<=a],
      z3.Or(r+k>M,r+k+(a-k)>M,a-k<0),
      'Each restore consumes one distinct archived ID; no new archive between steps. IDs/data/implementation not modeled.')
prove('guarded nonarchive edit cannot steal restore reserve',valid+[r+a+1<=M,k>=0,k<=a],
      z3.Or(r+1+k>M,r+1+k+(a-k)>M),
      'Constructor guard enforces r+1+a<=MAX; ordinary edit leaves archive count unchanged.')
prove('archive requires two free revision positions',valid+[a<128,r+a+2<=M],
      z3.Or(r+1>M,r+1+(a+1)>M),
      'Existing active ID, exact archive update, explicit admission guard. This is algebra, not verification of constructor control flow.')
u=z3.Int('unicode_scalar');units=z3.If(u<=0xffff,1,2);encoded=z3.If(u<=0x7f,1,z3.If(u<=0x7ff,2,z3.If(u<=0xffff,3,4)))
prove('UTF8 bytes <= three times UTF16 units',[u>=0,u<=0x10ffff,z3.Or(u<0xd800,u>0xdfff)],encoded>3*units,
      'Explicit Unicode scalar-width model excluding surrogates; not platform encoder verification.')
n=z3.Int('records')
prove('FTS1 and FTS2 maximum canonical snapshot size',[n>=0,n<=128],
      z3.Or(20+n*(36+1+2+3*256+8)>104340,20+n*(36+1+2+3*256+8)>128*1024),
      'Fixed-width UUID/flags/date fields, 256 UTF16-unit titles and preceding scalar bound. Not heap/RSS/time bound.')
s=z3.Solver();s.add(r==M-1,a==0,r<M,r+1+(a+1)>M);assert s.check()==z3.sat
counterexample={'result':'sat','values':str(s.model()),'smt2':s.to_smt2(),'scope':'Old archive admission can succeed without enough revision space to restore. Mathematical witness; separately compiled Kotlin witness is retained separately.'}
# Actual Linux descriptor experiment, NOT an Android exploit of the production path.
# It exhibits why the private-owner/no-surviving-writer assumption is necessary.
out=ROOT/'testing/build/r18-posix';out.mkdir(parents=True,exist_ok=True)
rw,path=tempfile.mkstemp(dir=out);ro=None
try:
    os.write(rw,b'first');os.fsync(rw);ro=os.open(path,os.O_RDONLY);os.unlink(path)
    before=os.pread(ro,5,0);os.pwrite(rw,b'other',0);os.fsync(rw);after=os.pread(ro,5,0)
    assert before==b'first' and after==b'other' and os.fstat(ro).st_nlink==0
    alias={'before':before.decode(),'after':after.decode(),'link_count':os.fstat(ro).st_nlink,'scope':'Actual Linux IO: a preexisting writable alias defeats immutability despite unlink and read-only reader. Production closes its writer and assumes a trusted private owner; this is NOT a reproduced Android exploit.'}
finally:
    if ro is not None:os.close(ro)
    os.close(rw)
    if os.path.exists(path):os.unlink(path)
sources=[Path(__file__),ROOT/'feature/automation/src/main/kotlin/dev/fusion/automation/TaskBook.kt']
artifact={'solver':z3.get_version_string(),'claims':'Six conditional mathematical UNSAT counterexample checks, satisfiable premises, one SAT old-boundary witness. No Kotlin/AtomicFile/Android/cryptographic proof. Linux alias witness is separately labeled.',
          'sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},'results':results,'old_boundary':counterexample,'unlink_alias':alias}
(ROOT/'docs/verification/r18-models.json').write_text(json.dumps(artifact,indent=2)+'\n')
print('R18: 6 conditional models UNSAT; premises SAT; old boundary SAT; actual Linux writable-alias counterexample. Z3 '+z3.get_version_string())
