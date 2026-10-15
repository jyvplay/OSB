# R12 pre-experiment plan — no gate promoted

Current native baseline: 1ea1faa, 29 API34 cases passed. a2b47e7 is documentation/audit work.

1. **Browser remote embedding**: use actual SurfaceControlViewHost/WebView in two dedicated
   same-UID processes, embed returned SurfacePackages in two SurfaceViews, inject real input,
   inspect resulting DOM only after input, and resize. Positive: independent input counters,
   different PIDs, nonempty actual provider identity. Reject missing surfaces, timeouts,
   DOM-only scripts presented as user interaction, and same-UID partition presented as security.
   Debug-only experiment before gated production integration; not S14/S29/S30 acceptance.
   Exact provider/API, IME/AX/security and own-compositor integration remain further tests.
2. **Notes recovery**: implement bounded strict codec plus Android CheckedAtomicFile store
   in no_backup, separate from saved .md documents. Coalesced draft checkpoints never save
   notes automatically. Corrupt recovery blocks writes, cancelled pending destructive actions
   are not persisted/replayed, stale recovered revisions still fail normal save checks.
   Tests: malformed lengths/UTF-8/digest/truncation/trailing bytes, native store roundtrip,
   ViewModel recreation and stale-revision/corrupt-file attacks. Distinguish acknowledged
   checkpoint recovery from every-keystroke or physical power-loss guarantees.
3. **Testability census**: classify all48 modules with explicit executable test targets,
   production behavior and limitations. Count substantial-testable subsets separately from
   full feature acceptance; compilation/folder presence does not qualify by itself.

Pinned AOSP SurfaceControlViewHost/SurfaceView/AtomicFile source was retrieved and Git-blob
verified before implementation; records in research/r12-primary.json. Source hypotheses
need actual native execution. No Office engine/payload, counsel or physical reviewer exists
in this environment. Office/kernel alternatives must retain explicit unresolved interfaces.
