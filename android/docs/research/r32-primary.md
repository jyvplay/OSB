# R32 primary-source research (search synthesis, 2026-10-22)

Scope note: this turn's findings come from fresh web-search result synthesis
(2026-10-22), NOT from fetched-page excerpts. Claims below are therefore
weaker than R28's fetched primaries: each section states what R32 takes, what
it refuses, and the fetch/device receipt that would promote the claim. R29's,
R30's, and R31's sections stand unless a section below explicitly supersedes
them.

## 1. Tails 7.15 still current (no 7.16 by 22 Oct); changelog detail

Search synthesis (2026-10-22): no 7.16 release has appeared, so 7.15 (13 Oct
2026) remains current and the ~27 Oct cadence prediction stands unconfirmed.
Changelog detail now enumerated: a Tor-bridge error-message fix, Unsafe
Browser empty-page fix (#21004), and deduped CPU-microcode handling (#21001).

R32 take: PRD baseline reconfirmed unchanged a third day running, with the
changelog items recorded as the concrete 7.15 delta. R32 refusal: same as
R29–R31 — no OS-level claims from inside an app. Promote with: fetched
release notes + same-hardware measured comparison (absent).

## 2. Signature 27 is the first GrapheneOS Motorola device; A17 rollout dated

Search synthesis (2026-10-22): the GrapheneOS releases page shows
`2026091000`/`2026061800` as the initial Android 17 builds. Signature 27
(announced 24 Sept 2026, Snapdragon 8 Elite Extreme Gen 6, 7 OS upgrades) is
confirmed as the first Motorola device GrapheneOS will support, with
preinstalled devices planned for 2027; Razr Fold/Ultra follow later, and 2026
Motorola models will never be supported retroactively. Motorola's own Android
17 rollout began the week of 6 Oct 2026.

R32 take: PRD extends P-GOS-1 with the Signature 27 specifics (first device,
2027 preinstall, no retroactive support) — the concrete near-term hardware
path for the recommended OS controls. R32 refusal: no in-app replication
claims; no claim about devices Fusion has never run on. Promote with:
Signature 27 / GrapheneOS device receipts.

## 3. Servo LTS-scope caveats: no-clear HTTP cache, servoshell out of scope

Search synthesis (2026-10-22): Servo's August report (417 commits) lands CSS
Grid by default, SpiderMonkey 153, a disk-backed HTTP cache with NO clear
API, and a Compose UI abstraction (#47439). The LTS book explicitly places
servoshell outside LTS scope.

R32 take: ALTERNATIVES B2 keeps its 0.6.0 LTS + 37.9 MB receipt but gains two
honest caveats — the cache cannot be cleared (amnesia-relevant) and the
reference shell is not LTS-covered, so any embed must bring its own
hosting/UI story. R32 refusal: still CANDIDATE, still no bundling claim.
Promote with: in-repo engine_intake + embed smoke + LICENSE-REVIEW closure.

## 4. "Expert features" teardown firms up; Intrusion Logging event list

Search synthesis (2026-10-22): a Play Services `26.36.30` teardown shows an
"Expert features" section making Intrusion Detection, USB Protection, and
Unsecured Wi-Fi protection OPTIONAL rather than default-on. The Intrusion
Logging event list is enumerated (app/process, DNS/IP, USB-transfer,
certificate, and lock events). USB Protection covers Pixel 6+ and select
Android 17 devices.

R32 take: PRD hardens the P-USB-2 caveat (USB Protection is now teardown-
confirmed optional, so Fusion relies on nothing) and extends P-A17-1 with
the event list. R32 refusal: no reliance on optional OS toggles; no
Intrusion Logging clone. Promote with: stable releases + device receipts.

## 5. Orbot 17.9.7 still latest; Tor VPN still beta

Search synthesis (2026-10-22): Orbot 17.9.7 (Tor 0.4.9.14) is still the
current Play release, with SecurePreferenceDataStore hardening noted in its
recent changes; Tor VPN remains a beta/experimental handoff.

R32 take: PRD reconfirms P-ANON-1/P-ANON-2 unchanged with the hardening note
recorded. R32 refusal: no routing code; no code until stable + review.
Promote with: stable releases + device receipts.
