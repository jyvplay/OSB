# R27 scope / acceptance contract — executed format/policy kernels + authored hub/dock wiring

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-21.
Fresh primary-source findings live in `research/r27-primary.md` with per-section
take/refusal notes. This plan states what R27 executes, what it only authors,
and what it refuses to claim.

## A. Executed on host JVM (10 kernels, 106 assertions)

| Kernel | Module | Behavior under check |
|---|---|---|
| TabletopArrange | :feature:launcher | 88px grid snap, snapped multi-pin moves, per-plane tidy sweep (no-op when snapped, rejects unsnapped), first-free scan with 512-pin capacity, LIMIT overflow |
| PodcastFeed | :feature:media | RSS 2.0 + Atom enclosure reader; audio/video only; skips + counts non-media; caps (200 items, 8/sec) |
| StreamPolicy | :feature:media | HLS variant playlist parser; explicit user choice by resolution/bandwidth; refuses VOD playlists and DRM (EXT-X-KEY) |
| Gazetteer | :feature:maps | 48 whole-degree offline cities; case-insensitive prefix search; haversine nearest |
| ContentBlocker | :feature:browser | hosts + `||domain^` / `@@` exception compiler; suffix decisions; refuses localhost/private/over-broad rules |
| MarkdownDoc | :core:documents | CommonMark-subset blocks + width-checked GFM tables; HTML stripped and counted |
| FirewallPolicy | :core:security | Fail-closed nftables proposal text (default drop, TransPort/DNSPort-shaped redirects, IPv6/UDP/ICMP drops); gated on authority attestation; review-only |
| UsbInventory | :core:security | lsusb-text parser + interface-suffix arrival/removal diff; fails closed on ambiguity |
| TypeScale | :core:designsystem | All 15 M3 baseline styles byte-verified + phi editorial ladder + title/body fit predicate |
| CronSpec | :feature:automation | Vixie 5-field parser (OR day union, 0/7 Sunday) with UTC next-fire minute scan |

Command: `python3 tools/run_r27_contracts.py --tools <host-tools>` (separate
per-group kotlinc compiles + consumer run on real fixtures in
`tools/fixtures/r27/`).

## B. Independent cross-checks (Python, no kotlinc needed)

- `tools/check_r27_reference.py`: recomputes podcast counts, 6 blocklist
  decisions, 2 cron vectors, the 15-row M3 table and gazetteer shape.
- `tools/test_r27_tools.py`: 11 tests (kernel hygiene, fixtures, M3 fidelity,
  inventory diff counts, cron cross-check, firewall fail-closed text).
- `tools/check_r27_pagematrix.py`: page/view beauty-audit matrix — 24 pages
  tracked; validates status files against the layout-bounds detector. Current
  honest coverage: 0.0 (no device exports exist); the gate tracks the gap.

## C. Authored Android edits (compile-gated only, NOT executed)

1. `AdaptiveDock.kt`: test tags aligned to the DesktopShelf scheme
   (`art-dock`/`business-taskbar`/`shelf-scroll`/`shelf-{id}`/`dock-glyph-{id}`/
   `dock-label-{id}`/`shelf-overflow`). Still UNWIRED — no behavior change.
2. `StreamPlayerActivity.kt`: `EXTRA_DIRECT_URL` launch contract
   (`isDirectUrl` + `launch()`); prefill only, nothing autoplays, service
   page URLs still refused.
3. `MediaHub.kt`: "Open direct-URL stream player…" button (`media-open-stream`)
   on the player tab launching StreamPlayerActivity.

Gate: `tools/check_r26_android_syntax.py` (0 syntax errors; resolution-only
diagnostics expected without android.jar). Full compilation and any behavior
claim require Studio/CI with the SDK plus device execution.

## D. Census effect

8 modules deepened with NEW executed kernels (16%): launcher, media, maps,
browser, documents, security, designsystem, automation. :app noted (tag
alignment, unwired). Subset census stays 50/50 (100%); full acceptance stays
0/50. Details: [R27-MODULE-DEEPENING](R27-MODULE-DEEPENING.md).

## E. Standing boundaries (unchanged)

Host evidence is not Android, device, service, anonymity, or acceptance
evidence. No stream extraction, no DRM/login/region bypass, no keylogger
detection, no Tails-equivalence claim. No working feature removed.
