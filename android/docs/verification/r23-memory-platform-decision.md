# R23 SharedMemory prerequisite decision — 2026-10-16T06:46 UTC

**Before production FormatLease/worker memory transport implementation.**

Candidate `20c1fd92559158bf8780b6c02907e3d95bfd4fa1`, completed privacy run [38862379063](https://github.com/jyvplay/OSB/actions/runs/38862379063), whole workflow/device conclusion SUCCESS,14PASS/0FAIL/0SKIP. Receipt: `r23-memory-prerequisite-result.json`; `r23_evidence_gate.py --claim r23_privacy_api34` actually returned allowed=true for that exact candidate. Compile/lint/Gradle R23 contracts also succeeded. Additional reveal/stale-timer and trusted accessibility-action assertions executed in that run.

Actual platform case `dev.fusion.privacy.R23MemoryBoundaryTest.existingWritableMapSurvivesRestrictionButUnmappedReadOnlyParcelRefusesNewWrites` passed. It observed the specific counterexample: an existing writable mapping still mutates bytes after future mappings are restricted. After unmapping the only owned writer and restricting, a real same-process Parcel recipient read2MiB exact bytes, could not widen protection or obtain a new writable mapping, and a readonly ByteBuffer rejected put.

Decision: permit implementation/testing of the narrow, owned, no-named-temp-file production transport described in `R23-MEMORY-TRANSPORT-PLAN.md`. **Do not accept that transport yet.** This substrate test is not cross-UID transfer, generic memfd sealing, proof against old mappings in an untrusted parent, no-swap/RAM secrecy, computation authentication, physical/API36/37/OEM evidence, full sandbox/Office or any S1–S30 spike pass. Existing readonly regular-FD route must remain separately enforced. A new candidate needs the plan's actual wire/UID/observer/corruption/limit/FD/UI regression gates.
