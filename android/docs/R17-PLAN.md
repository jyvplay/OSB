# R17 — first close R16's experimental plan

Work only on arena/31a7965e-osb. R16 completion here means the six declared bounded experiments and evidence/reporting, NOT complete Together/Office/performance acceptance.

Evidence threshold before each implementation commit: executable affected host/Python checks, project/source checks, preserved counterexample where applicable. Android source changes remain experimental until exact native CI. No module promotion from queued tests or build success alone.

Observed blockers: old focus counterexample did not reproduce at6506159, preventing actual candidate device tests; profile job succeeded at7a1eee3 but each large annotation was truncated to4096 characters. Preserve both. Classify historical positive/negative/invalid results separately without skipping candidate regressions; cap receipt chunks below4096 and verify reassembly/SHA256. Generate measured first-party-owned ART rules only, rebuild and require actual profile compilation.

Six distinct R16 mechanisms remain: bounded socket capability, direction/session AEAD, causal clock interval, native UI measurement, measured ART profile compilation, LOK feasibility/source boundary. R17 mechanisms will extend this portfolio only after R16 evidence closure. Full user workflow, legal/physical/API37/AX acceptance remains separate. No independent agents, theorem prover or physical lab is available.


## After verified R16 closure: persistent local tasks

Bounded PR03 increment in existing :feature:automation, not another counted module. Up to128 tasks; canonical UUIDs; nonblank strict-Unicode titles≤256 UTF16; optional ISO date0001..9999, date-only/no timezone or alarm semantics; reversible done flag. Immutable snapshots with nonnegative Long revision; revision-checked updates prevent stale same-process writers; reject overflow. Canonical bounded plaintext binary/CRC framing, not authentication. App-private no-backup AtomicFile storage, bounded reads, verified reopen, corrupt/partial data refuses and is retained. No delete, reminder, recurrence, CalendarContract, cloud, account or background action.

Source spike: pinned AOSP AtomicFile explicitly requires caller locking; existing Android app-private AtomicFile execution plus new actual native tests are required for this adapter. Use one process-wide lock and expected revision, not an unsupported cross-process locking claim. No power-loss/directory-fsync theorem. UI writes off-main-thread, disables operations until load succeeds, retains typed input on failure and exposes no automatic external actions. Full Tasks/AX acceptance remains open.

Evidence threshold: separately compiled model/codec public consumers and independent Python wire/date checks; actual Android persistence, stale-writer/corrupt-data tests and real UI add/complete/reopen before native claims. A failure preserves evidence, repairs inherit no trust.
