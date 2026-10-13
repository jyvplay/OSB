# Fusion Shell — Android source (work in progress)

A real Android Studio project. Open the `android/` directory (not the repo
root). It is a working desktop-style shell core; it is **not** the full v2.1
product.

## What actually runs (R13)

**Shell** — optional Home activity, first-party windows (drag, resize, tile
left/right/maximise, minimise, close), three workspaces, geometry restored on
relaunch, shelf, Ctrl+K palette. A connected secondary display shows a **live
session mirror** (titles, focus, geometry, workspace) — not an interactive
second compositor.

**Real casting (upgraded in R12)**
- **Google Cast (CA-08)**: a real, dependency-free castv2 client — TLS to :8009,
  hand-rolled protobuf `CastMessage` codec, CONNECT + heartbeat, LAUNCH the
  Default Media Receiver (CC1AD845), LOAD a media URL, PLAY/PAUSE/STOP/SEEK.
  No proprietary Cast SDK. Loopback-tested against a fake Chromecast.
- **AirPlay video-by-URL (CA-02)**: a real HTTP client — POST /play, /scrub,
  /rate, /stop, GET /playback-info. Loopback-tested. tvOS pairing not
  implemented; FairPlay mirroring not implemented.
- **DLNA (CA-10)**: SSDP discovery + AVTransport Play/Stop for an http(s) URL.
- **LocalMediaServer**: embedded HTTP server with RFC 7233 byte-range support,
  serving files under your granted SAF folder, so receivers can fetch media
  from `http://<phone>:8080/<path>`.
- Beam Hub discovers `_airplay._tcp` and `_googlecast._tcp`, resolves targets,
  and sends via the real clients.

**Storage and documents**
- SAF Files: granted tree only, browse, create folder, copy to a distinct name.
- **Delete to trash is reversible**: copy → verify → journal → delete. Restore
  writes a new document into the open folder; purge deletes forever. Files only,
  256 MB cap, app-private blobs.
- UTF-8 editor (≤2 MB) with Save-a-copy through the system picker.
- Documents window: own `.docx`/`.xlsx` text + cell scanner, `.zip` listing with
  slip/bomb guards (now tested via shared fixtures).
- PDF viewer (platform `PdfRenderer`), image probe/decode/export (JPEG/PNG/WebP),
  PSD composite preview (8-bit RGB raw + PackBits RLE).

**Messages (upgraded in R12)**
- **Real SMS**: reads inbox + sent via `Telephony.Sms`, sends SMS text via
  `SmsManager`, gated on READ_SMS / SEND_SMS runtime permissions. No SMS role,
  no MMS, no RCS, no iMessage/Matrix — stated in the UI.

**System**
- Screen recording: `MediaProjection` + `MediaRecorder` foreground service,
  MP4 to app-specific storage, video only, one consent per recording.
- Alerts: `NotificationListenerService` + SQLite log, 7-day retention, flood
  limit, wipe on disconnect. Enable it in system settings first.
- Encrypted backup: PBKDF2(210k) + AES-256-GCM export/restore of app-private
  notes. Wrong password fails closed; restore never overwrites.
- Peripherals: physical keyboard/mouse/touchpad + USB inventory, share-sheet
  out, shared-text in. No Bluetooth pairing reads.
- Accessibility state is read-only. Settings graph has undo plus a live probe.

**Other** — Keystore vault, private notes, calculator, MediaStore list,
launchable-app list, built-in shell (not a PTY), Together local model, Lens
preview, Automation durable drafts.

## What is NOT implemented

AirPlay RAOP audio streaming and FairPlay screen mirroring, Chromecast screen
mirroring, any cast receiver, interactive external compositor, Lens effects or
virtual camera, LibreOfficeKit editing, PSD layers/export, HEIC encoding,
MMS/RCS/iMessage/Matrix transports, scheduled automation, file move, benchmark
numbers. See `STATUS.md`.

## Build it on your laptop

This environment has **no Android SDK or Gradle runtime**, so no APK, sync,
unit-test run or emulator session has been executed here. Treat the first sync
as the real verification step.

1. Android Studio (Ladybug/Rabbit+), JDK 17, SDK Platform 35, Android 14+
   emulator image.
2. `File > Open` → select `android/`.
3. **The Gradle wrapper jar is not vendored** (binary files cannot be authored
   here), so `./gradlew` will not exist yet. Do one of these first:
   - In Studio: it will offer to use a local/bundled Gradle — accept **8.9**; or
   - With Gradle 8.9 installed: run `gradle wrapper --gradle-version 8.9`
     inside `android/` once, which generates `gradlew` + the wrapper jar.
4. Sync (AGP 8.7.3, Kotlin 2.0.21, Compose Compiler 2.0.21).
5. `:app:assembleDebug`, then run `app`.
6. JVM tests (cast clients, codec, JSON, SMS-independent logic, fixtures):
   `:core:windowing:test :core:index:test :core:commands:test
    :core:security:test :format:native:test :format:archive:test
    :format:ooxml:test :format:psd:test :cast:dlna:test :cast:airplay:test
    :cast:castv2:test :feature:beam:test :feature:together:test :testing:test`
7. Instrumented smoke (needs emulator): `:app:pixel6Api34DebugAndroidTest`
8. Emulator secondary display: Extended Controls → Displays → Add secondary
   display, then relaunch the app.

## Permissions actually requested

`INTERNET` (browser, DLNA, cast, media server) · `READ_MEDIA_IMAGES` (media
list) · `ACCESS_WIFI_STATE` + `CHANGE_WIFI_MULTICAST_STATE` (SSDP) ·
`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION` +
`POST_NOTIFICATIONS` (recording) · `CAMERA` (Lens preview, asked in context) ·
`READ_SMS` + `SEND_SMS` (Messages SMS tab, asked in context) · one SAF tree
grant you choose.

Not requested: `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, location,
contacts, microphone, Bluetooth, accessibility service. Notification access
stays off until you enable it.
