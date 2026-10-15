# R11 isolated-format boundary spike — candidate, not gate acceptance

This initial implementation transfers a read-only, offset-zero regular-file FD to
a fresh Android isolated-service instance. It computes an opaque byte digest only;
it cannot render/edit Office, PDF or images. No engine gate is promoted.

Observed/required elementary properties: different UID/PID, OS-stamped Messenger
sender UID, no INTERNET permission, distinct simultaneous instances, actual Binder
death from a five-second service-lifetime watchdog, writable/pipe/nonzero-offset/
oversized FD refusal, exact digest, unchanged source and single-use lease.

History: run38660636656 at ea9766d had4/5 boundary cases pass and the combined
private-file/INTERNET case fail. Run38661669902 at5a49eb0 diagnosed the failure:
**native ENOENT2 instead of assumed EACCES13**. The initial errno-specific hypothesis
was wrong. ENOENT alone was not accepted as isolation evidence.

Candidate1ee1589 adds exact parent/worker pathname equality, same stat device+inode,
positive delegated read-only FD access to that same existing private file, correct
binary digest and unchanged parent inode/content. Only EACCES denial or ENOENT
name unavailability is accepted; opening by pathname or any other errno fails.
The positive controls rule out a wrong/nonexistent fixture path. The old failed
results remain recorded, not relabeled green.

**Fresh run38662585401 at1ee1589: all5 boundary cases PASS; all24 instrumented
cases PASS.** See `verification/r11-device-final-annotations.json` and
`verification/r11-native-results.json`. This passes this elementary API34 emulator
spike experiment, **not** full FF12, an Office/PDF parser or an S1–S30 gate. Direct
socket denial, active malicious-parser CPU/RSS and parent-enforced death are untested.

Bounds:16MiB actual bytes; one job/instance;12s binding wait;8s result wait; worker
requests self-termination after5s since creation. Scheduler behavior can exceed a
requested deadline: tests allow15s death observation. No hard real-time guarantee.
A same-process watchdog is not robust to arbitrary native code execution disabling
it. A parent-controlled OS death primitive/isolated-service unbind semantics need
separate stress evidence. No URI grants, parent path opens, output FDs, arbitrary
commands, parser process or network service are represented as working features.

Only fresh test-owned files are used. Actual Android OS execution on a disposable
managed emulator is a runtime test, not physical-device/OEM/16K-page evidence.
