# R31 primary-source research (search synthesis, 2026-10-22)

Scope note: this turn's findings come from fresh web-search result synthesis
(2026-10-22), NOT from fetched-page excerpts. Claims below are therefore
weaker than R28's fetched primaries: each section states what R31 takes, what
it refuses, and the fetch/device receipt that would promote the claim. R29's
and R30's sections stand unless a section below explicitly supersedes them.

## 1. Tails 7.15 reconfirmed current (13 Oct 2026; 7.16 due on cadence)

Search synthesis (2026-10-22): endoflife.date, Wikipedia, and tails.net agree
7.15 (13 Oct 2026) is current; the two-week cadence since 7.12 implies ~27
Oct for 7.16 (predicted date, not a claimed release). 7.15 stays a routine
"announce" maintenance release (Tor Browser 15.0.25, Tor 0.4.9.14, firmware
refresh).

R31 take: PRD baseline reconfirmed unchanged a second day running. R31
refusal: same as R29/R30 — no OS-level claims from inside an app. Promote
with: fetched release notes + same-hardware measured comparison (absent).

## 2. GrapheneOS port control confirmed in depth; Motorola plans detailed

Search synthesis (2026-10-22): the GrapheneOS features page confirms five
USB-C port/pogo modes with "Charging-only when locked" default, enforced at
BOTH the hardware level (USB controller data-line disable) and the OS level
(kernel second layer). 5 Oct 2026 reporting details the Motorola partnership:
7 years of updates, Razr support, ported USB-C port protection, Linux 6.18
LTS start, reset-attack protection for firmware boot modes.

R31 take: PRD extends P-USB-3 (dual-layer enforcement named) and P-GOS-1
(Motorola detail); the hardware-level data-line disable is the concrete
BadUSB-closure mechanism Fusion recommends but cannot replicate. R31 refusal:
no in-app replication claims. Promote with: GrapheneOS device receipts.

## 3. Servo 0.6.0 LTS is latest with a published 37.9 MB Android AAR

Search synthesis (2026-10-22): Servo v0.6.0 (LTS) is the latest GitHub
release (25 Sept 2026) publishing `servo-aarch64-android.aar` (37.9 MB,
sha256 `7d59766a...e8a6`). LTS policy: new LTS every 6 months, 9-month
support, security fixes only. 0.5.0 was a monthly (488 July commits,
Android 10+ floor, DuckDuckGo rendering, 10x text).

R31 take: ALTERNATIVES B2 is updated to 0.6.0 LTS with a first size receipt
(37.9 MB AAR) — but stays CANDIDATE: the receipt is upstream-published, not
an in-repo engine_intake run, and license closure + device run are still
open. R31 refusal: no bundling claim; size alone is not acceptance. Promote
with: in-repo engine_intake + Servoshell smoke + LICENSE-REVIEW closure.

## 4. Orbot 17.9.7 current with enumerated audit fixes; Tor VPN still beta; Android 17 six-feature detail

Search synthesis (2026-10-22): Orbot 17.9.7 is still current (Play, 14 Oct:
Tor 0.4.9.14, IPtProxy 5.7.0/Snowflake 2.15.1); the 17.9.6 audit fixes are now
enumerated (ORB-02-011 critical Tor/VPN-disable via incomplete cleanup,
ORB-02-004 Kindness consent bypass, path traversal, removed hardcoded AWS
credentials). Tor VPN on F-Droid is 1.9.1Beta (build 242, 3 Sept) — still
beta. Android 17's 1 Oct Advanced Protection update is detailed: Intrusion
Logging (E2E-encrypted, Google cloud, 12-month rolling retention, SEPARATE
opt-in, built with Amnesty/RSF), USB Protection (Pixel 6+, locked =
charging-only), Accessibility Protection (verified Accessibility Tools only),
Disable WebGPU, Failed Authentication Lock, View Supporting Apps. Google is
also preparing an "Expert features" loosening (USB Protection may become
optional) and enterprise policy enforcement later in 2026.

R31 take: PRD records the audit-fix enumeration (P-ANON-1), the exact Tor VPN
beta build (P-ANON-2, still experimental), the Intrusion Logging retention +
separate-opt-in facts (P-A17-1), and a new P-KEY-4 row for Accessibility
Protection as the OS anti-overlay/keylogger control. R31 refusal: no routing
code; no reliance on USB Protection (it may become optional); no Intrusion
Logging clone. Promote with: stable releases + device receipts.
