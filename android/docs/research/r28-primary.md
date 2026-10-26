# R28 primary-source research (fetched 2026-10-21)

All claims below come from the fetched pages, not from memory. Each section ends
with what R28 takes from it and what it refuses.

## 1. Ladybird: independent engine, pre-alpha, no Android

Sources: https://ladybird.org/, https://github.com/LadybirdBrowser/ladybird,
https://en.wikipedia.org/wiki/Ladybird_(web_browser)

- Ladybird is a from-scratch engine (BSD-style license, 501(c)(3) nonprofit);
  official target: alpha 2026 for Linux and macOS, beta 2027, stable 2028.
- July 2026: style/layout ported C++ -> Rust, private browsing + profiles ship;
  August 2026: Twitch playback, in-engine DevTools. Pre-alpha: "developers only".
- Since 5 June 2026 the project no longer accepts public pull requests.
- No Android port exists; the roadmap names Linux/macOS only.

R28 take: none as an engine (no embeddable Android artifact, pre-alpha,
slipping dates). R28 refusal: do not claim a Ladybird route; keep the
GeckoView-production / Servo-experimental / WebView-boundary ladder.
Watch item only.

## 2. Servo 0.5/0.6-LTS: the embeddable Rust engine matures

Sources: https://servo.org/, https://en.wikipedia.org/wiki/Servo_(software),
https://www.phoronix.com/news/Servo-Embed-Crates-LTS,
https://byteiota.com/servo-0-1-0-ships-on-crates-io-embeddable-rust-browser/

- Servo 0.5.0 shipped 31 Aug 2026; v0.6.0 (LTS) is latest; 0.1.0 published to
  crates.io 13 Apr 2026 with a stable embedding API + 9-month LTS branches.
- servo.org advertises a WebView API for embedding; Servo ships inside Tauri
  as a system-webview alternative; Android support extends down to v10.
- License MPL-2.0; Linux Foundation Europe; Android Servoshell exists (0.2+).

R28 take: Servo graduates from "watched experiment" to "candidate embeddable
engine for a bounded reader surface", behind GeckoView. R28 refusal: no Servo
AAR intake yet (needs engine_intake inspection + license/SBOM closure first);
no rendering claim until a device run.

## 3. GeckoView 158.x: production engine, Mozilla Maven

Source: mvnrepository + third-party pinning reports (GeckoView
158.0.20261007201254, minSdk 26, Mozilla Maven).

R28 take: GeckoView remains the only production independent engine with a
pinned Android AAR. R28 refusal: ~100MB+ engine weight and Gecko update
cadence still unaccepted for bundling; handoff + boundary tests only.

## 4. Tor in 2026: C Tor maintenance, Arti 2.7, Tor VPN beta, Tails 7.15

Sources: https://gitlab.torproject.org/tpo/core/arti,
https://blog.torproject.org/tor-vpn-beta/,
https://blog.torproject.org/arti_2_7_0_released/,
https://blog.torproject.org/new-release-tails-7_14/,
https://en.wikipedia.org/wiki/Tails_(operating_system)

- Arti 2.7.0 (1 Oct 2026): full-featured Rust client (proxy, onion services,
  anticensorship, congestion control + Counter-Galois-Onion always on); relay
  support still in progress; "suitable for general client usage".
- Tor VPN for Android (Arti-based) in beta since Sept 2025, marked
  EXPERIMENTAL, "not suited to high-risk situations"; reproducible builds,
  F-Droid, Cure53 audit completed (Apr 2026). Orbot = C Tor, maintenance mode.
- Tails 7.15 (13 Oct 2026, current): Tor Browser 15.0.25, Tor 0.4.9.14,
  kernel 6.12.111, 2-week cadence; Persistent Storage preserved on upgrade.

R28 take: Tor VPN beta becomes the documented next handoff candidate AFTER
Orbot (experimental label inherited, never softened); Arti client maturity
noted for future embedding study. R28 refusal: no in-app Tor routing, no
VPN-service code, no Tails-equivalence claim; the "lighter than Tails" target
is reframed as a companion profile with measured receipts (see PRD).

## 5. Android 16/17 platform defenses: USB Protection + Intrusion Logging

Sources: https://blog.google/security/android-advanced-protection-updates/,
https://support.google.com/android/answer/16778864,
https://support.google.com/android/answer/16927813,
https://securitylab.amnesty.org/latest/2026/05/ (Amnesty),
https://grapheneos.org/features

- Advanced Protection USB Protection: new USB connections default to
  charging-only while locked (Pixel 6+ on A16; select A17 devices); existing
  connections persist; charging unaffected.
- Intrusion Logging (opt-in, Pixel A16+ incl. Dec update rollout): daily
  E2EE forensic logs (app activity, installs, network, USB transfers, cert
  changes, lock/unlock) to the user's cloud, 12-month retention, developed
  with Amnesty/RSF for consensual post-compromise analysis.
- GrapheneOS: 5-mode USB-C port control (hardware data-line disable, default
  charging-only-when-locked), per-app network permission; A16 port needed
  partial USB reimplementation.

R28 take: Fusion recommends (docs) enabling USB Protection + Intrusion Logging
where available; UsbWatchReceiver provides detection/alerting INSIDE the app
for the unlocked-attached case the OS leaves to the user. R28 refusal: no
claim to block hardware the OS allows; no forensic-logging of our own (that
would duplicate Intrusion Logging badly); passive loggers stay undetectable.

## 6. Office 2026: Collabora MPL vs ONLYOFFICE AGPL/proprietary-mobile split

Sources: https://en.wikipedia.org/wiki/Collabora_Online,
https://www.onlyoffice.com/docs, https://www.onlyoffice.com/download-desktop

- Collabora Online 26.04.4.3 (9 Oct 2026), MPL-2.0, LibreOffice-based, with
  Android/iOS offline apps; ODF-native, good OOXML compat.
- ONLYOFFICE Docs 10.0 (Oct 2026) core under AGPLv3 BUT the Android Documents
  app 9.4.1 is PROPRIETARY licensed. AGPLv3 server + proprietary mobile.

R28 take: Collabora stays the only office route compatible with the
permissive-bundling constraint; ONLYOFFICE Android is EXCLUDED from bundling
(proprietary) and Docs-server coupling is AGPL (network-copyleft affects
self-host operators, not our APK, but collaboration truthfully needs a
server). R28 refusal: no office engine bundled; values-only XLSX/TSV kernels
remain the in-app ceiling.

## 7. Privacy Sandbox retired (Oct 2025)

Source: https://usercentrics.com/knowledge-hub/what-is-google-privacy-sandbox/

- Google retired the remaining Privacy Sandbox APIs (Attribution, Topics,
  Protected Audience) for Chrome AND Android by Oct 2025.

R28 take: the SDK-Runtime sandbox path is treated as deprecated; the sandbox
ladder is now isolatedProcess + explicit AIDL/ContentProvider IPC (documented
alternative), with no Privacy Sandbox dependency. R28 refusal: no claim that
isolatedProcess equals a full sandbox audit; renderer isolation still needs
per-surface threat review.
