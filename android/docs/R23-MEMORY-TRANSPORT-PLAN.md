# R23 conditional production-memory transport design — not yet delivered

Recorded after initial12-case input/native119-case regression pass, before production transport code. Implementation is CONDITIONAL on exact-candidate actual SharedMemory prerequisite case and successful whole companion workflow. No candidate inherits acceptance merely from documentation. Existing FD route stays intact for file/digest/socket probes.

## Proposed local interface

- Existing owned arithmetic/CSV/XLSX callers already capture stable bounded private request bytes. They may use an anonymous `SharedMemory` region of `max(1, payload.size)` with operation-specific limits (arithmetic frame,64KiB CSV,2MiB XLSX).
- Parent creates/maps one owned writer, initializes exactly the payload (explicit zero padding for empty CSV), **unmaps in finally**, restricts future permissions to PROT_READ, then sends the Parcelable/byte length via the EXISTING isolated-service Binder channel. No URI/path/filename or writable mapping is published. Create/protection failure refuses; **no silent private-file fallback**.
- Child must accept exactly one transport (regular readonly FD OR SharedMemory), only permit memory for the three owned parse operations, validate operation/length/allocation bounds BEFORE copy, reject a new writable mapping if it succeeds, map/copy readonly and unmap/close in finally. Existing file route continues to check regular file, fresh offset0, O_RDONLY and actual consumed-output caps unchanged.
- Empty input is a0-byte payload in a1-byte zero-initialized region; padding is not parsed/hashed. Refuse a nonzero empty padding byte. Hash the actual payload, preserve existing request/reply correlation and data-only bounded result validation. Correlation does not authenticate child computation.
- Explicit transport name in reply/evidence must match parent's chosen transport. Device/inode fields for SharedMemory must be sentinel values, not invented filesystem measurements.
- Existing service sender UID checks, single admission,5s watchdog,8s reply/12s connect bounds, per-lease process lifecycle and no-INTERNET child remain. No generic engine gate is promoted.

## Ownership / proof boundary

Trusted parent is assumed to create the region fresh, retain no old writable map, serialize initialization and not leak the handle before restriction. Future mapping protection does NOT revoke old mappings or make a compromised parent trustworthy. A malicious child can still lie about semantic results and may deny service; its output remains untrusted bounded literal data. Host/OS can inspect or page anonymous memory. No swap, RSS, performance, RAM-erasure or global anonymity claim.

## Mandatory new falsifiers before any scoped acceptance

1. Real distinct-UID owned arithmetic, CSV (including empty and exact64KiB), and independent/literal/dense XLSX pass on new transport; no claimed file inode for memory.
2. Real FileObserver sees no new owned-input temporary pathname during the operations; unchanged directory listing alone is insufficient (an intermediate create/unlink could be missed).
3. Oversize/malformed inputs refuse, then a fresh isolated lease succeeds. No silent disk fallback.
4. Real wire-level tests supply unprotected memory, wrong declared size, ambiguous FD+memory and memory for an unsupported operation; worker must refuse and retain fixed lifetime/single-request semantics.
5. Readonly FD/digest/source-inode/cursor/socket-denial regressions remain unchanged; memory cannot accidentally bypass file admission.
6. Existing actual CSV/XLSX picker UI import/preview/new-copy/readback/reimport and whole native workflow pass on the exact candidate. No source URI overwrite or new fidelity claim.
7. Every changed module source/test hash regenerated and all affected source/host/Android gates rerun. A compiler or test failure stays in the ledger with a patch-specific reattack.

If the prerequisite or integration cannot be verified in this turn, keep the strongest actual experiment and exact remaining interface; do not enable speculative production code or claim the alternative delivered.
