# R18 preimplementation contract

Baseline: inherited da49491/run38701998103 completed79 PASS/0 FAIL/0 SKIP; retain receipt.47/48 testable subsets,0/48 full acceptance; LOK has no audited executable engine. Do not create an unrelated helper just to claim48/48.

Target: extend PR03 with real task editing, durable reversible archive/restore, bounded title/date filtering and ordering, preserving legacy data. No reminders, notification delivery, recurrence, permanent deletion, provider sync or full Tasks claim.

Six early mechanism branches:
1. Immutable task transitions: edit title/date preserves ID/done/archive; archive is reversible, does not free capacity; no-op retains revision; overflow/unknown IDs refuse.
2. Versioned canonical wire codec: FTS2 adds archive bit; exact legacy FTS1 read-only migration on load, new writes after explicit modification use FTS2. All128 active+archived tasks share old128KiB bound. CRC is accidental-corruption detection, never authentication. Old clients cannot read FTS2; UI discloses upgrade before mutation.
3. Serialized optimistic repository: same-process lock and expected revision for all mutations, reopen confirmation and bounded read; stale undo/edit fails, no rollback claim after uncertain publication.
4. Pure projection/query: at most256 UTF16 query, strict Unicode, case-insensitive Locale.ROOT literal substring; active/open/completed/archive scopes and stable date ordering, null last. No task mutation during search/sort and no culture-aware linguistic-search guarantee.
5. Actual Compose workflow: inspect/review edit, save/cancel, archive and restore, query/scope/order UI, IO off main thread. Existing task entry remains intact. Actual native UI lifecycle tests plus old regression. Full AX/keyboard/IME stability remains separate.
6. External verification and invariant models: independently encoded Python frames in both versions, date/projection references; actual native private-file/legacy/race tests; Z3 only for explicitly modeled transition/size properties, not Kotlin proof.

Before gated persistence changes: inspect pinned AtomicFile contract and execute the inherited actual store regressions/evidence. Native evidence exists for old adapter; new behavior requires own Android tests. Native source commits may follow passing local compilation/host/census/Python checks to trigger actual CI, but source existence or queued runs do not satisfy native acceptance. Retain failures; repaired candidates rerun affected gates plus a patch-specific attack. No full-spec module is promoted by these changes.

Available initially: Python/git/gh, network-restricted primary-source access and remote Android Actions. Kotlin/JRE and optional host cryptography/Z3 must be restored and actually executed before claims. No independent agents, local Android SDK, physical devices, counsel or expert AX review. Mathematical negative/conditional results do not establish global product impossibility.
