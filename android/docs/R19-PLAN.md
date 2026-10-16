# R19 preimplementation contract

Inherited actual 1b80ba7/run38832395859:86 PASS/0 FAIL/0 SKIP, collected this turn. Retain earlier d6f7393 85/86 and focused2/2: retries are not causal repairs.47/48 substantial subsets,0/48 fully accepted; no LOK helper inflation.

Scope: deepen existing Android productivity and original Office subsets, not promise all remaining specification work in one pass.

Independent mechanism candidates before implementation:
1. Immutable, exact-snapshot-bound one-step task undo. Undo is a new monotonically increasing revision, not rollback. An undoable commit must reserve the immediate undo's revision/archive capacity. No-op, stale/different-content state and MAX boundaries are adversaries.
2. Append-only task merge preview. Keep all existing records/order, append new IDs, recognize identical IDs, refuse differing same-ID records without mutation. Imported revision does not set the local revision. Limits and confirmation apply before commit; stale preview fails.
3. Bounded stream transfer:128KiB cap, strict existing FTS1/FTS2 parser, no-progress refusal, exact readback on explicit new-document export. No power-loss/provider atomicity or wall-clock IO bound claimed.
4. Android serialized commit/undo and actual system document picker UI. Preview reads do not save; confirmation uses exact expected snapshot. Undo is session-only and cleared by reload/recreation. Exports include archives and are explicitly plaintext. Native tests use real private files and platform MediaStore, not a fake provider.
5. Original multi-sheet literal XLSX export:1..16 sheets, existing literal-only semantics, aggregate20,000 cells/2MiB text, bounded ZIP output. Verify with independent openpyxl. Not XLSX import/render/formula/LOK parity.
6. Actual task-input observation: capture status and pre-submit geometry without bypassing touch or changing timeouts; keep old failure alive. Keyboard IME actions may be a separate explicit path, not evidence the old touch failure is repaired.
7. Sandbox alternative spike: execute real Android SharedMemory protection/writable-alias behavior before considering a production transport change. No mock worker and no new production SharedMemory path before evidence.

H+: these bounded operations are executable, preserve admitted data and refuse stale/conflicting/over-budget changes. H-: generic merge is automatically safe, undo can roll revision backwards, checksum equals authenticity, readonly mapping equals sealing, limited XLSX equals Office. H-boundary: revision/archive reserve, resource caps, provider/trusted-owner assumptions and real UI timing.

Thresholds: separately compile actual Kotlin production/consumer; independent reference/readback and patch attacks; actual native named outcomes for Android paths. Source/queued workflows are not passes. Every repair needs fresh affected tests. Six+ mechanisms must have artifacts, falsifiers and explicit local/full-spec gaps in the final A–J report. Tooling fixtures may test verifiers but never stand in for app functionality.

Initially available: git/gh/Python/shell and remote Actions. No local compiler/JRE/cache/SDK/prover at start; restore and actually run before claiming. No independent agents, physical devices, counsel or expert AX review. Primary research is restricted to available hosts; do not call a small pinned source review an exhaustive best-known frontier.
