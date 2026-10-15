# R11 isolated-format boundary spike — candidate, not gate acceptance

This initial implementation transfers a read-only, offset-zero regular-file FD to
a fresh Android isolated-service instance. It computes an opaque byte digest only;
it cannot render/edit Office, PDF or images. No engine gate is promoted.

Required observations before introducing a parser: actual different isolated UID
and PID, OS-stamped Messenger sender UID, parent private-file EACCES, denied INTERNET
permission, distinct simultaneous instances, real Binder death from a five-second
service-lifetime watchdog, refusal of write-enabled/pipe/nonzero-offset/oversized
FDs, exact digest, unchanged source and single-use lease. Device tests exist in
FormatIsolationDeviceTest. Status at authorship: UNRUN.

Bounds:16MiB actual bytes; one job/instance;12s binding wait;8s result wait; worker
requests self-termination after5s since creation. Scheduler behavior can exceed a
requested deadline: tests allow15s death observation. No hard real-time guarantee.
A same-process watchdog is not robust to arbitrary native code execution disabling
it. A parent-controlled OS death primitive/isolated-service unbind semantics need
separate stress evidence. No URI grants, parent path opens, output FDs, arbitrary
commands, parser process or network service are represented as working features.

Only fresh test-owned files are used. Actual Android OS execution on a disposable
managed emulator is a runtime test, not physical-device/OEM/16K-page evidence.
