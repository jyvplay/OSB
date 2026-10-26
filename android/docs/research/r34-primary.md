# R34 primary-source synthesis (2026-10-22; search synthesis, weaker than fetched primaries)

Scope note: this file synthesizes web-search result snippets taken 2026-10-22,
not fetched primary pages. Treat every claim as a lead graded by how many
independent snippets corroborate it, never as verified fact. It exists so the
round's privacy/hardware planning reacts to the freshest available signals
rather than month-old memory.

## Tails: 7.15 still current, micro-delta inside 7.15.x browsers

- No 7.16 anywhere yet; 7.15 remains the current release on tails.net and the
  Wikipedia timeline. The 7.14 (30 Sep) -> 7.15 cadence still points at ~27 Oct
  for 7.16, unchanged from R33.
- NEW micro-delta: Tor Browser inside Tails moved 15.0.24 -> 15.0.25 =
  NoScript 13.6.36.1984, OpenSSL 3.5.9, plus "Backport Security Fixes from
  158" (Firefox ESR 140-line backports). Planning impact: none for R34
  kernels, but the PRD keeps tracking the browser line because the Tails
  comparison table cites it.
- Corroboration: release-notes snippets + Wikipedia timeline agree on 7.15.

## GrapheneOS / Motorola: October build details, Pixel 6 sunset now

- Releases page shows 2026101600; the Oct 6 build 2026100600 carried the full
  2026-10-05 patch level plus October Pixel firmware and moved the project to
  Android 17.
- NEW constraint detail: the 2026 Motorola models are explicitly described as
  lacking MTE and adequate secure-element integration — the partnership's
  honesty page now names the gap the R32/R33 PRDs flagged speculatively.
- Pixel 6 / 6 Pro support ends October 2026 (i.e. now); partnership origin
  reconfirmed as the MWC announcement of 2 Mar 2026; Sept 16 project
  communication says upcoming Motorolas will be "far easier than Pixels" to
  support.
- Planning impact: the PRD's hardware ladder keeps Pixel (MTE + Titan) above
  current Moto hardware for key-storage claims; no kernel this round asserts
  anything MTE/SE-dependent.

## Servo: monthly churn vs LTS, per-host deletion exists

- crates.io publish dates reconfirmed: 0.6.0 on 25 Sep, 0.7.0 on 5 Oct.
- NEW embed-API evidence: monthlies carry breaking changes (toggle_sampling_
  profiler removed, MouseButton renamed) — concrete support for the standing
  LTS-as-a-base position; pinning a monthly would import API churn.
- NEW capability nuance: SiteDataManager::clear_site_data offers per-host
  deletion, which softens the "no HTTP-cache clearing" phrasing: the layer
  lacks bulk clearing primitives but per-host deletion exists. PRD wording
  updated accordingly.
- LTS terms reconfirmed: 6-month cycle, 9-month maintenance, security-only
  after the next LTS.
- Planning impact: none for R34 kernels (SafeNavigation already owns strict
  canonicalization, which is exactly why CacheKey was cut this round).

## Orbot / Tor: 17.9.7 still latest, component versions named

- 17.9.7 remains the latest release (APKMirror lists 34.18 MB on Oct 16, Play
  changelog Oct 14); no newer tag found.
- NEW component detail: the 17.9.7 notes name obfs4 0.9.0 and Snowflake
  2.15.1; commit 0ea112a4d; versionCodes 1797300100/101.
- tor itself still 0.4.9.14 in the carried notes; no contradictory signal.
- Planning impact: proxy-shape claims in the PRD keep citing 17.9.7 with the
  now-named transports.

## Android 16/17: Intrusion Logging and USB protection details

- NEW Intrusion-Logging detail: the log captures Chrome Incognito network
  events (sites visited, not pages); NEITHER the user NOR Google can delete
  entries early, even on account close. Event list: installs, process starts,
  DNS queries, IP connections, USB transfers, certificate installs, lock
  events.
- NEW USB detail: USB protection now covers all Pixel devices on Android 16+;
  USB Protection + Failed Authentication Lock ship only on select Android 17
  devices (Pixel 6+).
- Planning impact: honesty boundaries — a privacy feature that promises
  "leave no trace" on stock Android 16+ must reckon with undeletable
  intrusion logs; the PRD states this rather than hand-waving. No R34 kernel
  touches USB or logging surfaces.

## Carried forward (no contradictory signal)

- Tails 7.15 current; GrapheneOS Signature phone still aimed at the November
  US window (SD 8 Elite Extreme Gen 6, MTE + SE, 7+7 support); Servo 0.6.0
  LTS vs 0.7.0 monthly; Expert-features 6-item flux unresolved.
