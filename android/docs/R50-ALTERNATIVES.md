# R50 Architectural Alternatives — Overcoming Host Sandbox & Non-Android-Studio Constraints

## 1. Spreadsheet Matrix Linear Algebra & 3D Cross-Sheet Spans (`:format:ooxml`)

- **Option A (Rejected — External Apache Commons Math / EJML JARs)**: Adds third-party binary dependencies and mismatches Excel error semantics (`#NUM!` on singular matrices, `#VALUE!` on shape mismatch, `#REF!` on missing 3D sheet endpoints).
- **Option B (Selected — Clean-Room In-Module Gauss-Jordan + Partial-Pivoting LU + `MultiSheetBook` 3D Span Expander)**:
  - Implemented directly inside `FormulaEval.kt` (`168` functions), `SpillSheetEngine.kt` (`29` dynamic spill ops), and `MultiSheetBook.kt` (`Sheet1:Sheet4!B2:C5` sheet-index interval expansion before formula evaluation).
  - Zero external runtime dependencies; exact differential verification against Python 3.11 `numpy`-equivalent pure-Python exact rational/float64 linear algebra and `openpyxl 3.1.5`.

## 2. Fusion Lens Video/Audio Pipeline Without Android GPU/Camera2/AudioRecord (`:feature:lens`)

- **Option A (Rejected — Empty Stubs Waiting for OpenGL ES / Vulkan / MediaPipe)**: Leaves `LN-01..LN-11`, `LN-13`, `LN-14`, `LN-20..LN-23` unimplemented on host and untestable without physical camera hardware.
- **Option B (Selected — Pure-JVM Reference Framebuffer & PCM DSP Kernels + Compose UI Pipeline Controls)**:
  - `Fsr1EasuRcasUpscaler.kt` executes real MIT `FSR 1.0` 12-tap `EASU` + 5-tap `RCAS` over `RGB` floating-point/8-bit rasters and emits verifiable `.ppm` images.
  - `Cube3dLutColorGrader.kt` parses real Adobe `.cube` 3D LUT files and executes exact 6-simplex tetrahedral color interpolation + radial vignette.
  - `RnNoiseAec3AudioFilter.kt` executes real frame-based `NLMS` acoustic echo cancellation with double-talk detection + spectral band noise suppression over 16-bit PCM frames.
  - `AutoFrameGazeCropPlanner.kt`, `PresenterSceneCompositor.kt`, and `LensPresetRouteRouter.kt` compute deterministic crop rectangles, segmented person/slide/ink composites, and per-consumer camera/mic routing policies, while honestly keeping GPU timing/thermal Class-A claims at `0/50` until device profiling.

## 3. Permissive Messaging, Remote Desktop, & Media Sync (`:feature:messages`, `:feature:continuity`, `:feature:together`)

- **Option A (Rejected — Bundling AGPL Signal / GPL TightVNC / Native SSH Binaries)**: Violates the repository's strict permissive-only license gate (`MS-06` explicitly forbids AGPL Signal clients).
- **Option B (Selected — Clean-Room Protocol Framing & State Machines)**:
  - `MatrixXmppProtocolEngine.kt` (`MS-06`) implements clean-room Matrix Client-Server v1.11 JSON sync/send + Megolm room-encryption policy checks and `RFC 6120` XMPP stream/message stanza codecs under `Apache-2.0`, plus `BlueBubblesRestPacketCodec.kt` (`MS-01`) with explicit user-owned Mac/Apple-ID capability disclosure.
  - `RfbVncHandshakeClient.kt` (`CT-05`) + `SshTransportKexFrameCodec.kt` (`TD-03`) implement `RFC 6143` RFB `003.008` security negotiation/FramebufferUpdateRequest framing and `RFC 4253` SSH-2.0 binary packet/curve25519-sha256 KEX framing with downgrade rejection.
  - `SyncplayProtocolRoomEngine.kt` (`CA-11`) implements clean-room Syncplay newline-JSON `Hello`/`State`/`Set` drift-compensated own-copy media playback rooms.

## 4. True Byte-Scrubbing Redaction & Recursive Metadata Cleaning (`:format:pdf`, `:privacy`)

- **Option A (Rejected — Visual Overlay Redaction / Top-Level ZIP Property Wipe)**: Leaves underlying PDF `/Tj` strings and embedded `word/media/*.jpg` EXIF GPS tags intact for forensic extraction.
- **Option B (Selected — `PdfIncrementalSaveRedactor` + `Mat2RecursiveMetadataScrubber`)**:
  - Rewrites PDF content streams to excise redacted text tokens, zeros `/Info` + `/Metadata` XMP streams, and recursively unpacks/cleans/repacks nested images inside Office ZIP containers.
