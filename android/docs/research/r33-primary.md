# R33 primary-source research (search synthesis, 2026-10-22)

Scope note: this turn's findings come from fresh web-search result synthesis
(2026-10-22), NOT from fetched-page excerpts. Claims below are therefore
weaker than R28's fetched primaries: each section states what R33 takes, what
it refuses, and the fetch/device receipt that would promote the claim. R29's
through R32's sections stand unless a section below explicitly supersedes
them.

## 1. Tails 7.15 still current; expat DSA frames the hostile-file vector

Search synthesis (2026-10-22): endoflife.date, Wikipedia, DistroWatch, and
the Tor Project blog agree 7.15 (13 Oct 2026) is current; no 7.16 has
appeared, so the ~27 Oct cadence prediction stands unconfirmed. New detail:
the 7.12 notes record an expat 2.8.2 update fixing DSA-6404-1, where a
malicious file opened in LibreOffice, Audacity, or Git could escalate to
full Tails control and deanonymize the user; 7.15 also refreshed firmware
packages for newer graphics and Wi-Fi hardware.

R33 take: PRD reconfirms the baseline a fourth day running and records the
expat case as the concrete hostile-file-parsing threat that motivates the
isolated-parser ladder (S2) — including for Fusion's own Office/media
format readers. R33 refusal: same as R29–R32 — no OS-level claims from
inside an app. Promote with: fetched release notes + same-hardware measured
comparison (absent).

## 2. Signature 27 reconfirmed with November US window and hardware specifics

Search synthesis (2026-10-22): the Signature 27 remains the confirmed first
GrapheneOS Motorola device. New specifics: US-bound with a November arrival
window on Motorola's North American page (no exact date), Snapdragon 8 Elite
Extreme Gen 6, 200 MP telephoto + 50 MP main cameras, Bang & Olufsen-tuned
audio, seven OS upgrades plus seven years of security updates, hardware
Memory Tagging Extension (MTE) and secure-element integration; 2027 Razr
foldables follow.

R33 take: PRD extends P-GOS-1 with the November window and the MTE/secure-
element specifics — the concrete near-term hardware path for the recommended
OS controls, now dated. R33 refusal: no in-app replication claims; no claim
about devices Fusion has never run on. Promote with: Signature 27 /
GrapheneOS device receipts.

## 3. Servo 0.7.0 monthly exists alongside 0.6.0 LTS; an embed existence proof

Search synthesis (2026-10-22): Servo v0.7.0 was published 6 Oct 2026 as a
monthly release alongside the 0.6.0 LTS line (CI already inspects its AAR
headers); the LTS remains the support-covered base. Separately, the
kayfgit/browser project reports shipping Servo 0.6 as a crash-isolated
second engine — an independent existence proof that embedding is done, not
a Fusion claim. Phoronix reconfirms the no-clear disk HTTP cache detail.

R33 take: ALTERNATIVES B2 stays on 0.6.0 LTS as the candidate (support-
covered; monthlies are not the embed base) and records the 0.7.0 monthly
plus the third-party embed report as context. R33 refusal: still CANDIDATE,
still no bundling claim. Promote with: in-repo engine_intake + embed smoke
+ LICENSE-REVIEW closure.

## 4. Expert-features six-item list with removal caveats; Wi-Fi item unshipped

Search synthesis (2026-10-22): teardown detail now enumerates six "Expert
features" items — Intrusion logging, USB protection, block auto-connection
to unsecured Wi-Fi, Unknown apps, Spam filter, Suspicious links in Google
Messages — but the resource codes for the last three show signs of
temporary removal (they may not ship). The Wi-Fi item (open/WEP/OWE
networks) has NOT rolled out under Advanced Protection as of Sept 2026 and
may debut with expert features; the Advanced Protection framework itself
remains, with expert mode as manual control inside it. Related strings:
Google-Account access-log toggles for Intrusion Logging, and a fast-
charging caveat (full-rate charging may wait for unlock under USB
Protection).

R33 take: PRD extends P-A17-1/P-USB-2 with the itemized list, the removal
caveat, and the unshipped-Wi-Fi fact — the "rely on nothing" posture
hardens further, since even the optional toggles are in flux. R33 refusal:
no reliance on optional/in-flux OS toggles; no Intrusion Logging clone.
Promote with: stable releases + device receipts.

## 5. Orbot 17.9.7 still latest with enumerated changelog; Tor VPN still beta

Search synthesis (2026-10-22): Orbot 17.9.7 (tor 0.4.9.14) is still current
(Play 14 Oct, APKMirror 16 Oct, 17.9.7-RC-1 tag). The changelog is now
enumerated: redesigned Kindness Mode setup with reliable restart, Snowflake
proxy fixes, UPnP mappings surviving restarts, custom-bridge fixes including
unusual spacing, proxy-port/IPv6/app-routing fixes, Ukraine added to exit
countries, Swahili translations, and no more external-app activation
(ORB-02-013). No new Tor VPN information — it stands as beta/experimental.

R33 take: PRD reconfirms P-ANON-1/P-ANON-2 with the changelog recorded.
R33 refusal: no routing code; no code until stable + review. Promote with:
stable releases + device receipts.
