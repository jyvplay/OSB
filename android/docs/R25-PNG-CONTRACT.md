# Planned bounded PNG metadata copy — contract before implementation

This is an additional R25 privacy/productivity branch motivated by Tails' explicit metadata warning. It is **not** a full image anonymizer, malware-removal engine, or substitute for all original image/Office requirements. This deepens an existing substantial module; no new module-count credit is taken. Native integration remains gated separately.

## Admitted format / resource regime

Candidate kernel: original pure Kotlin kernel inside the existing **`:format:image`** owner, no platform/native decoder dependency in this kernel. Inspection found that image formats already have an owner; do not split a new module merely to inflate the census. Input<=2MiB,<=1024 chunks, non-interlaced8-bit truecolour/RGBA only, positive dimensions<=4096/side and<=4,194,304 pixels. Validate PNG signature, ASCII chunk-type bits, framing/CRC, IHDR/IEND uniqueness/order, consecutive nonempty aggregate IDAT, full zlib completion/adler check, exact inflated scanline count, valid0..4 filter markers and no trailing compressed/file bytes. Unknown critical chunks, animation/interlace, palette/grayscale/16-bit and unsupported rendering profiles are refused explicitly rather than silently converted.

Implemented retained chunk set: IHDR/IDAT/IEND plus valid tRNS for RGB and canonical sRGB/gAMA/cHRM fields. Other profiles/HDR/animation/significant-bit/background modes are refused; non-square pHYs is refused. Text/EXIF/other admitted ancillary data is removed without decoding its contents. Personal/textual/EXIF/time/unknown ancillary data may be removed, but render-affecting profile handling must not be guessed. If equivalent colour/orientation behavior cannot be justified, refuse that regime or label the exact change; do not promise all appearance preservation.

Output: a **new copy**, never the source file. Structurally revalidate the output and compare preserved critical image data. Do not return metadata values in reports; report bounded counts/types/byte sizes. Input/output hashes are integrity/linkability observations, not anonymity.

## Success predicate / proof obligation

For every admitted input x and produced y:

1. x is not modified; y is within the declared byte/dimension/chunk budgets.
2. y contains only the declared retained chunk types, with correct order/CRC; removed categories cannot be smuggled in trailing/extra zlib streams or unknown chunks.
3. IHDR and concatenated IDAT are byte-identical, and the fully validated scanline stream is unchanged. Under the specified conformant decoder/colour assumptions, decoded pixel samples are identical. This is **not** universal display-colour, orientation, printing or animation equivalence.
4. Malformed/off-regime inputs fail without producing a falsely approved copy.
5. Native picker/worker/save-copy/readback must be exercised before claiming an Android user workflow, separate from host kernel behavior.

Threshold: separate production/consumer compilation; real before/after PNG files; independent decoder/reference checks; attacks on CRC/order/length/deflate/extra stream/metadata; original-file hash unchanged. Native tests must use actual file descriptors/picker/UI where claimed. No fake native image or canned service response counts.

## Strongest attacker and limitations

The strongest attacker embeds an identity in the **visible pixels**, steganography, quantization/deflate choices, retained colour values, or a feature not admitted by this parser. This construction cannot erase that while preserving the image samples. A JPEG/Office/video cleaner is a different format contract, not automatically solved by PNG support. A metadata cleaner is not an exploit sanitizer; keep hostile parsing isolated and bounded in any Android route. SHA256 does not authenticate the source or guarantee erasure of its old copies.

Cheapest falsifiers: a valid EXIF/text-bearing image plus an independent decoder; a valid-CRC extra zlib stream; an unknown critical chunk; a claimed-clean file retaining an ancillary marker; a one-over dimension/scanline budget; a corrupted source that the output accidentally repairs; a real save-copy readback mismatch. A noncompliant native callback/provider can still block outside the pure kernel; that interface needs its own process/timeout evidence.

**Explicit presentation boundary:** encoded samples are preserved, not EXIF orientation or printing/DPI behavior. Removing EXIF or square-pixel resolution metadata can change those presentations; no claim of an anonymous or visually identity-free image. The current separately compiled host kernel passed7150 assertions and53 independent Pillow file/readback cases; native integration has not yet been run.


## Authored native route awaiting its first device execution

The existing image-format owner now contains a one-shot isolated-service memory protocol. The caller transfers a sealed read-only SharedMemory snapshot, binds a fresh isolated instance, observes the reply's real Messenger sender UID, checks the output digest and exact retained-chunk derivative, and closes its handles. The child performs actual INET-denial observation and no native image decoding. The parent never treats a UID, checksum or worker reply as generic malware/fidelity proof.

The nonexported PNG-copy Activity uses actual system pickers, off-UI provider I/O, a single process-level provider lane, an explicit NEW-document action and exact readback. A nonresponsive provider can retain that one lane; no hard provider cancellation or alias-safe new-file guarantee against a malicious provider is inferred. Recreation loses the prepared copy. The separate no-INTERNET privacy APK gains no import/export capability.

Three native cases are declared before execution: real isolated rejection/positive decoded-pixel control, actual picker/save/readback/source preservation, and cancel/recreation invalidation. The new page has a separate native frame/geometry capture group. Full suite now requires146 exact named cases, R25 readiness6 and PNG3; these additions do not inherit the earlier143 pass.

Native repair update: the first isolated bind rejected a hyphenated instance name. It now uses an alphanumeric `png`+UUID name while preserving the independent full request nonce. The next actual isolated-corruption/positive-decode case passed, including distinct UID/INET denial and Android pixel equality. The picker cases still failed in an image grid that exposed only time/category text; the next driver identifies the exact owned filename by native text **or accessibility description**, within the same10s budget, with no arbitrary first-item click or injected picker result. Three/four native action rectangles (Close added explicitly) remain measured in the new-page capture.
