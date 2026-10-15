#!/usr/bin/env python3
"""REAL Linux x86_64 child-only seccomp/CPU experiment. NOT an Android/parser sandbox.
Does not filter this supervising process. No fixture can count as kernel evidence.
"""
import ctypes
import errno
import hashlib
import json
import os
from pathlib import Path
import resource
import signal
import subprocess
import sys
import time

SCRIPT=Path(__file__).resolve()

def require(condition,message):
    if not condition: raise RuntimeError(message)

def emit(**fields):
    print(json.dumps(fields),flush=True)

class Filter(ctypes.Structure):
    _fields_=[('code',ctypes.c_ushort),('jt',ctypes.c_ubyte),('jf',ctypes.c_ubyte),('k',ctypes.c_uint32)]
class Program(ctypes.Structure):
    _fields_=[('len',ctypes.c_ushort),('filter',ctypes.POINTER(Filter))]

# Linux x86_64 only: audit architecture and x32 syscall tagging checked in BPF itself.
FORK_SYSCALLS={'clone':56,'fork':57,'vfork':58,'clone3':435}

def native():
    libc=ctypes.CDLL(None,use_errno=True)
    libc.prctl.restype=ctypes.c_int
    libc.syscall.restype=ctypes.c_long
    return libc

def no_fork(libc):
    require(len(list(Path('/proc/self/task').iterdir()))==1,'Must install before any extra thread exists')
    require(libc.prctl(38,1,0,0,0)==0,'PR_SET_NO_NEW_PRIVS failed')
    # LD arch; JEQ x86_64; KILL other ABIs; LD nr; kill x32; deny process/thread creation.
    rows=[(0x20,0,0,4),(0x15,1,0,0xc000003e),(0x06,0,0,0x80000000),
          (0x20,0,0,0),(0x35,0,1,0x40000000),(0x06,0,0,0x80000000)]
    for number in FORK_SYSCALLS.values(): rows.extend([(0x15,0,1,number),(0x06,0,0,0x00050000|errno.EPERM)])
    rows.append((0x06,0,0,0x7fff0000))
    filters=(Filter*len(rows))(*(Filter(*row) for row in rows));program=Program(len(rows),filters)
    require(libc.prctl(22,2,ctypes.byref(program),0,0)==0,'SECCOMP_MODE_FILTER failed')

def attacks(libc):
    results={}
    for name,nr in FORK_SYSCALLS.items():
        ctypes.set_errno(0)
        result=libc.syscall(nr,0,0,0,0,0,0);error=ctypes.get_errno()
        # No valid uncontrolled fork should occur; if filter failed fail closed immediately.
        if result==0: os._exit(97)
        require(result==-1 and error==errno.EPERM,f'{name} not denied by EPERM: {result},{error}')
        results[name]=error
    ctypes.set_errno(0);result=libc.prctl(22,0,0,0,0)
    require(result==-1,'Filter could be disabled')
    results['disable_filter_errno']=ctypes.get_errno()
    ctypes.set_errno(0);result=libc.prctl(38,0,0,0,0)
    require(result==-1,'No-new-privileges could be cleared')
    results['clear_no_new_privs_errno']=ctypes.get_errno()
    require(libc.prctl(39,0,0,0,0)==1,'No-new-privileges not retained')
    return results

def burn(seconds):
    start=time.process_time()
    while time.process_time()-start<seconds: sum(i*i for i in range(1000))

