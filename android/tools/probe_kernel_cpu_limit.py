#!/usr/bin/env python3
"""Real Linux kernel experiment, NOT an Android result or complete request-resource sandbox.
A bounded child deliberately ignores SIGXCPU; observe hard RLIMIT_CPU, then a control.
The syscall uses libc directly so Python validation cannot stand in for kernel refusal.
"""
import hashlib
import ctypes
import errno
import json
import os
from pathlib import Path
import resource
import signal
import subprocess
import sys
import time

if len(sys.argv)>1 and sys.argv[1]=='--child':
    class Limit(ctypes.Structure):
        _fields_=[('soft',ctypes.c_ulong),('hard',ctypes.c_ulong)]
    libc=ctypes.CDLL(None,use_errno=True)
    libc.setrlimit.argtypes=[ctypes.c_int,ctypes.POINTER(Limit)]
    libc.setrlimit.restype=ctypes.c_int
    mode=sys.argv[2]
    constrained=mode in ('hard','fork-reset')
    if constrained:
        if libc.setrlimit(resource.RLIMIT_CPU,ctypes.byref(Limit(1,2)))!=0: raise RuntimeError('Could not set CPU limit')
        ctypes.set_errno(0)
        raised=libc.setrlimit(resource.RLIMIT_CPU,ctypes.byref(Limit(1,3)))
        failure=ctypes.get_errno()
        if raised!=-1 or failure!=errno.EPERM: raise RuntimeError('Unprivileged hard-limit hypothesis not met')
        signal.signal(signal.SIGXCPU,signal.SIG_IGN)
        print(json.dumps({'uid':os.getuid(),'raise_return':raised,'raise_errno':failure,'soft_signal':'ignored','hard_cpu_seconds':2}),flush=True)
    child_pid=os.fork() if mode=='fork-reset' else None
    start=time.process_time()
    duration=1.25 if mode=='fork-reset' else 2.5
    while time.process_time()-start<duration:
        sum(i*i for i in range(1000))
    if child_pid is not None and child_pid>0:
        _,status=os.waitpid(child_pid,0)
        if os.waitstatus_to_exitcode(status)!=0: raise RuntimeError('Fork control failed')
    print(f'Completed {duration} CPU seconds; mode={mode}',flush=True)
    raise SystemExit(0)

if sys.platform!='linux': raise SystemExit('This experiment is Linux-specific, not a portable simulation')
report_path=Path(__file__).resolve().parents[1]/'docs/verification/r11b-kernel-cpu.json'
report_path.unlink(missing_ok=True)
results=[]
for mode in ('hard','control','fork-reset'):
    before=resource.getrusage(resource.RUSAGE_CHILDREN)
    start=time.monotonic()
    child=subprocess.Popen([sys.executable,__file__,'--child',mode],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        stdout,stderr=child.communicate(timeout=15)
    except subprocess.TimeoutExpired:
        child.kill();child.communicate()
        raise RuntimeError('Wall timeout: cleanup kill is NOT kernel-limit evidence')
    after=resource.getrusage(resource.RUSAGE_CHILDREN)
    cpu=(after.ru_utime+after.ru_stime)-(before.ru_utime+before.ru_stime)
    if mode=='hard':
        if not (child.returncode==-signal.SIGKILL and 1.8<=cpu<=3.0 and '"raise_errno": 1' in stdout): raise RuntimeError((child.returncode,cpu,stdout,stderr))
    else:
        if not (child.returncode==0 and 2.5<=cpu<=3.5): raise RuntimeError((child.returncode,cpu,stdout,stderr))
    results.append({'mode':mode,'exit_code':child.returncode,'cpu_seconds':cpu,'wall_seconds':time.monotonic()-start,'stdout':stdout,'stderr':stderr})
report={'status':'PASS','runtime':os.uname().release,'architecture':os.uname().machine,'source_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'results':results,
 'scope':'Observed native libc EPERM on raising hard CPU limit; real SIGKILL after ignored soft signal; unconstrained control completed. Linux host only, not theorem or Android acceptance.',
 'residual':'The fork-reset attack ACTUALLY completed more than2.5 aggregate CPU seconds under inherited2s per-process hard limits. Not a process-tree limit. Android fork denial/seccomp/SELinux/ABI/16K, existing mappings/RSS and native parser integration remain untested. Parent timeout kills never count as passes.'}
report_path.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
