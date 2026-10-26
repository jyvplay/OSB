# R35 primary-source synthesis (2026-10-22; search synthesis, weaker than fetched primaries)

Scope note: this file synthesizes web-search result snippets taken 2026-10-22,
not fetched primary pages. Treat every claim as a lead graded by how many
independent snippets corroborate it, never as verified fact. It exists so the
round's privacy/hardware planning reacts to the freshest available signals
rather than month-old memory.

## Tails: 7.15 still current, component bumps itemized

- No 7.16 anywhere yet; 7.15 (13 Oct) remains current on tails.net,
  Wikipedia, endoflife.date, and the Tor blog.
- NEW itemization: 7.15 = Tor Browser 15.0.25 + Tor client 0.4.9.14 +
  updated graphics/Wi-Fi firmware; 7.14 carried Tor 0.4.9.13 and kernel
  6.12.111, so 7.15 bumped the Tor client .13 -> .14.
- Two-week cadence since 7.12 reconfirmed by multiple outlets; next release
  due late October. ISO size ~1.8 GB (a standing footprint anchor for the
  "lighter than Tails" honesty rule).
- Planning impact: none for R35 kernels; the PRD keeps the component table
  current.

## GrapheneOS / Motorola: Signature 27 details fill in

- First-non-Pixel support confirmed 22–25 Sep across a dozen outlets; SD 8
  Elite Extreme Gen 6; 7 OS upgrades + 7 years security; November US window
  with no exact date; global rollout later in the year.
- NEW details: 50 MP main (Sony Lytia 910, 1/1.28") + 200 MP periscope +
  Bang & Olufsen speakers; Linux 6.18 LTS base; reset-attack protection for
  firmware boot modes; preinstalled GrapheneOS builds "being planned".
- Gap side reconfirmed with specifics: SD 8 Elite Gen 5 introduced MTE but
  it is broken on non-Elite variants; Titan M2/M3 remains the secure-element
  bar ("far harder" to match); 2026 Signature/Razr line lacks MTE + adequate
  SE integration.
- Planning impact: the PRD's hardware ladder is unchanged (MTE+SE first);
  the Signature 27 is now the named phone to watch for receipts, not a
  device Fusion has.

## Servo: August report lands, LTS scope clarified

- 0.7.0 (5 Oct) still the latest monthly; 0.6.0 still the LTS; no 0.7.1 or
  0.8.0 signal.
- NEW ("August in Servo" blog, posted 9 Oct): 417 commits; CSS Grid on by
  default; navigation/document-loading/session-history improvements
  (windows + iframes more reliable); the two embed-API breaking changes
  reconfirmed in the project's own words.
- NEW downstream detail (kayfgit/browser PR): SpiderMonkey ESR 153 base,
  mouse-button rename Left/Middle/Right -> Primary/Auxiliary/Secondary, a
  still-needed vendored servo-paint-api WGL fix, content-security-policy
  0.8.2. Independent 0.6-LTS embed proof stands.
- NEW scope clarification (Servo Book): only the servo library + deps are in
  LTS scope — servoshell (the demo browser) is explicitly OUT. LTS
  terms otherwise reconfirmed (6-month cadence, 9-month support,
  security-only).
- NEW security detail: 0.7.0 pulled mozangle 0.7.1 with fixes from
  FIREFOX_153_3_0esr.
- Planning impact: none for R35 kernels; the alternatives table inherits
  the LTS-scope correction (an embed takes the library, never servoshell).

## Orbot / Tor: no newer release, 17.9.7 stands

- Queries for 17.9.8 / 17.10 return only stale mirrors (sidebars top out at
  17.9.3 from April); no newer tag, no new changelog.
- 17.9.7 + obfs4 0.9.0 + Snowflake 2.15.1 + tor 0.4.9.14 stand as the cited
  baseline.
- Planning impact: none; P-ANON-1 reconfirmed without changes.

## Android 16/17: Oct 1 Advanced Protection update enumerated

- Google's Oct 1, 2026 post (Cybersecurity Awareness Month) enumerates SIX
  Advanced Protection features: Intrusion Logging, USB Protection,
  Accessibility Protection (verified tools only), Disable WebGPU (Chrome
  attack surface), Failed Authentication Lock (full lockdown), and View
  Supporting Apps (NEW: settings page listing apps that check AP status).
- Intrusion Logging detail hardened: opt-in (separate manual toggle),
  end-to-end encrypted, user-only readable, rolling 12-month retention,
  auto-delete after; built with Amnesty International + Reporters Without
  Borders input; MUST be enabled before an incident (no retroactive
  recovery); event list and Incognito sites-not-pages behavior reconfirmed;
  rolling out to A16 December update and newer.
- USB Protection: new connections charging-only while locked; connections
  established BEFORE locking stay active (documented limitation, like the
  unlocked-period and pre-boot logging gaps).
- Availability: all six on Android 17 devices; USB Protection + Failed
  Authentication Lock only on select A17 (Pixel 6+); USB Protection also on
  all Pixel A16+.
- Expert-features flux (15 Sep): Google preparing to move USB Protection,
  Intrusion Detection, and Unsecured Wi-Fi into an optional Expert section
  — the six-item list remains in motion.
- Planning impact: PRD rows P-A17-1/P-FOR-1/P-USB-2 updated with the
  enumerated six, the opt-in/12-month terms, and the stated limitations. No
  R35 kernel touches these surfaces.

## Carried forward (no contradictory signal)

- Tails 7.15 current, ~27 Oct next; Signature 27 November window; Servo
  0.6.0 LTS vs 0.7.0 monthly; Tor VPN still beta/experimental; Expert
  six-item flux unresolved.