def child(mode):
    libc=native()
    if mode=='control':
        pid=os.fork()
        if pid==0: os._exit(0)
        _,status=os.waitpid(pid,0);require(os.waitstatus_to_exitcode(status)==0,'Fork positive control failed')
        emit(mode=mode,actual_fork_pid=pid);burn(2.5);return
    if mode=='install':
        require(os.getuid()!=0,'Unprivileged experiment required')
        resource.setrlimit(resource.RLIMIT_CPU,(1,2));signal.signal(signal.SIGXCPU,signal.SIG_IGN)
        no_fork(libc);emit(phase='before-exec',attacks=attacks(libc))
        burn(.75)
        os.execv(sys.executable,[sys.executable,str(SCRIPT),'--child','after-exec'])
    require(mode=='after-exec','Unexpected child mode')
    # exec is an actual new interpreter, not a Python function pretending to restart.
    usage=resource.getrusage(resource.RUSAGE_SELF);cpu=usage.ru_utime+usage.ru_stime
    require(cpu>=.75,'exec unexpectedly reset CPU accounting')
    require(resource.getrlimit(resource.RLIMIT_CPU)==(1,2),'exec lost hard CPU cap')
    class Limit(ctypes.Structure): _fields_=[('soft',ctypes.c_ulong),('hard',ctypes.c_ulong)]
    libc.setrlimit.argtypes=[ctypes.c_int,ctypes.POINTER(Limit)]
    ctypes.set_errno(0);result=libc.setrlimit(resource.RLIMIT_CPU,ctypes.byref(Limit(1,3)))
    require(result==-1 and ctypes.get_errno()==errno.EPERM,'Hard limit raise was not EPERM')
    signal.signal(signal.SIGXCPU,signal.SIG_IGN)
    emit(phase='after-real-exec',pid=os.getpid(),uid=os.getuid(),cpu_seconds=cpu,attacks=attacks(libc),hard_raise_errno=errno.EPERM)
    burn(3);raise RuntimeError('Survived hard CPU cap')

def main():
    require(sys.platform=='linux' and os.uname().machine=='x86_64' and ctypes.sizeof(ctypes.c_void_p)==8,'Only LP64 Linux x86_64 supported; no fabricated portable result')
    if len(sys.argv)>1 and sys.argv[1]=='--child': child(sys.argv[2]);return
    path=SCRIPT.parents[1]/'docs/verification/r12-no-fork-cpu.json';path.unlink(missing_ok=True)
    results=[]
    for mode in ('control','install'):
        before=resource.getrusage(resource.RUSAGE_CHILDREN);start=time.monotonic()
        process=subprocess.Popen([sys.executable,str(SCRIPT),'--child',mode],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,start_new_session=True)
        try: stdout,stderr=process.communicate(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid,signal.SIGKILL);process.communicate()
            raise RuntimeError('Cleanup timeout; never accepted as kernel enforcement')
        after=resource.getrusage(resource.RUSAGE_CHILDREN)
        cpu=after.ru_utime+after.ru_stime-before.ru_utime-before.ru_stime
        if mode=='control': require(process.returncode==0 and cpu>=2.5,'Positive control failed')
        else:
            require(process.returncode==-signal.SIGKILL and 1.8<=cpu<=3.0,'No observed kernel hard-limit kill')
            rows=[json.loads(line) for line in stdout.splitlines()]
            require([r['phase'] for r in rows]==['before-exec','after-real-exec'],'Missing exec/filter evidence')
        results.append({'mode':mode,'exit_code':process.returncode,'cpu_seconds':cpu,'wall_seconds':time.monotonic()-start,'stdout':stdout,'stderr':stderr})
    report={'status':'PASS','kernel':os.uname().release,'architecture':os.uname().machine,'source_sha256':hashlib.sha256(SCRIPT.read_bytes()).hexdigest(),'results':results,
      'scope':'Observed deny of clone/fork/vfork/clone3, failed filter/no-new-privs removal, retained filter and CPU accounting across real exec, and hard CPU SIGKILL. Unconstrained fork/CPU control succeeded.',
      'residual':'Linux host single-thread LP64 only. All other syscalls ALLOWED. No memory/wall/filesystem/network confinement, inherited children or Android/ARM64/16K/JNI/parser/Office compatibility acceptance. Denying clone also denies new threads and may break engines.'}
    path.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))

if __name__=='__main__': main()
