#!/usr/bin/env python3
"""SMT checks of explicitly stated integer models, NOT verification of Kotlin/Android execution."""
import hashlib,json
from pathlib import Path
import z3
ROOT=Path(__file__).resolve().parents[1];results=[]
def prove(name,assumptions,bad,hypotheses):
    solver=z3.Solver();solver.set(timeout=10000);solver.add(*assumptions)
    feasible=solver.check();assert feasible==z3.sat,'Vacuous/inconclusive assumptions'
    witness=str(solver.model());solver.add(bad)
    answer=solver.check()
    results.append({'model':name,'result':str(answer),'hypotheses':hypotheses,'assumptions_result':str(feasible),'assumption_witness':witness,'smt2':solver.to_smt2()})
    if answer!=z3.unsat:raise AssertionError((name,answer,solver.model() if answer==z3.sat else 'unknown'))
M=2**63-1
start,forward,back,processing,offset=z3.Ints('start forward back processing offset')
t0=start;t1=start+forward+offset;t2=t1+processing;t3=start+forward+processing+back
lower=t2-t3;upper=t1-t0;rtt=(t3-t0)-(t2-t1);mid=lower+rtt/2
assumptions=[forward>=0,back>=0,processing>=0]+[v>=0 for v in [t0,t1,t2,t3]]+[v<=M for v in [t0,t1,t2,t3]]
prove('causal equal-rate clock interval and representable midpoint',assumptions,
 z3.Or(offset<lower,offset>upper,upper-lower!=rtt,rtt<0,rtt>M,mid<lower,mid>upper,mid < -2**63,mid>M),
 'Exact integer clock readings, equal rates/constant offset, nonnegative path/processing delays, all timestamps0..Long.MAX. Not oscillator/drift/quantization or dishonest-peer verification.')
r,expected=z3.Ints('revision expected')
prove('two serialized successful mutations cannot share one expected revision',
 [r>=0,r<M,expected==r],z3.And(expected==r,expected==r+1),
 'First successful mutation publishes r+1 under a process-global lock before second comparison; no rollback/external writer. Does not prove locking or persistence implementation.')
u=z3.Int('scalar');units=z3.If(u<=0xffff,1,2);encoded=z3.If(u<=0x7f,1,z3.If(u<=0x7ff,2,z3.If(u<=0xffff,3,4)))
prove('UTF8 scalar bytes <= three times UTF16 units',
 [u>=0,u<=0x10ffff,z3.Or(u<0xd800,u>0xdfff)],encoded>3*units,
 'Explicit standard UTF8/UTF16 scalar-width model; rejects unpaired surrogates. Not proof of a platform encoder.')
n=z3.Int('task_count')
prove('max canonical task snapshot <=104340 bytes <128KiB',
 [n>=0,n<=128],z3.Or(20+n*(36+1+2+3*256+8)>104340,20+n*(36+1+2+3*256+8)>128*1024),
 'Count<=128, title<=256 UTF16 units and preceding scalar-width bound;20 total header/CRC bytes and47 per-record fixed bytes. Not process RSS/latency.')
attack=z3.Solver();attack.set(timeout=10000);attack.add(*assumptions,mid!=offset)
assert attack.check()==z3.sat
m=attack.model()
bias={k:str(m.eval(v)) for k,v in [('t0',t0),('t1',t1),('t2',t2),('t3',t3),('offset',offset),('lower',lower),('upper',upper),('midpoint',mid)]}
artifact={'midpoint_counterexample':{'result':'sat','values':bias,'scope':'Mathematical asymmetric-delay model, NOT a measured network'},'solver' :z3.get_version_string(),'source_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
 'claims':'UNSAT means no counterexample in these mathematical models. NOT a Kotlin, JCA, Android, AtomicFile or cryptographic proof.', 'results':results}
(ROOT/'docs/verification/r17-smt-models.json').write_text(json.dumps(artifact,indent=2)+'\n')
print('R17: four conditional integer models UNSAT for counterexamples, Z3 '+z3.get_version_string())
