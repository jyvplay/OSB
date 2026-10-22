# R36 primary-source synthesis (2026-10-22; search synthesis, weaker than fetched primaries)

Scope note: this file synthesizes web-search result snippets taken 2026-10-22,
not fetched primary pages. Treat every claim as a lead graded by how many
independent snippets corroborate it, never as verified fact. It exists so the
round's privacy/hardware planning reacts to the freshest available signals
rather than month-old memory. R36 re-checked all five R35 topics the same
day; almost everything stands, with micro-details noted.

## Tails: 7.15 still current, Unsafe-Browser micro-detail

- No 7.16 anywhere yet; 7.15 (13 Oct) remains current. Two-week cadence
  stands; next release due late October. ISO size ~1.8 GB (standing
  footprint anchor for the "lighter than Tails" honesty rule).
- NEW micro-detail: 7.15 shows an empty page instead of the Tor Browser
  homepage on new Unsafe-Browser tabs (#21004).
- Planning impact: none for R36 kernels; the PRD keeps the component table
  current.

## GrapheneOS / Motorola: November window stands, no date yet

- Signature 27 still the confirmed first non-Pixel device; Motorola's US
  page still says November with no exact day; GrapheneOS build undated;
  preinstalled builds still planned-not-dated. GSMArena lists exp. release
  Oct–Nov, Android 17, 7 OS upgrades.
- One outlier (efani blog) tables "expected release 2027" — contradicted
  by Motorola's own November window and graded unreliable (likely confused
  with the 2027 Razr expansion).
- Planning impact: none; Signature 27 remains the named phone to watch for
  receipts, not a device Fusion has.

## Servo: 0.7.0 stands, no 0.8.0; downstream ships the LTS

- 0.7.0 (early Oct) still the latest monthly; 0.6.0 still the LTS; no
  0.7.1 or 0.8.0 signal. actpkg/servo WASM component still pinned at
  engine 0.5.0.
- NEW downstream proof: kayfgit/browser now SHIPS Servo 0.6.0 as a regular
  crash-isolated engine in release builds (not an experiment) — the LTS
  embed path is proven by a third party. Proves the path, not our intake.
- Planning impact: none for R36 kernels; the alternatives table inherits
  the shipped-embed confirmation.

## Orbot / Tor: no newer release, 17.9.7 stands

- Queries for 17.9.8 / 17.10 return only stale mirrors (APKMirror sidebars
  top out at 17.9.3 from April); no newer tag, no new changelog.
- 17.9.7 + obfs4 0.9.0 + Snowflake 2.15.1 + tor 0.4.9.14 stand as the cited
  baseline.
- Planning impact: none; P-ANON-1 reconfirmed without changes.

## Android 16/17: Oct 1 six stand, terms hardened

- The six Advanced Protection features (Intrusion Logging, USB Protection,
  Accessibility Protection, Disable WebGPU, Failed Authentication Lock,
  View Supporting Apps) stand with no newer announcement; newest coverage
  is still the Oct 1–6 window.
- NEW micro-details: IL logs cannot be manually deleted before the 12-month
  expiry even after logging is disabled; USB Protection confirmed on all
  Pixel A16+; IL rolling out to A16 December update and newer; IL developed
  with Amnesty International + Reporters Without Borders input (reconfirmed).
- Planning impact: PRD rows P-A17-1/P-FOR-1/P-USB-2 reconfirmed with the
  hardened terms. No R36 kernel touches these surfaces.

## Carried forward (no contradictory signal)

- Tails 7.15 current, ~27 Oct next; Signature 27 November window;
  Servo 0.6.0 LTS vs 0.7.0 monthly; Tor VPN still beta/experimental;
  Expert six-item flux unresolved.
