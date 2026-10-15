"""Bounded binary subprocess capture. Executes real commands; no Android substitution.
Limits pipe buffers and direct-child lifetime. Not a sandbox or a descendant-process supervisor.
"""
import subprocess
import threading
import time


def capture(command, *, cwd=None, env=None, timeout=120, max_output=32*1024*1024, max_error=256*1024):
    if timeout <= 0 or max_output <= 0 or max_error <= 0:
        raise ValueError('Positive subprocess limits required')
    process = subprocess.Popen(list(map(str, command)), cwd=cwd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    buffers = [bytearray(), bytearray()]
    failures = []
    deadline = time.monotonic() + timeout

    def kill():
        try:
            process.kill()
        except ProcessLookupError:
            pass

    def consume(stream, slot, limit):
        try:
            with stream:
                while True:
                    data = stream.read1(65536)
                    if not data:
                        return
                    if len(buffers[slot]) + len(data) > limit:
                        failures.append('Subprocess output exceeded its byte budget')
                        kill()
                        return
                    buffers[slot].extend(data)
        except OSError as error:
            failures.append('Subprocess pipe failed: '+str(error))
            kill()

    readers = [threading.Thread(target=consume, args=(process.stdout,0,max_output), daemon=True),
               threading.Thread(target=consume, args=(process.stderr,1,max_error), daemon=True)]
    try:
        for reader in readers:
            reader.start()
        process.wait(timeout=timeout)
        for reader in readers:
            reader.join(max(0,deadline-time.monotonic()))
        if any(reader.is_alive() for reader in readers):
            raise subprocess.TimeoutExpired(command, timeout)
        if failures:
            raise RuntimeError(failures[0])
        if process.returncode:
            raise RuntimeError(f'Binary command failed ({process.returncode}): '+bytes(buffers[1]).decode(errors='replace'))
        return bytes(buffers[0])
    finally:
        if process.poll() is None:
            kill()
        process.wait()
        for reader in readers:
            if reader.ident is not None:
                reader.join(1)
