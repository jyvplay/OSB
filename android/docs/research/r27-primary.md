# R27 primary-source research (fetched 2026-10-21)

All claims below come from the fetched pages, not from memory. Each section ends
with what R27 takes from it and what it refuses.

## 1. Tails Tor enforcement (firewall model)

Source: https://tails.net/contribute/design/Tor_enforcement/

- Tails routes almost all TCP through Tor's TransPort (transparent proxy port)
  and resolves DNS through Tor's DNSPort; the firewall DEFAULT-DROPS outbound
  and only allowlists loopback, Tor itself, and a small local-service set.
- The ruleset is generated (ferm/iptables heritage) and ships in the OS image;
  enforcement lives in kernel netfilter, not in an app sandbox.
- IPv6, UDP (outside narrow exceptions) and ICMP are dropped; automapped
  127.192/10 + .onion handling is part of the design.

R27 take: FirewallPolicy emits a fail-closed nftables-shaped PROPOSAL (default
drop, loopback/local allowlist, TransPort/DNSPort redirect-shaped rules,
explicit IPv6/UDP/ICMP drops) as review-only text. R27 refusal: no rule is
applied, no root/nftables path is claimed, and nothing here is anonymity
evidence — enforcement requires OS-level integration Tails does in its image.

## 2. Podcast enclosures (RSS 2.0 + PSP-1)

Sources: https://github.com/Podcast-Standards-Project/PSP-1-Podcast-RSS-Specification,
https://podcasters.apple.com/support/823-podcast-requirements

- PSP-1 builds on RSS 2.0: each episode is an `<item>` with a REQUIRED
  `<enclosure url length type>`; `type` is a MIME audio/video value.
- Apple requires the enclosure URL reachable over HTTPS with a supported audio
  type; iTunes namespace tags (`itunes:*`) are de-facto metadata, not transport.
- Atom carries the same payload as `<link rel="enclosure" href type length>`.

R27 take: PodcastFeed accepts exactly RSS 2.0 items with audio/video
enclosures and Atom entries with enclosure links; non-media enclosures are
counted and skipped. R27 refusal: no fetching, no download manager, no
episode-artwork or namespace harvesting beyond title/link.

## 3. Cron dialects (Vixie vs Quartz)

Sources: https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html,
cron expression references (crontab guru / CronBase comparisons)

- Unix/Vixie cron is 5 fields (minute hour dom month dow, DOW 0/7 = Sunday);
  when BOTH dom and dow are restricted, Vixie fires on EITHER (OR semantics).
- Quartz is 6-7 fields with a leading seconds field, DOW 1 = Sunday, and
  requires one of day-of-month/day-of-week to be `?` (no OR union).

R27 take: CronSpec implements strict Vixie 5-field semantics including the OR
union and 0/7 Sunday alias, computing next-fire in UTC. R27 refusal: no Quartz
fields, no `?`/`L`/`W`/`#` extensions, no scheduler/daemon — parsing and
next-fire only.

## 4. Markdown (CommonMark 0.31.2 core + GFM tables)

Sources: https://spec.commonmark.org/0.31.2/, https://github.github.com/gfm/

- CommonMark core defines ATX/Setext headings, fenced/indented code, block
  quotes, ordered/bullet lists, thematic breaks, paragraphs; inline HTML is
  passed through by the reference implementation.
- Tables, strikethrough, task lists and autolinks are GFM EXTENSIONS, not
  core; a GFM table needs a header row, a delimiter row of matching width, and
  same-width body rows.

R27 take: MarkdownDoc covers core blocks plus the GFM table extension with
width-checked delimiters/rows; raw HTML is stripped and COUNTED (fail-visible,
not passed through). R27 refusal: no other GFM extensions, no HTML rendering,
no reference-link resolution beyond literal text.

## 5. Material 3 type scale (15 styles)

Source: https://developer.android.com/develop/ui/compose/designsystems/material3
(+ M3 typography references; M3 Expressive current as of Android 16)

- The M3 baseline scale is 15 styles: Display L/M/S (57/64, 45/52, 36/44),
  Headline L/M/S (32/40, 28/36, 24/32), Title L/M/S (22/28, 16/24, 14/20),
  Body L/M/S (16/24, 14/20, 12/16), Label L/M/S (14/20, 12/16, 11/16),
  sizes in sp with the listed line heights.

R27 take: TypeScale embeds the full 15-style table and the host + Python
gates verify every (size, line-height) pair byte-for-byte. R27 refusal: no
font loading, no rendered-text measurement, no dynamic-type substitution —
values only, plus a phi editorial ladder for the silver Art theme.
